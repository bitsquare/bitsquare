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

package bisq.core.trade;

import bisq.core.locale.CurrencyUtil;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.FeeTxOfferPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.proto.CoreProtoResolver;
import bisq.core.util.VolumeUtil;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.PubKeyRing;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.util.JsonExclude;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import org.bitcoinj.core.Coin;

import org.apache.commons.lang3.StringUtils;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
@Value
public final class Contract implements NetworkPayload {
    private final FeeTxOfferPayload offerPayload;
    private final long tradeAmount;
    private final long tradePrice;
    private final String takerFeeTxID;
    private final NodeAddress buyerNodeAddress;
    private final NodeAddress sellerNodeAddress;
    private final NodeAddress mediatorNodeAddress;
    private final boolean isBuyerMakerAndSellerTaker;
    private final String makerAccountId;
    private final String takerAccountId;
    private final PaymentAccountPayload makerPaymentAccountPayload;
    private final PaymentAccountPayload takerPaymentAccountPayload;
    @JsonExclude
    private final PubKeyRing makerPubKeyRing;
    @JsonExclude
    private final PubKeyRing takerPubKeyRing;
    private final String makerPayoutAddressString;
    private final String takerPayoutAddressString;
    @JsonExclude
    private final byte[] makerMultiSigPubKey;
    @JsonExclude
    private final byte[] takerMultiSigPubKey;

    // Added in v1.2.0
    private long lockTime;
    private final NodeAddress refundAgentNodeAddress;

