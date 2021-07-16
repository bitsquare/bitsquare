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
import bisq.proto.grpc.OfferInfo;

import protobuf.PaymentAccount;

import com.google.common.annotations.VisibleForTesting;

import java.text.SimpleDateFormat;

import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static bisq.cli.ColumnHeaderConstants.*;
import static bisq.cli.CurrencyFormat.*;
import static bisq.cli.DirectionFormat.directionFormat;
import static bisq.cli.DirectionFormat.getLongestDirectionColWidth;
import static com.google.common.base.Strings.padEnd;
import static com.google.common.base.Strings.padStart;
import static java.lang.String.format;
import static java.util.Collections.max;
import static java.util.Comparator.comparing;
import static java.util.TimeZone.getTimeZone;

@VisibleForTesting
public class TableFormat {

    static final TimeZone TZ_UTC = getTimeZone("UTC");
    static final SimpleDateFormat DATE_FORMAT_ISO_8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    public static String formatAddressBalanceTbl(List<AddressBalanceInfo> addressBalanceInfo) {
        String headerFormatString = COL_HEADER_ADDRESS + COL_HEADER_DELIMITER
                + COL_HEADER_AVAILABLE_BALANCE + COL_HEADER_DELIMITER
                + COL_HEADER_CONFIRMATIONS + COL_HEADER_DELIMITER
                + COL_HEADER_IS_USED_ADDRESS + COL_HEADER_DELIMITER + "\n";
        String headerLine = format(headerFormatString, "BTC");

        String colDataFormat = "%-" + COL_HEADER_ADDRESS.length() + "s" // lt justify
                + "  %" + (COL_HEADER_AVAILABLE_BALANCE.length() - 1) + "s" // rt justify
                + "  %" + COL_HEADER_CONFIRMATIONS.length() + "d"       // rt justify
                + "  %-" + COL_HEADER_IS_USED_ADDRESS.length() + "s";  // lt justify
        return headerLine
                + addressBalanceInfo.stream()
                .map(info -> format(colDataFormat,
                        info.getAddress(),
                        formatSatoshis(info.getBalance()),
                        info.getNumConfirmations(),
                        info.getIsAddressUnused() ? "NO" : "YES"))
                .collect(Collectors.joining("\n"));
    }

    public static String formatBalancesTbls(BalancesInfo balancesInfo) {
        return "BTC" + "\n"
                + formatBtcBalanceInfoTbl(balancesInfo.getBtc()) + "\n"
                + "BSQ" + "\n"
                + formatBsqBalanceInfoTbl(balancesInfo.getBsq());
    }

    public static String formatBsqBalanceInfoTbl(BsqBalanceInfo bsqBalanceInfo) {
        String headerLine = COL_HEADER_AVAILABLE_CONFIRMED_BALANCE + COL_HEADER_DELIMITER
                + COL_HEADER_UNVERIFIED_BALANCE + COL_HEADER_DELIMITER
                + COL_HEADER_UNCONFIRMED_CHANGE_BALANCE + COL_HEADER_DELIMITER
                + COL_HEADER_LOCKED_FOR_VOTING_BALANCE + COL_HEADER_DELIMITER
                + COL_HEADER_LOCKUP_BONDS_BALANCE + COL_HEADER_DELIMITER
                + COL_HEADER_UNLOCKING_BONDS_BALANCE + COL_HEADER_DELIMITER + "\n";
        String colDataFormat = "%" + COL_HEADER_AVAILABLE_CONFIRMED_BALANCE.length() + "s" // rt justify
                + " %" + (COL_HEADER_UNVERIFIED_BALANCE.length() + 1) + "s" // rt justify
                + " %" + (COL_HEADER_UNCONFIRMED_CHANGE_BALANCE.length() + 1) + "s" // rt justify
                + " %" + (COL_HEADER_LOCKED_FOR_VOTING_BALANCE.length() + 1) + "s" // rt justify
                + " %" + (COL_HEADER_LOCKUP_BONDS_BALANCE.length() + 1) + "s" // rt justify
                + " %" + (COL_HEADER_UNLOCKING_BONDS_BALANCE.length() + 1) + "s"; // rt justify
        return headerLine + format(colDataFormat,
                formatBsq(bsqBalanceInfo.getAvailableConfirmedBalance()),
                formatBsq(bsqBalanceInfo.getUnverifiedBalance()),
                formatBsq(bsqBalanceInfo.getUnconfirmedChangeBalance()),
                formatBsq(bsqBalanceInfo.getLockedForVotingBalance()),
                formatBsq(bsqBalanceInfo.getLockupBondsBalance()),
                formatBsq(bsqBalanceInfo.getUnlockingBondsBalance()));
    }

