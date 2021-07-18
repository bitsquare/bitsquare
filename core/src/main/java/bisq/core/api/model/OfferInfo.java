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

package bisq.core.api.model;

import bisq.core.offer.Offer;
import bisq.core.offer.OpenOffer;

import bisq.common.Payload;

import java.util.Objects;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@EqualsAndHashCode
@ToString
@Getter
public class OfferInfo implements Payload {

    // The client cannot see bisq.core.Offer or its fromProto method.  We use the lighter
    // weight OfferInfo proto wrapper instead, containing just enough fields to view,
    // create and take offers.

    private final String id;
    private final String direction;
    private final long price;
    private final boolean useMarketBasedPrice;
    private final double marketPriceMargin;
    private final long amount;
    private final long minAmount;
    private final long volume;
    private final long minVolume;
    private final long txFee;
    private final long makerFee;
    private final String offerFeePaymentTxId;
    private final long buyerSecurityDeposit;
    private final long sellerSecurityDeposit;
    private final long triggerPrice;
    private final boolean isCurrencyForMakerFeeBtc;
    private final String paymentAccountId;
    private final String paymentMethodId;
    private final String paymentMethodShortName;
    // For fiat offer the baseCurrencyCode is BTC and the counterCurrencyCode is the fiat currency
    // For altcoin offers it is the opposite. baseCurrencyCode is the altcoin and the counterCurrencyCode is BTC.
    private final String baseCurrencyCode;
    private final String counterCurrencyCode;
    private final long date;
    private final String state;
    private final boolean isActivated;
    private final boolean isMyOffer;

    public OfferInfo(OfferInfoBuilder builder) {
        this.id = builder.id;
        this.direction = builder.direction;
        this.price = builder.price;
        this.useMarketBasedPrice = builder.useMarketBasedPrice;
        this.marketPriceMargin = builder.marketPriceMargin;
        this.amount = builder.amount;
        this.minAmount = builder.minAmount;
        this.volume = builder.volume;
        this.minVolume = builder.minVolume;
        this.txFee = builder.txFee;
        this.makerFee = builder.makerFee;
        this.offerFeePaymentTxId = builder.offerFeePaymentTxId;
        this.buyerSecurityDeposit = builder.buyerSecurityDeposit;
        this.sellerSecurityDeposit = builder.sellerSecurityDeposit;
        this.triggerPrice = builder.triggerPrice;
        this.isCurrencyForMakerFeeBtc = builder.isCurrencyForMakerFeeBtc;
        this.paymentAccountId = builder.paymentAccountId;
        this.paymentMethodId = builder.paymentMethodId;
        this.paymentMethodShortName = builder.paymentMethodShortName;
        this.baseCurrencyCode = builder.baseCurrencyCode;
        this.counterCurrencyCode = builder.counterCurrencyCode;
        this.date = builder.date;
        this.state = builder.state;
        this.isActivated = builder.isActivated;
        this.isMyOffer = builder.isMyOffer;
    }

    public static OfferInfo toOfferInfo(Offer offer) {
        // Offer is not mine.
        return getOfferInfoBuilder(offer, false).build();
    }

    public static OfferInfo toOfferInfo(OpenOffer openOffer) {
        // OpenOffer is mine.
        return getOfferInfoBuilder(openOffer.getOffer(), true)
                .withTriggerPrice(openOffer.getTriggerPrice())
                .withIsActivated(!openOffer.isDeactivated())
                .build();
    }

