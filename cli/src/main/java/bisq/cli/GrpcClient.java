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

package bisq.cli;

import bisq.proto.grpc.AddressBalanceInfo;
import bisq.proto.grpc.BalancesInfo;
import bisq.proto.grpc.BsqBalanceInfo;
import bisq.proto.grpc.BtcBalanceInfo;
import bisq.proto.grpc.GetMethodHelpRequest;
import bisq.proto.grpc.GetVersionRequest;
import bisq.proto.grpc.OfferInfo;
import bisq.proto.grpc.RegisterDisputeAgentRequest;
import bisq.proto.grpc.StopRequest;
import bisq.proto.grpc.TakeOfferReply;
import bisq.proto.grpc.TradeInfo;
import bisq.proto.grpc.TxFeeRateInfo;
import bisq.proto.grpc.TxInfo;

import protobuf.PaymentAccount;
import protobuf.PaymentMethod;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static bisq.proto.grpc.EditOfferRequest.EditType;



import bisq.cli.request.OffersServiceRequest;
import bisq.cli.request.PaymentAccountsServiceRequest;
import bisq.cli.request.TradesServiceRequest;
import bisq.cli.request.WalletsServiceRequest;


@SuppressWarnings("ResultOfMethodCallIgnored")
@Slf4j
public final class GrpcClient {

    private final GrpcStubs grpcStubs;
    private final OffersServiceRequest offersServiceRequest;
    private final TradesServiceRequest tradesServiceRequest;
    private final WalletsServiceRequest walletsServiceRequest;
    private final PaymentAccountsServiceRequest paymentAccountsServiceRequest;

    public GrpcClient(String apiHost,
                      int apiPort,
                      String apiPassword) {
        this.grpcStubs = new GrpcStubs(apiHost, apiPort, apiPassword);
        this.offersServiceRequest = new OffersServiceRequest(grpcStubs);
        this.tradesServiceRequest = new TradesServiceRequest(grpcStubs);
        this.walletsServiceRequest = new WalletsServiceRequest(grpcStubs);
        this.paymentAccountsServiceRequest = new PaymentAccountsServiceRequest(grpcStubs);
    }

    public String getVersion() {
        var request = GetVersionRequest.newBuilder().build();
        return grpcStubs.versionService.getVersion(request).getVersion();
    }

    public BalancesInfo getBalances() {
        return walletsServiceRequest.getBalances();
    }

    public BsqBalanceInfo getBsqBalances() {
        return walletsServiceRequest.getBsqBalances();
    }

    public BtcBalanceInfo getBtcBalances() {
        return walletsServiceRequest.getBtcBalances();
    }

    public BalancesInfo getBalances(String currencyCode) {
        return walletsServiceRequest.getBalances(currencyCode);
    }

    public AddressBalanceInfo getAddressBalance(String address) {
        return walletsServiceRequest.getAddressBalance(address);
    }

    public double getBtcPrice(String currencyCode) {
        return walletsServiceRequest.getBtcPrice(currencyCode);
    }

    public List<AddressBalanceInfo> getFundingAddresses() {
        return walletsServiceRequest.getFundingAddresses();
    }

    public String getUnusedBsqAddress() {
        return walletsServiceRequest.getUnusedBsqAddress();
    }

    public String getUnusedBtcAddress() {
        return walletsServiceRequest.getUnusedBtcAddress();
    }

    public TxInfo sendBsq(String address, String amount, String txFeeRate) {
        return walletsServiceRequest.sendBsq(address, amount, txFeeRate);
    }

    public TxInfo sendBtc(String address, String amount, String txFeeRate, String memo) {
        return walletsServiceRequest.sendBtc(address, amount, txFeeRate, memo);
    }

    public boolean verifyBsqSentToAddress(String address, String amount) {
        return walletsServiceRequest.verifyBsqSentToAddress(address, amount);
    }

    public TxFeeRateInfo getTxFeeRate() {
        return walletsServiceRequest.getTxFeeRate();
    }

    public TxFeeRateInfo setTxFeeRate(long txFeeRate) {
        return walletsServiceRequest.setTxFeeRate(txFeeRate);
    }

    public TxFeeRateInfo unsetTxFeeRate() {
        return walletsServiceRequest.unsetTxFeeRate();
    }

    public TxInfo getTransaction(String txId) {
        return walletsServiceRequest.getTransaction(txId);
    }