    public static String formatBtcBalanceInfoTbl(BtcBalanceInfo btcBalanceInfo) {
        String headerLine = COL_HEADER_AVAILABLE_BALANCE + COL_HEADER_DELIMITER
                + COL_HEADER_RESERVED_BALANCE + COL_HEADER_DELIMITER
                + COL_HEADER_TOTAL_AVAILABLE_BALANCE + COL_HEADER_DELIMITER
                + COL_HEADER_LOCKED_BALANCE + COL_HEADER_DELIMITER + "\n";
        String colDataFormat = "%" + COL_HEADER_AVAILABLE_BALANCE.length() + "s" // rt justify
                + " %" + (COL_HEADER_RESERVED_BALANCE.length() + 1) + "s" // rt justify
                + " %" + (COL_HEADER_TOTAL_AVAILABLE_BALANCE.length() + 1) + "s" // rt justify
                + " %" + (COL_HEADER_LOCKED_BALANCE.length() + 1) + "s"; // rt justify
        return headerLine + format(colDataFormat,
                formatSatoshis(btcBalanceInfo.getAvailableBalance()),
                formatSatoshis(btcBalanceInfo.getReservedBalance()),
                formatSatoshis(btcBalanceInfo.getTotalAvailableBalance()),
                formatSatoshis(btcBalanceInfo.getLockedBalance()));
    }

    public static String formatPaymentAcctTbl(List<PaymentAccount> paymentAccounts) {
        // Some column values might be longer than header, so we need to calculate them.
        int nameColWidth = getLongestColumnSize(
                COL_HEADER_NAME.length(),
                paymentAccounts.stream().map(PaymentAccount::getAccountName)
                        .collect(Collectors.toList()));
        int paymentMethodColWidth = getLongestColumnSize(
                COL_HEADER_PAYMENT_METHOD.length(),
                paymentAccounts.stream().map(a -> a.getPaymentMethod().getId())
                        .collect(Collectors.toList()));
        String headerLine = padEnd(COL_HEADER_NAME, nameColWidth, ' ') + COL_HEADER_DELIMITER
                + COL_HEADER_CURRENCY + COL_HEADER_DELIMITER
                + padEnd(COL_HEADER_PAYMENT_METHOD, paymentMethodColWidth, ' ') + COL_HEADER_DELIMITER
                + COL_HEADER_UUID + COL_HEADER_DELIMITER + "\n";
        String colDataFormat = "%-" + nameColWidth + "s"            // left justify
                + "  %-" + COL_HEADER_CURRENCY.length() + "s"       // left justify
                + "  %-" + paymentMethodColWidth + "s"              // left justify
                + "  %-" + COL_HEADER_UUID.length() + "s";          // left justify
        return headerLine
                + paymentAccounts.stream()
                .map(a -> format(colDataFormat,
                        a.getAccountName(),
                        a.getSelectedTradeCurrency().getCode(),
                        a.getPaymentMethod().getId(),
                        a.getId()))
                .collect(Collectors.joining("\n"));
    }

