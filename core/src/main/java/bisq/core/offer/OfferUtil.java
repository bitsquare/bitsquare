/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.offer;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.filter.FilterManager;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.payment.CashByMailAccount;
import bisq.core.payment.F2FAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.ReferralIdService;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.AutoConfirmSettings;
import bisq.core.user.Preferences;
import bisq.core.util.AveragePriceUtil;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.coin.CoinUtil;

import bisq.network.p2p.P2PService;

import bisq.common.app.Capabilities;
import bisq.common.util.MathUtils;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.utils.Fiat;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.common.util.MathUtils.roundDoubleToLong;
import static bisq.common.util.MathUtils.scaleUpByPowerOf10;
import static bisq.core.btc.wallet.Restrictions.getMaxBuyerSecurityDepositAsPercent;
import static bisq.core.btc.wallet.Restrictions.getMinBuyerSecurityDepositAsPercent;
import static bisq.core.btc.wallet.Restrictions.getMinNonDustOutput;
import static bisq.core.btc.wallet.Restrictions.isDust;
import static bisq.core.offer.OfferPayload.*;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

/**
 * This class holds utility methods for creating, editing and taking an Offer.
 */
@Slf4j
@Singleton
public class OfferUtil {

    private final AccountAgeWitnessService accountAgeWitnessService;
    private final BsqWalletService bsqWalletService;
    private final FilterManager filterManager;
    private final Preferences preferences;
    private final PriceFeedService priceFeedService;
    private final P2PService p2PService;
    private final ReferralIdService referralIdService;
    private final TradeStatisticsManager tradeStatisticsManager;

    private final Predicate<String> isValidFeePaymentCurrencyCode = (c) ->
            c.equalsIgnoreCase("BSQ") || c.equalsIgnoreCase("BTC");

    @Inject
    public OfferUtil(AccountAgeWitnessService accountAgeWitnessService,
                     BsqWalletService bsqWalletService,
                     FilterManager filterManager,
                     Preferences preferences,
                     PriceFeedService priceFeedService,
                     P2PService p2PService,
                     ReferralIdService referralIdService,
                     TradeStatisticsManager tradeStatisticsManager) {
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.bsqWalletService = bsqWalletService;
        this.filterManager = filterManager;
        this.preferences = preferences;
        this.priceFeedService = priceFeedService;
        this.p2PService = p2PService;
        this.referralIdService = referralIdService;
        this.tradeStatisticsManager = tradeStatisticsManager;
    }

    public void maybeSetFeePaymentCurrencyPreference(String feeCurrencyCode) {
        if (!feeCurrencyCode.isEmpty()) {
            if (!isValidFeePaymentCurrencyCode.test(feeCurrencyCode))
                throw new IllegalStateException(format("%s cannot be used to pay trade fees",
                        feeCurrencyCode.toUpperCase()));

            if (feeCurrencyCode.equalsIgnoreCase("BSQ") && preferences.isPayFeeInBtc())
                preferences.setPayFeeInBtc(false);
            else if (feeCurrencyCode.equalsIgnoreCase("BTC") && !preferences.isPayFeeInBtc())
                preferences.setPayFeeInBtc(true);
        }
    }

    /**
     * Given the direction, is this a BUY?
     *
     * @param direction the offer direction
     * @return {@code true} for an offer to buy BTC from the taker, {@code false} for an
     * offer to sell BTC to the taker
     */
    public boolean isBuyOffer(Direction direction) {
        return direction == Direction.BUY;
    }

    public long getMaxTradeLimit(PaymentAccount paymentAccount,
                                 String currencyCode,
                                 Direction direction) {
        return paymentAccount != null
                ? accountAgeWitnessService.getMyTradeLimit(paymentAccount, currencyCode, direction)
                : 0;
    }

    /**
     * Return true if a balance can cover a cost.
     *
     * @param cost the cost of a trade
     * @param balance a wallet balance
     * @return true if balance >= cost
     */
    public boolean isBalanceSufficient(Coin cost, Coin balance) {
        return cost != null && balance.compareTo(cost) >= 0;
    }

