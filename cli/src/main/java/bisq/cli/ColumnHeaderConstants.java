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

import static com.google.common.base.Strings.padEnd;
import static com.google.common.base.Strings.padStart;

class ColumnHeaderConstants {

    // For inserting 2 spaces between column headers.
    static final String COL_HEADER_DELIMITER = "  ";

    // Table column header format specs, right padded with two spaces.  In some cases
    // such as COL_HEADER_CREATION_DATE, COL_HEADER_VOLUME and COL_HEADER_UUID, the
    // expected max data string length is accounted for.  In others, column header
    // lengths are expected to be greater than any column value length.
    static final String COL_HEADER_ADDRESS = padEnd("%-3s Address", 52, ' ');
    static final String COL_HEADER_AMOUNT = "BTC(min - max)";
    static final String COL_HEADER_AVAILABLE_BALANCE = "Available Balance";
    static final String COL_HEADER_AVAILABLE_CONFIRMED_BALANCE = "Available Confirmed Balance";
    static final String COL_HEADER_UNCONFIRMED_CHANGE_BALANCE = "Unconfirmed Change Balance";
    static final String COL_HEADER_RESERVED_BALANCE = "Reserved Balance";
    static final String COL_HEADER_TOTAL_AVAILABLE_BALANCE = "Total Available Balance";
    static final String COL_HEADER_LOCKED_BALANCE = "Locked Balance";
    static final String COL_HEADER_LOCKED_FOR_VOTING_BALANCE = "Locked For Voting Balance";
    static final String COL_HEADER_LOCKUP_BONDS_BALANCE = "Lockup Bonds Balance";
    static final String COL_HEADER_UNLOCKING_BONDS_BALANCE = "Unlocking Bonds Balance";
    static final String COL_HEADER_UNVERIFIED_BALANCE = "Unverified Balance";
    static final String COL_HEADER_CONFIRMATIONS = "Confirmations";
    static final String COL_HEADER_IS_USED_ADDRESS = "Is Used";
    static final String COL_HEADER_CREATION_DATE = padEnd("Creation Date (UTC)", 20, ' ');
    static final String COL_HEADER_CURRENCY = "Currency";
    static final String COL_HEADER_DIRECTION = "Buy/Sell";
    static final String COL_HEADER_ENABLED = "Enabled";
    static final String COL_HEADER_NAME = "Name";
    static final String COL_HEADER_PAYMENT_METHOD = "Payment Method";
    static final String COL_HEADER_PRICE = "Price in %-3s for 1 BTC";
    static final String COL_HEADER_PRICE_OF_ALTCOIN = "Price in BTC for 1 %-3s";
    static final String COL_HEADER_TRADE_AMOUNT = padStart("Amount(%-3s)", 12, ' ');
    static final String COL_HEADER_TRADE_BSQ_BUYER_ADDRESS = "BSQ Buyer Address";
    static final String COL_HEADER_TRADE_BUYER_COST = padEnd("Buyer Cost(%-3s)", 15, ' ');
    static final String COL_HEADER_TRADE_DEPOSIT_CONFIRMED = "Deposit Confirmed";
    static final String COL_HEADER_TRADE_DEPOSIT_PUBLISHED = "Deposit Published";
    static final String COL_HEADER_TRADE_PAYMENT_SENT = padEnd("%-3s Sent", 8, ' ');
    static final String COL_HEADER_TRADE_PAYMENT_RECEIVED = padEnd("%-3s Received", 12, ' ');
    static final String COL_HEADER_TRADE_PAYOUT_PUBLISHED = "Payout Published";
    static final String COL_HEADER_TRADE_WITHDRAWN = "Withdrawn";
    static final String COL_HEADER_TRADE_ROLE = "My Role";
    static final String COL_HEADER_TRADE_SHORT_ID = "ID";
    static final String COL_HEADER_TRADE_TX_FEE = padEnd("Tx Fee(BTC)", 12, ' ');
    static final String COL_HEADER_TRADE_MAKER_FEE = padEnd("Maker Fee(%-3s)", 12, ' '); // "Maker Fee(%-3s)";
    static final String COL_HEADER_TRADE_TAKER_FEE = padEnd("Taker Fee(%-3s)", 12, ' '); // "Taker Fee(%-3s)";
    static final String COL_HEADER_TRIGGER_PRICE = "Trigger Price(%-3s)";
    static final String COL_HEADER_TX_ID = "Tx ID";
    static final String COL_HEADER_TX_INPUT_SUM = "Tx Inputs (BTC)";
    static final String COL_HEADER_TX_OUTPUT_SUM = "Tx Outputs (BTC)";
    static final String COL_HEADER_TX_FEE = "Tx Fee (BTC)";
    static final String COL_HEADER_TX_SIZE = "Tx Size (Bytes)";
    static final String COL_HEADER_TX_IS_CONFIRMED = "Is Confirmed";
    static final String COL_HEADER_TX_MEMO = "Memo";

    static final String COL_HEADER_VOLUME = padEnd("%-3s(min - max)", 15, ' ');

    static final String COL_HEADER_UUID = padEnd("ID", 52, ' ');
}