    public static String formatOfferTable(List<OfferInfo> offers, String currencyCode) {
        if (offers == null || offers.isEmpty())
            throw new IllegalArgumentException(format("%s offer list is empty", currencyCode.toLowerCase()));

        String baseCurrencyCode = offers.get(0).getBaseCurrencyCode();
        boolean isMyOffer = offers.get(0).getIsMyOffer();
        return baseCurrencyCode.equalsIgnoreCase("BTC")
                ? formatFiatOfferTable(offers, currencyCode, isMyOffer)
                : formatCryptoCurrencyOfferTable(offers, baseCurrencyCode, isMyOffer);
    }

    private static String formatFiatOfferTable(List<OfferInfo> offers,
                                               String fiatCurrencyCode,
                                               boolean isMyOffer) {
        // Some column values might be longer than header, so we need to calculate them.
        int amountColWith = getLongestAmountColWidth(offers);
        int volumeColWidth = getLongestVolumeColWidth(offers);
        int paymentMethodColWidth = getLongestPaymentMethodColWidth(offers);
        // "Enabled" and "Trigger Price" columns are displayed for my offers only.
        String enabledHeaderFormat = isMyOffer ?
                COL_HEADER_ENABLED + COL_HEADER_DELIMITER
                : "";
        String triggerPriceHeaderFormat = isMyOffer ?
                // COL_HEADER_TRIGGER_PRICE includes %s -> fiatCurrencyCode
                COL_HEADER_TRIGGER_PRICE + COL_HEADER_DELIMITER
                : "";
        String headersFormat = enabledHeaderFormat
                + COL_HEADER_DIRECTION + COL_HEADER_DELIMITER
                // COL_HEADER_PRICE includes %s -> fiatCurrencyCode
                + COL_HEADER_PRICE + COL_HEADER_DELIMITER
                + padStart(COL_HEADER_AMOUNT, amountColWith, ' ') + COL_HEADER_DELIMITER
                // COL_HEADER_VOLUME includes %s -> fiatCurrencyCode
                + padStart(COL_HEADER_VOLUME, volumeColWidth, ' ') + COL_HEADER_DELIMITER
                + triggerPriceHeaderFormat
                + padEnd(COL_HEADER_PAYMENT_METHOD, paymentMethodColWidth, ' ') + COL_HEADER_DELIMITER
                + COL_HEADER_CREATION_DATE + COL_HEADER_DELIMITER
                + COL_HEADER_UUID.trim() + "%n";
        String headerLine = format(headersFormat,
                fiatCurrencyCode.toUpperCase(),
                fiatCurrencyCode.toUpperCase(),
                // COL_HEADER_TRIGGER_PRICE includes %s -> fiatCurrencyCode
                isMyOffer ? fiatCurrencyCode.toUpperCase() : "");
        String colDataFormat = getFiatOfferColDataFormat(isMyOffer,
                amountColWith,
                volumeColWidth,
                paymentMethodColWidth);
        return formattedFiatOfferTable(offers, isMyOffer, headerLine, colDataFormat);
    }

    private static String formattedFiatOfferTable(List<OfferInfo> offers,
                                                  boolean isMyOffer,
                                                  String headerLine,
                                                  String colDataFormat) {
        if (isMyOffer) {
            return headerLine
                    + offers.stream()
                    .map(o -> format(colDataFormat,
                            o.getIsActivated() ? "YES" : "NO",
                            o.getDirection(),
                            formatPrice(o.getPrice()),
                            formatAmountRange(o.getMinAmount(), o.getAmount()),
                            formatVolumeRange(o.getMinVolume(), o.getVolume()),
                            o.getTriggerPrice() == 0 ? "" : formatPrice(o.getTriggerPrice()),
                            o.getPaymentMethodShortName(),
                            formatTimestamp(o.getDate()),
                            o.getId()))
                    .collect(Collectors.joining("\n"));
        } else {
            return headerLine
                    + offers.stream()
                    .map(o -> format(colDataFormat,
                            o.getDirection(),
                            formatPrice(o.getPrice()),
                            formatAmountRange(o.getMinAmount(), o.getAmount()),
                            formatVolumeRange(o.getMinVolume(), o.getVolume()),
                            o.getPaymentMethodShortName(),
                            formatTimestamp(o.getDate()),
                            o.getId()))
                    .collect(Collectors.joining("\n"));
        }
    }

