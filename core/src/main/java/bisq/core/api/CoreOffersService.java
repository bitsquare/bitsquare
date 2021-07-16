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

package bisq.core.api;

import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Price;
import bisq.core.offer.CreateOfferService;
import bisq.core.offer.MutableOfferPayloadFields;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferBookService;
import bisq.core.offer.OfferFilter;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OfferUtil;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.PaymentAccount;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.User;

import bisq.common.crypto.KeyRing;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.utils.Fiat;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.math.BigDecimal;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static bisq.common.util.MathUtils.exactMultiply;
import static bisq.common.util.MathUtils.roundDoubleToLong;
import static bisq.common.util.MathUtils.scaleUpByPowerOf10;
import static bisq.core.locale.CurrencyUtil.isCryptoCurrency;
import static bisq.core.offer.Offer.State;
import static bisq.core.offer.OfferPayload.Direction;
import static bisq.core.offer.OfferPayload.Direction.BUY;
import static bisq.core.offer.OpenOffer.State.AVAILABLE;
import static bisq.core.offer.OpenOffer.State.DEACTIVATED;
import static bisq.core.payment.PaymentAccountUtil.isPaymentAccountValidForOffer;
import static bisq.proto.grpc.EditOfferRequest.EditType;
import static bisq.proto.grpc.EditOfferRequest.EditType.*;
import static java.lang.String.format;
import static java.util.Comparator.comparing;

@Singleton
@Slf4j
class CoreOffersService {

    private final Supplier<Comparator<Offer>> priceComparator = () ->
            comparing(Offer::getPrice);

    private final Supplier<Comparator<OpenOffer>> openOfferPriceComparator = () ->
            comparing(openOffer -> openOffer.getOffer().getPrice());

    private final CoreContext coreContext;
    private final KeyRing keyRing;
    // Dependencies on core api services in this package must be kept to an absolute
    // minimum, but some trading functions require an unlocked wallet's key, so an
    // exception is made in this case.
    private final CoreWalletsService coreWalletsService;
    private final CreateOfferService createOfferService;
    private final OfferBookService offerBookService;
    private final OfferFilter offerFilter;
    private final OpenOfferManager openOfferManager;
    private final OfferUtil offerUtil;
    private final PriceFeedService priceFeedService;
    private final User user;

    @Inject
    public CoreOffersService(CoreContext coreContext,
                             KeyRing keyRing,
                             CoreWalletsService coreWalletsService,
                             CreateOfferService createOfferService,
                             OfferBookService offerBookService,
                             OfferFilter offerFilter,
                             OpenOfferManager openOfferManager,
                             OfferUtil offerUtil,
                             PriceFeedService priceFeedService,
                             User user) {
        this.coreContext = coreContext;
        this.keyRing = keyRing;
        this.coreWalletsService = coreWalletsService;
        this.createOfferService = createOfferService;
        this.offerBookService = offerBookService;
        this.offerFilter = offerFilter;
        this.openOfferManager = openOfferManager;
        this.offerUtil = offerUtil;
        this.priceFeedService = priceFeedService;
        this.user = user;
    }

    Offer getOffer(String id) {
        return offerBookService.getOffers().stream()
                .filter(o -> o.getId().equals(id))
                .filter(o -> !o.isMyOffer(keyRing))
                .filter(o -> offerFilter.canTakeOffer(o, coreContext.isApiUser()).isValid())
                .findAny().orElseThrow(() ->
                        new IllegalStateException(format("offer with id '%s' not found", id)));
    }

    OpenOffer getMyOffer(String id) {
        return openOfferManager.getObservableList().stream()
                .filter(o -> o.getId().equals(id))
                .filter(o -> o.getOffer().isMyOffer(keyRing))
                .findAny().orElseThrow(() ->
                        new IllegalStateException(format("offer with id '%s' not found", id)));
    }

    List<Offer> getOffers(String direction, String currencyCode) {
        return offerBookService.getOffers().stream()
                .filter(o -> !o.isMyOffer(keyRing))
                .filter(o -> offerMatchesDirectionAndCurrency(o, direction, currencyCode))
                .filter(o -> offerFilter.canTakeOffer(o, coreContext.isApiUser()).isValid())
                .sorted(priceComparator(direction))
                .collect(Collectors.toList());
    }

    List<OpenOffer> getMyOffers(String direction, String currencyCode) {
        return openOfferManager.getObservableList().stream()
                .filter(o -> o.getOffer().isMyOffer(keyRing))
                .filter(o -> offerMatchesDirectionAndCurrency(o.getOffer(), direction, currencyCode))
                .sorted(openOfferPriceComparator(direction))
                .collect(Collectors.toList());
    }

    OpenOffer getMyOpenOffer(String id) {
        return openOfferManager.getOpenOfferById(id)
                .filter(open -> open.getOffer().isMyOffer(keyRing))
                .orElseThrow(() ->
                        new IllegalStateException(format("offer with id '%s' not found", id)));
    }

