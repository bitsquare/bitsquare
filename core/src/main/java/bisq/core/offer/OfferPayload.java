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

package bisq.core.offer;

import bisq.network.p2p.storage.payload.ExpirablePayload;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import bisq.network.p2p.storage.payload.RequiresOwnerIsOnlinePayload;

import bisq.common.crypto.PubKeyRing;

import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.EqualsAndHashCode;

import javax.annotation.Nullable;

@EqualsAndHashCode
public abstract class OfferPayload implements ProtectedStoragePayload, ExpirablePayload, RequiresOwnerIsOnlinePayload {
    public static final long TTL = TimeUnit.MINUTES.toMillis(9);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum Direction {
        BUY,
        SELL
    }

    // Keys for extra map
    // Only set for fiat offers
    public static final String ACCOUNT_AGE_WITNESS_HASH = "accountAgeWitnessHash";
    public static final String REFERRAL_ID = "referralId";
    // Only used in payment method F2F
    public static final String F2F_CITY = "f2fCity";
    public static final String F2F_EXTRA_INFO = "f2fExtraInfo";
    public static final String CASH_BY_MAIL_EXTRA_INFO = "cashByMailExtraInfo";

    // Comma separated list of ordinal of a bisq.common.app.Capability. E.g. ordinal of
    // Capability.SIGNED_ACCOUNT_AGE_WITNESS is 11 and Capability.MEDIATION is 12 so if we want to signal that maker
    // of the offer supports both capabilities we add "11, 12" to capabilities.
    public static final String CAPABILITIES = "capabilities";
    // If maker is seller and has xmrAutoConf enabled it is set to "1" otherwise it is not set
    public static final String XMR_AUTO_CONF = "xmrAutoConf";
    public static final String XMR_AUTO_CONF_ENABLED_VALUE = "1";


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    abstract public protobuf.StoragePayload toProtoMessage();

    abstract public long getAmount();

    abstract public long getMinAmount();

    // For fiat offer the baseCurrencyCode is BTC and the counterCurrencyCode is the fiat currency
    // For altcoin offers it is the opposite. baseCurrencyCode is the altcoin and the counterCurrencyCode is BTC.
    abstract public String getBaseCurrencyCode();

    abstract public String getCounterCurrencyCode();

    abstract public long getDate();

    abstract public Direction getDirection();

    @Nullable
    abstract public String getHashOfChallenge();

    abstract public String getId();

    abstract public boolean isCurrencyForMakerFeeBtc();

    // Reserved for possible future use to support private trades where the taker needs to have an accessKey
    abstract public boolean isPrivateOffer();

    abstract public long getMakerFee();

    abstract public long getTxFee();

    abstract public String getMakerPaymentAccountId();

    abstract public long getBuyerSecurityDeposit();

    abstract public long getSellerSecurityDeposit();

    abstract public long getMaxTradeLimit();

    abstract public long getMaxTradePeriod();

    abstract public String getPaymentMethodId();

    abstract public long getPrice();

    abstract public double getMarketPriceMargin();

    abstract public boolean isUseMarketBasedPrice();

    abstract public int getProtocolVersion();

    abstract public PubKeyRing getPubKeyRing();

    abstract public String getVersionNr();

    @Nullable
    abstract public List<String> getAcceptedBankIds();

    @Nullable
    abstract public String getBankId();

    @Nullable
    abstract public List<String> getAcceptedCountryCodes();

    @Nullable
    abstract public String getCountryCode();

    abstract public boolean isUseAutoClose();

    abstract public long getBlockHeightAtOfferCreation();

    abstract public long getLowerClosePrice();

    abstract public long getUpperClosePrice();

    abstract public boolean isUseReOpenAfterAutoClose();


    // In the offer we support base and counter currency
    // Fiat offers have base currency BTC and counterCurrency Fiat
    // Altcoins have base currency Altcoin and counterCurrency BTC
    // The rest of the app does not support yet that concept of base currency and counter currencies
    // so we map here for convenience
    public String getCurrencyCode() {
        return getBaseCurrencyCode().equals("BTC") ? getCounterCurrencyCode() : getBaseCurrencyCode();
    }
}