    /**
     * Return the wallet balance shortage for a given trade cost, or zero if there is
     * no shortage.
     *
     * @param cost the cost of a trade
     * @param balance a wallet balance
     * @return the wallet balance shortage for the given cost, else zero.
     */
    public Coin getBalanceShortage(Coin cost, Coin balance) {
        if (cost != null) {
            Coin shortage = cost.subtract(balance);
            return shortage.isNegative() ? Coin.ZERO : shortage;
        } else {
            return Coin.ZERO;
        }
    }

    /**
     * Returns the usable BSQ balance.
     *
     * @return Coin the usable BSQ balance
     */
    public Coin getUsableBsqBalance() {
        // We have to keep a minimum amount of BSQ == bitcoin dust limit, otherwise there
        // would be dust violations for change UTXOs; essentially means the minimum usable
        // balance of BSQ is 5.46.
        Coin usableBsqBalance = bsqWalletService.getAvailableConfirmedBalance().subtract(getMinNonDustOutput());
        return usableBsqBalance.isNegative() ? Coin.ZERO : usableBsqBalance;
    }

    public double calculateManualPrice(double volumeAsDouble, double amountAsDouble) {
        return volumeAsDouble / amountAsDouble;
    }

    public double calculateMarketPriceMargin(double manualPrice, double marketPrice) {
        return MathUtils.roundDouble(manualPrice / marketPrice, 4);
    }

    /**
     * Returns the makerFee as Coin, this can be priced in BTC or BSQ.
     *
     * @param amount           the amount of BTC to trade
     * @return the maker fee for the given trade amount, or {@code null} if the amount
     * is {@code null}
     */
    @Nullable
    public Coin getMakerFee(@Nullable Coin amount) {
        boolean isCurrencyForMakerFeeBtc = isCurrencyForMakerFeeBtc(amount);
        return CoinUtil.getMakerFee(isCurrencyForMakerFeeBtc, amount);
    }

    public Coin getTxFeeByVsize(Coin txFeePerVbyteFromFeeService, int vsizeInVbytes) {
        return txFeePerVbyteFromFeeService.multiply(getAverageTakerFeeTxVsize(vsizeInVbytes));
    }

    // We use the sum of the size of the trade fee and the deposit tx to get an average.
    // Miners will take the trade fee tx if the total fee of both dependent txs are good
    // enough.  With that we avoid that we overpay in case that the trade fee has many
    // inputs and we would apply that fee for the other 2 txs as well. We still might
    // overpay a bit for the payout tx.
    public int getAverageTakerFeeTxVsize(int txVsize) {
        return (txVsize + 233) / 2;
    }

    /**
     * Checks if the maker fee should be paid in BTC, this can be the case due to user
     * preference or because the user doesn't have enough BSQ.
     *
     * @param amount           the amount of BTC to trade
     * @return {@code true} if BTC is preferred or the trade amount is nonnull and there
     * isn't enough BSQ for it.
     */
    public boolean isCurrencyForMakerFeeBtc(@Nullable Coin amount) {
        boolean payFeeInBtc = preferences.getPayFeeInBtc();
        boolean bsqForFeeAvailable = isBsqForMakerFeeAvailable(amount);
        return payFeeInBtc || !bsqForFeeAvailable;
    }

    /**
     * Checks if the available BSQ balance is sufficient to pay for the offer's maker fee.
     *
     * @param amount           the amount of BTC to trade
     * @return {@code true} if the balance is sufficient, {@code false} otherwise
     */
    public boolean isBsqForMakerFeeAvailable(@Nullable Coin amount) {
        Coin availableBalance = bsqWalletService.getAvailableConfirmedBalance();
        Coin makerFee = CoinUtil.getMakerFee(false, amount);

        // If we don't know yet the maker fee (amount is not set) we return true,
        // otherwise we would disable BSQ fee each time we open the create offer screen
        // as there the amount is not set.
        if (makerFee == null)
            return true;

        Coin surplusFunds = availableBalance.subtract(makerFee);
        if (isDust(surplusFunds)) {
            return false; // we can't be left with dust
        }
        return !availableBalance.subtract(makerFee).isNegative();
    }


