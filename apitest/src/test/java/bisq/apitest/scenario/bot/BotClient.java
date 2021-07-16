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

import bisq.proto.grpc.BalancesInfo;
import bisq.proto.grpc.GetPaymentAccountsRequest;
import bisq.proto.grpc.OfferInfo;
import bisq.proto.grpc.TradeInfo;

import protobuf.PaymentAccount;

import java.text.DecimalFormat;

import java.util.List;
import java.util.function.BiPredicate;

import lombok.extern.slf4j.Slf4j;

import static org.apache.commons.lang3.StringUtils.capitalize;



import bisq.cli.GrpcClient;

/**
 * Convenience GrpcClient wrapper for bots using gRPC services.
 */
@SuppressWarnings({"JavaDoc", "unused"})
@Slf4j
public class BotClient {

    private static final DecimalFormat FIXED_PRICE_FMT = new DecimalFormat("###########0");

    private final GrpcClient grpcClient;

    public BotClient(GrpcClient grpcClient) {
        this.grpcClient = grpcClient;
    }

    /**
     * Returns current BSQ and BTC balance information.
     * @return BalancesInfo
     */
    public BalancesInfo getBalance() {
        return grpcClient.getBalances();
    }

    /**
     * Return the most recent BTC market price for the given currencyCode.
     * @param currencyCode
     * @return double
     */
    public double getCurrentBTCMarketPrice(String currencyCode) {
        return grpcClient.getBtcPrice(currencyCode);
    }

    /**
     * Return the most recent BTC market price for the given currencyCode as an integer string.
     * @param currencyCode
     * @return String
     */
    public String getCurrentBTCMarketPriceAsIntegerString(String currencyCode) {
        return FIXED_PRICE_FMT.format(getCurrentBTCMarketPrice(currencyCode));
    }

    /**
     * Return all BUY and SELL offers for the given currencyCode.
     * @param currencyCode
     * @return List<OfferInfo>
     */
    public List<OfferInfo> getOffers(String currencyCode) {
        var buyOffers = getBuyOffers(currencyCode);
        if (buyOffers.size() > 0) {
            return buyOffers;
        } else {
            return getSellOffers(currencyCode);
        }
    }

    /**
     * Return BUY offers for the given currencyCode.
     * @param currencyCode
     * @return List<OfferInfo>
     */
    public List<OfferInfo> getBuyOffers(String currencyCode) {
        return grpcClient.getOffers("BUY", currencyCode);
    }

    /**
     * Return SELL offers for the given currencyCode.
     * @param currencyCode
     * @return List<OfferInfo>
     */
    public List<OfferInfo> getSellOffers(String currencyCode) {
        return grpcClient.getOffers("SELL", currencyCode);
    }

    /**
     * Create and return a new Offer using a market based price.
     * @param paymentAccount
     * @param direction
     * @param currencyCode
     * @param amountInSatoshis
     * @param minAmountInSatoshis
     * @param priceMarginAsPercent
     * @param securityDepositAsPercent
     * @param feeCurrency
     * @return OfferInfo
     */
    public OfferInfo createOfferAtMarketBasedPrice(PaymentAccount paymentAccount,
                                                   String direction,
                                                   String currencyCode,
                                                   long amountInSatoshis,
                                                   long minAmountInSatoshis,
                                                   double priceMarginAsPercent,
                                                   double securityDepositAsPercent,
                                                   String feeCurrency,
                                                   long triggerPrice) {
        return grpcClient.createMarketBasedPricedOffer(direction,
                currencyCode,
                amountInSatoshis,
                minAmountInSatoshis,
                priceMarginAsPercent,
                securityDepositAsPercent,
                paymentAccount.getId(),
                feeCurrency,
                triggerPrice);
    }

    /**
     * Create and return a new Offer using a fixed price.
     * @param paymentAccount
     * @param direction
     * @param currencyCode
     * @param amountInSatoshis
     * @param minAmountInSatoshis
     * @param fixedOfferPriceAsString
     * @param securityDepositAsPercent
     * @param feeCurrency
     * @return OfferInfo
     */
    public OfferInfo createOfferAtFixedPrice(PaymentAccount paymentAccount,
                                             String direction,
                                             String currencyCode,
                                             long amountInSatoshis,
                                             long minAmountInSatoshis,
                                             String fixedOfferPriceAsString,
                                             double securityDepositAsPercent,
                                             String feeCurrency) {
        return grpcClient.createFixedPricedOffer(direction,
                currencyCode,
                amountInSatoshis,
                minAmountInSatoshis,
                fixedOfferPriceAsString,
                securityDepositAsPercent,
                paymentAccount.getId(),
                feeCurrency);
    }

    public TradeInfo takeOffer(String offerId, PaymentAccount paymentAccount, String feeCurrency) {
        return grpcClient.takeOffer(offerId, paymentAccount.getId(), feeCurrency);
    }

