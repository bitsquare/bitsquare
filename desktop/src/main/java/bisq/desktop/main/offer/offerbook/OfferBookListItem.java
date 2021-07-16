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

package bisq.desktop.main.offer.offerbook;

import bisq.desktop.util.GUIUtil;

import bisq.core.account.sign.SignedWitnessService;
import bisq.core.account.witness.AccountAgeWitness;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.payment.payload.PaymentMethod;

import de.jensd.fx.glyphs.GlyphIcons;
import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

@Slf4j

public class OfferBookListItem {
    @Getter
    private final Offer offer;

    // We cache the data once created for performance reasons. AccountAgeWitnessService calls can
    // be a bit expensive.
    private WitnessAgeData witnessAgeData;

    public OfferBookListItem(Offer offer) {
        this.offer = offer;
    }

    public WitnessAgeData getWitnessAgeData(AccountAgeWitnessService accountAgeWitnessService,
                                            SignedWitnessService signedWitnessService) {
        if (witnessAgeData == null) {
            if (CurrencyUtil.isCryptoCurrency(offer.getCurrencyCode())) {
                witnessAgeData = new WitnessAgeData(WitnessAgeData.TYPE_ALTCOINS);
            } else if (PaymentMethod.hasChargebackRisk(offer.getPaymentMethod(), offer.getCurrencyCode())) {
                // Fiat and signed witness required
                Optional<AccountAgeWitness> optionalWitness = accountAgeWitnessService.findWitness(offer);
                AccountAgeWitnessService.SignState signState = optionalWitness
                        .map(accountAgeWitnessService::getSignState)
                        .orElse(AccountAgeWitnessService.SignState.UNSIGNED);

                boolean isSignedAccountAgeWitness = optionalWitness
                        .map(signedWitnessService::isSignedAccountAgeWitness)
                        .orElse(false);

                if (isSignedAccountAgeWitness || !signState.equals(AccountAgeWitnessService.SignState.UNSIGNED)) {
                    // either signed & limits lifted, or waiting for limits to be lifted
                    // Or banned
                    witnessAgeData = new WitnessAgeData(
                            signState.isLimitLifted() ? WitnessAgeData.TYPE_SIGNED_AND_LIMIT_LIFTED : WitnessAgeData.TYPE_SIGNED_OR_BANNED,
                            optionalWitness.map(witness -> accountAgeWitnessService.getWitnessSignAge(witness, new Date())).orElse(0L),
                            signState);
                } else {
                    witnessAgeData = new WitnessAgeData(
                            WitnessAgeData.TYPE_NOT_SIGNED,
                            optionalWitness.map(e -> accountAgeWitnessService.getAccountAge(e, new Date())).orElse(0L),
                            signState
                    );
                }
            } else {
                // Fiat, no signed witness required, we show account age
                witnessAgeData = new WitnessAgeData(
                        WitnessAgeData.TYPE_NOT_SIGNING_REQUIRED,
                        accountAgeWitnessService.getAccountAge(offer)
                );
            }
        }
        return witnessAgeData;
    }

    @Value
    public static class WitnessAgeData implements Comparable<WitnessAgeData> {
        String displayString;
        String info;
        GlyphIcons icon;
        // Used for sorting
        Long type;
        // Used for sorting
        Long days;

        public static final long TYPE_SIGNED_AND_LIMIT_LIFTED = 4L;
        public static final long TYPE_SIGNED_OR_BANNED = 3L;
        public static final long TYPE_NOT_SIGNED = 2L;
        public static final long TYPE_NOT_SIGNING_REQUIRED = 1L;
        public static final long TYPE_ALTCOINS = 0L;

        public WitnessAgeData(long type) {
            this(type, 0, null);
        }

        public WitnessAgeData(long type, long days) {
            this(type, days, null);
        }

        public WitnessAgeData(long type, long age, AccountAgeWitnessService.SignState signState) {
            this.type = type;
            long days = age > -1 ? TimeUnit.MILLISECONDS.toDays(age) : 0;
            this.days = days;

            if (type == TYPE_SIGNED_AND_LIMIT_LIFTED) {
                this.displayString = Res.get("offerbook.timeSinceSigning.daysSinceSigning", days);
                this.info = Res.get("offerbook.timeSinceSigning.tooltip.info.signedAndLifted");
                this.icon = GUIUtil.getIconForSignState(signState);
            } else if (type == TYPE_SIGNED_OR_BANNED) {
                this.displayString = Res.get("offerbook.timeSinceSigning.daysSinceSigning", days);
                this.info = Res.get("offerbook.timeSinceSigning.tooltip.info.signed");
                this.icon = GUIUtil.getIconForSignState(signState);
            } else if (type == TYPE_NOT_SIGNED) {
                this.displayString = Res.get("offerbook.timeSinceSigning.notSigned");
                this.info = Res.get("offerbook.timeSinceSigning.tooltip.info.unsigned");
                this.icon = GUIUtil.getIconForSignState(signState);
            } else if (type == TYPE_NOT_SIGNING_REQUIRED) {
                this.displayString = Res.get("offerbook.timeSinceSigning.notSigned.ageDays", days);
                this.info = Res.get("shared.notSigned.noNeedDays", days);
                this.icon = MaterialDesignIcon.CHECKBOX_MARKED_OUTLINE;
            } else {
                this.displayString = Res.get("offerbook.timeSinceSigning.notSigned.noNeed");
                this.info = Res.get("shared.notSigned.noNeedAlts");
                this.icon = MaterialDesignIcon.INFORMATION_OUTLINE;
            }
        }

        public boolean isAccountSigned() {
            return this.type == TYPE_SIGNED_AND_LIMIT_LIFTED || this.type == TYPE_SIGNED_OR_BANNED;
        }

        public boolean isLimitLifted() {
            return this.type == TYPE_SIGNED_AND_LIMIT_LIFTED;
        }

        public boolean isSigningRequired() {
            return this.type != TYPE_NOT_SIGNING_REQUIRED && this.type != TYPE_ALTCOINS;
        }

        @Override
        public int compareTo(@NotNull WitnessAgeData o) {
            return (int) (this.type.equals(o.getType()) ? this.days - o.getDays() : this.type - o.getType());
        }
    }
}