    boolean isMyOffer(String id) {
        return openOfferManager.getOpenOfferById(id)
                .filter(open -> open.getOffer().isMyOffer(keyRing))
                .isPresent();
    }

    // Create and place new offer.
    void createAndPlaceOffer(String currencyCode,
                             String directionAsString,
                             String priceAsString,
                             boolean useMarketBasedPrice,
                             double marketPriceMargin,
                             long amountAsLong,
                             long minAmountAsLong,
                             double buyerSecurityDeposit,
                             long triggerPrice,
                             String paymentAccountId,
                             String makerFeeCurrencyCode,
                             Consumer<Offer> resultHandler) {
        coreWalletsService.verifyWalletsAreAvailable();
        coreWalletsService.verifyEncryptedWalletIsUnlocked();
        offerUtil.maybeSetFeePaymentCurrencyPreference(makerFeeCurrencyCode);

        PaymentAccount paymentAccount = user.getPaymentAccount(paymentAccountId);
        if (paymentAccount == null)
            throw new IllegalArgumentException(format("payment account with id %s not found", paymentAccountId));

        String upperCaseCurrencyCode = currencyCode.toUpperCase();
        String offerId = createOfferService.getRandomOfferId();
        Direction direction = Direction.valueOf(directionAsString.toUpperCase());
        Price price = Price.valueOf(upperCaseCurrencyCode, priceStringToLong(priceAsString, upperCaseCurrencyCode));
        Coin amount = Coin.valueOf(amountAsLong);
        Coin minAmount = Coin.valueOf(minAmountAsLong);
        Coin useDefaultTxFee = Coin.ZERO;
        Offer offer = createOfferService.createAndGetOffer(offerId,
                direction,
                upperCaseCurrencyCode,
                amount,
                minAmount,
                price,
                useDefaultTxFee,
                useMarketBasedPrice,
                exactMultiply(marketPriceMargin, 0.01),
                buyerSecurityDeposit,
                paymentAccount);

        verifyPaymentAccountIsValidForNewOffer(offer, paymentAccount);

        // We don't support atm funding from external wallet to keep it simple.
        boolean useSavingsWallet = true;
        //noinspection ConstantConditions
        placeOffer(offer,
                buyerSecurityDeposit,
                triggerPrice,
                useSavingsWallet,
                transaction -> resultHandler.accept(offer));
    }

    // Edit a placed offer.
    void editOffer(String offerId,
                   String editedPriceAsString,
                   boolean editedUseMarketBasedPrice,
                   double editedMarketPriceMargin,
                   long editedTriggerPrice,
                   int editedEnable,
                   EditType editType) {
        OpenOffer openOffer = getMyOpenOffer(offerId);
        new EditOfferValidator(openOffer,
                editedPriceAsString,
                editedUseMarketBasedPrice,
                editedMarketPriceMargin,
                editedTriggerPrice,
                editedEnable,
                editType).validate();
        log.info("Validated 'editoffer' params offerId={}"
                        + "\n\teditedPriceAsString={}"
                        + "\n\teditedUseMarketBasedPrice={}"
                        + "\n\teditedMarketPriceMargin={}"
                        + "\n\teditedTriggerPrice={}"
                        + "\n\teditedEnable={}"
                        + "\n\teditType={}",
                offerId,
                editedPriceAsString,
                editedUseMarketBasedPrice,
                editedMarketPriceMargin,
                editedTriggerPrice,
                editedEnable,
                editType);
        OpenOffer.State currentOfferState = openOffer.getState();
        // Client sent (sint32) editedEnable, not a bool (with default=false).
        // If editedEnable = -1, do not change current state
        // If editedEnable =  0, set state = AVAILABLE
        // If editedEnable =  1, set state = DEACTIVATED
        OpenOffer.State newOfferState = editedEnable < 0
                ? currentOfferState
                : editedEnable > 0 ? AVAILABLE : DEACTIVATED;
        OfferPayload editedPayload = getMergedOfferPayload(openOffer,
                editedPriceAsString,
                editedMarketPriceMargin,
                editType);
        Offer editedOffer = new Offer(editedPayload);
        priceFeedService.setCurrencyCode(openOffer.getOffer().getOfferPayload().getCurrencyCode());
        editedOffer.setPriceFeedService(priceFeedService);
        editedOffer.setState(State.AVAILABLE);
        openOfferManager.editOpenOfferStart(openOffer,
                () -> log.info("EditOpenOfferStart: offer {}", openOffer.getId()),
                log::error);
        openOfferManager.editOpenOfferPublish(editedOffer,
                editedTriggerPrice,
                newOfferState,
                () -> log.info("EditOpenOfferPublish: offer {}", openOffer.getId()),
                log::error);
    }

    void cancelOffer(String id) {
        OpenOffer openOffer = getMyOffer(id);
        openOfferManager.removeOffer(openOffer.getOffer(),
                () -> {
                },
                log::error);
    }

