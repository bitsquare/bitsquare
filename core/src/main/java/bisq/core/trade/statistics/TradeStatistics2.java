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

package bisq.core.trade.statistics;

import bisq.core.monetary.Altcoin;
import bisq.core.monetary.AltcoinExchangeRate;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.FeeTxOfferPayload;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.trade.Trade;
import bisq.core.util.VolumeUtil;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProcessOncePersistableNetworkPayload;

import bisq.common.app.Capabilities;
import bisq.common.app.Capability;
import bisq.common.crypto.Hash;
import bisq.common.proto.ProtoUtil;
import bisq.common.util.CollectionUtils;
import bisq.common.util.ExtraDataMapValidator;
import bisq.common.util.JsonExclude;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;

import com.google.common.base.Charsets;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Serialized size is about 180-210 byte. Nov 2017 we have 5500 objects
 */
@Deprecated
@Slf4j
@Value
public final class TradeStatistics2 implements ProcessOncePersistableNetworkPayload, PersistableNetworkPayload,
        CapabilityRequiringPayload, Comparable<TradeStatistics2> {

    public static TradeStatistics2 from(Trade trade,
                                        @Nullable String referralId,
                                        boolean isTorNetworkNode) {
        Map<String, String> extraDataMap = new HashMap<>();
        if (referralId != null) {
            extraDataMap.put(OfferPayload.REFERRAL_ID, referralId);
        }

        NodeAddress mediatorNodeAddress = trade.getMediatorNodeAddress();
        if (mediatorNodeAddress != null) {
            // The first 4 chars are sufficient to identify a mediator.
            // For testing with regtest/localhost we use the full address as its localhost and would result in
            // same values for multiple mediators.
            String address = isTorNetworkNode ?
                    mediatorNodeAddress.getFullAddress().substring(0, 4) :
                    mediatorNodeAddress.getFullAddress();
            extraDataMap.put(TradeStatistics2.MEDIATOR_ADDRESS, address);
        }

        Offer offer = trade.getOffer();
        checkNotNull(offer, "offer must not ne null");
        checkNotNull(trade.getTradeAmount(), "trade.getTradeAmount() must not ne null");

        checkArgument(offer.getOfferPayload() instanceof FeeTxOfferPayload);
        FeeTxOfferPayload offerPayload = (FeeTxOfferPayload) offer.getOfferPayload();

        return new TradeStatistics2(offerPayload,
                trade.getTradePrice(),
                trade.getTradeAmount(),
                trade.getDate(),
                trade.getDepositTxId(),
                extraDataMap);
    }

    @SuppressWarnings("SpellCheckingInspection")
    public static final String MEDIATOR_ADDRESS = "medAddr";
    @SuppressWarnings("SpellCheckingInspection")
    public static final String REFUND_AGENT_ADDRESS = "refAddr";

    private final OfferPayload.Direction direction;
    private final String baseCurrency;
    private final String counterCurrency;
    private final String offerPaymentMethod;
    private final long offerDate;
    private final boolean offerUseMarketBasedPrice;
    private final double offerMarketPriceMargin;
    private final long offerAmount;
    private final long offerMinAmount;
    private final String offerId;
    private final long tradePrice;
    private final long tradeAmount;
    // tradeDate is different for both peers so we ignore it for hash
    @JsonExclude
    private final long tradeDate;
    @JsonExclude
    @Nullable
    private final String depositTxId;

    // Hash get set in constructor from json of all the other data fields (with hash = null).
    @JsonExclude
    private final byte[] hash;
    // PB field signature_pub_key_bytes not used anymore from v0.6 on

    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    @JsonExclude
    private Map<String, String> extraDataMap;

    public TradeStatistics2(FeeTxOfferPayload offerPayload,
                            Price tradePrice,
                            Coin tradeAmount,
                            Date tradeDate,
                            String depositTxId,
                            Map<String, String> extraDataMap) {
        this(offerPayload.getDirection(),
                offerPayload.getBaseCurrencyCode(),
                offerPayload.getCounterCurrencyCode(),
                offerPayload.getPaymentMethodId(),
                offerPayload.getDate(),
                offerPayload.isUseMarketBasedPrice(),
                offerPayload.getMarketPriceMargin(),
                offerPayload.getAmount(),
                offerPayload.getMinAmount(),
                offerPayload.getId(),
                tradePrice.getValue(),
                tradeAmount.value,
                tradeDate.getTime(),
                depositTxId,
                null,
                extraDataMap);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TradeStatistics2(OfferPayload.Direction direction,
                            String baseCurrency,
                            String counterCurrency,
                            String offerPaymentMethod,
                            long offerDate,
                            boolean offerUseMarketBasedPrice,
                            double offerMarketPriceMargin,
                            long offerAmount,
                            long offerMinAmount,
                            String offerId,
                            long tradePrice,
                            long tradeAmount,
                            long tradeDate,
                            @Nullable String depositTxId,
                            @Nullable byte[] hash,
                            @Nullable Map<String, String> extraDataMap) {
        this.direction = direction;
        this.baseCurrency = baseCurrency;
        this.counterCurrency = counterCurrency;
        this.offerPaymentMethod = offerPaymentMethod;
        this.offerDate = offerDate;
        this.offerUseMarketBasedPrice = offerUseMarketBasedPrice;
        this.offerMarketPriceMargin = offerMarketPriceMargin;
        this.offerAmount = offerAmount;
        this.offerMinAmount = offerMinAmount;
        this.offerId = offerId;
        this.tradePrice = tradePrice;
        this.tradeAmount = tradeAmount;
        this.tradeDate = tradeDate;
        this.depositTxId = depositTxId;
        this.extraDataMap = ExtraDataMapValidator.getValidatedExtraDataMap(extraDataMap);

        this.hash = hash == null ? createHash() : hash;
    }

    public byte[] createHash() {
        // We create hash from all fields excluding hash itself. We use json as simple data serialisation.
        // TradeDate is different for both peers so we ignore it for hash. ExtraDataMap is ignored as well as at
        // software updates we might have different entries which would cause a different hash.
        return Hash.getSha256Ripemd160hash(Utilities.objectToJson(this).getBytes(Charsets.UTF_8));
    }

    private protobuf.TradeStatistics2.Builder getBuilder() {
        final protobuf.TradeStatistics2.Builder builder = protobuf.TradeStatistics2.newBuilder()
                .setDirection(FeeTxOfferPayload.toProtoMessage(direction))
                .setBaseCurrency(baseCurrency)
                .setCounterCurrency(counterCurrency)
                .setPaymentMethodId(offerPaymentMethod)
                .setOfferDate(offerDate)
                .setOfferUseMarketBasedPrice(offerUseMarketBasedPrice)
                .setOfferMarketPriceMargin(offerMarketPriceMargin)
                .setOfferAmount(offerAmount)
                .setOfferMinAmount(offerMinAmount)
                .setOfferId(offerId)
                .setTradePrice(tradePrice)
                .setTradeAmount(tradeAmount)
                .setTradeDate(tradeDate)
                .setHash(ByteString.copyFrom(hash));
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);
        Optional.ofNullable(depositTxId).ifPresent(builder::setDepositTxId);
        return builder;
    }

    public protobuf.TradeStatistics2 toProtoTradeStatistics2() {
        return getBuilder().build();
    }

    @Override
    public protobuf.PersistableNetworkPayload toProtoMessage() {
        return protobuf.PersistableNetworkPayload.newBuilder().setTradeStatistics2(getBuilder()).build();
    }

    public static TradeStatistics2 fromProto(protobuf.TradeStatistics2 proto) {
        return new TradeStatistics2(
                FeeTxOfferPayload.fromProto(proto.getDirection()),
                proto.getBaseCurrency(),
                proto.getCounterCurrency(),
                proto.getPaymentMethodId(),
                proto.getOfferDate(),
                proto.getOfferUseMarketBasedPrice(),
                proto.getOfferMarketPriceMargin(),
                proto.getOfferAmount(),
                proto.getOfferMinAmount(),
                proto.getOfferId(),
                proto.getTradePrice(),
                proto.getTradeAmount(),
                proto.getTradeDate(),
                ProtoUtil.stringOrNullFromProto(proto.getDepositTxId()),
                null,   // We want to clean up the hashes with the changed hash method in v.1.2.0 so we don't use the value from the proto
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public byte[] getHash() {
        return hash;
    }

    @Override
    public boolean verifyHashSize() {
        checkNotNull(hash, "hash must not be null");
        return hash.length == 20;
    }

    // With v1.2.0 we changed the way how the hash is created. To not create too heavy load for seed nodes from
    // requests from old nodes we use the TRADE_STATISTICS_HASH_UPDATE capability to send trade statistics only to new
    // nodes. As trade statistics are only used for informational purpose it will not have any critical issue for the
    // old nodes beside that they don't see the latest trades. We added TRADE_STATISTICS_HASH_UPDATE in v1.2.2 to fix a
    // problem of not handling the hashes correctly.
    @Override
    public Capabilities getRequiredCapabilities() {
        return new Capabilities(Capability.TRADE_STATISTICS_HASH_UPDATE);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////


    public Date getTradeDate() {
        return new Date(tradeDate);
    }

    public Price getTradePrice() {
        return Price.valueOf(getCurrencyCode(), tradePrice);
    }

    public String getCurrencyCode() {
        return baseCurrency.equals("BTC") ? counterCurrency : baseCurrency;
    }

    public Coin getTradeAmount() {
        return Coin.valueOf(tradeAmount);
    }

    public Volume getTradeVolume() {
        if (getTradePrice().getMonetary() instanceof Altcoin) {
            return new Volume(new AltcoinExchangeRate((Altcoin) getTradePrice().getMonetary()).coinToAltcoin(getTradeAmount()));
        } else {
            Volume volume = new Volume(new ExchangeRate((Fiat) getTradePrice().getMonetary()).coinToFiat(getTradeAmount()));
            return VolumeUtil.getRoundedFiatVolume(volume);
        }
    }

    public boolean isValid() {
        // Exclude a disputed BSQ trade where the price was off by a factor 10 due to a mistake by the maker.
        // Since the trade wasn't executed it's better to filter it out to avoid it having an undue influence on the
        // BSQ trade stats.
        boolean excludedFailedTrade = offerId.equals("6E5KOI6O-3a06a037-6f03-4bfa-98c2-59f49f73466a-112");
        boolean depositTxIdValid = depositTxId == null || !depositTxId.isEmpty();
        return tradeAmount > 0 && tradePrice > 0 && !excludedFailedTrade && depositTxIdValid;
    }

    // TODO: Can be removed as soon as everyone uses v1.2.6+
    @Override
    public int compareTo(@NotNull TradeStatistics2 o) {
        if (direction.equals(o.direction) &&
                baseCurrency.equals(o.baseCurrency) &&
                counterCurrency.equals(o.counterCurrency) &&
                offerPaymentMethod.equals(o.offerPaymentMethod) &&
                offerDate == o.offerDate &&
                offerUseMarketBasedPrice == o.offerUseMarketBasedPrice &&
                offerAmount == o.offerAmount &&
                offerMinAmount == o.offerMinAmount &&
                offerId.equals(o.offerId) &&
                tradePrice == o.tradePrice &&
                tradeAmount == o.tradeAmount) {
            return 0;
        }

        return -1;
    }

    @Override
    public String toString() {
        return "TradeStatistics2{" +
                "\n     direction=" + direction +
                ",\n     baseCurrency='" + baseCurrency + '\'' +
                ",\n     counterCurrency='" + counterCurrency + '\'' +
                ",\n     offerPaymentMethod='" + offerPaymentMethod + '\'' +
                ",\n     offerDate=" + offerDate +
                ",\n     offerUseMarketBasedPrice=" + offerUseMarketBasedPrice +
                ",\n     offerMarketPriceMargin=" + offerMarketPriceMargin +
                ",\n     offerAmount=" + offerAmount +
                ",\n     offerMinAmount=" + offerMinAmount +
                ",\n     offerId='" + offerId + '\'' +
                ",\n     tradePrice=" + tradePrice +
                ",\n     tradeAmount=" + tradeAmount +
                ",\n     tradeDate=" + tradeDate +
                ",\n     depositTxId='" + depositTxId + '\'' +
                ",\n     hash=" + Utilities.bytesAsHexString(hash) +
                ",\n     extraDataMap=" + extraDataMap +
                "\n}";
    }
}
