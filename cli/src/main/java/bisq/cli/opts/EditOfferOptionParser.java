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

package bisq.cli.opts;


import bisq.proto.grpc.EditOfferRequest;

import joptsimple.OptionSpec;

import java.math.BigDecimal;

import static bisq.cli.opts.OptLabel.*;
import static bisq.proto.grpc.EditOfferRequest.EditType.*;
import static java.lang.String.format;



import org.checkerframework.checker.nullness.qual.Nullable;

public class EditOfferOptionParser extends AbstractMethodOptionParser implements MethodOpts {

    static int OPT_ENABLE_ON = 1;
    static int OPT_ENABLE_OFF = 0;
    static int OPT_ENABLE_IGNORED = -1;

    final OptionSpec<String> offerIdOpt = parser.accepts(OPT_OFFER_ID, "id of offer to cancel")
            .withRequiredArg();

    final OptionSpec<String> fixedPriceOpt = parser.accepts(OPT_FIXED_PRICE, "fixed btc price")
            .withOptionalArg()
            .defaultsTo("0");

    final OptionSpec<String> mktPriceMarginOpt = parser.accepts(OPT_MKT_PRICE_MARGIN,
            "market btc price margin (%)")
            .withOptionalArg()
            .defaultsTo("0.00");

    final OptionSpec<String> triggerPriceOpt = parser.accepts(OPT_TRIGGER_PRICE,
            "trigger price (applies to mkt price margin based offers)")
            .withOptionalArg()
            .defaultsTo("0");

    // The 'enable' string opt is optional, and can be empty (meaning do not change
    // activation state).  For this reason, a boolean type is not used (can only be
    // true or false).
    final OptionSpec<String> enableOpt = parser.accepts(OPT_ENABLE,
            "enable or disable offer")
            .withOptionalArg()
            .ofType(String.class);

    private EditOfferRequest.EditType offerEditType;

    public EditOfferOptionParser(String[] args) {
        super(args);
    }

    public EditOfferOptionParser parse() {
        super.parse();

        // Short circuit opt validation if user just wants help.
        if (options.has(helpOpt))
            return this;

        if (!options.has(offerIdOpt) || options.valueOf(offerIdOpt).isEmpty())
            throw new IllegalArgumentException("no offer id specified");

        boolean hasNoEditDetails = !options.has(fixedPriceOpt)
                && !options.has(mktPriceMarginOpt)
                && !options.has(triggerPriceOpt)
                && !options.has(enableOpt);
        if (hasNoEditDetails)
            throw new IllegalArgumentException("no edit details specified");

        if (options.has(enableOpt)) {
            if (valueNotSpecified.test(enableOpt))
                throw new IllegalArgumentException("invalid enable value specified, must be true|false");

            var enableOptValue = options.valueOf(enableOpt);
            if (!enableOptValue.equalsIgnoreCase("true")
                    && !enableOptValue.equalsIgnoreCase("false"))
                throw new IllegalArgumentException("invalid enable value specified, must be true|false");

            // A single enable opt is a valid opt combo.
            boolean enableOptIsOnlyOpt = !options.has(fixedPriceOpt)
                    && !options.has(mktPriceMarginOpt)
                    && !options.has(triggerPriceOpt);
            if (enableOptIsOnlyOpt) {
                offerEditType = ACTIVATION_STATE_ONLY;
                return this;
            }
        }

        if (options.has(fixedPriceOpt)) {
            if (valueNotSpecified.test(fixedPriceOpt))
                throw new IllegalArgumentException("no fixed price specified");

            String fixedPriceAsString = options.valueOf(fixedPriceOpt);
            verifyStringIsValidDouble(fixedPriceAsString);

            boolean fixedPriceOptIsOnlyOpt = !options.has(mktPriceMarginOpt)
                    && !options.has(triggerPriceOpt)
                    && !options.has(enableOpt);
            if (fixedPriceOptIsOnlyOpt) {
                offerEditType = FIXED_PRICE_ONLY;
                return this;
            }

            boolean fixedPriceOptAndEnableOptAreOnlyOpts = options.has(enableOpt)
                    && !options.has(mktPriceMarginOpt)
                    && !options.has(triggerPriceOpt);
            if (fixedPriceOptAndEnableOptAreOnlyOpts) {
                offerEditType = FIXED_PRICE_AND_ACTIVATION_STATE;
                return this;
            }
        }

        if (options.has(mktPriceMarginOpt)) {
            if (valueNotSpecified.test(mktPriceMarginOpt))
                throw new IllegalArgumentException("no mkt price margin specified");

            String priceMarginAsString = options.valueOf(mktPriceMarginOpt);
            if (priceMarginAsString.isEmpty())
                throw new IllegalArgumentException("no market price margin specified");

            verifyStringIsValidDouble(priceMarginAsString);

            boolean mktPriceMarginOptIsOnlyOpt = !options.has(triggerPriceOpt)
                    && !options.has(fixedPriceOpt)
                    && !options.has(enableOpt);
            if (mktPriceMarginOptIsOnlyOpt) {
                offerEditType = MKT_PRICE_MARGIN_ONLY;
                return this;
            }

            boolean mktPriceMarginOptAndEnableOptAreOnlyOpts = options.has(enableOpt)
                    && !options.has(triggerPriceOpt);
            if (mktPriceMarginOptAndEnableOptAreOnlyOpts) {
                offerEditType = MKT_PRICE_MARGIN_AND_ACTIVATION_STATE;
                return this;
            }
        }

        if (options.has(triggerPriceOpt)) {
            if (valueNotSpecified.test(triggerPriceOpt))
                throw new IllegalArgumentException("no trigger price specified");

            String triggerPriceAsString = options.valueOf(fixedPriceOpt);
            if (triggerPriceAsString.isEmpty())
                throw new IllegalArgumentException("trigger price not specified");

            verifyStringIsValidDouble(triggerPriceAsString);

            boolean triggerPriceOptIsOnlyOpt = !options.has(mktPriceMarginOpt)
                    && !options.has(fixedPriceOpt)
                    && !options.has(enableOpt);
            if (triggerPriceOptIsOnlyOpt) {
                offerEditType = TRIGGER_PRICE_ONLY;
                return this;
            }

            boolean triggerPriceOptAndEnableOptAreOnlyOpts = !options.has(mktPriceMarginOpt)
                    && !options.has(fixedPriceOpt)
                    && options.has(enableOpt);
            if (triggerPriceOptAndEnableOptAreOnlyOpts) {
                offerEditType = TRIGGER_PRICE_AND_ACTIVATION_STATE;
                return this;
            }
        }

        if (options.has(mktPriceMarginOpt) && options.has(fixedPriceOpt))
            throw new IllegalArgumentException("cannot specify market price margin and fixed price");

        if (options.has(fixedPriceOpt) && options.has(triggerPriceOpt))
            throw new IllegalArgumentException("trigger price cannot be set on fixed price offers");

        if (options.has(mktPriceMarginOpt) && options.has(triggerPriceOpt) && !options.has(enableOpt)) {
            offerEditType = MKT_PRICE_MARGIN_AND_TRIGGER_PRICE;
            return this;
        }

        if (options.has(mktPriceMarginOpt) && options.has(triggerPriceOpt) && options.has(enableOpt)) {
            offerEditType = MKT_PRICE_MARGIN_AND_TRIGGER_PRICE_AND_ACTIVATION_STATE;
            return this;
        }

        return this;
    }

