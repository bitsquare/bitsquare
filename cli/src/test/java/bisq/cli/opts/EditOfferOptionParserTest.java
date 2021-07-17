package bisq.cli.opts;

import org.junit.jupiter.api.Test;

import static bisq.cli.Method.editoffer;
import static bisq.cli.opts.EditOfferOptionParser.OPT_ENABLE_IGNORED;
import static bisq.cli.opts.EditOfferOptionParser.OPT_ENABLE_OFF;
import static bisq.cli.opts.EditOfferOptionParser.OPT_ENABLE_ON;
import static bisq.cli.opts.OptLabel.*;
import static bisq.proto.grpc.EditOfferRequest.EditType.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// This opt parser test ahs the most thorough coverage,
// and is a reference for other opt parser tests.
public class EditOfferOptionParserTest {

    private static final String PASSWORD_OPT = "--" + OPT_PASSWORD + "=" + "xyz";

    @Test
    public void testEditOfferWithMissingOfferIdOptShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                editoffer.name()
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new EditOfferOptionParser(args).parse());
        assertEquals("no offer id specified", exception.getMessage());
    }

    @Test
    public void testEditOfferWithoutAnyOptsShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                editoffer.name(),
                "--" + OPT_OFFER_ID + "=" + "ABC-OFFER-ID"
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new EditOfferOptionParser(args).parse());
        assertEquals("no edit details specified", exception.getMessage());
    }

    @Test
    public void testEditOfferWithEmptyEnableOptValueShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                editoffer.name(),
                "--" + OPT_OFFER_ID + "=" + "ABC-OFFER-ID",
                "--" + OPT_ENABLE + "=" // missing opt value
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new EditOfferOptionParser(args).parse());
        assertEquals("invalid enable value specified, must be true|false",
                exception.getMessage());
    }

    @Test
    public void testEditOfferWithMissingEnableValueShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                editoffer.name(),
                "--" + OPT_OFFER_ID + "=" + "ABC-OFFER-ID",
                "--" + OPT_ENABLE // missing equals sign & opt value
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new EditOfferOptionParser(args).parse());
        assertEquals("invalid enable value specified, must be true|false",
                exception.getMessage());
    }

    @Test
    public void testEditOfferWithInvalidEnableValueShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                editoffer.name(),
                "--" + OPT_OFFER_ID + "=" + "ABC-OFFER-ID",
                "--" + OPT_ENABLE + "=0"
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new EditOfferOptionParser(args).parse());
        assertEquals("invalid enable value specified, must be true|false",
                exception.getMessage());
    }

    @Test
    public void testEditOfferWithMktPriceOptAndFixedPriceOptShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                editoffer.name(),
                "--" + OPT_OFFER_ID + "=" + "ABC-OFFER-ID",
                "--" + OPT_MKT_PRICE_MARGIN + "=0.11",
                "--" + OPT_FIXED_PRICE + "=50000.0000"
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new EditOfferOptionParser(args).parse());
        assertEquals("cannot specify market price margin and fixed price",
                exception.getMessage());
    }

    @Test
    public void testEditOfferWithFixedPriceOptAndTriggerPriceOptShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                editoffer.name(),
                "--" + OPT_OFFER_ID + "=" + "ABC-OFFER-ID",
                "--" + OPT_FIXED_PRICE + "=50000.0000",
                "--" + OPT_TRIGGER_PRICE + "=51000.0000"
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new EditOfferOptionParser(args).parse());
        assertEquals("trigger price cannot be set on fixed price offers",
                exception.getMessage());
    }

    @Test
    public void testEditOfferActivationStateOnly() {
        String[] args = new String[]{
                PASSWORD_OPT,
                editoffer.name(),
                "--" + OPT_OFFER_ID + "=" + "ABC-OFFER-ID",
                "--" + OPT_ENABLE + "=" + "true"
        };
        EditOfferOptionParser parser = new EditOfferOptionParser(args).parse();
        assertEquals(ACTIVATION_STATE_ONLY, parser.getOfferEditType());
        assertEquals(OPT_ENABLE_ON, parser.getEnableAsSignedInt());
    }

    @Test
    public void testEditOfferFixedPriceWithoutOptValueShouldThrowException1() {
        String[] args = new String[]{
                PASSWORD_OPT,
                editoffer.name(),
                "--" + OPT_OFFER_ID + "=" + "ABC-OFFER-ID",
                "--" + OPT_FIXED_PRICE + "="
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new EditOfferOptionParser(args).parse());
        assertEquals("no fixed price specified",
                exception.getMessage());
    }

    @Test
    public void testEditOfferFixedPriceWithoutOptValueShouldThrowException2() {
        String[] args = new String[]{
                PASSWORD_OPT,
                editoffer.name(),
                "--" + OPT_OFFER_ID + "=" + "ABC-OFFER-ID",
                "--" + OPT_FIXED_PRICE
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new EditOfferOptionParser(args).parse());
        assertEquals("no fixed price specified",
                exception.getMessage());
    }

    @Test
    public void testEditOfferFixedPriceOnly() {
        String fixedPriceAsString = "50000.0000";
        String[] args = new String[]{
                PASSWORD_OPT,
                editoffer.name(),
                "--" + OPT_OFFER_ID + "=" + "ABC-OFFER-ID",
                "--" + OPT_FIXED_PRICE + "=" + fixedPriceAsString
        };
        EditOfferOptionParser parser = new EditOfferOptionParser(args).parse();
        assertEquals(FIXED_PRICE_ONLY, parser.getOfferEditType());
        assertEquals(fixedPriceAsString, parser.getFixedPrice());
        assertFalse(parser.isUsingMktPriceMargin());
        assertEquals("0.00", parser.getMktPriceMargin());
        assertEquals(OPT_ENABLE_IGNORED, parser.getEnableAsSignedInt());
    }

    @Test
    public void testEditOfferFixedPriceAndActivationStateOnly() {
        String fixedPriceAsString = "50000.0000";
        String[] args = new String[]{
                PASSWORD_OPT,
                editoffer.name(),
                "--" + OPT_OFFER_ID + "=" + "ABC-OFFER-ID",
                "--" + OPT_FIXED_PRICE + "=" + fixedPriceAsString,
                "--" + OPT_ENABLE + "=" + "false"
        };
        EditOfferOptionParser parser = new EditOfferOptionParser(args).parse();
        assertEquals(FIXED_PRICE_AND_ACTIVATION_STATE, parser.getOfferEditType());
        assertEquals(fixedPriceAsString, parser.getFixedPrice());
        assertFalse(parser.isUsingMktPriceMargin());
        assertEquals("0.00", parser.getMktPriceMargin());
        assertEquals(OPT_ENABLE_OFF, parser.getEnableAsSignedInt());
    }

    @Test
    public void testEditOfferMktPriceMarginOnly() {
        String mktPriceMarginAsString = "0.25";
        String[] args = new String[]{
                PASSWORD_OPT,
                editoffer.name(),
                "--" + OPT_OFFER_ID + "=" + "ABC-OFFER-ID",
                "--" + OPT_MKT_PRICE_MARGIN + "=" + mktPriceMarginAsString
        };
        EditOfferOptionParser parser = new EditOfferOptionParser(args).parse();
        assertEquals(MKT_PRICE_MARGIN_ONLY, parser.getOfferEditType());
        assertTrue(parser.isUsingMktPriceMargin());
        assertEquals(mktPriceMarginAsString, parser.getMktPriceMargin());
        assertEquals("0", parser.getTriggerPrice());
        assertEquals(OPT_ENABLE_IGNORED, parser.getEnableAsSignedInt());
    }

    @Test
    public void testEditOfferMktPriceMarginWithoutOptValueShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                editoffer.name(),
                "--" + OPT_OFFER_ID + "=" + "ABC-OFFER-ID",
                "--" + OPT_MKT_PRICE_MARGIN
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new EditOfferOptionParser(args).parse());
        assertEquals("no mkt price margin specified",
                exception.getMessage());
    }

    @Test
    public void testEditOfferMktPriceMarginAndActivationStateOnly() {
        String mktPriceMarginAsString = "0.15";
        String[] args = new String[]{
                PASSWORD_OPT,
                editoffer.name(),
                "--" + OPT_OFFER_ID + "=" + "ABC-OFFER-ID",
                "--" + OPT_MKT_PRICE_MARGIN + "=" + mktPriceMarginAsString,
                "--" + OPT_ENABLE + "=" + "false"
        };
        EditOfferOptionParser parser = new EditOfferOptionParser(args).parse();
        assertEquals(MKT_PRICE_MARGIN_AND_ACTIVATION_STATE, parser.getOfferEditType());
        assertTrue(parser.isUsingMktPriceMargin());
        assertEquals(mktPriceMarginAsString, parser.getMktPriceMargin());
        assertEquals("0", parser.getTriggerPrice());
        assertEquals(OPT_ENABLE_OFF, parser.getEnableAsSignedInt());
    }

    @Test
    public void testEditTriggerPriceOnly() {
        String triggerPriceAsString = "50000.0000";
        String[] args = new String[]{
                PASSWORD_OPT,
                editoffer.name(),
                "--" + OPT_OFFER_ID + "=" + "ABC-OFFER-ID",
                "--" + OPT_TRIGGER_PRICE + "=" + triggerPriceAsString
        };
        EditOfferOptionParser parser = new EditOfferOptionParser(args).parse();
        assertEquals(TRIGGER_PRICE_ONLY, parser.getOfferEditType());
        assertEquals(triggerPriceAsString, parser.getTriggerPrice());
        assertTrue(parser.isUsingMktPriceMargin());
        assertEquals("0.00", parser.getMktPriceMargin());
        assertEquals(OPT_ENABLE_IGNORED, parser.getEnableAsSignedInt());
    }

    @Test
    public void testEditTriggerPriceWithoutOptValueShouldThrowException1() {
        String[] args = new String[]{
                PASSWORD_OPT,
                editoffer.name(),
                "--" + OPT_OFFER_ID + "=" + "ABC-OFFER-ID",
                "--" + OPT_TRIGGER_PRICE + "="
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new EditOfferOptionParser(args).parse());
        assertEquals("no trigger price specified",
                exception.getMessage());
    }

    @Test
    public void testEditTriggerPriceWithoutOptValueShouldThrowException2() {
        String[] args = new String[]{
                PASSWORD_OPT,
                editoffer.name(),
                "--" + OPT_OFFER_ID + "=" + "ABC-OFFER-ID",
                "--" + OPT_TRIGGER_PRICE
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new EditOfferOptionParser(args).parse());
        assertEquals("no trigger price specified",
                exception.getMessage());
    }

    @Test
    public void testEditTriggerPriceAndActivationStateOnly() {
        String triggerPriceAsString = "50000.0000";
        String[] args = new String[]{
                PASSWORD_OPT,
                editoffer.name(),
                "--" + OPT_OFFER_ID + "=" + "ABC-OFFER-ID",
                "--" + OPT_TRIGGER_PRICE + "=" + triggerPriceAsString,
                "--" + OPT_ENABLE + "=" + "true"
        };
        EditOfferOptionParser parser = new EditOfferOptionParser(args).parse();
        assertEquals(TRIGGER_PRICE_AND_ACTIVATION_STATE, parser.getOfferEditType());
        assertEquals(triggerPriceAsString, parser.getTriggerPrice());
        assertTrue(parser.isUsingMktPriceMargin());
        assertEquals("0.00", parser.getMktPriceMargin());
        assertEquals("0", parser.getFixedPrice());
        assertEquals(OPT_ENABLE_ON, parser.getEnableAsSignedInt());
    }

    @Test
    public void testEditMKtPriceMarginAndTriggerPrice() {
        String mktPriceMarginAsString = "0.25";
        String triggerPriceAsString = "50000.0000";
        String[] args = new String[]{
                PASSWORD_OPT,
                editoffer.name(),
                "--" + OPT_OFFER_ID + "=" + "ABC-OFFER-ID",
                "--" + OPT_MKT_PRICE_MARGIN + "=" + mktPriceMarginAsString,
                "--" + OPT_TRIGGER_PRICE + "=" + triggerPriceAsString
        };
        EditOfferOptionParser parser = new EditOfferOptionParser(args).parse();
        assertEquals(MKT_PRICE_MARGIN_AND_TRIGGER_PRICE, parser.getOfferEditType());
        assertEquals(triggerPriceAsString, parser.getTriggerPrice());
        assertTrue(parser.isUsingMktPriceMargin());
        assertEquals(mktPriceMarginAsString, parser.getMktPriceMargin());
        assertEquals("0", parser.getFixedPrice());
        assertEquals(OPT_ENABLE_IGNORED, parser.getEnableAsSignedInt());
    }

    @Test
    public void testEditMKtPriceMarginAndTriggerPriceAndEnableState() {
        String mktPriceMarginAsString = "0.25";
        String triggerPriceAsString = "50000.0000";
        String[] args = new String[]{
                PASSWORD_OPT,
                editoffer.name(),
                "--" + OPT_OFFER_ID + "=" + "ABC-OFFER-ID",
                "--" + OPT_MKT_PRICE_MARGIN + "=" + mktPriceMarginAsString,
                "--" + OPT_TRIGGER_PRICE + "=" + triggerPriceAsString,
                "--" + OPT_ENABLE + "=" + "FALSE"
        };
        EditOfferOptionParser parser = new EditOfferOptionParser(args).parse();
        assertEquals(MKT_PRICE_MARGIN_AND_TRIGGER_PRICE_AND_ACTIVATION_STATE, parser.getOfferEditType());
        assertEquals(triggerPriceAsString, parser.getTriggerPrice());
        assertTrue(parser.isUsingMktPriceMargin());
        assertEquals(mktPriceMarginAsString, parser.getMktPriceMargin());
        assertEquals("0", parser.getFixedPrice());
        assertEquals(OPT_ENABLE_OFF, parser.getEnableAsSignedInt());
    }
}
