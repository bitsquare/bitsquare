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

import bisq.proto.grpc.CancelOfferRequest;
import bisq.proto.grpc.CreateOfferRequest;
import bisq.proto.grpc.EditOfferRequest;
import bisq.proto.grpc.GetMyOfferRequest;
import bisq.proto.grpc.GetMyOffersRequest;
import bisq.proto.grpc.GetOfferRequest;
import bisq.proto.grpc.GetOffersRequest;
import bisq.proto.grpc.OfferInfo;

import java.math.BigDecimal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static bisq.proto.grpc.EditOfferRequest.EditType.ACTIVATION_STATE_ONLY;
import static bisq.proto.grpc.EditOfferRequest.EditType.FIXED_PRICE_ONLY;
import static bisq.proto.grpc.EditOfferRequest.EditType.MKT_PRICE_MARGIN_ONLY;
import static bisq.proto.grpc.EditOfferRequest.EditType.TRIGGER_PRICE_ONLY;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static protobuf.OfferPayload.Direction.BUY;
import static protobuf.OfferPayload.Direction.SELL;



import bisq.cli.GrpcStubs;

public class OffersServiceRequest {

    private final GrpcStubs grpcStubs;

    public OffersServiceRequest(GrpcStubs grpcStubs) {
        this.grpcStubs = grpcStubs;
    }

