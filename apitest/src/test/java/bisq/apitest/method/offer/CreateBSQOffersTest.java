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

package bisq.apitest.method.offer;

import bisq.proto.grpc.OfferInfo;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.cli.TableFormat.formatBalancesTbls;
import static bisq.cli.TableFormat.formatOfferTable;
import static bisq.core.btc.wallet.Restrictions.getDefaultBuyerSecurityDepositAsPercent;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static protobuf.OfferPayload.Direction.BUY;
import static protobuf.OfferPayload.Direction.SELL;

@Disabled
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CreateBSQOffersTest extends AbstractOfferTest {

    private static final String MAKER_FEE_CURRENCY_CODE = BSQ;

    @BeforeAll
    public static void setUp() {
        AbstractOfferTest.setUp();
        createBsqPaymentAccounts();
    }

    @Test
    @Order(1)
    public void testCreateBuy1BTCFor20KBSQOffer() {
        // Remember alt coin trades are BTC trades.  When placing an offer, you are
        // offering to buy or sell BTC, not BSQ, XMR, etc.  In this test case,
        // Alice places an offer to BUY BTC with BSQ.
        var newOffer = aliceClient.createFixedPricedOffer(BUY.name(),
                BSQ,
                100_000_000L,
                100_000_000L,
                "0.00005",   // FIXED PRICE IN BTC (satoshis) FOR 1 BSQ
                getDefaultBuyerSecurityDepositAsPercent(),
                alicesBsqAcct.getId(),
                MAKER_FEE_CURRENCY_CODE);
        log.info("Sell BSQ (Buy BTC) OFFER:\n{}", formatOfferTable(singletonList(newOffer), BSQ));
        String newOfferId = newOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals(BUY.name(), newOffer.getDirection());
        assertFalse(newOffer.getUseMarketBasedPrice());
        assertEquals(5_000, newOffer.getPrice());
        assertEquals(100_000_000L, newOffer.getAmount());
        assertEquals(100_000_000L, newOffer.getMinAmount());
        assertEquals(15_000_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(alicesBsqAcct.getId(), newOffer.getPaymentAccountId());
        assertEquals(BSQ, newOffer.getBaseCurrencyCode());
        assertEquals(BTC, newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());

        genBtcBlockAndWaitForOfferPreparation();

        newOffer = aliceClient.getMyOffer(newOfferId);
        assertEquals(newOfferId, newOffer.getId());
        assertEquals(BUY.name(), newOffer.getDirection());
        assertFalse(newOffer.getUseMarketBasedPrice());
        assertEquals(5_000, newOffer.getPrice());
        assertEquals(100_000_000L, newOffer.getAmount());
        assertEquals(100_000_000L, newOffer.getMinAmount());
        assertEquals(15_000_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(alicesBsqAcct.getId(), newOffer.getPaymentAccountId());
        assertEquals(BSQ, newOffer.getBaseCurrencyCode());
        assertEquals(BTC, newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());
    }

    @Test
    @Order(2)
    public void testCreateSell1BTCFor20KBSQOffer() {
        // Alice places an offer to SELL BTC for BSQ.
        var newOffer = aliceClient.createFixedPricedOffer(SELL.name(),
                BSQ,
                100_000_000L,
                100_000_000L,
                "0.00005",   // FIXED PRICE IN BTC (satoshis) FOR 1 BSQ
                getDefaultBuyerSecurityDepositAsPercent(),
                alicesBsqAcct.getId(),
                MAKER_FEE_CURRENCY_CODE);
        log.info("SELL 20K BSQ OFFER:\n{}", formatOfferTable(singletonList(newOffer), BSQ));
        String newOfferId = newOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals(SELL.name(), newOffer.getDirection());
        assertFalse(newOffer.getUseMarketBasedPrice());
        assertEquals(5_000, newOffer.getPrice());
        assertEquals(100_000_000L, newOffer.getAmount());
        assertEquals(100_000_000L, newOffer.getMinAmount());
        assertEquals(15_000_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(alicesBsqAcct.getId(), newOffer.getPaymentAccountId());
        assertEquals(BSQ, newOffer.getBaseCurrencyCode());
        assertEquals(BTC, newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());

        genBtcBlockAndWaitForOfferPreparation();

        newOffer = aliceClient.getMyOffer(newOfferId);
        assertEquals(newOfferId, newOffer.getId());
        assertEquals(SELL.name(), newOffer.getDirection());
        assertFalse(newOffer.getUseMarketBasedPrice());
        assertEquals(5_000, newOffer.getPrice());
        assertEquals(100_000_000L, newOffer.getAmount());
        assertEquals(100_000_000L, newOffer.getMinAmount());
        assertEquals(15_000_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(alicesBsqAcct.getId(), newOffer.getPaymentAccountId());
        assertEquals(BSQ, newOffer.getBaseCurrencyCode());
        assertEquals(BTC, newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());
    }

    @Test
    @Order(3)
    public void testCreateBuyBTCWith1To2KBSQOffer() {
        // Alice places an offer to BUY 0.05 - 0.10 BTC with BSQ.
        var newOffer = aliceClient.createFixedPricedOffer(BUY.name(),
                BSQ,
                10_000_000L,
                5_000_000L,
                "0.00005",   // FIXED PRICE IN BTC sats FOR 1 BSQ
                getDefaultBuyerSecurityDepositAsPercent(),
                alicesBsqAcct.getId(),
                MAKER_FEE_CURRENCY_CODE);
        log.info("BUY 1-2K BSQ OFFER:\n{}", formatOfferTable(singletonList(newOffer), BSQ));
        String newOfferId = newOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals(BUY.name(), newOffer.getDirection());
        assertFalse(newOffer.getUseMarketBasedPrice());
        assertEquals(5_000, newOffer.getPrice());
        assertEquals(10_000_000L, newOffer.getAmount());
        assertEquals(5_000_000L, newOffer.getMinAmount());
        assertEquals(1_500_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(alicesBsqAcct.getId(), newOffer.getPaymentAccountId());
        assertEquals(BSQ, newOffer.getBaseCurrencyCode());
        assertEquals(BTC, newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());

        genBtcBlockAndWaitForOfferPreparation();

        newOffer = aliceClient.getMyOffer(newOfferId);
        assertEquals(newOfferId, newOffer.getId());
        assertEquals(BUY.name(), newOffer.getDirection());
        assertFalse(newOffer.getUseMarketBasedPrice());
        assertEquals(5_000, newOffer.getPrice());
        assertEquals(10_000_000L, newOffer.getAmount());
        assertEquals(5_000_000L, newOffer.getMinAmount());
        assertEquals(1_500_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(alicesBsqAcct.getId(), newOffer.getPaymentAccountId());
        assertEquals(BSQ, newOffer.getBaseCurrencyCode());
        assertEquals(BTC, newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());
    }

    @Test
    @Order(4)
    public void testCreateSellBTCFor5To10KBSQOffer() {
        // Alice places an offer to SELL 0.25 - 0.50 BTC for BSQ.
        var newOffer = aliceClient.createFixedPricedOffer(SELL.name(),
                BSQ,
                50_000_000L,
                25_000_000L,
                "0.00005",   // FIXED PRICE IN BTC sats FOR 1 BSQ
                getDefaultBuyerSecurityDepositAsPercent(),
                alicesBsqAcct.getId(),
                MAKER_FEE_CURRENCY_CODE);
        log.info("SELL 5-10K BSQ OFFER:\n{}", formatOfferTable(singletonList(newOffer), BSQ));
        String newOfferId = newOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals(SELL.name(), newOffer.getDirection());
        assertFalse(newOffer.getUseMarketBasedPrice());
        assertEquals(5_000, newOffer.getPrice());
        assertEquals(50_000_000L, newOffer.getAmount());
        assertEquals(25_000_000L, newOffer.getMinAmount());
        assertEquals(7_500_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(alicesBsqAcct.getId(), newOffer.getPaymentAccountId());
        assertEquals(BSQ, newOffer.getBaseCurrencyCode());
        assertEquals(BTC, newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());

        genBtcBlockAndWaitForOfferPreparation();

        newOffer = aliceClient.getMyOffer(newOfferId);
        assertEquals(newOfferId, newOffer.getId());
        assertEquals(SELL.name(), newOffer.getDirection());
        assertFalse(newOffer.getUseMarketBasedPrice());
        assertEquals(5_000, newOffer.getPrice());
        assertEquals(50_000_000L, newOffer.getAmount());
        assertEquals(25_000_000L, newOffer.getMinAmount());
        assertEquals(7_500_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(alicesBsqAcct.getId(), newOffer.getPaymentAccountId());
        assertEquals(BSQ, newOffer.getBaseCurrencyCode());
        assertEquals(BTC, newOffer.getCounterCurrencyCode());
        assertFalse(newOffer.getIsCurrencyForMakerFeeBtc());
    }

    @Test
    @Order(4)
    public void testGetAllMyBsqOffers() {
        List<OfferInfo> offers = aliceClient.getMyBsqOffersSortedByDate();
        log.info("ALL ALICE'S BSQ OFFERS:\n{}", formatOfferTable(offers, BSQ));
        assertEquals(4, offers.size());
        log.info("ALICE'S BALANCES\n{}", formatBalancesTbls(aliceClient.getBalances()));
    }

    @Test
    @Order(5)
    public void testGetAvailableBsqOffers() {
        List<OfferInfo> offers = bobClient.getBsqOffersSortedByDate();
        log.info("ALL BOB'S AVAILABLE BSQ OFFERS:\n{}", formatOfferTable(offers, BSQ));
        assertEquals(4, offers.size());
        log.info("BOB'S BALANCES\n{}", formatBalancesTbls(bobClient.getBalances()));
    }

    @Test
    @Order(6)
    public void testBreakpoint() {
        log.debug("hit me");
    }

    private void genBtcBlockAndWaitForOfferPreparation() {
        // Extra time is needed for the OfferUtils#isBsqForMakerFeeAvailable, which
        // can sometimes return an incorrect false value if the BsqWallet's
        // available confirmed balance is temporarily = zero during BSQ offer prep.
        genBtcBlocksThenWait(1, 5000);
    }
}