    public Contract(FeeTxOfferPayload offerPayload,
                    long tradeAmount,
                    long tradePrice,
                    String takerFeeTxID,
                    NodeAddress buyerNodeAddress,
                    NodeAddress sellerNodeAddress,
                    NodeAddress mediatorNodeAddress,
                    boolean isBuyerMakerAndSellerTaker,
                    String makerAccountId,
                    String takerAccountId,
                    PaymentAccountPayload makerPaymentAccountPayload,
                    PaymentAccountPayload takerPaymentAccountPayload,
                    PubKeyRing makerPubKeyRing,
                    PubKeyRing takerPubKeyRing,
                    String makerPayoutAddressString,
                    String takerPayoutAddressString,
                    byte[] makerMultiSigPubKey,
                    byte[] takerMultiSigPubKey,
                    long lockTime,
                    NodeAddress refundAgentNodeAddress) {
        this.offerPayload = offerPayload;
        this.tradeAmount = tradeAmount;
        this.tradePrice = tradePrice;
        this.takerFeeTxID = takerFeeTxID;
        this.buyerNodeAddress = buyerNodeAddress;
        this.sellerNodeAddress = sellerNodeAddress;
        this.mediatorNodeAddress = mediatorNodeAddress;
        this.isBuyerMakerAndSellerTaker = isBuyerMakerAndSellerTaker;
        this.makerAccountId = makerAccountId;
        this.takerAccountId = takerAccountId;
        this.makerPaymentAccountPayload = makerPaymentAccountPayload;
        this.takerPaymentAccountPayload = takerPaymentAccountPayload;
        this.makerPubKeyRing = makerPubKeyRing;
        this.takerPubKeyRing = takerPubKeyRing;
        this.makerPayoutAddressString = makerPayoutAddressString;
        this.takerPayoutAddressString = takerPayoutAddressString;
        this.makerMultiSigPubKey = makerMultiSigPubKey;
        this.takerMultiSigPubKey = takerMultiSigPubKey;
        this.lockTime = lockTime;
        this.refundAgentNodeAddress = refundAgentNodeAddress;

        String makerPaymentMethodId = makerPaymentAccountPayload.getPaymentMethodId();
        String takerPaymentMethodId = takerPaymentAccountPayload.getPaymentMethodId();
        // For SEPA offers we accept also SEPA_INSTANT takers
        // Otherwise both ids need to be the same
        boolean result = (makerPaymentMethodId.equals(PaymentMethod.SEPA_ID) && takerPaymentMethodId.equals(PaymentMethod.SEPA_INSTANT_ID)) ||
                makerPaymentMethodId.equals(takerPaymentMethodId);
        checkArgument(result, "payment methods of maker and taker must be the same.\n" +
                "makerPaymentMethodId=" + makerPaymentMethodId + "\n" +
                "takerPaymentMethodId=" + takerPaymentMethodId);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Contract fromProto(protobuf.Contract proto, CoreProtoResolver coreProtoResolver) {
        return new Contract(FeeTxOfferPayload.fromProto(proto.getFeeTxOfferPayload()),
                proto.getTradeAmount(),
                proto.getTradePrice(),
                proto.getTakerFeeTxId(),
                NodeAddress.fromProto(proto.getBuyerNodeAddress()),
                NodeAddress.fromProto(proto.getSellerNodeAddress()),
                NodeAddress.fromProto(proto.getMediatorNodeAddress()),
                proto.getIsBuyerMakerAndSellerTaker(),
                proto.getMakerAccountId(),
                proto.getTakerAccountId(),
                coreProtoResolver.fromProto(proto.getMakerPaymentAccountPayload()),
                coreProtoResolver.fromProto(proto.getTakerPaymentAccountPayload()),
                PubKeyRing.fromProto(proto.getMakerPubKeyRing()),
                PubKeyRing.fromProto(proto.getTakerPubKeyRing()),
                proto.getMakerPayoutAddressString(),
                proto.getTakerPayoutAddressString(),
                proto.getMakerMultiSigPubKey().toByteArray(),
                proto.getTakerMultiSigPubKey().toByteArray(),
                proto.getLockTime(),
                NodeAddress.fromProto(proto.getRefundAgentNodeAddress()));
    }

    @Override
    public protobuf.Contract toProtoMessage() {
        return protobuf.Contract.newBuilder()
                .setFeeTxOfferPayload(offerPayload.toProtoMessage().getFeeTxOfferPayload())
                .setTradeAmount(tradeAmount)
                .setTradePrice(tradePrice)
                .setTakerFeeTxId(takerFeeTxID)
                .setBuyerNodeAddress(buyerNodeAddress.toProtoMessage())
                .setSellerNodeAddress(sellerNodeAddress.toProtoMessage())
                .setMediatorNodeAddress(mediatorNodeAddress.toProtoMessage())
                .setIsBuyerMakerAndSellerTaker(isBuyerMakerAndSellerTaker)
                .setMakerAccountId(makerAccountId)
                .setTakerAccountId(takerAccountId)
                .setMakerPaymentAccountPayload((protobuf.PaymentAccountPayload) makerPaymentAccountPayload.toProtoMessage())
                .setTakerPaymentAccountPayload((protobuf.PaymentAccountPayload) takerPaymentAccountPayload.toProtoMessage())
                .setMakerPubKeyRing(makerPubKeyRing.toProtoMessage())
                .setTakerPubKeyRing(takerPubKeyRing.toProtoMessage())
                .setMakerPayoutAddressString(makerPayoutAddressString)
                .setTakerPayoutAddressString(takerPayoutAddressString)
                .setMakerMultiSigPubKey(ByteString.copyFrom(makerMultiSigPubKey))
                .setTakerMultiSigPubKey(ByteString.copyFrom(takerMultiSigPubKey))
                .setLockTime(lockTime)
                .setRefundAgentNodeAddress(refundAgentNodeAddress.toProtoMessage())
                .build();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getBuyerPayoutAddressString() {
        return isBuyerMakerAndSellerTaker ? makerPayoutAddressString : takerPayoutAddressString;
    }

    public String getSellerPayoutAddressString() {
        return isBuyerMakerAndSellerTaker ? takerPayoutAddressString : makerPayoutAddressString;
    }

    public PubKeyRing getBuyerPubKeyRing() {
        return isBuyerMakerAndSellerTaker ? makerPubKeyRing : takerPubKeyRing;
    }

    public PubKeyRing getSellerPubKeyRing() {
        return isBuyerMakerAndSellerTaker ? takerPubKeyRing : makerPubKeyRing;
    }

    public byte[] getBuyerMultiSigPubKey() {
        return isBuyerMakerAndSellerTaker ? makerMultiSigPubKey : takerMultiSigPubKey;
    }

    public byte[] getSellerMultiSigPubKey() {
        return isBuyerMakerAndSellerTaker ? takerMultiSigPubKey : makerMultiSigPubKey;
    }

    public PaymentAccountPayload getBuyerPaymentAccountPayload() {
        return isBuyerMakerAndSellerTaker ? makerPaymentAccountPayload : takerPaymentAccountPayload;
    }

    public PaymentAccountPayload getSellerPaymentAccountPayload() {
        return isBuyerMakerAndSellerTaker ? takerPaymentAccountPayload : makerPaymentAccountPayload;
    }

    public String getPaymentMethodId() {
        return makerPaymentAccountPayload.getPaymentMethodId();
    }

    public Coin getTradeAmount() {
        return Coin.valueOf(tradeAmount);
    }

    public Volume getTradeVolume() {
        Volume volumeByAmount = getTradePrice().getVolumeByAmount(getTradeAmount());

        if (getPaymentMethodId().equals(PaymentMethod.HAL_CASH_ID))
            volumeByAmount = VolumeUtil.getAdjustedVolumeForHalCash(volumeByAmount);
        else if (CurrencyUtil.isFiatCurrency(getOfferPayload().getCurrencyCode()))
            volumeByAmount = VolumeUtil.getRoundedFiatVolume(volumeByAmount);

        return volumeByAmount;
    }

    public Price getTradePrice() {
        return Price.valueOf(offerPayload.getCurrencyCode(), tradePrice);
    }

    public NodeAddress getMyNodeAddress(PubKeyRing myPubKeyRing) {
        if (myPubKeyRing.equals(getBuyerPubKeyRing()))
            return buyerNodeAddress;
        else
            return sellerNodeAddress;
    }

    public NodeAddress getPeersNodeAddress(PubKeyRing myPubKeyRing) {
        if (myPubKeyRing.equals(getSellerPubKeyRing()))
            return buyerNodeAddress;
        else
            return sellerNodeAddress;
    }

    public PubKeyRing getPeersPubKeyRing(PubKeyRing myPubKeyRing) {
        if (myPubKeyRing.equals(getSellerPubKeyRing()))
            return getBuyerPubKeyRing();
        else
            return getSellerPubKeyRing();
    }

    public boolean isMyRoleBuyer(PubKeyRing myPubKeyRing) {
        return getBuyerPubKeyRing().equals(myPubKeyRing);
    }

    public boolean isMyRoleMaker(PubKeyRing myPubKeyRing) {
        return isBuyerMakerAndSellerTaker() == isMyRoleBuyer(myPubKeyRing);
    }

    public void printDiff(@Nullable String peersContractAsJson) {
        String json = Utilities.objectToJson(this);
        String diff = StringUtils.difference(json, peersContractAsJson);
        if (!diff.isEmpty()) {
            log.warn("Diff of both contracts: \n" + diff);
            log.warn("\n\n------------------------------------------------------------\n"
                    + "Contract as json\n"
                    + json
                    + "\n------------------------------------------------------------\n");

            log.warn("\n\n------------------------------------------------------------\n"
                    + "Peers contract as json\n"
                    + peersContractAsJson
                    + "\n------------------------------------------------------------\n");
        } else {
            log.debug("Both contracts are the same");
        }
    }

    @Override
    public String toString() {
        return "Contract{" +
                "\n     offerPayload=" + offerPayload +
                ",\n     tradeAmount=" + tradeAmount +
                ",\n     tradePrice=" + tradePrice +
                ",\n     takerFeeTxID='" + takerFeeTxID + '\'' +
                ",\n     buyerNodeAddress=" + buyerNodeAddress +
                ",\n     sellerNodeAddress=" + sellerNodeAddress +
                ",\n     mediatorNodeAddress=" + mediatorNodeAddress +
                ",\n     refundAgentNodeAddress=" + refundAgentNodeAddress +
                ",\n     isBuyerMakerAndSellerTaker=" + isBuyerMakerAndSellerTaker +
                ",\n     makerAccountId='" + makerAccountId + '\'' +
                ",\n     takerAccountId='" + takerAccountId + '\'' +
                ",\n     makerPaymentAccountPayload=" + makerPaymentAccountPayload +
                ",\n     takerPaymentAccountPayload=" + takerPaymentAccountPayload +
                ",\n     makerPubKeyRing=" + makerPubKeyRing +
                ",\n     takerPubKeyRing=" + takerPubKeyRing +
                ",\n     makerPayoutAddressString='" + makerPayoutAddressString + '\'' +
                ",\n     takerPayoutAddressString='" + takerPayoutAddressString + '\'' +
                ",\n     makerMultiSigPubKey=" + Utilities.bytesAsHexString(makerMultiSigPubKey) +
                ",\n     takerMultiSigPubKey=" + Utilities.bytesAsHexString(takerMultiSigPubKey) +
                ",\n     buyerMultiSigPubKey=" + Utilities.bytesAsHexString(getBuyerMultiSigPubKey()) +
                ",\n     sellerMultiSigPubKey=" + Utilities.bytesAsHexString(getSellerMultiSigPubKey()) +
                ",\n     lockTime=" + lockTime +
                "\n}";
    }
}