    public OfferInfo createFixedPricedOffer(String direction,
                                            String currencyCode,
                                            long amount,
                                            long minAmount,
                                            String fixedPrice,
                                            double securityDeposit,
                                            String paymentAcctId,
                                            String makerFeeCurrencyCode) {
        return createOffer(direction,
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
        return createOffer(direction,
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
        var request = CreateOfferRequest.newBuilder()
                .setDirection(direction)
                .setCurrencyCode(currencyCode)
                .setAmount(amount)
                .setMinAmount(minAmount)
                .setUseMarketBasedPrice(useMarketBasedPrice)
                .setPrice(fixedPrice)
                .setMarketPriceMargin(marketPriceMargin)
                .setBuyerSecurityDeposit(securityDeposit)
                .setPaymentAccountId(paymentAcctId)
                .setMakerFeeCurrencyCode(makerFeeCurrencyCode)
                .setTriggerPrice(triggerPrice)
                .build();
        return grpcStubs.offersService.createOffer(request).getOffer();
    }

    // TODO Make sure this is not duplicated anywhere on CLI side.
    private final Function<Long, String> scaledPriceStringFormat = (price) -> {
        BigDecimal factor = new BigDecimal(10).pow(4);
        //noinspection BigDecimalMethodWithoutRoundingCalled
        return new BigDecimal(price).divide(factor).toPlainString();
    };

    public void editOfferActivationState(String offerId, int enable) {
        var offer = getMyOffer(offerId);
        var scaledPriceString = offer.getUseMarketBasedPrice()
                ? "0.00"
                : scaledPriceStringFormat.apply(offer.getPrice());
        editOffer(offerId,
                scaledPriceString,
                offer.getUseMarketBasedPrice(),
                offer.getMarketPriceMargin(),
                offer.getTriggerPrice(),
                enable,
                ACTIVATION_STATE_ONLY);
    }

    public void editOfferFixedPrice(String offerId, String rawPriceString) {
        var offer = getMyOffer(offerId);
        editOffer(offerId,
                rawPriceString,
                false,
                offer.getMarketPriceMargin(),
                offer.getTriggerPrice(),
                offer.getIsActivated() ? 1 : 0,
                FIXED_PRICE_ONLY);
    }

    public void editOfferPriceMargin(String offerId, double marketPriceMargin) {
        var offer = getMyOffer(offerId);
        editOffer(offerId,
                "0.00",
                true,
                marketPriceMargin,
                offer.getTriggerPrice(),
                offer.getIsActivated() ? 1 : 0,
                MKT_PRICE_MARGIN_ONLY);
    }

    public void editOfferTriggerPrice(String offerId, long triggerPrice) {
        var offer = getMyOffer(offerId);
        editOffer(offerId,
                "0.00",
                offer.getUseMarketBasedPrice(),
                offer.getMarketPriceMargin(),
                triggerPrice,
                offer.getIsActivated() ? 1 : 0,
                TRIGGER_PRICE_ONLY);
    }

    public void editOffer(String offerId,
                          String scaledPriceString,
                          boolean useMarketBasedPrice,
                          double marketPriceMargin,
                          long triggerPrice,
                          int enable,
                          EditOfferRequest.EditType editType) {
        // Take care when using this method directly:
        //  useMarketBasedPrice = true if margin based offer, false for fixed priced offer
        //  scaledPriceString fmt = ######.####
        var request = EditOfferRequest.newBuilder()
                .setId(offerId)
                .setPrice(scaledPriceString)
                .setUseMarketBasedPrice(useMarketBasedPrice)
                .setMarketPriceMargin(marketPriceMargin)
                .setTriggerPrice(triggerPrice)
                .setEnable(enable)
                .setEditType(editType)
                .build();
        //noinspection ResultOfMethodCallIgnored
        grpcStubs.offersService.editOffer(request);
    }

    public void cancelOffer(String offerId) {
        var request = CancelOfferRequest.newBuilder()
                .setId(offerId)
                .build();
        //noinspection ResultOfMethodCallIgnored
        grpcStubs.offersService.cancelOffer(request);
    }

    public OfferInfo getOffer(String offerId) {
        var request = GetOfferRequest.newBuilder()
                .setId(offerId)
                .build();
        return grpcStubs.offersService.getOffer(request).getOffer();
    }

    public OfferInfo getMyOffer(String offerId) {
        var request = GetMyOfferRequest.newBuilder()
                .setId(offerId)
                .build();
        return grpcStubs.offersService.getMyOffer(request).getOffer();
    }

    public List<OfferInfo> getOffers(String direction, String currencyCode) {
        if (isSupportedCryptoCurrency(currencyCode)) {
            return getCryptoCurrencyOffers(direction, currencyCode);
        } else {
            var request = GetOffersRequest.newBuilder()
                    .setDirection(direction)
                    .setCurrencyCode(currencyCode)
                    .build();
            return grpcStubs.offersService.getOffers(request).getOffersList();
        }
    }

    public List<OfferInfo> getCryptoCurrencyOffers(String direction, String currencyCode) {
        return getOffers(direction, "BTC").stream()
                .filter(o -> o.getBaseCurrencyCode().equalsIgnoreCase(currencyCode))
                .collect(toList());
    }

    public List<OfferInfo> getOffersSortedByDate(String currencyCode) {
        ArrayList<OfferInfo> offers = new ArrayList<>();
        offers.addAll(getOffers(BUY.name(), currencyCode));
        offers.addAll(getOffers(SELL.name(), currencyCode));
        return sortOffersByDate(offers);
    }

    public List<OfferInfo> getOffersSortedByDate(String direction, String currencyCode) {
        var offers = getOffers(direction, currencyCode);
        return offers.isEmpty() ? offers : sortOffersByDate(offers);
    }

    public List<OfferInfo> getBsqOffersSortedByDate() {
        ArrayList<OfferInfo> offers = new ArrayList<>();
        offers.addAll(getCryptoCurrencyOffers(BUY.name(), "BSQ"));
        offers.addAll(getCryptoCurrencyOffers(SELL.name(), "BSQ"));
        return sortOffersByDate(offers);
    }

    public List<OfferInfo> getMyOffers(String direction, String currencyCode) {
        if (isSupportedCryptoCurrency(currencyCode)) {
            return getMyCryptoCurrencyOffers(direction, currencyCode);
        } else {
            var request = GetMyOffersRequest.newBuilder()
                    .setDirection(direction)
                    .setCurrencyCode(currencyCode)
                    .build();
            return grpcStubs.offersService.getMyOffers(request).getOffersList();
        }
    }

    public List<OfferInfo> getMyCryptoCurrencyOffers(String direction, String currencyCode) {
        return getMyOffers(direction, "BTC").stream()
                .filter(o -> o.getBaseCurrencyCode().equalsIgnoreCase(currencyCode))
                .collect(toList());
    }

    public List<OfferInfo> getMyOffersSortedByDate(String direction, String currencyCode) {
        var offers = getMyOffers(direction, currencyCode);
        return offers.isEmpty() ? offers : sortOffersByDate(offers);
    }

    public List<OfferInfo> getMyOffersSortedByDate(String currencyCode) {
        ArrayList<OfferInfo> offers = new ArrayList<>();
        offers.addAll(getMyOffers(BUY.name(), currencyCode));
        offers.addAll(getMyOffers(SELL.name(), currencyCode));
        return sortOffersByDate(offers);
    }

    public List<OfferInfo> getMyBsqOffersSortedByDate() {
        ArrayList<OfferInfo> offers = new ArrayList<>();
        offers.addAll(getMyCryptoCurrencyOffers(BUY.name(), "BSQ"));
        offers.addAll(getMyCryptoCurrencyOffers(SELL.name(), "BSQ"));
        return sortOffersByDate(offers);
    }

    public OfferInfo getMostRecentOffer(String direction, String currencyCode) {
        List<OfferInfo> offers = getOffersSortedByDate(direction, currencyCode);
        return offers.isEmpty() ? null : offers.get(offers.size() - 1);
    }

    public List<OfferInfo> sortOffersByDate(List<OfferInfo> offerInfoList) {
        return offerInfoList.stream()
                .sorted(comparing(OfferInfo::getDate))
                .collect(toList());
    }

    private static boolean isSupportedCryptoCurrency(String currencyCode) {
        return getSupportedCryptoCurrencies().contains(currencyCode.toUpperCase());
    }

    private static List<String> getSupportedCryptoCurrencies() {
        final List<String> result = new ArrayList<>();
        result.add("BSQ");
        result.sort(String::compareTo);
        return result;
    }
}
