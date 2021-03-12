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

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.PubKeyRing;
import bisq.common.proto.ProtoUtil;
import bisq.common.util.CollectionUtils;
import bisq.common.util.ExtraDataMapValidator;
import bisq.common.util.JsonExclude;

import java.security.PublicKey;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

// OfferPayload has about 1.4 kb. We should look into options to make it smaller but will be hard to do it in a
// backward compatible way. Maybe a candidate when segwit activation is done as hardfork?

@EqualsAndHashCode(callSuper = true)
@Getter
@Slf4j
public final class FeeTxOfferPayload extends OfferPayload {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final String id;
    private final long date;
    private final NodeAddress ownerNodeAddress;
    @JsonExclude
    private final PubKeyRing pubKeyRing;
    private final Direction direction;
    // price if fixed price is used (usePercentageBasedPrice = false), otherwise 0
    private final long price;
    // Distance form market price if percentage based price is used (usePercentageBasedPrice = true), otherwise 0.
    // E.g. 0.1 -> 10%. Can be negative as well. Depending on direction the marketPriceMargin is above or below the market price.
    // Positive values is always the usual case where you want a better price as the market.
    // E.g. Buy offer with market price 400.- leads to a 360.- price.
    // Sell offer with market price 400.- leads to a 440.- price.
    private final double marketPriceMargin;
    // We use 2 type of prices: fixed price or price based on distance from market price
    private final boolean useMarketBasedPrice;
    private final long amount;
    private final long minAmount;

    // For fiat offer the baseCurrencyCode is BTC and the counterCurrencyCode is the fiat currency
    // For altcoin offers it is the opposite. baseCurrencyCode is the altcoin and the counterCurrencyCode is BTC.
    private final String baseCurrencyCode;
    private final String counterCurrencyCode;

    @Deprecated
    // Not used anymore but we cannot set it Nullable or remove it to not break backward compatibility (diff. hash)
    private final List<NodeAddress> arbitratorNodeAddresses;
    @Deprecated
    // Not used anymore but we cannot set it Nullable or remove it to not break backward compatibility (diff. hash)
    private final List<NodeAddress> mediatorNodeAddresses;
    private final String paymentMethodId;
    private final String makerPaymentAccountId;
    // Mutable property. Has to be set before offer is save in P2P network as it changes the objects hash!
    @Nullable
    @Setter
    private String offerFeePaymentTxId;
    @Nullable
    private final String countryCode;
    @Nullable
    private final List<String> acceptedCountryCodes;
    @Nullable
    private final String bankId;
    @Nullable
    private final List<String> acceptedBankIds;
    private final String versionNr;
    private final long blockHeightAtOfferCreation;
    private final long txFee;
    private final long makerFee;
    private final boolean isCurrencyForMakerFeeBtc;
    private final long buyerSecurityDeposit;
    private final long sellerSecurityDeposit;
    private final long maxTradeLimit;
    private final long maxTradePeriod;

    // reserved for future use cases
    // Close offer when certain price is reached
    private final boolean useAutoClose;
    // If useReOpenAfterAutoClose=true we re-open a new offer with the remaining funds if the trade amount
    // was less than the offer's max. trade amount.
    private final boolean useReOpenAfterAutoClose;
    // Used when useAutoClose is set for canceling the offer when lowerClosePrice is triggered
    private final long lowerClosePrice;
    // Used when useAutoClose is set for canceling the offer when upperClosePrice is triggered
    private final long upperClosePrice;
    // Reserved for possible future use to support private trades where the taker needs to have an accessKey
    private final boolean isPrivateOffer;
    @Nullable
    private final String hashOfChallenge;

    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new
    // field in a class would break that hash and therefore break the storage mechanism.