    private void placeOffer(Offer offer,
                            double buyerSecurityDeposit,
                            long triggerPrice,
                            boolean useSavingsWallet,
                            Consumer<Transaction> resultHandler) {
        openOfferManager.placeOffer(offer,
                buyerSecurityDeposit,
                useSavingsWallet,
                triggerPrice,
                resultHandler::accept,
                log::error);

        if (offer.getErrorMessage() != null)
            throw new IllegalStateException(offer.getErrorMessage());
    }

    private OfferPayload getMergedOfferPayload(OpenOffer openOffer,
                                               String editedPriceAsString,
                                               double editedMarketPriceMargin,
                                               EditType editType) {
        // API supports editing (1) price, OR (2) marketPriceMargin & useMarketBasedPrice
        // OfferPayload fields.  API does not support editing payment acct or currency
        // code fields.  Note: triggerPrice isDeactivated fields are in OpenOffer, not
        // in OfferPayload.
        Offer offer = openOffer.getOffer();
        String currencyCode = offer.getOfferPayload().getCurrencyCode();
        boolean isEditingPrice = editType.equals(FIXED_PRICE_ONLY) || editType.equals(FIXED_PRICE_AND_ACTIVATION_STATE);
        Price editedPrice;
        if (isEditingPrice) {
            editedPrice = Price.valueOf(currencyCode, priceStringToLong(editedPriceAsString, currencyCode));
        } else {
            editedPrice = offer.getPrice();
        }
        boolean isUsingMktPriceMargin = editType.equals(MKT_PRICE_MARGIN_ONLY)
                || editType.equals(MKT_PRICE_MARGIN_AND_ACTIVATION_STATE)
                || editType.equals(TRIGGER_PRICE_ONLY)
                || editType.equals(TRIGGER_PRICE_AND_ACTIVATION_STATE)
                || editType.equals(MKT_PRICE_MARGIN_AND_TRIGGER_PRICE)
                || editType.equals(MKT_PRICE_MARGIN_AND_TRIGGER_PRICE_AND_ACTIVATION_STATE);
        MutableOfferPayloadFields mutableOfferPayloadFields = new MutableOfferPayloadFields(
                Objects.requireNonNull(editedPrice).getValue(),
                isUsingMktPriceMargin ? exactMultiply(editedMarketPriceMargin, 0.01) : 0.00,
                isUsingMktPriceMargin,
                offer.getOfferPayload().getBaseCurrencyCode(),
                offer.getOfferPayload().getCounterCurrencyCode(),
                offer.getPaymentMethod().getId(),
                offer.getMakerPaymentAccountId(),
                offer.getOfferPayload().getCountryCode(),
                offer.getOfferPayload().getAcceptedCountryCodes(),
                offer.getOfferPayload().getBankId(),
                offer.getOfferPayload().getAcceptedBankIds());
        log.info("Merging OfferPayload with {}", mutableOfferPayloadFields);
        return offerUtil.getMergedOfferPayload(openOffer, mutableOfferPayloadFields);
    }

    private void verifyPaymentAccountIsValidForNewOffer(Offer offer, PaymentAccount paymentAccount) {
        if (!isPaymentAccountValidForOffer(offer, paymentAccount)) {
            String error = format("cannot create %s offer with payment account %s",
                    offer.getOfferPayload().getCounterCurrencyCode(),
                    paymentAccount.getId());
            throw new IllegalStateException(error);
        }
    }

    private boolean offerMatchesDirectionAndCurrency(Offer offer,
                                                     String direction,
                                                     String currencyCode) {
        var offerOfWantedDirection = offer.getDirection().name().equalsIgnoreCase(direction);
        var offerInWantedCurrency = offer.getOfferPayload().getCounterCurrencyCode()
                .equalsIgnoreCase(currencyCode);
        return offerOfWantedDirection && offerInWantedCurrency;
    }

    private Comparator<OpenOffer> openOfferPriceComparator(String direction) {
        // A buyer probably wants to see sell orders in price ascending order.
        // A seller probably wants to see buy orders in price descending order.
        return direction.equalsIgnoreCase(BUY.name())
                ? openOfferPriceComparator.get().reversed()
                : openOfferPriceComparator.get();
    }

    private Comparator<Offer> priceComparator(String direction) {
        // A buyer probably wants to see sell orders in price ascending order.
        // A seller probably wants to see buy orders in price descending order.
        return direction.equalsIgnoreCase(BUY.name())
                ? priceComparator.get().reversed()
                : priceComparator.get();
    }

    private long priceStringToLong(String priceAsString, String currencyCode) {
        int precision = isCryptoCurrency(currencyCode) ? Altcoin.SMALLEST_UNIT_EXPONENT : Fiat.SMALLEST_UNIT_EXPONENT;
        double priceAsDouble = new BigDecimal(priceAsString).doubleValue();
        double scaled = scaleUpByPowerOf10(priceAsDouble, precision);
        return roundDoubleToLong(scaled);
    }
}