    @Nullable
    public Coin getTakerFee(boolean isCurrencyForTakerFeeBtc, @Nullable Coin amount) {
        if (amount != null) {
            Coin feePerBtc = CoinUtil.getFeePerBtc(FeeService.getTakerFeePerBtc(isCurrencyForTakerFeeBtc), amount);
            return CoinUtil.maxCoin(feePerBtc, FeeService.getMinTakerFee(isCurrencyForTakerFeeBtc));
        } else {
            return null;
        }
    }

    public boolean isCurrencyForTakerFeeBtc(Coin amount) {
        boolean payFeeInBtc = preferences.getPayFeeInBtc();
        boolean bsqForFeeAvailable = isBsqForTakerFeeAvailable(amount);
        return payFeeInBtc || !bsqForFeeAvailable;
    }

    public boolean isBsqForTakerFeeAvailable(@Nullable Coin amount) {
        Coin availableBalance = bsqWalletService.getAvailableConfirmedBalance();
        Coin takerFee = getTakerFee(false, amount);

        // If we don't know yet the maker fee (amount is not set) we return true,
        // otherwise we would disable BSQ fee each time we open the create offer screen
        // as there the amount is not set.
        if (takerFee == null)
            return true;

        Coin surplusFunds = availableBalance.subtract(takerFee);
        if (isDust(surplusFunds)) {
            return false; // we can't be left with dust
        }
        return !availableBalance.subtract(takerFee).isNegative();
    }

    public boolean isBlockChainPaymentMethod(Offer offer) {
        return offer != null && offer.getPaymentMethod().isAsset();
    }

    public Optional<Volume> getFeeInUserFiatCurrency(Coin makerFee,
                                                     boolean isCurrencyForMakerFeeBtc,
                                                     CoinFormatter bsqFormatter) {
        String userCurrencyCode = preferences.getPreferredTradeCurrency().getCode();
        if (CurrencyUtil.isCryptoCurrency(userCurrencyCode)) {
            // In case the user has selected a altcoin as preferredTradeCurrency
            // we derive the fiat currency from the user country
            String countryCode = preferences.getUserCountry().code;
            userCurrencyCode = CurrencyUtil.getCurrencyByCountryCode(countryCode).getCode();
        }

        return getFeeInUserFiatCurrency(makerFee,
                isCurrencyForMakerFeeBtc,
                userCurrencyCode,
                bsqFormatter);
    }

    public Map<String, String> getExtraDataMap(PaymentAccount paymentAccount,
                                               String currencyCode,
                                               Direction direction) {
        Map<String, String> extraDataMap = new HashMap<>();
        if (CurrencyUtil.isFiatCurrency(currencyCode)) {
            String myWitnessHashAsHex = accountAgeWitnessService
                    .getMyWitnessHashAsHex(paymentAccount.getPaymentAccountPayload());
            extraDataMap.put(ACCOUNT_AGE_WITNESS_HASH, myWitnessHashAsHex);
        }

        if (referralIdService.getOptionalReferralId().isPresent()) {
            extraDataMap.put(REFERRAL_ID, referralIdService.getOptionalReferralId().get());
        }

        if (paymentAccount instanceof F2FAccount) {
            extraDataMap.put(F2F_CITY, ((F2FAccount) paymentAccount).getCity());
            extraDataMap.put(F2F_EXTRA_INFO, ((F2FAccount) paymentAccount).getExtraInfo());
        }

        if (paymentAccount instanceof CashByMailAccount) {
            extraDataMap.put(CASH_BY_MAIL_EXTRA_INFO, ((CashByMailAccount) paymentAccount).getExtraInfo());
        }

        extraDataMap.put(CAPABILITIES, Capabilities.app.toStringList());

        if (currencyCode.equals("XMR") && direction == Direction.SELL) {
            preferences.getAutoConfirmSettingsList().stream()
                    .filter(e -> e.getCurrencyCode().equals("XMR"))
                    .filter(AutoConfirmSettings::isEnabled)
                    .forEach(e -> extraDataMap.put(XMR_AUTO_CONF, XMR_AUTO_CONF_ENABLED_VALUE));
        }

        return extraDataMap.isEmpty() ? null : extraDataMap;
    }

