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

package bisq.cli.request;

import bisq.proto.grpc.ConfirmPaymentReceivedRequest;
import bisq.proto.grpc.ConfirmPaymentStartedRequest;
import bisq.proto.grpc.GetTradeRequest;
import bisq.proto.grpc.KeepFundsRequest;
import bisq.proto.grpc.TakeOfferReply;
import bisq.proto.grpc.TakeOfferRequest;
import bisq.proto.grpc.TradeInfo;
import bisq.proto.grpc.WithdrawFundsRequest;



import bisq.cli.GrpcStubs;

public class TradesServiceRequest {

    private final GrpcStubs grpcStubs;

    public TradesServiceRequest(GrpcStubs grpcStubs) {
        this.grpcStubs = grpcStubs;
    }

    public TakeOfferReply getTakeOfferReply(String offerId, String paymentAccountId, String takerFeeCurrencyCode) {
        var request = TakeOfferRequest.newBuilder()
                .setOfferId(offerId)
                .setPaymentAccountId(paymentAccountId)
                .setTakerFeeCurrencyCode(takerFeeCurrencyCode)
                .build();
        return grpcStubs.tradesService.takeOffer(request);
    }

    public TradeInfo takeOffer(String offerId, String paymentAccountId, String takerFeeCurrencyCode) {
        var reply = getTakeOfferReply(offerId, paymentAccountId, takerFeeCurrencyCode);
        if (reply.hasTrade())
            return reply.getTrade();
        else
            throw new IllegalStateException(reply.getFailureReason().getDescription());
    }

    public TradeInfo getTrade(String tradeId) {
        var request = GetTradeRequest.newBuilder()
                .setTradeId(tradeId)
                .build();
        return grpcStubs.tradesService.getTrade(request).getTrade();
    }

    public void confirmPaymentStarted(String tradeId) {
        var request = ConfirmPaymentStartedRequest.newBuilder()
                .setTradeId(tradeId)
                .build();
        grpcStubs.tradesService.confirmPaymentStarted(request);
    }

    public void confirmPaymentReceived(String tradeId) {
        var request = ConfirmPaymentReceivedRequest.newBuilder()
                .setTradeId(tradeId)
                .build();
        grpcStubs.tradesService.confirmPaymentReceived(request);
    }

    public void keepFunds(String tradeId) {
        var request = KeepFundsRequest.newBuilder()
                .setTradeId(tradeId)
                .build();
        grpcStubs.tradesService.keepFunds(request);
    }

    public void withdrawFunds(String tradeId, String address, String memo) {
        var request = WithdrawFundsRequest.newBuilder()
                .setTradeId(tradeId)
                .setAddress(address)
                .setMemo(memo)
                .build();
        grpcStubs.tradesService.withdrawFunds(request);
    }
}