    /**
     * Returns a persisted Trade with the given tradeId, or throws an exception.
     * @param tradeId
     * @return TradeInfo
     */
    public TradeInfo getTrade(String tradeId) {
        return grpcClient.getTrade(tradeId);
    }

    /**
     * Predicate returns true if the given exception indicates the trade with the given
     * tradeId exists, but the trade's contract has not been fully prepared.
     */
    public final BiPredicate<Exception, String> tradeContractIsNotReady = (exception, tradeId) -> {
        if (exception.getMessage().contains("no contract was found")) {
            log.warn("Trade {} exists but is not fully prepared: {}.",
                    tradeId,
                    toCleanGrpcExceptionMessage(exception));
            return true;
        } else {
            return false;
        }
    };

    /**
     * Returns a trade's contract as a Json string, or null if the trade exists
     * but the contract is not ready.
     * @param tradeId
     * @return String
     */
    public String getTradeContract(String tradeId) {
        try {
            var trade = grpcClient.getTrade(tradeId);
            return trade.getContractAsJson();
        } catch (Exception ex) {
            if (tradeContractIsNotReady.test(ex, tradeId))
                return null;
            else
                throw ex;
        }
    }

    /**
     * Returns true if the trade's taker deposit fee transaction has been published.
     * @param tradeId a valid trade id
     * @return boolean
     */
    public boolean isTakerDepositFeeTxPublished(String tradeId) {
        return grpcClient.getTrade(tradeId).getIsPayoutPublished();
    }

    /**
     * Returns true if the trade's taker deposit fee transaction has been confirmed.
     * @param tradeId a valid trade id
     * @return boolean
     */
    public boolean isTakerDepositFeeTxConfirmed(String tradeId) {
        return grpcClient.getTrade(tradeId).getIsDepositConfirmed();
    }

    /**
     * Returns true if the trade's 'start payment' message has been sent by the buyer.
     * @param tradeId a valid trade id
     * @return boolean
     */
    public boolean isTradePaymentStartedSent(String tradeId) {
        return grpcClient.getTrade(tradeId).getIsFiatSent();
    }

    /**
     * Returns true if the trade's 'payment received' message has been sent by the seller.
     * @param tradeId a valid trade id
     * @return boolean
     */
    public boolean isTradePaymentReceivedConfirmationSent(String tradeId) {
        return grpcClient.getTrade(tradeId).getIsFiatReceived();
    }

    /**
     * Returns true if the trade's payout transaction has been published.
     * @param tradeId a valid trade id
     * @return boolean
     */
    public boolean isTradePayoutTxPublished(String tradeId) {
        return grpcClient.getTrade(tradeId).getIsPayoutPublished();
    }

    /**
     * Sends a 'confirm payment started message' for a trade with the given tradeId,
     * or throws an exception.
     * @param tradeId
     */
    public void sendConfirmPaymentStartedMessage(String tradeId) {
        grpcClient.confirmPaymentStarted(tradeId);
    }

    /**
     * Sends a 'confirm payment received message' for a trade with the given tradeId,
     * or throws an exception.
     * @param tradeId
     */
    public void sendConfirmPaymentReceivedMessage(String tradeId) {
        grpcClient.confirmPaymentReceived(tradeId);
    }

    /**
     * Sends a 'keep funds in wallet message' for a trade with the given tradeId,
     * or throws an exception.
     * @param tradeId
     */
    public void sendKeepFundsMessage(String tradeId) {
        grpcClient.keepFunds(tradeId);
    }

    /**
     * Create and save a new PaymentAccount with details in the given json.
     * @param json
     * @return PaymentAccount
     */
    public PaymentAccount createNewPaymentAccount(String json) {
        return grpcClient.createPaymentAccount(json);
    }

    /**
     * Returns a persisted PaymentAccount with the given paymentAccountId, or throws
     * an exception.
     * @param paymentAccountId The id of the PaymentAccount being looked up.
     * @return PaymentAccount
     */
    public PaymentAccount getPaymentAccount(String paymentAccountId) {
        return grpcClient.getPaymentAccounts().stream()
                .filter(a -> (a.getId().equals(paymentAccountId)))
                .findFirst()
                .orElseThrow(() ->
                        new PaymentAccountNotFoundException("Could not find a payment account with id "
                                + paymentAccountId + "."));
    }

    /**
     * Returns a persisted PaymentAccount with the given accountName, or throws
     * an exception.
     * @param accountName
     * @return PaymentAccount
     */
    public PaymentAccount getPaymentAccountWithName(String accountName) {
        var req = GetPaymentAccountsRequest.newBuilder().build();
        return grpcClient.getPaymentAccounts().stream()
                .filter(a -> (a.getAccountName().equals(accountName)))
                .findFirst()
                .orElseThrow(() ->
                        new PaymentAccountNotFoundException("Could not find a payment account with name "
                                + accountName + "."));
    }

    public String toCleanGrpcExceptionMessage(Exception ex) {
        return capitalize(ex.getMessage().replaceFirst("^[A-Z_]+: ", ""));
    }
}
