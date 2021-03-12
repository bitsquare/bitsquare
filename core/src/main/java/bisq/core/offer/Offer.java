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

package bisq.core.offer;

import bisq.core.exceptions.TradePriceOutOfToleranceException;
import bisq.core.locale.CurrencyUtil;
import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.availability.OfferAvailabilityModel;
import bisq.core.offer.availability.OfferAvailabilityProtocol;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.util.VolumeUtil;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.JsonExclude;
import bisq.common.util.MathUtils;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.security.PublicKey;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class Offer implements NetworkPayload, PersistablePayload {

    // We allow max. 1 % difference between own offerPayload price calculation and takers calculation.
    // Market price might be different at maker's and takers side so we need a bit of tolerance.
    // The tolerance will get smaller once we have multiple price feeds avoiding fast price fluctuations
    // from one provider.
    private final static double PRICE_TOLERANCE = 0.01;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enums
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum State {
        UNKNOWN,
        OFFER_FEE_PAID,
        AVAILABLE,
        NOT_AVAILABLE,
        REMOVED,
        MAKER_OFFLINE
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Getter
    private final OfferPayload offerPayload;

    @JsonExclude
    @Getter
    final transient private ObjectProperty<Offer.State> stateProperty = new SimpleObjectProperty<>(Offer.State.UNKNOWN);
    @JsonExclude
    @Nullable
    transient private OfferAvailabilityProtocol availabilityProtocol;
    @JsonExclude
    @Getter
    final transient private StringProperty errorMessageProperty = new SimpleStringProperty();
    @JsonExclude
    @Nullable
    @Setter
    transient private PriceFeedService priceFeedService;

    // Used only as cache
    @Nullable
    @JsonExclude
    transient private String currencyCode;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Offer(OfferPayload offerPayload) {
        this.offerPayload = offerPayload;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.Offer toProtoMessage() {
        return protobuf.Offer.newBuilder().setFeeTxOfferPayload(offerPayload.toProtoMessage().getFeeTxOfferPayload())
                .build();
    }

    public static Offer fromProto(protobuf.Offer proto) {
        return new Offer(FeeTxOfferPayload.fromProto(proto.getFeeTxOfferPayload()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Availability
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void checkOfferAvailability(OfferAvailabilityModel model, ResultHandler resultHandler,
                                       ErrorMessageHandler errorMessageHandler) {
        availabilityProtocol = new OfferAvailabilityProtocol(model,
                () -> {
                    cancelAvailabilityRequest();
                    resultHandler.handleResult();
                },
                (errorMessage) -> {
                    if (availabilityProtocol != null)
                        availabilityProtocol.cancel();
                    log.error(errorMessage);
                    errorMessageHandler.handleErrorMessage(errorMessage);
                });
        availabilityProtocol.sendOfferAvailabilityRequest();
    }

    public void cancelAvailabilityRequest() {
        if (availabilityProtocol != null)
            availabilityProtocol.cancel();
    }

    @Nullable
    public Price getPrice() {
        String currencyCode = getCurrencyCode();
        if (!offerPayload.isUseMarketBasedPrice()) {
            return Price.valueOf(currencyCode, offerPayload.getPrice());
        }

        checkNotNull(priceFeedService, "priceFeed must not be null");
        MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode);
        if (marketPrice != null && marketPrice.isRecentExternalPriceAvailable()) {
            double factor;
            double marketPriceMargin = offerPayload.getMarketPriceMargin();
            if (CurrencyUtil.isCryptoCurrency(currencyCode)) {
                factor = getDirection() == OfferPayload.Direction.SELL ?
                        1 - marketPriceMargin : 1 + marketPriceMargin;
            } else {
                factor = getDirection() == OfferPayload.Direction.BUY ?
                        1 - marketPriceMargin : 1 + marketPriceMargin;
            }
            double marketPriceAsDouble = marketPrice.getPrice();
            double targetPriceAsDouble = marketPriceAsDouble * factor;
            try {
                int precision = CurrencyUtil.isCryptoCurrency(currencyCode) ?
                        Altcoin.SMALLEST_UNIT_EXPONENT :
                        Fiat.SMALLEST_UNIT_EXPONENT;
                double scaled = MathUtils.scaleUpByPowerOf10(targetPriceAsDouble, precision);
                final long roundedToLong = MathUtils.roundDoubleToLong(scaled);
                return Price.valueOf(currencyCode, roundedToLong);
            } catch (Exception e) {
                log.error("Exception at getPrice / parseToFiat: " + e.toString() + "\n" +
                        "That case should never happen.");
                return null;
            }
        } else {
            log.trace("We don't have a market price. " +
                    "That case could only happen if you don't have a price feed.");
            return null;
        }
    }

    public void checkTradePriceTolerance(long takersTradePrice) throws TradePriceOutOfToleranceException,
            MarketPriceNotAvailableException, IllegalArgumentException {
        Price tradePrice = Price.valueOf(getCurrencyCode(), takersTradePrice);
        Price offerPrice = getPrice();
        if (offerPrice == null)
            throw new MarketPriceNotAvailableException("Market price required for calculating trade price is not available.");

        checkArgument(takersTradePrice > 0, "takersTradePrice must be positive");

        double relation = (double) takersTradePrice / (double) offerPrice.getValue();
        // We allow max. 2 % difference between own offerPayload price calculation and takers calculation.
        // Market price might be different at maker's and takers side so we need a bit of tolerance.
        // The tolerance will get smaller once we have multiple price feeds avoiding fast price fluctuations
        // from one provider.

        double deviation = Math.abs(1 - relation);
        log.info("Price at take-offer time: id={}, currency={}, takersPrice={}, makersPrice={}, deviation={}",
                getShortId(), getCurrencyCode(), takersTradePrice, offerPrice.getValue(),
                deviation * 100 + "%");
        if (deviation > PRICE_TOLERANCE) {
            String msg = "Taker's trade price is too far away from our calculated price based on the market price.\n" +
                    "takersPrice=" + tradePrice.getValue() + "\n" +
                    "makersPrice=" + offerPrice.getValue();
            log.warn(msg);
            throw new TradePriceOutOfToleranceException(msg);
        }
    }

    @Nullable
    public Volume getVolumeByAmount(Coin amount) {
        Price price = getPrice();
        if (price == null || amount == null) {
            return null;
        }
        Volume volumeByAmount = price.getVolumeByAmount(amount);
        if (offerPayload.getPaymentMethodId().equals(PaymentMethod.HAL_CASH_ID))
            volumeByAmount = VolumeUtil.getAdjustedVolumeForHalCash(volumeByAmount);
        else if (CurrencyUtil.isFiatCurrency(offerPayload.getCurrencyCode()))
            volumeByAmount = VolumeUtil.getRoundedFiatVolume(volumeByAmount);

        return volumeByAmount;
    }

    public void resetState() {
        setState(Offer.State.UNKNOWN);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setState(Offer.State state) {
        stateProperty().set(state);
    }

    public ObjectProperty<Offer.State> stateProperty() {
        return stateProperty;
    }

    public void setOfferFeePaymentTxId(String offerFeePaymentTxID) {
        getFeeTxPayload(offerPayload).ifPresent(payload -> payload.setOfferFeePaymentTxId(offerFeePaymentTxID));
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessageProperty.set(errorMessage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter
    ///////////////////////////////////////////////////////////////////////////////////////////

    // converted payload properties

    public Coin getTxFee() {
        return Coin.valueOf(offerPayload.getTxFee());
    }

    public Coin getMakerFee() {
        return Coin.valueOf(offerPayload.getMakerFee());
    }

    public boolean isCurrencyForMakerFeeBtc() {
        return offerPayload.isCurrencyForMakerFeeBtc();
    }

    public Coin getBuyerSecurityDeposit() {
        return Coin.valueOf(offerPayload.getBuyerSecurityDeposit());
    }

    public Coin getSellerSecurityDeposit() {
        return Coin.valueOf(offerPayload.getSellerSecurityDeposit());
    }

    public Coin getMaxTradeLimit() {
        return Coin.valueOf(offerPayload.getMaxTradeLimit());
    }

    public Coin getAmount() {
        return Coin.valueOf(offerPayload.getAmount());
    }

    public Coin getMinAmount() {
        return Coin.valueOf(offerPayload.getMinAmount());
    }

    public boolean isRange() {
        return offerPayload.getAmount() != offerPayload.getMinAmount();
    }

    public Date getDate() {
        return new Date(offerPayload.getDate());
    }

    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.getPaymentMethodById(offerPayload.getPaymentMethodId());
    }

    // utils
    public String getShortId() {
        return Utilities.getShortId(offerPayload.getId());
    }

    @Nullable
    public Volume getVolume() {
        return getVolumeByAmount(getAmount());
    }

    @Nullable
    public Volume getMinVolume() {
        return getVolumeByAmount(getMinAmount());
    }

    public boolean isBuyOffer() {
        return getDirection() == OfferPayload.Direction.BUY;
    }

    public OfferPayload.Direction getMirroredDirection() {
        return getDirection() == OfferPayload.Direction.BUY ? OfferPayload.Direction.SELL : OfferPayload.Direction.BUY;
    }

    public boolean isMyOffer(KeyRing keyRing) {
        return getPubKeyRing().equals(keyRing.getPubKeyRing());
    }


    public Optional<String> getAccountAgeWitnessHashAsHex() {
        Map<String, String> extraDataMap = getExtraDataMap();
        if (extraDataMap != null && extraDataMap.containsKey(OfferPayload.ACCOUNT_AGE_WITNESS_HASH))
            return Optional.of(extraDataMap.get(OfferPayload.ACCOUNT_AGE_WITNESS_HASH));
        else
            return Optional.empty();
    }

    public String getF2FCity() {
        if (getExtraDataMap() != null && getExtraDataMap().containsKey(OfferPayload.F2F_CITY))
            return getExtraDataMap().get(OfferPayload.F2F_CITY);
        else
            return "";
    }

    public String getExtraInfo() {
        if (getExtraDataMap() != null && getExtraDataMap().containsKey(OfferPayload.F2F_EXTRA_INFO))
            return getExtraDataMap().get(OfferPayload.F2F_EXTRA_INFO);
        else if (getExtraDataMap() != null && getExtraDataMap().containsKey(OfferPayload.CASH_BY_MAIL_EXTRA_INFO))
            return getExtraDataMap().get(OfferPayload.CASH_BY_MAIL_EXTRA_INFO);
        else
            return "";
    }

    public String getPaymentMethodNameWithCountryCode() {
        String method = this.getPaymentMethod().getShortName();
        String methodCountryCode = this.getCountryCode();
        if (methodCountryCode != null)
            method = method + " (" + methodCountryCode + ")";
        return method;
    }

    // domain properties
    public Offer.State getState() {
        return stateProperty.get();
    }

    public ReadOnlyStringProperty errorMessageProperty() {
        return errorMessageProperty;
    }

    public String getErrorMessage() {
        return errorMessageProperty.get();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Delegate Getter (boilerplate code generated via IntelliJ generate delegate feature)
    ///////////////////////////////////////////////////////////////////////////////////////////

    public OfferPayload.Direction getDirection() {
        return offerPayload.getDirection();
    }

    public String getId() {
        return offerPayload.getId();
    }

    @Nullable
    public List<String> getAcceptedBankIds() {
        return offerPayload.getAcceptedBankIds();
    }

    @Nullable
    public String getBankId() {
        return offerPayload.getBankId();
    }

    @Nullable
    public List<String> getAcceptedCountryCodes() {
        return offerPayload.getAcceptedCountryCodes();
    }

    @Nullable
    public String getCountryCode() {
        return offerPayload.getCountryCode();
    }

    public String getCurrencyCode() {
        if (currencyCode != null) {
            return currencyCode;
        }

        currencyCode = offerPayload.getBaseCurrencyCode().equals("BTC") ?
                offerPayload.getCounterCurrencyCode() :
                offerPayload.getBaseCurrencyCode();
        return currencyCode;
    }

    public long getProtocolVersion() {
        return offerPayload.getProtocolVersion();
    }

    public boolean isUseMarketBasedPrice() {
        return offerPayload.isUseMarketBasedPrice();
    }

    public double getMarketPriceMargin() {
        return offerPayload.getMarketPriceMargin();
    }

    public NodeAddress getMakerNodeAddress() {
        return offerPayload.getOwnerNodeAddress();
    }

    public PubKeyRing getPubKeyRing() {
        return offerPayload.getPubKeyRing();
    }

    public String getMakerPaymentAccountId() {
        return offerPayload.getMakerPaymentAccountId();
    }

    public String getOfferFeePaymentTxId() {
        return getFeeTxPayload(offerPayload).map(FeeTxOfferPayload::getOfferFeePaymentTxId).orElse(null);
    }

    public String getVersionNr() {
        return offerPayload.getVersionNr();
    }

    public long getMaxTradePeriod() {
        return offerPayload.getMaxTradePeriod();
    }

    public NodeAddress getOwnerNodeAddress() {
        return offerPayload.getOwnerNodeAddress();
    }

    // Yet unused
    public PublicKey getOwnerPubKey() {
        return offerPayload.getOwnerPubKey();
    }

    @Nullable
    public Map<String, String> getExtraDataMap() {
        return offerPayload.getExtraDataMap();
    }

    public boolean isUseAutoClose() {
        return offerPayload.isUseAutoClose();
    }

    public long getBlockHeightAtOfferCreation() {
        return offerPayload.getBlockHeightAtOfferCreation();
    }

    @Nullable
    public String getHashOfChallenge() {
        return offerPayload.getHashOfChallenge();
    }

    public boolean isPrivateOffer() {
        return offerPayload.isPrivateOffer();
    }

    public long getUpperClosePrice() {
        return offerPayload.getUpperClosePrice();
    }

    public long getLowerClosePrice() {
        return offerPayload.getLowerClosePrice();
    }

    public boolean isUseReOpenAfterAutoClose() {
        return offerPayload.isUseReOpenAfterAutoClose();
    }

    public boolean isXmrAutoConf() {
        if (!isXmr()) {
            return false;
        }
        if (getExtraDataMap() == null || !getExtraDataMap().containsKey(OfferPayload.XMR_AUTO_CONF)) {
            return false;
        }

        return getExtraDataMap().get(OfferPayload.XMR_AUTO_CONF).equals(OfferPayload.XMR_AUTO_CONF_ENABLED_VALUE);
    }

    public boolean isXmr() {
        return getCurrencyCode().equals("XMR");
    }

    private Optional<FeeTxOfferPayload> getFeeTxPayload(OfferPayload offerPayload) {
        if (offerPayload instanceof FeeTxOfferPayload) {
            return Optional.of((FeeTxOfferPayload) offerPayload);
        }
        return Optional.empty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Offer offer = (Offer) o;

        if (offerPayload != null ? !offerPayload.equals(offer.offerPayload) : offer.offerPayload != null) return false;
        //noinspection SimplifiableIfStatement
        if (getState() != offer.getState()) return false;
        return !(getErrorMessage() != null ? !getErrorMessage().equals(offer.getErrorMessage()) : offer.getErrorMessage() != null);

    }

    @Override
    public int hashCode() {
        int result = offerPayload != null ? offerPayload.hashCode() : 0;
        result = 31 * result + (getState() != null ? getState().hashCode() : 0);
        result = 31 * result + (getErrorMessage() != null ? getErrorMessage().hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Offer{" +
                "getErrorMessage()='" + getErrorMessage() + '\'' +
                ", state=" + getState() +
                ", offerPayload=" + offerPayload +
                '}';
    }
}