    // extraDataMap used from v0.6 on for hashOfPaymentAccount
    // key ACCOUNT_AGE_WITNESS, value: hex string of hashOfPaymentAccount byte array
    @Nullable
    private final Map<String, String> extraDataMap;
    private final int protocolVersion;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public FeeTxOfferPayload(String id,
                             long date,
                             NodeAddress ownerNodeAddress,
                             PubKeyRing pubKeyRing,
                             Direction direction,
                             long price,
                             double marketPriceMargin,
                             boolean useMarketBasedPrice,
                             long amount,
                             long minAmount,
                             String baseCurrencyCode,
                             String counterCurrencyCode,
                             List<NodeAddress> arbitratorNodeAddresses,
                             List<NodeAddress> mediatorNodeAddresses,
                             String paymentMethodId,
                             String makerPaymentAccountId,
                             @Nullable String offerFeePaymentTxId,
                             @Nullable String countryCode,
                             @Nullable List<String> acceptedCountryCodes,
                             @Nullable String bankId,
                             @Nullable List<String> acceptedBankIds,
                             String versionNr,
                             long blockHeightAtOfferCreation,
                             long txFee,
                             long makerFee,
                             boolean isCurrencyForMakerFeeBtc,
                             long buyerSecurityDeposit,
                             long sellerSecurityDeposit,
                             long maxTradeLimit,
                             long maxTradePeriod,
                             boolean useAutoClose,
                             boolean useReOpenAfterAutoClose,
                             long lowerClosePrice,
                             long upperClosePrice,
                             boolean isPrivateOffer,
                             @Nullable String hashOfChallenge,
                             @Nullable Map<String, String> extraDataMap,
                             int protocolVersion) {
        this.id = id;
        this.date = date;
        this.ownerNodeAddress = ownerNodeAddress;
        this.pubKeyRing = pubKeyRing;
        this.direction = direction;
        this.price = price;
        this.marketPriceMargin = marketPriceMargin;
        this.useMarketBasedPrice = useMarketBasedPrice;
        this.amount = amount;
        this.minAmount = minAmount;
        this.baseCurrencyCode = baseCurrencyCode;
        this.counterCurrencyCode = counterCurrencyCode;
        this.arbitratorNodeAddresses = arbitratorNodeAddresses;
        this.mediatorNodeAddresses = mediatorNodeAddresses;
        this.paymentMethodId = paymentMethodId;
        this.makerPaymentAccountId = makerPaymentAccountId;
        this.offerFeePaymentTxId = offerFeePaymentTxId;
        this.countryCode = countryCode;
        this.acceptedCountryCodes = acceptedCountryCodes;
        this.bankId = bankId;
        this.acceptedBankIds = acceptedBankIds;
        this.versionNr = versionNr;
        this.blockHeightAtOfferCreation = blockHeightAtOfferCreation;
        this.txFee = txFee;
        this.makerFee = makerFee;
        this.isCurrencyForMakerFeeBtc = isCurrencyForMakerFeeBtc;
        this.buyerSecurityDeposit = buyerSecurityDeposit;
        this.sellerSecurityDeposit = sellerSecurityDeposit;
        this.maxTradeLimit = maxTradeLimit;
        this.maxTradePeriod = maxTradePeriod;
        this.useAutoClose = useAutoClose;
        this.useReOpenAfterAutoClose = useReOpenAfterAutoClose;
        this.lowerClosePrice = lowerClosePrice;
        this.upperClosePrice = upperClosePrice;
        this.isPrivateOffer = isPrivateOffer;
        this.hashOfChallenge = hashOfChallenge;
        this.extraDataMap = ExtraDataMapValidator.getValidatedExtraDataMap(extraDataMap);
        this.protocolVersion = protocolVersion;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.StoragePayload toProtoMessage() {
        protobuf.FeeTxOfferPayload.Builder builder = protobuf.FeeTxOfferPayload.newBuilder()
                .setId(id)
                .setDate(date)
                .setOwnerNodeAddress(ownerNodeAddress.toProtoMessage())
                .setPubKeyRing(pubKeyRing.toProtoMessage())
                .setDirection(toProtoMessage(direction))
                .setPrice(price)
                .setMarketPriceMargin(marketPriceMargin)
                .setUseMarketBasedPrice(useMarketBasedPrice)
                .setAmount(amount)
                .setMinAmount(minAmount)
                .setBaseCurrencyCode(baseCurrencyCode)
                .setCounterCurrencyCode(counterCurrencyCode)
                .addAllArbitratorNodeAddresses(arbitratorNodeAddresses.stream()
                        .map(NodeAddress::toProtoMessage)
                        .collect(Collectors.toList()))
                .addAllMediatorNodeAddresses(mediatorNodeAddresses.stream()
                        .map(NodeAddress::toProtoMessage)
                        .collect(Collectors.toList()))
                .setPaymentMethodId(paymentMethodId)
                .setMakerPaymentAccountId(makerPaymentAccountId)
                .setVersionNr(versionNr)
                .setBlockHeightAtOfferCreation(blockHeightAtOfferCreation)
                .setTxFee(txFee)
                .setMakerFee(makerFee)
                .setIsCurrencyForMakerFeeBtc(isCurrencyForMakerFeeBtc)
                .setBuyerSecurityDeposit(buyerSecurityDeposit)
                .setSellerSecurityDeposit(sellerSecurityDeposit)
                .setMaxTradeLimit(maxTradeLimit)
                .setMaxTradePeriod(maxTradePeriod)
                .setUseAutoClose(useAutoClose)
                .setUseReOpenAfterAutoClose(useReOpenAfterAutoClose)
                .setLowerClosePrice(lowerClosePrice)
                .setUpperClosePrice(upperClosePrice)
                .setIsPrivateOffer(isPrivateOffer)
                .setProtocolVersion(protocolVersion);

        builder.setOfferFeePaymentTxId(checkNotNull(offerFeePaymentTxId,
                "OfferPayload is in invalid state: offerFeePaymentTxID is not set when adding to P2P network."));

        Optional.ofNullable(countryCode).ifPresent(builder::setCountryCode);
        Optional.ofNullable(bankId).ifPresent(builder::setBankId);
        Optional.ofNullable(acceptedBankIds).ifPresent(builder::addAllAcceptedBankIds);
        Optional.ofNullable(acceptedCountryCodes).ifPresent(builder::addAllAcceptedCountryCodes);
        Optional.ofNullable(hashOfChallenge).ifPresent(builder::setHashOfChallenge);
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);

        return protobuf.StoragePayload.newBuilder().setFeeTxOfferPayload(builder).build();
    }