    private static String getFiatOfferColDataFormat(boolean isMyOffer,
                                                    int amountColWith,
                                                    int volumeColWidth,
                                                    int paymentMethodColWidth) {
        if (isMyOffer) {
            return "%-" + (COL_HEADER_ENABLED.length() + COL_HEADER_DELIMITER.length()) + "s"
                    + "%-" + (COL_HEADER_DIRECTION.length() + COL_HEADER_DELIMITER.length()) + "s"
                    + "%" + (COL_HEADER_PRICE.length() - 1) + "s"
                    + "  %" + amountColWith + "s"
                    + "  %" + (volumeColWidth - 1) + "s"
                    + "  %" + (COL_HEADER_TRIGGER_PRICE.length() - 1) + "s"
                    + "  %-" + paymentMethodColWidth + "s"
                    + "  %-" + (COL_HEADER_CREATION_DATE.length()) + "s"
                    + "  %-" + COL_HEADER_UUID.length() + "s";
        } else {
            return "%-" + (COL_HEADER_DIRECTION.length() + COL_HEADER_DELIMITER.length()) + "s"
                    + "%" + (COL_HEADER_PRICE.length() - 1) + "s"
                    + "  %" + amountColWith + "s"
                    + "  %" + (volumeColWidth - 1) + "s"
                    + "  %-" + paymentMethodColWidth + "s"
                    + "  %-" + (COL_HEADER_CREATION_DATE.length()) + "s"
                    + "  %-" + COL_HEADER_UUID.length() + "s";
        }
    }

    private static String formatCryptoCurrencyOfferTable(List<OfferInfo> offers,
                                                         String cryptoCurrencyCode,
                                                         boolean isMyOffer) {
        // Some column values might be longer than header, so we need to calculate them.
        int directionColWidth = getLongestDirectionColWidth(offers);
        int amountColWith = getLongestAmountColWidth(offers);
        int volumeColWidth = getLongestCryptoCurrencyVolumeColWidth(offers);
        int paymentMethodColWidth = getLongestPaymentMethodColWidth(offers);
        // "Enabled" column is displayed for my offers only.
        String enabledHeaderFormat = isMyOffer ?
                COL_HEADER_ENABLED + COL_HEADER_DELIMITER
                : "";
        // TODO use memoize function to avoid duplicate the formatting done above?
        String headersFormat = enabledHeaderFormat
                + padEnd(COL_HEADER_DIRECTION, directionColWidth, ' ') + COL_HEADER_DELIMITER
                + COL_HEADER_PRICE_OF_ALTCOIN + COL_HEADER_DELIMITER   // includes %s -> cryptoCurrencyCode
                + padStart(COL_HEADER_AMOUNT, amountColWith, ' ') + COL_HEADER_DELIMITER
                // COL_HEADER_VOLUME  includes %s -> cryptoCurrencyCode
                + padStart(COL_HEADER_VOLUME, volumeColWidth, ' ') + COL_HEADER_DELIMITER
                + padEnd(COL_HEADER_PAYMENT_METHOD, paymentMethodColWidth, ' ') + COL_HEADER_DELIMITER
                + COL_HEADER_CREATION_DATE + COL_HEADER_DELIMITER
                + COL_HEADER_UUID.trim() + "%n";
        String headerLine = format(headersFormat,
                cryptoCurrencyCode.toUpperCase(),
                cryptoCurrencyCode.toUpperCase());
        String colDataFormat;
        if (isMyOffer) {
            colDataFormat = "%-" + (COL_HEADER_ENABLED.length() + COL_HEADER_DELIMITER.length()) + "s"
                    + "%-" + directionColWidth + "s"
                    + "%" + (COL_HEADER_PRICE_OF_ALTCOIN.length() + 1) + "s"
                    + "  %" + amountColWith + "s"
                    + "  %" + (volumeColWidth - 1) + "s"
                    + "  %-" + paymentMethodColWidth + "s"
                    + "  %-" + (COL_HEADER_CREATION_DATE.length()) + "s"
                    + "  %-" + COL_HEADER_UUID.length() + "s";
        } else {
            colDataFormat = "%-" + directionColWidth + "s"
                    + "%" + (COL_HEADER_PRICE_OF_ALTCOIN.length() + 1) + "s"
                    + "  %" + amountColWith + "s"
                    + "  %" + (volumeColWidth - 1) + "s"
                    + "  %-" + paymentMethodColWidth + "s"
                    + "  %-" + (COL_HEADER_CREATION_DATE.length()) + "s"
                    + "  %-" + COL_HEADER_UUID.length() + "s";
        }
        if (isMyOffer) {
            return headerLine
                    + offers.stream()
                    .map(o -> format(colDataFormat,
                            o.getIsActivated() ? "YES" : "NO",
                            directionFormat.apply(o),
                            formatCryptoCurrencyPrice(o.getPrice()),
                            formatAmountRange(o.getMinAmount(), o.getAmount()),
                            formatCryptoCurrencyVolumeRange(o.getMinVolume(), o.getVolume()),
                            o.getPaymentMethodShortName(),
                            formatTimestamp(o.getDate()),
                            o.getId()))
                    .collect(Collectors.joining("\n"));
        } else {
            return headerLine
                    + offers.stream()
                    .map(o -> format(colDataFormat,
                            directionFormat.apply(o),
                            formatCryptoCurrencyPrice(o.getPrice()),
                            formatAmountRange(o.getMinAmount(), o.getAmount()),
                            formatCryptoCurrencyVolumeRange(o.getMinVolume(), o.getVolume()),
                            o.getPaymentMethodShortName(),
                            formatTimestamp(o.getDate()),
                            o.getId()))
                    .collect(Collectors.joining("\n"));
        }
    }

