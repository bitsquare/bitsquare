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

import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Price;
import bisq.core.payment.PaymentAccount;

import bisq.proto.grpc.OfferInfo;

import org.bitcoinj.utils.Fiat;

import java.text.DecimalFormat;

import java.math.BigDecimal;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.config.ApiTestConfig.BTC;
import static bisq.cli.TableFormat.formatOfferTable;
import static bisq.common.util.MathUtils.roundDouble;
import static bisq.common.util.MathUtils.scaleDownByPowerOf10;
import static bisq.common.util.MathUtils.scaleUpByPowerOf10;
import static bisq.core.btc.wallet.Restrictions.getDefaultBuyerSecurityDepositAsPercent;
import static bisq.core.locale.CurrencyUtil.isCryptoCurrency;
import static java.lang.Math.abs;
import static java.lang.String.format;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static protobuf.OfferPayload.Direction.BUY;
import static protobuf.OfferPayload.Direction.SELL;

@Disabled
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CreateOfferUsingMarketPriceMarginTest extends AbstractOfferTest {

    private static final DecimalFormat PCT_FORMAT = new DecimalFormat("##0.00");
    private static final double MKT_PRICE_MARGIN_ERROR_TOLERANCE = 0.0050;      // 0.50%
    private static final double MKT_PRICE_MARGIN_WARNING_TOLERANCE = 0.0001;    // 0.01%

    private static final String MAKER_FEE_CURRENCY_CODE = BTC;

    @Test
    @Order(1)
    public void testCreateUSDBTCBuyOffer5PctPriceMargin() {
        PaymentAccount usdAccount = createDummyF2FAccount(aliceClient, "US");
        double priceMarginPctInput = 5.00;
        var newOffer = aliceClient.createMarketBasedPricedOffer(BUY.name(),
                "usd",
                10_000_000L,
                10_000_000L,
                priceMarginPctInput,
                getDefaultBuyerSecurityDepositAsPercent(),
                usdAccount.getId(),
                MAKER_FEE_CURRENCY_CODE,
                NO_TRIGGER_PRICE);
        log.info("OFFER #1:\n{}", formatOfferTable(singletonList(newOffer), "usd"));
        String newOfferId = newOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals(BUY.name(), newOffer.getDirection());
        assertTrue(newOffer.getUseMarketBasedPrice());
        assertEquals(10_000_000, newOffer.getAmount());
        assertEquals(10_000_000, newOffer.getMinAmount());
        assertEquals(1_500_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(usdAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals(BTC, newOffer.getBaseCurrencyCode());
        assertEquals("USD", newOffer.getCounterCurrencyCode());
        assertTrue(newOffer.getIsCurrencyForMakerFeeBtc());

        newOffer = aliceClient.getMyOffer(newOfferId);
        assertEquals(newOfferId, newOffer.getId());
        assertEquals(BUY.name(), newOffer.getDirection());
        assertTrue(newOffer.getUseMarketBasedPrice());
        assertEquals(10_000_000, newOffer.getAmount());
        assertEquals(10_000_000, newOffer.getMinAmount());
        assertEquals(1_500_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(usdAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals(BTC, newOffer.getBaseCurrencyCode());
        assertEquals("USD", newOffer.getCounterCurrencyCode());
        assertTrue(newOffer.getIsCurrencyForMakerFeeBtc());

        assertCalculatedPriceIsCorrect(newOffer, priceMarginPctInput);
    }

    @Test
    @Order(2)
    public void testCreateNZDBTCBuyOfferMinus2PctPriceMargin() {
        PaymentAccount nzdAccount = createDummyF2FAccount(aliceClient, "NZ");
        double priceMarginPctInput = -2.00;
        var newOffer = aliceClient.createMarketBasedPricedOffer(BUY.name(),
                "nzd",
                10_000_000L,
                10_000_000L,
                priceMarginPctInput,
                getDefaultBuyerSecurityDepositAsPercent(),
                nzdAccount.getId(),
                MAKER_FEE_CURRENCY_CODE,
                NO_TRIGGER_PRICE);
        log.info("OFFER #2:\n{}", formatOfferTable(singletonList(newOffer), "nzd"));
        String newOfferId = newOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals(BUY.name(), newOffer.getDirection());
        assertTrue(newOffer.getUseMarketBasedPrice());
        assertEquals(10_000_000, newOffer.getAmount());
        assertEquals(10_000_000, newOffer.getMinAmount());
        assertEquals(1_500_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(nzdAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals(BTC, newOffer.getBaseCurrencyCode());
        assertEquals("NZD", newOffer.getCounterCurrencyCode());
        assertTrue(newOffer.getIsCurrencyForMakerFeeBtc());

        newOffer = aliceClient.getMyOffer(newOfferId);
        assertEquals(newOfferId, newOffer.getId());
        assertEquals(BUY.name(), newOffer.getDirection());
        assertTrue(newOffer.getUseMarketBasedPrice());
        assertEquals(10_000_000, newOffer.getAmount());
        assertEquals(10_000_000, newOffer.getMinAmount());
        assertEquals(1_500_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(nzdAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals(BTC, newOffer.getBaseCurrencyCode());
        assertEquals("NZD", newOffer.getCounterCurrencyCode());
        assertTrue(newOffer.getIsCurrencyForMakerFeeBtc());

        assertCalculatedPriceIsCorrect(newOffer, priceMarginPctInput);
    }

    @Test
    @Order(3)
    public void testCreateGBPBTCSellOfferMinus1Point5PctPriceMargin() {
        PaymentAccount gbpAccount = createDummyF2FAccount(aliceClient, "GB");
        double priceMarginPctInput = -1.5;
        var newOffer = aliceClient.createMarketBasedPricedOffer(SELL.name(),
                "gbp",
                10_000_000L,
                5_000_000L,
                priceMarginPctInput,
                getDefaultBuyerSecurityDepositAsPercent(),
                gbpAccount.getId(),
                MAKER_FEE_CURRENCY_CODE,
                NO_TRIGGER_PRICE);
        log.info("OFFER #3:\n{}", formatOfferTable(singletonList(newOffer), "gbp"));
        String newOfferId = newOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals(SELL.name(), newOffer.getDirection());
        assertTrue(newOffer.getUseMarketBasedPrice());
        assertEquals(10_000_000, newOffer.getAmount());
        assertEquals(5_000_000, newOffer.getMinAmount());
        assertEquals(1_500_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(gbpAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals(BTC, newOffer.getBaseCurrencyCode());
        assertEquals("GBP", newOffer.getCounterCurrencyCode());
        assertTrue(newOffer.getIsCurrencyForMakerFeeBtc());

        newOffer = aliceClient.getMyOffer(newOfferId);
        assertEquals(newOfferId, newOffer.getId());
        assertEquals(SELL.name(), newOffer.getDirection());
        assertTrue(newOffer.getUseMarketBasedPrice());
        assertEquals(10_000_000, newOffer.getAmount());
        assertEquals(5_000_000, newOffer.getMinAmount());
        assertEquals(1_500_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(gbpAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals(BTC, newOffer.getBaseCurrencyCode());
        assertEquals("GBP", newOffer.getCounterCurrencyCode());
        assertTrue(newOffer.getIsCurrencyForMakerFeeBtc());

        assertCalculatedPriceIsCorrect(newOffer, priceMarginPctInput);
    }

    @Test
    @Order(4)
    public void testCreateBRLBTCSellOffer6Point55PctPriceMargin() {
        PaymentAccount brlAccount = createDummyF2FAccount(aliceClient, "BR");
        double priceMarginPctInput = 6.55;
        var newOffer = aliceClient.createMarketBasedPricedOffer(SELL.name(),
                "brl",
                10_000_000L,
                5_000_000L,
                priceMarginPctInput,
                getDefaultBuyerSecurityDepositAsPercent(),
                brlAccount.getId(),
                MAKER_FEE_CURRENCY_CODE,
                NO_TRIGGER_PRICE);
        log.info("OFFER #4:\n{}", formatOfferTable(singletonList(newOffer), "brl"));
        String newOfferId = newOffer.getId();
        assertNotEquals("", newOfferId);
        assertEquals(SELL.name(), newOffer.getDirection());
        assertTrue(newOffer.getUseMarketBasedPrice());
        assertEquals(10_000_000, newOffer.getAmount());
        assertEquals(5_000_000, newOffer.getMinAmount());
        assertEquals(1_500_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(brlAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals(BTC, newOffer.getBaseCurrencyCode());
        assertEquals("BRL", newOffer.getCounterCurrencyCode());
        assertTrue(newOffer.getIsCurrencyForMakerFeeBtc());

        newOffer = aliceClient.getMyOffer(newOfferId);
        assertEquals(newOfferId, newOffer.getId());
        assertEquals(SELL.name(), newOffer.getDirection());
        assertTrue(newOffer.getUseMarketBasedPrice());
        assertEquals(10_000_000, newOffer.getAmount());
        assertEquals(5_000_000, newOffer.getMinAmount());
        assertEquals(1_500_000, newOffer.getBuyerSecurityDeposit());
        assertEquals(brlAccount.getId(), newOffer.getPaymentAccountId());
        assertEquals(BTC, newOffer.getBaseCurrencyCode());
        assertEquals("BRL", newOffer.getCounterCurrencyCode());
        assertTrue(newOffer.getIsCurrencyForMakerFeeBtc());

        assertCalculatedPriceIsCorrect(newOffer, priceMarginPctInput);
    }

    @Test
    @Order(5)
    public void testCreateUSDBTCBuyOfferWithTriggerPrice() {
        PaymentAccount usdAccount = createDummyF2FAccount(aliceClient, "US");
        double mktPriceAsDouble = aliceClient.getBtcPrice("usd");
        BigDecimal mktPrice = new BigDecimal(Double.toString(mktPriceAsDouble));
        BigDecimal triggerPrice = mktPrice.add(new BigDecimal("1000.9999"));
        // TODO Duplicate this Price class logic in CLI.
        long triggerPriceAsLong = Price.parse("USD", triggerPrice.toString()).getValue();

        var newOffer = aliceClient.createMarketBasedPricedOffer(BUY.name(),
                "usd",
                10_000_000L,
                5_000_000L,
                0.0,
                getDefaultBuyerSecurityDepositAsPercent(),
                usdAccount.getId(),
                MAKER_FEE_CURRENCY_CODE,
                triggerPriceAsLong);
        genBtcBlocksThenWait(1, 4000); // give time to add to offer book
        newOffer = aliceClient.getMyOffer(newOffer.getId());
        log.info("OFFER #5:\n{}", formatOfferTable(singletonList(newOffer), "usd"));
        assertEquals(triggerPriceAsLong, newOffer.getTriggerPrice());
    }

    public static void main(String[] args) {
        // TODO DELETE ME
        String triggerPriceAsString = "10.1111";
        Price price = Price.parse("USD", triggerPriceAsString);
        long triggerPriceAsLong = price.getValue();
        log.info("triggerPriceAsString: {}", triggerPriceAsString);
        log.info("triggerPriceAsPrice:  {}", price);
        log.info("triggerPriceAsLong:   {}", triggerPriceAsLong);
    }

    private void assertCalculatedPriceIsCorrect(OfferInfo offer, double priceMarginPctInput) {
        assertTrue(() -> {
            String counterCurrencyCode = offer.getCounterCurrencyCode();
            double mktPrice = aliceClient.getBtcPrice(counterCurrencyCode);
            double scaledOfferPrice = getScaledOfferPrice(offer.getPrice(), counterCurrencyCode);
            double expectedDiffPct = scaleDownByPowerOf10(priceMarginPctInput, 2);
            double actualDiffPct = offer.getDirection().equals(BUY.name())
                    ? getPercentageDifference(scaledOfferPrice, mktPrice)
                    : getPercentageDifference(mktPrice, scaledOfferPrice);
            double pctDiffDelta = abs(expectedDiffPct) - abs(actualDiffPct);
            return isCalculatedPriceWithinErrorTolerance(pctDiffDelta,
                    expectedDiffPct,
                    actualDiffPct,
                    mktPrice,
                    scaledOfferPrice,
                    offer);
        });
    }

    private double getPercentageDifference(double price1, double price2) {
        return BigDecimal.valueOf(roundDouble((1 - (price1 / price2)), 5))
                .setScale(4, HALF_UP)
                .doubleValue();
    }

    private double getScaledOfferPrice(double offerPrice, String currencyCode) {
        int precision = isCryptoCurrency(currencyCode) ? Altcoin.SMALLEST_UNIT_EXPONENT : Fiat.SMALLEST_UNIT_EXPONENT;
        return scaleDownByPowerOf10(offerPrice, precision);
    }

    private boolean isCalculatedPriceWithinErrorTolerance(double delta,
                                                          double expectedDiffPct,
                                                          double actualDiffPct,
                                                          double mktPrice,
                                                          double scaledOfferPrice,
                                                          OfferInfo offer) {
        if (abs(delta) > MKT_PRICE_MARGIN_ERROR_TOLERANCE) {
            logCalculatedPricePoppedErrorTolerance(expectedDiffPct,
                    actualDiffPct,
                    mktPrice,
                    scaledOfferPrice);
            log.error(offer.toString());
            return false;
        }

        if (abs(delta) >= MKT_PRICE_MARGIN_WARNING_TOLERANCE) {
            logCalculatedPricePoppedWarningTolerance(expectedDiffPct,
                    actualDiffPct,
                    mktPrice,
                    scaledOfferPrice);
            log.warn(offer.toString());
        }

        return true;
    }

    private void logCalculatedPricePoppedWarningTolerance(double expectedDiffPct,
                                                          double actualDiffPct,
                                                          double mktPrice,
                                                          double scaledOfferPrice) {
        log.warn(format("Calculated price %.4f & mkt price %.4f differ by ~ %s%s,"
                        + " not by %s%s, outside the %s%s warning tolerance,"
                        + " but within the %s%s error tolerance.",
                scaledOfferPrice, mktPrice,
                PCT_FORMAT.format(scaleUpByPowerOf10(actualDiffPct, 2)), "%",
                PCT_FORMAT.format(scaleUpByPowerOf10(expectedDiffPct, 2)), "%",
                PCT_FORMAT.format(scaleUpByPowerOf10(MKT_PRICE_MARGIN_WARNING_TOLERANCE, 2)), "%",
                PCT_FORMAT.format(scaleUpByPowerOf10(MKT_PRICE_MARGIN_ERROR_TOLERANCE, 2)), "%"));
    }

    private void logCalculatedPricePoppedErrorTolerance(double expectedDiffPct,
                                                        double actualDiffPct,
                                                        double mktPrice,
                                                        double scaledOfferPrice) {
        log.error(format("Calculated price %.4f & mkt price %.4f differ by ~ %s%s,"
                        + " not by %s%s, outside the %s%s error tolerance.",
                scaledOfferPrice, mktPrice,
                PCT_FORMAT.format(scaleUpByPowerOf10(actualDiffPct, 2)), "%",
                PCT_FORMAT.format(scaleUpByPowerOf10(expectedDiffPct, 2)), "%",
                PCT_FORMAT.format(scaleUpByPowerOf10(MKT_PRICE_MARGIN_ERROR_TOLERANCE, 2)), "%"));
    }
}