    public static FeeTxOfferPayload fromProto(protobuf.FeeTxOfferPayload proto) {
        checkArgument(!proto.getOfferFeePaymentTxId().isEmpty(), "OfferFeePaymentTxId must be set in PB.OfferPayload");
        List<String> acceptedBankIds = proto.getAcceptedBankIdsList().isEmpty() ?
                null : new ArrayList<>(proto.getAcceptedBankIdsList());
        List<String> acceptedCountryCodes = proto.getAcceptedCountryCodesList().isEmpty() ?
                null : new ArrayList<>(proto.getAcceptedCountryCodesList());
        String hashOfChallenge = ProtoUtil.stringOrNullFromProto(proto.getHashOfChallenge());
        Map<String, String> extraDataMapMap = CollectionUtils.isEmpty(proto.getExtraDataMap()) ?
                null : proto.getExtraDataMap();

        return new FeeTxOfferPayload(proto.getId(),
                proto.getDate(),
                NodeAddress.fromProto(proto.getOwnerNodeAddress()),
                PubKeyRing.fromProto(proto.getPubKeyRing()),
                fromProto(proto.getDirection()),
                proto.getPrice(),
                proto.getMarketPriceMargin(),
                proto.getUseMarketBasedPrice(),
                proto.getAmount(),
                proto.getMinAmount(),
                proto.getBaseCurrencyCode(),
                proto.getCounterCurrencyCode(),
                proto.getArbitratorNodeAddressesList().stream()
                        .map(NodeAddress::fromProto)
                        .collect(Collectors.toList()),
                proto.getMediatorNodeAddressesList().stream()
                        .map(NodeAddress::fromProto)
                        .collect(Collectors.toList()),
                proto.getPaymentMethodId(),
                proto.getMakerPaymentAccountId(),
                proto.getOfferFeePaymentTxId(),
                ProtoUtil.stringOrNullFromProto(proto.getCountryCode()),
                acceptedCountryCodes,
                ProtoUtil.stringOrNullFromProto(proto.getBankId()),
                acceptedBankIds,
                proto.getVersionNr(),
                proto.getBlockHeightAtOfferCreation(),
                proto.getTxFee(),
                proto.getMakerFee(),
                proto.getIsCurrencyForMakerFeeBtc(),
                proto.getBuyerSecurityDeposit(),
                proto.getSellerSecurityDeposit(),
                proto.getMaxTradeLimit(),
                proto.getMaxTradePeriod(),
                proto.getUseAutoClose(),
                proto.getUseReOpenAfterAutoClose(),
                proto.getLowerClosePrice(),
                proto.getUpperClosePrice(),
                proto.getIsPrivateOffer(),
                hashOfChallenge,
                extraDataMapMap,
                proto.getProtocolVersion());
    }