    public void validateOfferData(double buyerSecurityDeposit,
                                  PaymentAccount paymentAccount,
                                  String currencyCode,
                                  Coin makerFeeAsCoin) {
        checkNotNull(makerFeeAsCoin, "makerFee must not be null");
        checkNotNull(p2PService.getAddress(), "Address must not be null");
        checkArgument(buyerSecurityDeposit <= getMaxBuyerSecurityDepositAsPercent(),
                "securityDeposit must not exceed " +
                        getMaxBuyerSecurityDepositAsPercent());
        checkArgument(buyerSecurityDeposit >= getMinBuyerSecurityDepositAsPercent(),
                "securityDeposit must not be less than " +
                        getMinBuyerSecurityDepositAsPercent());
        checkArgument(!filterManager.isCurrencyBanned(currencyCode),
                Res.get("offerbook.warning.currencyBanned"));
        checkArgument(!filterManager.isPaymentMethodBanned(paymentAccount.getPaymentMethod()),
                Res.get("offerbook.warning.paymentMethodBanned"));
    }

    // Returns an edited payload: a merge of the original offerPayload and
    // editedOfferPayload fields.  Mutable fields are sourced from
    // mutableOfferPayloadFields param, e.g., payment account details, price, etc.
    // Immutable fields are sourced from the original openOffer param.
    public OfferPayload getMergedOfferPayload(OpenOffer openOffer,
                                              MutableOfferPayloadFields mutableOfferPayloadFields) {
        OfferPayload originalOfferPayload = openOffer.getOffer().getOfferPayload();
        return new OfferPayload(originalOfferPayload.getId(),
                originalOfferPayload.getDate(),
                originalOfferPayload.getOwnerNodeAddress(),
                originalOfferPayload.getPubKeyRing(),
                originalOfferPayload.getDirection(),
                mutableOfferPayloadFields.getPrice(),
                mutableOfferPayloadFields.getMarketPriceMargin(),
                mutableOfferPayloadFields.isUseMarketBasedPrice(),
                originalOfferPayload.getAmount(),
                originalOfferPayload.getMinAmount(),
                mutableOfferPayloadFields.getBaseCurrencyCode(),
                mutableOfferPayloadFields.getCounterCurrencyCode(),
                originalOfferPayload.getArbitratorNodeAddresses(),
                originalOfferPayload.getMediatorNodeAddresses(),
                mutableOfferPayloadFields.getPaymentMethodId(),
                mutableOfferPayloadFields.getMakerPaymentAccountId(),
                originalOfferPayload.getOfferFeePaymentTxId(),
                mutableOfferPayloadFields.getCountryCode(),
                mutableOfferPayloadFields.getAcceptedCountryCodes(),
                mutableOfferPayloadFields.getBankId(),
                mutableOfferPayloadFields.getAcceptedBankIds(),
                originalOfferPayload.getVersionNr(),
                originalOfferPayload.getBlockHeightAtOfferCreation(),
                originalOfferPayload.getTxFee(),
                originalOfferPayload.getMakerFee(),
                originalOfferPayload.isCurrencyForMakerFeeBtc(),
                originalOfferPayload.getBuyerSecurityDeposit(),
                originalOfferPayload.getSellerSecurityDeposit(),
                originalOfferPayload.getMaxTradeLimit(),
                originalOfferPayload.getMaxTradePeriod(),
                originalOfferPayload.isUseAutoClose(),
                originalOfferPayload.isUseReOpenAfterAutoClose(),
                originalOfferPayload.getLowerClosePrice(),
                originalOfferPayload.getUpperClosePrice(),
                originalOfferPayload.isPrivateOffer(),
                originalOfferPayload.getHashOfChallenge(),
                originalOfferPayload.getExtraDataMap(),
                originalOfferPayload.getProtocolVersion());
    }

