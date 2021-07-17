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

package bisq.apitest.scenario;

import bisq.core.payment.PaymentAccount;

import bisq.proto.grpc.OfferInfo;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;

import static bisq.apitest.config.ApiTestConfig.BTC;
import static bisq.cli.CurrencyFormat.formatPrice;
import static bisq.core.btc.wallet.Restrictions.getDefaultBuyerSecurityDepositAsPercent;
import static java.lang.System.getenv;
import static org.junit.jupiter.api.Assertions.fail;
import static protobuf.OfferPayload.Direction.BUY;
import static protobuf.OfferPayload.Direction.SELL;



import bisq.apitest.method.offer.AbstractOfferTest;

/**
 * Used to verify trigger based, automatic offer deactivation works.
 * Disabled by default.
 * Set ENV or IDE-ENV LONG_RUNNING_OFFER_DEACTIVATION_TEST_ENABLED=true to run.
 */
@EnabledIf("envLongRunningTestEnabled")
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LongRunningOfferDeactivationTest extends AbstractOfferTest {

    private static final int MAX_ITERATIONS = 500;

    @Test
    @Order(1)
    public void testSellOfferAutoDisable(final TestInfo testInfo) {
        PaymentAccount paymentAcct = createDummyF2FAccount(aliceClient, "US");
        double mktPriceAsDouble = aliceClient.getBtcPrice("USD");
        long triggerPrice = calcPriceAsLong.apply(mktPriceAsDouble, -50.0000);
        log.info("Current USD mkt price = {}  Trigger Price = {}", mktPriceAsDouble, formatPrice(triggerPrice));
        OfferInfo offer = aliceClient.createMarketBasedPricedOffer(SELL.name(),
                "USD",
                1_000_000,
                1_000_000,
                0.00,
                getDefaultBuyerSecurityDepositAsPercent(),
                paymentAcct.getId(),
                BTC,
                triggerPrice);
        log.info("SELL offer {} created with margin based price {}.",
                offer.getId(),
                formatPrice(offer.getPrice()));
        genBtcBlocksThenWait(1, 2500);  // Wait for offer book entry.

        offer = aliceClient.getMyOffer(offer.getId()); // Offer has trigger price now.
        log.info("SELL offer should be automatically disabled when mkt price falls below {}.",
                formatPrice(offer.getTriggerPrice()));

        int numIterations = 0;
        while (++numIterations < MAX_ITERATIONS) {
            offer = aliceClient.getMyOffer(offer.getId());

            var mktPrice = aliceClient.getBtcPrice("USD");
            if (offer.getIsActivated()) {
                log.info("Offer still enabled at mkt price {} > {} trigger price",
                        mktPrice,
                        formatPrice(offer.getTriggerPrice()));
                sleep(1000 * 60); // 60s
            } else {
                log.info("Successful test completion after offer disabled at mkt price {} < {} trigger price.",
                        mktPrice,
                        formatPrice(offer.getTriggerPrice()));
                break;
            }
            if (numIterations == MAX_ITERATIONS)
                fail("Offer never disabled");

            genBtcBlocksThenWait(1, 0);
        }
    }

    @Test
    @Order(2)
    public void testBuyOfferAutoDisable(final TestInfo testInfo) {
        PaymentAccount paymentAcct = createDummyF2FAccount(aliceClient, "US");
        double mktPriceAsDouble = aliceClient.getBtcPrice("USD");
        long triggerPrice = calcPriceAsLong.apply(mktPriceAsDouble, 50.0000);
        log.info("Current USD mkt price = {}  Trigger Price = {}", mktPriceAsDouble, formatPrice(triggerPrice));
        OfferInfo offer = aliceClient.createMarketBasedPricedOffer(BUY.name(),
                "USD",
                1_000_000,
                1_000_000,
                0.00,
                getDefaultBuyerSecurityDepositAsPercent(),
                paymentAcct.getId(),
                BTC,
                triggerPrice);
        log.info("BUY offer {} created with margin based price {}.",
                offer.getId(),
                formatPrice(offer.getPrice()));
        genBtcBlocksThenWait(1, 2500);  // Wait for offer book entry.

        offer = aliceClient.getMyOffer(offer.getId()); // Offer has trigger price now.
        log.info("BUY offer should be automatically disabled when mkt price rises above {}.",
                formatPrice(offer.getTriggerPrice()));

        int numIterations = 0;
        while (++numIterations < MAX_ITERATIONS) {
            offer = aliceClient.getMyOffer(offer.getId());

            var mktPrice = aliceClient.getBtcPrice("USD");
            if (offer.getIsActivated()) {
                log.info("Offer still enabled at mkt price {} < {} trigger price",
                        mktPrice,
                        formatPrice(offer.getTriggerPrice()));
                sleep(1000 * 60); // 60s
            } else {
                log.info("Successful test completion after offer disabled at mkt price {} > {} trigger price.",
                        mktPrice,
                        formatPrice(offer.getTriggerPrice()));
                break;
            }
            if (numIterations == MAX_ITERATIONS)
                fail("Offer never disabled");

            genBtcBlocksThenWait(1, 0);
        }
    }

    protected static boolean envLongRunningTestEnabled() {
        String envName = "LONG_RUNNING_OFFER_DEACTIVATION_TEST_ENABLED";
        String envX = getenv(envName);
        if (envX != null) {
            log.info("Enabled, found {}.", envName);
            return true;
        } else {
            log.info("Skipped, no environment variable {} defined.", envName);
            log.info("To enable on Mac OS or Linux:"
                    + "\tIf running in terminal, export LONG_RUNNING_OFFER_DEACTIVATION_TEST_ENABLED=true in bash shell."
                    + "\tIf running in Intellij, set LONG_RUNNING_OFFER_DEACTIVATION_TEST_ENABLED=true in launcher's Environment variables field.");
            return false;
        }
    }
}
