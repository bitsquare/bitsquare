/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.apitest.method.trade;

import bisq.core.payment.PaymentAccount;

import bisq.proto.grpc.TradeInfo;

import io.grpc.StatusRuntimeException;

import java.util.function.Predicate;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.config.ApiTestConfig.BTC;
import static bisq.cli.TableFormat.formatBalancesTbls;
import static bisq.core.btc.wallet.Restrictions.getDefaultBuyerSecurityDepositAsPercent;
import static bisq.core.trade.Trade.Phase.DEPOSIT_CONFIRMED;
import static bisq.core.trade.Trade.Phase.FIAT_SENT;
import static bisq.core.trade.Trade.Phase.PAYOUT_PUBLISHED;
import static bisq.core.trade.Trade.Phase.WITHDRAWN;
import static bisq.core.trade.Trade.State.*;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static protobuf.Offer.State.OFFER_FEE_PAID;
import static protobuf.OfferPayload.Direction.SELL;
import static protobuf.OpenOffer.State.AVAILABLE;

@Disabled
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TakeSellBTCOfferTest extends AbstractTradeTest {

    // Alice is maker/seller, Bob is taker/buyer.

    // Maker and Taker fees are in BTC.
    private static final String TRADE_FEE_CURRENCY_CODE = BTC;

    private static final String WITHDRAWAL_TX_MEMO = "Bob's trade withdrawal";

    @Test
    @Order(1)
    public void testTakeAlicesSellOffer(final TestInfo testInfo) {
        try {
            PaymentAccount alicesUsdAccount = createDummyF2FAccount(aliceClient, "US");
            var alicesOffer = aliceClient.createMarketBasedPricedOffer(SELL.name(),
                    "usd",
                    12_500_000L,
                    12_500_000L, // min-amount = amount
                    0.00,
                    getDefaultBuyerSecurityDepositAsPercent(),
                    alicesUsdAccount.getId(),
                    TRADE_FEE_CURRENCY_CODE,
                    NO_TRIGGER_PRICE);
            var offerId = alicesOffer.getId();
            assertTrue(alicesOffer.getIsCurrencyForMakerFeeBtc());

            // Wait for Alice's AddToOfferBook task.
            // Wait times vary;  my logs show >= 2 second delay, but taking sell offers
            // seems to require more time to prepare.
            sleep(3000); // TODO loop instead of hard code wait time
            var alicesUsdOffers = aliceClient.getMyOffersSortedByDate(SELL.name(), "usd");
            assertEquals(1, alicesUsdOffers.size());

            PaymentAccount bobsUsdAccount = createDummyF2FAccount(bobClient, "US");
            var trade = takeAlicesOffer(offerId, bobsUsdAccount.getId(), TRADE_FEE_CURRENCY_CODE);
            assertNotNull(trade);
            assertEquals(offerId, trade.getTradeId());
            assertTrue(trade.getIsCurrencyForTakerFeeBtc());
            // Cache the trade id for the other tests.
            tradeId = trade.getTradeId();

            genBtcBlocksThenWait(1, 4000);
            var takeableUsdOffers = bobClient.getOffersSortedByDate(SELL.name(), "usd");
            assertEquals(0, takeableUsdOffers.size());

            genBtcBlocksThenWait(1, 2500);

            for (int i = 1; i <= maxTradeStateAndPhaseChecks.get(); i++) {
                trade = bobClient.getTrade(trade.getTradeId());

                if (!trade.getIsDepositConfirmed()) {
                    log.warn("Bob still waiting on trade {} tx {}: DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN, attempt # {}",
                            trade.getShortId(),
                            trade.getDepositTxId(),
                            i);
                    genBtcBlocksThenWait(1, 4000);
                    continue;
                } else {
                    EXPECTED_PROTOCOL_STATUS.setState(DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN)
                            .setPhase(DEPOSIT_CONFIRMED)
                            .setDepositPublished(true)
                            .setDepositConfirmed(true);
                    verifyExpectedProtocolStatus(trade);
                    logTrade(log, testInfo, "Bob's view after deposit is confirmed", trade, true);
                    break;
                }
            }

            if (!trade.getIsDepositConfirmed()) {
                fail(format("INVALID_PHASE for Bob's trade %s in STATE=%s PHASE=%s, deposit tx was never confirmed.",
                        trade.getShortId(),
                        trade.getState(),
                        trade.getPhase()));
            }

        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(2)
    public void testBobsConfirmPaymentStarted(final TestInfo testInfo) {
        try {
            var trade = bobClient.getTrade(tradeId);

            Predicate<TradeInfo> tradeStateAndPhaseCorrect = (t) ->
                    t.getState().equals(DEPOSIT_CONFIRMED_IN_BLOCK_CHAIN.name()) && t.getPhase().equals(DEPOSIT_CONFIRMED.name());
            for (int i = 1; i <= maxTradeStateAndPhaseChecks.get(); i++) {
                if (!tradeStateAndPhaseCorrect.test(trade)) {
                    log.warn("INVALID_PHASE for Bob's trade {} in STATE={} PHASE={}, cannot confirm payment started yet.",
                            trade.getShortId(),
                            trade.getState(),
                            trade.getPhase());
                    // fail("Bad trade state and phase.");
                    sleep(1000 * 10);
                    trade = bobClient.getTrade(tradeId);
                    continue;
                } else {
                    break;
                }
            }

            if (!tradeStateAndPhaseCorrect.test(trade)) {
                fail(format("INVALID_PHASE for Bob's trade %s in STATE=%s PHASE=%s, could not confirm payment started.",
                        trade.getShortId(),
                        trade.getState(),
                        trade.getPhase()));
            }

            bobClient.confirmPaymentStarted(tradeId);
            sleep(6000);

            for (int i = 1; i <= maxTradeStateAndPhaseChecks.get(); i++) {
                trade = bobClient.getTrade(tradeId);

                if (!trade.getIsFiatSent()) {
                    log.warn("Bob still waiting for trade {} BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG, attempt # {}",
                            trade.getShortId(),
                            i);
                    sleep(5000);
                    continue;
                } else {
                    // Note: offer.state == available
                    assertEquals(AVAILABLE.name(), trade.getOffer().getState());
                    EXPECTED_PROTOCOL_STATUS.setState(BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG)
                            .setPhase(FIAT_SENT)
                            .setFiatSent(true);
                    verifyExpectedProtocolStatus(trade);
                    logTrade(log, testInfo, "Bob's view after confirming fiat payment sent", trade);
                    break;
                }
            }
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(3)
    public void testAlicesConfirmPaymentReceived(final TestInfo testInfo) {
        try {
            var trade = aliceClient.getTrade(tradeId);

            Predicate<TradeInfo> tradeStateAndPhaseCorrect = (t) ->
                    t.getState().equals(SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG.name())
                            && (t.getPhase().equals(PAYOUT_PUBLISHED.name()) || t.getPhase().equals(FIAT_SENT.name()));
            for (int i = 1; i <= maxTradeStateAndPhaseChecks.get(); i++) {
                if (!tradeStateAndPhaseCorrect.test(trade)) {
                    log.warn("INVALID_PHASE for Alice's trade {} in STATE={} PHASE={}, cannot confirm payment received yet.",
                            trade.getShortId(),
                            trade.getState(),
                            trade.getPhase());
                    // fail("Bad trade state and phase.");
                    sleep(1000 * 10);
                    trade = aliceClient.getTrade(tradeId);
                    continue;
                } else {
                    break;
                }
            }

            if (!tradeStateAndPhaseCorrect.test(trade)) {
                fail(format("INVALID_PHASE for Alice's trade %s in STATE=%s PHASE=%s, could not confirm payment received.",
                        trade.getShortId(),
                        trade.getState(),
                        trade.getPhase()));
            }

            aliceClient.confirmPaymentReceived(trade.getTradeId());
            sleep(3000);

            trade = aliceClient.getTrade(tradeId);
            assertEquals(OFFER_FEE_PAID.name(), trade.getOffer().getState());
            EXPECTED_PROTOCOL_STATUS.setState(SELLER_SAW_ARRIVED_PAYOUT_TX_PUBLISHED_MSG)
                    .setPhase(PAYOUT_PUBLISHED)
                    .setPayoutPublished(true)
                    .setFiatReceived(true);
            verifyExpectedProtocolStatus(trade);
            logTrade(log, testInfo, "Alice's view after confirming fiat payment received", trade);
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }

    @Test
    @Order(4)
    public void testBobsBtcWithdrawalToExternalAddress(final TestInfo testInfo) {
        try {
            genBtcBlocksThenWait(1, 1000);

            var trade = bobClient.getTrade(tradeId);
            logTrade(log, testInfo, "Bob's view before withdrawing funds to external wallet", trade);

            String toAddress = bitcoinCli.getNewBtcAddress();
            bobClient.withdrawFunds(tradeId, toAddress, WITHDRAWAL_TX_MEMO);

            genBtcBlocksThenWait(1, 1000);

            trade = bobClient.getTrade(tradeId);
            EXPECTED_PROTOCOL_STATUS.setState(WITHDRAW_COMPLETED)
                    .setPhase(WITHDRAWN)
                    .setWithdrawn(true);
            verifyExpectedProtocolStatus(trade);
            logTrade(log, testInfo, "Bob's view after withdrawing BTC funds to external wallet", trade);

            logTrade(log, testInfo, "Alice's Maker/Buyer View (Done)", aliceClient.getTrade(tradeId), true);
            logTrade(log, testInfo, "Bob's Taker/Seller View (Done)", bobClient.getTrade(tradeId), true);

            var alicesBalances = aliceClient.getBalances();
            log.info("{} Alice's Current Balance:\n{}",
                    testName(testInfo),
                    formatBalancesTbls(alicesBalances));
            var bobsBalances = bobClient.getBalances();
            log.info("{} Bob's Current Balance:\n{}",
                    testName(testInfo),
                    formatBalancesTbls(bobsBalances));
        } catch (StatusRuntimeException e) {
            fail(e);
        }
    }
}