    public OfferInfo createFixedPricedOffer(String direction,
                                            String currencyCode,
                                            long amount,
                                            long minAmount,
                                            String fixedPrice,
                                            double securityDeposit,
                                            String paymentAcctId,
                                            String makerFeeCurrencyCode) {
        return offersServiceRequest.createOffer(direction,
                currencyCode,
                amount,
                minAmount,
                false,
                fixedPrice,
                0.00,
                securityDeposit,
                paymentAcctId,
                makerFeeCurrencyCode,
                0 /* no trigger price */);
    }

    public OfferInfo createMarketBasedPricedOffer(String direction,
                                                  String currencyCode,
                                                  long amount,
                                                  long minAmount,
                                                  double marketPriceMargin,
                                                  double securityDeposit,
                                                  String paymentAcctId,
                                                  String makerFeeCurrencyCode,
                                                  long triggerPrice) {
        return offersServiceRequest.createOffer(direction,
                currencyCode,
                amount,
                minAmount,
                true,
                "0",
                marketPriceMargin,
                securityDeposit,
                paymentAcctId,
                makerFeeCurrencyCode,
                triggerPrice);
    }

    public OfferInfo createOffer(String direction,
                                 String currencyCode,
                                 long amount,
                                 long minAmount,
                                 boolean useMarketBasedPrice,
                                 String fixedPrice,
                                 double marketPriceMargin,
                                 double securityDeposit,
                                 String paymentAcctId,
                                 String makerFeeCurrencyCode,
                                 long triggerPrice) {
        return offersServiceRequest.createOffer(direction,
                currencyCode,
                amount,
                minAmount,
                useMarketBasedPrice,
                fixedPrice,
                marketPriceMargin,
                securityDeposit,
                paymentAcctId,
                makerFeeCurrencyCode,
                triggerPrice);
    }

    public void editOfferActivationState(String offerId, int enable) {
        offersServiceRequest.editOfferActivationState(offerId, enable);
    }

    public void editOfferFixedPrice(String offerId, String priceAsString) {
        offersServiceRequest.editOfferFixedPrice(offerId, priceAsString);
    }

    public void editOfferPriceMargin(String offerId, double marketPriceMargin) {
        offersServiceRequest.editOfferPriceMargin(offerId, marketPriceMargin);
    }

    public void editOfferTriggerPrice(String offerId, long triggerPrice) {
        offersServiceRequest.editOfferTriggerPrice(offerId, triggerPrice);
    }

    public void editOffer(String offerId,
                          String priceAsString,
                          boolean useMarketBasedPrice,
                          double marketPriceMargin,
                          long triggerPrice,
                          int enable,
                          EditType editType) {
        // Take care when using this method directly:
        //  useMarketBasedPrice = true if margin based offer, false for fixed priced offer
        //  scaledPriceString fmt = ######.####
        offersServiceRequest.editOffer(offerId,
                priceAsString,
                useMarketBasedPrice,
                marketPriceMargin,
                triggerPrice,
                enable,
                editType);
    }

    public void cancelOffer(String offerId) {
        offersServiceRequest.cancelOffer(offerId);
    }

    public OfferInfo getOffer(String offerId) {
        return offersServiceRequest.getOffer(offerId);
    }

    public OfferInfo getMyOffer(String offerId) {
        return offersServiceRequest.getMyOffer(offerId);
    }

    public List<OfferInfo> getOffers(String direction, String currencyCode) {
        return offersServiceRequest.getOffers(direction, currencyCode);
    }

    public List<OfferInfo> getCryptoCurrencyOffers(String direction, String currencyCode) {
        return offersServiceRequest.getCryptoCurrencyOffers(direction, currencyCode);
    }

    public List<OfferInfo> getOffersSortedByDate(String currencyCode) {
        return offersServiceRequest.getOffersSortedByDate(currencyCode);
    }

    public List<OfferInfo> getOffersSortedByDate(String direction, String currencyCode) {
        return offersServiceRequest.getOffersSortedByDate(direction, currencyCode);
    }

    public List<OfferInfo> getBsqOffersSortedByDate() {
        return offersServiceRequest.getBsqOffersSortedByDate();
    }

    public List<OfferInfo> getMyOffers(String direction, String currencyCode) {
        return offersServiceRequest.getMyOffers(direction, currencyCode);
    }

    public List<OfferInfo> getMyCryptoCurrencyOffers(String direction, String currencyCode) {
        return offersServiceRequest.getMyCryptoCurrencyOffers(direction, currencyCode);
    }