    private Optional<Volume> getFeeInUserFiatCurrency(Coin makerFee,
                                                      boolean isCurrencyForMakerFeeBtc,
                                                      String userCurrencyCode,
                                                      CoinFormatter bsqFormatter) {
        MarketPrice marketPrice = priceFeedService.getMarketPrice(userCurrencyCode);
        if (marketPrice != null && makerFee != null) {
            long marketPriceAsLong = roundDoubleToLong(
                    scaleUpByPowerOf10(marketPrice.getPrice(), Fiat.SMALLEST_UNIT_EXPONENT));
            Price userCurrencyPrice = Price.valueOf(userCurrencyCode, marketPriceAsLong);

            if (isCurrencyForMakerFeeBtc) {
                return Optional.of(userCurrencyPrice.getVolumeByAmount(makerFee));
            } else {
                // We use the current market price for the fiat currency and the 30 day average BSQ price
                Tuple2<Price, Price> tuple = AveragePriceUtil.getAveragePriceTuple(preferences,
                        tradeStatisticsManager,
                        30);
                Price bsqPrice = tuple.second;
                if (bsqPrice.isPositive()) {
                    String inputValue = bsqFormatter.formatCoin(makerFee);
                    Volume makerFeeAsVolume = Volume.parse(inputValue, "BSQ");
                    Coin requiredBtc = bsqPrice.getAmountByVolume(makerFeeAsVolume);
                    Volume volumeByAmount = userCurrencyPrice.getVolumeByAmount(requiredBtc);
                    return Optional.of(volumeByAmount);
                } else {
                    return Optional.empty();
                }
            }
        } else {
            return Optional.empty();
        }
    }

    public static Optional<String> getInvalidMakerFeeTxErrorMessage(Offer offer, BtcWalletService btcWalletService) {
        String offerFeePaymentTxId = offer.getOfferFeePaymentTxId();
        if (offerFeePaymentTxId == null) {
            return Optional.empty();
        }

        Transaction makerFeeTx = btcWalletService.getTransaction(offerFeePaymentTxId);
        if (makerFeeTx == null) {
            return Optional.empty();
        }

        String errorMsg = null;
        String header = "The offer with offer ID '" + offer.getShortId() +
                "' has an invalid maker fee transaction.\n\n";
        String spendingTransaction = null;
        String extraString = "\nYou have to remove that offer to avoid failed trades.\n" +
                "If this happened because of a bug please contact the Bisq developers " +
                "and you can request reimbursement for the lost maker fee.";
        if (makerFeeTx.getOutputs().size() > 1) {
            // Our output to fund the deposit tx is at index 1
            TransactionOutput output = makerFeeTx.getOutput(1);
            TransactionInput spentByTransactionInput = output.getSpentBy();
            if (spentByTransactionInput != null) {
                spendingTransaction = spentByTransactionInput.getConnectedTransaction() != null ?
                        spentByTransactionInput.getConnectedTransaction().toString() :
                        "null";
                // We this is an exceptional case we do not translate that error msg.
                errorMsg = "The output of the maker fee tx is already spent.\n" +
                        extraString +
                        "\n\nTransaction input which spent the reserved funds for that offer: '" +
                        spentByTransactionInput.getConnectedTransaction().getTxId().toString() + ":" +
                        (spentByTransactionInput.getConnectedOutput() != null ?
                                spentByTransactionInput.getConnectedOutput().getIndex() + "'" :
                                "null'");
                log.error("spentByTransactionInput {}", spentByTransactionInput);
            }
        } else {
            errorMsg = "The maker fee tx is invalid as it does not has at least 2 outputs." + extraString +
                    "\nMakerFeeTx=" + makerFeeTx.toString();
        }

        if (errorMsg == null) {
            return Optional.empty();
        }

        errorMsg = header + errorMsg;
        log.error(errorMsg);
        if (spendingTransaction != null) {
            log.error("Spending transaction: {}", spendingTransaction);
        }

        return Optional.of(errorMsg);
    }
}