    private static OfferInfoBuilder getOfferInfoBuilder(Offer offer, boolean isMyOffer) {
        return new OfferInfoBuilder()
                .withId(offer.getId())
                .withDirection(offer.getDirection().name())
                .withPrice(Objects.requireNonNull(offer.getPrice()).getValue())
                .withUseMarketBasedPrice(offer.isUseMarketBasedPrice())
                .withMarketPriceMargin(offer.getMarketPriceMargin())
                .withAmount(offer.getAmount().value)
                .withMinAmount(offer.getMinAmount().value)
                .withVolume(Objects.requireNonNull(offer.getVolume()).getValue())
                .withMinVolume(Objects.requireNonNull(offer.getMinVolume()).getValue())
                .withMakerFee(offer.getMakerFee().value)
                .withTxFee(offer.getTxFee().value)
                .withOfferFeePaymentTxId(offer.getOfferFeePaymentTxId())
                .withBuyerSecurityDeposit(offer.getBuyerSecurityDeposit().value)
                .withSellerSecurityDeposit(offer.getSellerSecurityDeposit().value)
                .withIsCurrencyForMakerFeeBtc(offer.isCurrencyForMakerFeeBtc())
                .withPaymentAccountId(offer.getMakerPaymentAccountId())
                .withPaymentMethodId(offer.getPaymentMethod().getId())
                .withPaymentMethodShortName(offer.getPaymentMethod().getShortName())
                .withBaseCurrencyCode(offer.getOfferPayload().getBaseCurrencyCode())
                .withCounterCurrencyCode(offer.getOfferPayload().getCounterCurrencyCode())
                .withDate(offer.getDate().getTime())
                .withState(offer.getState().name())
                .withIsMyOffer(isMyOffer);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public bisq.proto.grpc.OfferInfo toProtoMessage() {
        return bisq.proto.grpc.OfferInfo.newBuilder()
                .setId(id)
                .setDirection(direction)
                .setPrice(price)
                .setUseMarketBasedPrice(useMarketBasedPrice)
                .setMarketPriceMargin(marketPriceMargin)
                .setAmount(amount)
                .setMinAmount(minAmount)
                .setVolume(volume)
                .setMinVolume(minVolume)
                .setMakerFee(makerFee)
                .setTxFee(txFee)
                .setOfferFeePaymentTxId(offerFeePaymentTxId)
                .setBuyerSecurityDeposit(buyerSecurityDeposit)
                .setSellerSecurityDeposit(sellerSecurityDeposit)
                .setTriggerPrice(triggerPrice)
                .setIsCurrencyForMakerFeeBtc(isCurrencyForMakerFeeBtc)
                .setPaymentAccountId(paymentAccountId)
                .setPaymentMethodId(paymentMethodId)
                .setPaymentMethodShortName(paymentMethodShortName)
                .setBaseCurrencyCode(baseCurrencyCode)
                .setCounterCurrencyCode(counterCurrencyCode)
                .setDate(date)
                .setState(state)
                .setIsActivated(isActivated)
                .setIsMyOffer(isMyOffer)
                .build();
    }

    @SuppressWarnings("unused")
    public static OfferInfo fromProto(bisq.proto.grpc.OfferInfo proto) {
        return new OfferInfoBuilder()
                .withId(proto.getId())
                .withDirection(proto.getDirection())
                .withPrice(proto.getPrice())
                .withUseMarketBasedPrice(proto.getUseMarketBasedPrice())
                .withMarketPriceMargin(proto.getMarketPriceMargin())
                .withAmount(proto.getAmount())
                .withMinAmount(proto.getMinAmount())
                .withVolume(proto.getVolume())
                .withMinVolume(proto.getMinVolume())
                .withMakerFee(proto.getMakerFee())
                .withTxFee(proto.getTxFee())
                .withOfferFeePaymentTxId(proto.getOfferFeePaymentTxId())
                .withBuyerSecurityDeposit(proto.getBuyerSecurityDeposit())
                .withSellerSecurityDeposit(proto.getSellerSecurityDeposit())
                .withTriggerPrice(proto.getTriggerPrice())
                .withIsCurrencyForMakerFeeBtc(proto.getIsCurrencyForMakerFeeBtc())
                .withPaymentAccountId(proto.getPaymentAccountId())
                .withPaymentMethodId(proto.getPaymentMethodId())
                .withPaymentMethodShortName(proto.getPaymentMethodShortName())
                .withBaseCurrencyCode(proto.getBaseCurrencyCode())
                .withCounterCurrencyCode(proto.getCounterCurrencyCode())
                .withDate(proto.getDate())
                .withState(proto.getState())
                .withIsActivated(proto.getIsActivated())
                .withIsMyOffer(proto.getIsMyOffer())
                .build();
    }

    /*
     * OfferInfoBuilder helps avoid bungling use of a large OfferInfo constructor
     * argument list.  If consecutive argument values of the same type are not
     * ordered correctly, the compiler won't complain but the resulting bugs could
     * be hard to find and fix.
     */
    public static class OfferInfoBuilder {
        private String id;
        private String direction;
        private long price;
        private boolean useMarketBasedPrice;
        private double marketPriceMargin;
        private long amount;
        private long minAmount;
        private long volume;
        private long minVolume;
        private long txFee;
        private long makerFee;
        private String offerFeePaymentTxId;
        private long buyerSecurityDeposit;
        private long sellerSecurityDeposit;
        private long triggerPrice;
        private boolean isCurrencyForMakerFeeBtc;
        private String paymentAccountId;
        private String paymentMethodId;
        private String paymentMethodShortName;
        private String baseCurrencyCode;
        private String counterCurrencyCode;
        private long date;
        private String state;
        private boolean isActivated;
        private boolean isMyOffer;

        public OfferInfoBuilder withId(String id) {
            this.id = id;
            return this;
        }

        public OfferInfoBuilder withDirection(String direction) {
            this.direction = direction;
            return this;
        }

        public OfferInfoBuilder withPrice(long price) {
            this.price = price;
            return this;
        }

        public OfferInfoBuilder withUseMarketBasedPrice(boolean useMarketBasedPrice) {
            this.useMarketBasedPrice = useMarketBasedPrice;
            return this;
        }

        public OfferInfoBuilder withMarketPriceMargin(double useMarketBasedPrice) {
            this.marketPriceMargin = useMarketBasedPrice;
            return this;
        }

        public OfferInfoBuilder withAmount(long amount) {
            this.amount = amount;
            return this;
        }

        public OfferInfoBuilder withMinAmount(long minAmount) {
            this.minAmount = minAmount;
            return this;
        }

        public OfferInfoBuilder withVolume(long volume) {
            this.volume = volume;
            return this;
        }

        public OfferInfoBuilder withMinVolume(long minVolume) {
            this.minVolume = minVolume;
            return this;
        }

        public OfferInfoBuilder withTxFee(long txFee) {
            this.txFee = txFee;
            return this;
        }

        public OfferInfoBuilder withMakerFee(long makerFee) {
            this.makerFee = makerFee;
            return this;
        }

        public OfferInfoBuilder withOfferFeePaymentTxId(String offerFeePaymentTxId) {
            this.offerFeePaymentTxId = offerFeePaymentTxId;
            return this;
        }

        public OfferInfoBuilder withBuyerSecurityDeposit(long buyerSecurityDeposit) {
            this.buyerSecurityDeposit = buyerSecurityDeposit;
            return this;
        }

        public OfferInfoBuilder withSellerSecurityDeposit(long sellerSecurityDeposit) {
            this.sellerSecurityDeposit = sellerSecurityDeposit;
            return this;
        }

        public OfferInfoBuilder withTriggerPrice(long triggerPrice) {
            this.triggerPrice = triggerPrice;
            return this;
        }

        public OfferInfoBuilder withIsCurrencyForMakerFeeBtc(boolean isCurrencyForMakerFeeBtc) {
            this.isCurrencyForMakerFeeBtc = isCurrencyForMakerFeeBtc;
            return this;
        }

        public OfferInfoBuilder withPaymentAccountId(String paymentAccountId) {
            this.paymentAccountId = paymentAccountId;
            return this;
        }

        public OfferInfoBuilder withPaymentMethodId(String paymentMethodId) {
            this.paymentMethodId = paymentMethodId;
            return this;
        }

        public OfferInfoBuilder withPaymentMethodShortName(String paymentMethodShortName) {
            this.paymentMethodShortName = paymentMethodShortName;
            return this;
        }

        public OfferInfoBuilder withBaseCurrencyCode(String baseCurrencyCode) {
            this.baseCurrencyCode = baseCurrencyCode;
            return this;
        }

        public OfferInfoBuilder withCounterCurrencyCode(String counterCurrencyCode) {
            this.counterCurrencyCode = counterCurrencyCode;
            return this;
        }

        public OfferInfoBuilder withDate(long date) {
            this.date = date;
            return this;
        }

        public OfferInfoBuilder withState(String state) {
            this.state = state;
            return this;
        }

        public OfferInfoBuilder withIsActivated(boolean isActivated) {
            this.isActivated = isActivated;
            return this;
        }

        public OfferInfoBuilder withIsMyOffer(boolean isMyOffer) {
            this.isMyOffer = isMyOffer;
            return this;
        }

        public OfferInfo build() {
            return new OfferInfo(this);
        }
    }
}