    public List<OfferInfo> getMyOffersSortedByDate(String direction, String currencyCode) {
        return offersServiceRequest.getMyOffersSortedByDate(direction, currencyCode);
    }

    public List<OfferInfo> getMyOffersSortedByDate(String currencyCode) {
        return offersServiceRequest.getMyOffersSortedByDate(currencyCode);
    }

    public List<OfferInfo> getMyBsqOffersSortedByDate() {
        return offersServiceRequest.getMyBsqOffersSortedByDate();
    }

    public OfferInfo getMostRecentOffer(String direction, String currencyCode) {
        return offersServiceRequest.getMostRecentOffer(direction, currencyCode);
    }

    public List<OfferInfo> sortOffersByDate(List<OfferInfo> offerInfoList) {
        return offersServiceRequest.sortOffersByDate(offerInfoList);
    }

    public TakeOfferReply getTakeOfferReply(String offerId, String paymentAccountId, String takerFeeCurrencyCode) {
        return tradesServiceRequest.getTakeOfferReply(offerId, paymentAccountId, takerFeeCurrencyCode);
    }

    public TradeInfo takeOffer(String offerId, String paymentAccountId, String takerFeeCurrencyCode) {
        return tradesServiceRequest.takeOffer(offerId, paymentAccountId, takerFeeCurrencyCode);
    }

    public TradeInfo getTrade(String tradeId) {
        return tradesServiceRequest.getTrade(tradeId);
    }

    public void confirmPaymentStarted(String tradeId) {
        tradesServiceRequest.confirmPaymentStarted(tradeId);
    }

    public void confirmPaymentReceived(String tradeId) {
        tradesServiceRequest.confirmPaymentReceived(tradeId);
    }

    public void keepFunds(String tradeId) {
        tradesServiceRequest.keepFunds(tradeId);
    }

    public void withdrawFunds(String tradeId, String address, String memo) {
        tradesServiceRequest.withdrawFunds(tradeId, address, memo);
    }

    public List<PaymentMethod> getPaymentMethods() {
        return paymentAccountsServiceRequest.getPaymentMethods();
    }

    public String getPaymentAcctFormAsJson(String paymentMethodId) {
        return paymentAccountsServiceRequest.getPaymentAcctFormAsJson(paymentMethodId);
    }

    public PaymentAccount createPaymentAccount(String json) {
        return paymentAccountsServiceRequest.createPaymentAccount(json);
    }

    public List<PaymentAccount> getPaymentAccounts() {
        return paymentAccountsServiceRequest.getPaymentAccounts();
    }

    public PaymentAccount createCryptoCurrencyPaymentAccount(String accountName,
                                                             String currencyCode,
                                                             String address,
                                                             boolean tradeInstant) {
        return paymentAccountsServiceRequest.createCryptoCurrencyPaymentAccount(accountName,
                currencyCode,
                address,
                tradeInstant);
    }

    public List<PaymentMethod> getCryptoPaymentMethods() {
        return paymentAccountsServiceRequest.getCryptoPaymentMethods();
    }

    public void lockWallet() {
        walletsServiceRequest.lockWallet();
    }

    public void unlockWallet(String walletPassword, long timeout) {
        walletsServiceRequest.unlockWallet(walletPassword, timeout);
    }

    public void removeWalletPassword(String walletPassword) {
        walletsServiceRequest.removeWalletPassword(walletPassword);
    }

    public void setWalletPassword(String walletPassword) {
        walletsServiceRequest.setWalletPassword(walletPassword);
    }

    public void setWalletPassword(String oldWalletPassword, String newWalletPassword) {
        walletsServiceRequest.setWalletPassword(oldWalletPassword, newWalletPassword);
    }

    public void registerDisputeAgent(String disputeAgentType, String registrationKey) {
        var request = RegisterDisputeAgentRequest.newBuilder()
                .setDisputeAgentType(disputeAgentType).setRegistrationKey(registrationKey).build();
        grpcStubs.disputeAgentsService.registerDisputeAgent(request);
    }

    public void stopServer() {
        var request = StopRequest.newBuilder().build();
        grpcStubs.shutdownService.stop(request);
    }

    public String getMethodHelp(Method method) {
        var request = GetMethodHelpRequest.newBuilder().setMethodName(method.name()).build();
        return grpcStubs.helpService.getMethodHelp(request).getMethodHelp();
    }
}
