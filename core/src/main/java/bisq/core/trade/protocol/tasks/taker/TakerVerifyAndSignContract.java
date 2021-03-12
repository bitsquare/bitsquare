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

package bisq.core.trade.protocol.tasks.taker;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.FeeTxOfferPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.trade.Contract;
import bisq.core.trade.SellerAsTakerTrade;
import bisq.core.trade.Trade;
import bisq.core.trade.protocol.TradingPeer;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.Hash;
import bisq.common.crypto.Sig;
import bisq.common.taskrunner.TaskRunner;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class TakerVerifyAndSignContract extends TradeTask {
    public TakerVerifyAndSignContract(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            String takerFeeTxId = checkNotNull(processModel.getTakeOfferFeeTxId());
            TradingPeer maker = processModel.getTradingPeer();
            PaymentAccountPayload makerPaymentAccountPayload = checkNotNull(maker.getPaymentAccountPayload());
            PaymentAccountPayload takerPaymentAccountPayload = checkNotNull(processModel.getPaymentAccountPayload(trade));

            boolean isBuyerMakerAndSellerTaker = trade instanceof SellerAsTakerTrade;
            NodeAddress buyerNodeAddress = isBuyerMakerAndSellerTaker ?
                    processModel.getTempTradingPeerNodeAddress() :
                    processModel.getMyNodeAddress();
            NodeAddress sellerNodeAddress = isBuyerMakerAndSellerTaker ?
                    processModel.getMyNodeAddress() :
                    processModel.getTempTradingPeerNodeAddress();

            BtcWalletService walletService = processModel.getBtcWalletService();
            String id = processModel.getOffer().getId();
            AddressEntry takerPayoutAddressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.TRADE_PAYOUT);
            String takerPayoutAddressString = takerPayoutAddressEntry.getAddressString();
            AddressEntry takerMultiSigAddressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.MULTI_SIG);
            byte[] takerMultiSigPubKey = processModel.getMyMultiSigPubKey();
            checkArgument(Arrays.equals(takerMultiSigPubKey,
                    takerMultiSigAddressEntry.getPubKey()),
                    "takerMultiSigPubKey from AddressEntry must match the one from the trade data. trade id =" + id);

            Coin tradeAmount = checkNotNull(trade.getTradeAmount());

            checkArgument(processModel.getOffer().getOfferPayload() instanceof FeeTxOfferPayload);
            FeeTxOfferPayload offerPayload = (FeeTxOfferPayload) processModel.getOffer().getOfferPayload();

            Contract contract = new Contract(
                    offerPayload,
                    tradeAmount.value,
                    trade.getTradePrice().getValue(),
                    takerFeeTxId,
                    buyerNodeAddress,
                    sellerNodeAddress,
                    trade.getMediatorNodeAddress(),
                    isBuyerMakerAndSellerTaker,
                    maker.getAccountId(),
                    processModel.getAccountId(),
                    makerPaymentAccountPayload,
                    takerPaymentAccountPayload,
                    maker.getPubKeyRing(),
                    processModel.getPubKeyRing(),
                    maker.getPayoutAddressString(),
                    takerPayoutAddressString,
                    maker.getMultiSigPubKey(),
                    takerMultiSigPubKey,
                    trade.getLockTime(),
                    trade.getRefundAgentNodeAddress()
            );
            String contractAsJson = Utilities.objectToJson(contract);

            if (!contractAsJson.equals(processModel.getTradingPeer().getContractAsJson())) {
                contract.printDiff(processModel.getTradingPeer().getContractAsJson());
                failed("Contracts are not matching");
            }

            String signature = Sig.sign(processModel.getKeyRing().getSignatureKeyPair().getPrivate(), contractAsJson);
            trade.setContract(contract);
            trade.setContractAsJson(contractAsJson);

            byte[] contractHash = Hash.getSha256Hash(checkNotNull(contractAsJson));
            trade.setContractHash(contractHash);

            trade.setTakerContractSignature(signature);

            processModel.getTradeManager().requestPersistence();
            try {
                checkNotNull(maker.getPubKeyRing(), "maker.getPubKeyRing() must nto be null");
                Sig.verify(maker.getPubKeyRing().getSignaturePubKey(),
                        contractAsJson,
                        maker.getContractSignature());
                complete();
            } catch (Throwable t) {
                failed("Contract signature verification failed. " + t.getMessage());
            }
        } catch (Throwable t) {
            failed(t);
        }
    }
}