    private static int getLongestPaymentMethodColWidth(List<OfferInfo> offers) {
        return getLongestColumnSize(
                COL_HEADER_PAYMENT_METHOD.length(),
                offers.stream()
                        .map(OfferInfo::getPaymentMethodShortName)
                        .collect(Collectors.toList()));
    }

    private static int getLongestAmountColWidth(List<OfferInfo> offers) {
        return getLongestColumnSize(
                COL_HEADER_AMOUNT.length(),
                offers.stream()
                        .map(o -> formatAmountRange(o.getMinAmount(), o.getAmount()))
                        .collect(Collectors.toList()));
    }

    private static int getLongestVolumeColWidth(List<OfferInfo> offers) {
        // Pad this col width by 1 space.
        return 1 + getLongestColumnSize(
                COL_HEADER_VOLUME.length(),
                offers.stream()
                        .map(o -> formatVolumeRange(o.getMinVolume(), o.getVolume()))
                        .collect(Collectors.toList()));
    }

    private static int getLongestCryptoCurrencyVolumeColWidth(List<OfferInfo> offers) {
        // Pad this col width by 1 space.
        return 1 + getLongestColumnSize(
                COL_HEADER_VOLUME.length(),
                offers.stream()
                        .map(o -> formatCryptoCurrencyVolumeRange(o.getMinVolume(), o.getVolume()))
                        .collect(Collectors.toList()));
    }

    // Return size of the longest string value, or the header.len, whichever is greater.
    private static int getLongestColumnSize(int headerLength, List<String> strings) {
        int longest = max(strings, comparing(String::length)).length();
        return Math.max(longest, headerLength);
    }

    private static String formatTimestamp(long timestamp) {
        DATE_FORMAT_ISO_8601.setTimeZone(TZ_UTC);
        return DATE_FORMAT_ISO_8601.format(new Date(timestamp));
    }
}