    public String getOfferId() {
        return options.valueOf(offerIdOpt);
    }

    public String getFixedPrice() {
        if (offerEditType.equals(FIXED_PRICE_ONLY) || offerEditType.equals(FIXED_PRICE_AND_ACTIVATION_STATE)) {
            return options.has(fixedPriceOpt) ? options.valueOf(fixedPriceOpt) : "0";
        } else {
            return "0";
        }
    }

    public String getTriggerPrice() {
        if (offerEditType.equals(TRIGGER_PRICE_ONLY)
                || offerEditType.equals(TRIGGER_PRICE_AND_ACTIVATION_STATE)
                || offerEditType.equals(MKT_PRICE_MARGIN_AND_TRIGGER_PRICE)
                || offerEditType.equals(MKT_PRICE_MARGIN_AND_TRIGGER_PRICE_AND_ACTIVATION_STATE)) {
            return options.has(triggerPriceOpt) ? options.valueOf(triggerPriceOpt) : "0";
        } else {
            return "0";
        }
    }

    public BigDecimal getTriggerPriceAsBigDecimal() {
        return new BigDecimal(getTriggerPrice());
    }

    public String getMktPriceMargin() {
        if (offerEditType.equals(MKT_PRICE_MARGIN_ONLY)
                || offerEditType.equals(MKT_PRICE_MARGIN_AND_ACTIVATION_STATE)
                || offerEditType.equals(MKT_PRICE_MARGIN_AND_TRIGGER_PRICE)
                || offerEditType.equals(MKT_PRICE_MARGIN_AND_TRIGGER_PRICE_AND_ACTIVATION_STATE)) {
            return isUsingMktPriceMargin() ? options.valueOf(mktPriceMarginOpt) : "0.00";
        } else {
            return "0.00";
        }
    }

    public BigDecimal getMktPriceMarginAsBigDecimal() {
        return new BigDecimal(options.valueOf(mktPriceMarginOpt));
    }

    public boolean isUsingMktPriceMargin() {
        return !offerEditType.equals(FIXED_PRICE_ONLY)
                && !offerEditType.equals(FIXED_PRICE_AND_ACTIVATION_STATE);
    }

    public int getEnableAsSignedInt() {
        // Client sends sint32 in grpc request, not a bool that can only be true or false.
        // If enable = -1, do not change activation state
        // If enable =  0, set state = AVAILABLE
        // If enable =  1, set state = DEACTIVATED
        @Nullable
        Boolean input = isEnable();
        return input == null
                ? OPT_ENABLE_IGNORED
                : input ? OPT_ENABLE_ON : OPT_ENABLE_OFF;
    }

    @Nullable
    public Boolean isEnable() {
        return options.has(enableOpt)
                ? Boolean.valueOf(options.valueOf(enableOpt))
                : null;
    }

    public EditOfferRequest.EditType getOfferEditType() {
        return offerEditType;
    }

    private void verifyStringIsValidDouble(String string) {
        try {
            Double.valueOf(string);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(format("%s is not a number", string));
        }
    }
}