    public static Direction fromProto(protobuf.FeeTxOfferPayload.Direction direction) {
        return ProtoUtil.enumFromProto(Direction.class, direction.name());
    }

    public static protobuf.FeeTxOfferPayload.Direction toProtoMessage(Direction direction) {
        return protobuf.FeeTxOfferPayload.Direction.valueOf(direction.name());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public long getTTL() {
        return TTL;
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return pubKeyRing.getSignaturePubKey();
    }

    @Override
    public String toString() {
        return "OfferPayload{" +
                "\n     id='" + id + '\'' +
                ",\n     date=" + new Date(date) +
                ",\n     ownerNodeAddress=" + ownerNodeAddress +
                ",\n     pubKeyRing=" + pubKeyRing +
                ",\n     direction=" + direction +
                ",\n     price=" + price +
                ",\n     marketPriceMargin=" + marketPriceMargin +
                ",\n     useMarketBasedPrice=" + useMarketBasedPrice +
                ",\n     amount=" + amount +
                ",\n     minAmount=" + minAmount +
                ",\n     baseCurrencyCode='" + baseCurrencyCode + '\'' +
                ",\n     counterCurrencyCode='" + counterCurrencyCode + '\'' +
                ",\n     paymentMethodId='" + paymentMethodId + '\'' +
                ",\n     makerPaymentAccountId='" + makerPaymentAccountId + '\'' +
                ",\n     offerFeePaymentTxId='" + offerFeePaymentTxId + '\'' +
                ",\n     countryCode='" + countryCode + '\'' +
                ",\n     acceptedCountryCodes=" + acceptedCountryCodes +
                ",\n     bankId='" + bankId + '\'' +
                ",\n     acceptedBankIds=" + acceptedBankIds +
                ",\n     versionNr='" + versionNr + '\'' +
                ",\n     blockHeightAtOfferCreation=" + blockHeightAtOfferCreation +
                ",\n     txFee=" + txFee +
                ",\n     makerFee=" + makerFee +
                ",\n     isCurrencyForMakerFeeBtc=" + isCurrencyForMakerFeeBtc +
                ",\n     buyerSecurityDeposit=" + buyerSecurityDeposit +
                ",\n     sellerSecurityDeposit=" + sellerSecurityDeposit +
                ",\n     maxTradeLimit=" + maxTradeLimit +
                ",\n     maxTradePeriod=" + maxTradePeriod +
                ",\n     useAutoClose=" + useAutoClose +
                ",\n     useReOpenAfterAutoClose=" + useReOpenAfterAutoClose +
                ",\n     lowerClosePrice=" + lowerClosePrice +
                ",\n     upperClosePrice=" + upperClosePrice +
                ",\n     isPrivateOffer=" + isPrivateOffer +
                ",\n     hashOfChallenge='" + hashOfChallenge + '\'' +
                ",\n     extraDataMap=" + extraDataMap +
                ",\n     protocolVersion=" + protocolVersion +
                "\n}";
    }
}
