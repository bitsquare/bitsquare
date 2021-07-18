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

package bisq.apitest.scenario.bot;

import bisq.proto.grpc.OfferInfo;

import protobuf.PaymentAccount;

import java.security.SecureRandom;

import java.text.DecimalFormat;

import java.math.BigDecimal;

import java.util.Objects;
import java.util.function.Supplier;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static bisq.cli.CurrencyFormat.formatInternalFiatPrice;
import static bisq.cli.CurrencyFormat.formatSatoshis;
import static bisq.common.util.MathUtils.scaleDownByPowerOf10;
import static bisq.core.btc.wallet.Restrictions.getDefaultBuyerSecurityDepositAsPercent;
import static bisq.core.payment.payload.PaymentMethod.F2F_ID;
import static java.lang.String.format;
import static java.math.RoundingMode.HALF_UP;

@Slf4j
public class RandomOffer {
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final DecimalFormat FIXED_PRICE_FMT = new DecimalFormat("###########0");

    @SuppressWarnings("FieldCanBeLocal")
    // If not an F2F account, keep amount <= 0.01 BTC to avoid hitting unsigned
    // acct trading limit.
    private final Supplier<Long> nextAmount = () ->
            this.getPaymentAccount().getPaymentMethod().getId().equals(F2F_ID)
                    ? (long) (10000000 + RANDOM.nextInt(2500000))
                    : (long) (750000 + RANDOM.nextInt(250000));

    @SuppressWarnings("FieldCanBeLocal")
    private final Supplier<Long> nextMinAmount = () -> {
        boolean useMinAmount = RANDOM.nextBoolean();
        if (useMinAmount) {
            return this.getPaymentAccount().getPaymentMethod().getId().equals(F2F_ID)
                    ? this.getAmount() - 5000000L
                    : this.getAmount() - 50000L;
        } else {
            return this.getAmount();
        }
    };

    @SuppressWarnings("FieldCanBeLocal")
    private final Supplier<Double> nextPriceMargin = () -> {
        boolean useZeroMargin = RANDOM.nextBoolean();
        if (useZeroMargin) {
            return 0.00;
        } else {
            BigDecimal min = BigDecimal.valueOf(-5.0).setScale(2, HALF_UP);
            BigDecimal max = BigDecimal.valueOf(5.0).setScale(2, HALF_UP);
            BigDecimal randomBigDecimal = min.add(BigDecimal.valueOf(RANDOM.nextDouble()).multiply(max.subtract(min)));
            return randomBigDecimal.setScale(2, HALF_UP).doubleValue();
        }
    };

    private final BotClient botClient;
    @Getter
    private final PaymentAccount paymentAccount;
    @Getter
    private final String direction;
    @Getter
    private final String currencyCode;
    @Getter
    private final long amount;
    @Getter
    private final long minAmount;
    @Getter
    private final boolean useMarketBasedPrice;
    @Getter
    private final double priceMargin;
    @Getter
    private final String feeCurrency;

    @Getter
    private String fixedOfferPrice = "0";
    @Getter
    private OfferInfo offer;
    @Getter
    private String id;

    public RandomOffer(BotClient botClient, PaymentAccount paymentAccount) {
        this.botClient = botClient;
        this.paymentAccount = paymentAccount;
        this.direction = RANDOM.nextBoolean() ? "BUY" : "SELL";
        this.currencyCode = Objects.requireNonNull(paymentAccount.getSelectedTradeCurrency()).getCode();
        this.amount = nextAmount.get();
        this.minAmount = nextMinAmount.get();
        this.useMarketBasedPrice = RANDOM.nextBoolean();
        this.priceMargin = nextPriceMargin.get();
        this.feeCurrency = RANDOM.nextBoolean() ? "BSQ" : "BTC";
    }

    public RandomOffer create() throws InvalidRandomOfferException {
        try {
            printDescription();
            if (useMarketBasedPrice) {
                this.offer = botClient.createOfferAtMarketBasedPrice(paymentAccount,
                        direction,
                        currencyCode,
                        amount,
                        minAmount,
                        priceMargin,
                        getDefaultBuyerSecurityDepositAsPercent(),
                        feeCurrency,
                        0 /*no trigger price*/);
            } else {
                this.offer = botClient.createOfferAtFixedPrice(paymentAccount,
                        direction,
                        currencyCode,
                        amount,
                        minAmount,
                        fixedOfferPrice,
                        getDefaultBuyerSecurityDepositAsPercent(),
                        feeCurrency);
            }
            this.id = offer.getId();
            return this;
        } catch (Exception ex) {
            String error = format("Could not create valid %s offer for %s BTC:  %s",
                    currencyCode,
                    formatSatoshis(amount),
                    ex.getMessage());
            throw new InvalidRandomOfferException(error, ex);
        }
    }

    private void printDescription() {
        double currentMarketPrice = botClient.getCurrentBTCMarketPrice(currencyCode);
        // Calculate a fixed price based on the random mkt price margin, even if we don't use it.
        double differenceFromMarketPrice = currentMarketPrice * scaleDownByPowerOf10(priceMargin, 2);
        double fixedOfferPriceAsDouble = direction.equals("BUY")
                ? currentMarketPrice - differenceFromMarketPrice
                : currentMarketPrice + differenceFromMarketPrice;
        this.fixedOfferPrice = FIXED_PRICE_FMT.format(fixedOfferPriceAsDouble);
        String description = format("Creating new %s %s / %s offer for amount = %s BTC, min-amount = %s BTC.",
                useMarketBasedPrice ? "mkt-based-price" : "fixed-priced",
                direction,
                currencyCode,
                formatSatoshis(amount),
                formatSatoshis(minAmount));
        log.info(description);
        if (useMarketBasedPrice) {
            log.info("Offer Price Margin = {}%", priceMargin);
            log.info("Expected Offer Price = {} {}", formatInternalFiatPrice(Double.parseDouble(fixedOfferPrice)), currencyCode);
        } else {

            log.info("Fixed Offer Price    = {} {}", fixedOfferPrice, currencyCode);
        }
        log.info("Current Market Price = {} {}", formatInternalFiatPrice(currentMarketPrice), currencyCode);
    }
}
