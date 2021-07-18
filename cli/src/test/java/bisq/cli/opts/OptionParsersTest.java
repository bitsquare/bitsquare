package bisq.cli.opts;

import org.junit.jupiter.api.Test;

import static bisq.cli.Method.canceloffer;
import static bisq.cli.Method.createcryptopaymentacct;
import static bisq.cli.Method.createoffer;
import static bisq.cli.Method.createpaymentacct;
import static bisq.cli.opts.OptLabel.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


public class OptionParsersTest {

    private static final String PASSWORD_OPT = "--" + OPT_PASSWORD + "=" + "xyz";

    // canceloffer opt parser tests

    @Test
    public void testCancelOfferWithMissingOfferIdOptShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                canceloffer.name()
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new CancelOfferOptionParser(args).parse());
        assertEquals("no offer id specified", exception.getMessage());
    }

    @Test
    public void testCancelOfferWithEmptyOfferIdOptShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                canceloffer.name(),
                "--" + OPT_OFFER_ID + "=" // missing opt value
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new CancelOfferOptionParser(args).parse());
        assertEquals("no offer id specified", exception.getMessage());
    }

    @Test
    public void testCancelOfferWithMissingOfferIdValueShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                canceloffer.name(),
                "--" + OPT_OFFER_ID // missing equals sign & opt value
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new CancelOfferOptionParser(args).parse());
        assertEquals("offer-id requires an argument", exception.getMessage());
    }

    @Test
    public void testValidCancelOfferOpts() {
        String[] args = new String[]{
                PASSWORD_OPT,
                canceloffer.name(),
                "--" + OPT_OFFER_ID + "=" + "ABC-OFFER-ID"
        };
        new CancelOfferOptionParser(args).parse();
    }

    // createoffer opt parser tests

    @Test
    public void testCreateOfferOptParserWithMissingPaymentAccountIdOptShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                createoffer.name()
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new CreateOfferOptionParser(args).parse());
        assertEquals("no payment account id specified", exception.getMessage());
    }

    @Test
    public void testCreateOfferOptParserWithEmptyPaymentAccountIdOptShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                createoffer.name(),
                "--" + OPT_PAYMENT_ACCOUNT
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new CreateOfferOptionParser(args).parse());
        assertEquals("payment-account requires an argument", exception.getMessage());
    }

    @Test
    public void testCreateOfferOptParserWithMissingDirectionOptShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                createoffer.name(),
                "--" + OPT_PAYMENT_ACCOUNT + "=" + "abc-payment-acct-id-123"
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new CreateOfferOptionParser(args).parse());
        assertEquals("no direction (buy|sell) specified", exception.getMessage());
    }


    @Test
    public void testCreateOfferOptParserWithMissingDirectionOptValueShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                createoffer.name(),
                "--" + OPT_PAYMENT_ACCOUNT + "=" + "abc-payment-acct-id-123",
                "--" + OPT_DIRECTION + "=" + ""
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new CreateOfferOptionParser(args).parse());
        assertEquals("no direction (buy|sell) specified", exception.getMessage());
    }

    @Test
    public void testValidCreateOfferOpts() {
        String[] args = new String[]{
                PASSWORD_OPT,
                createoffer.name(),
                "--" + OPT_PAYMENT_ACCOUNT + "=" + "abc-payment-acct-id-123",
                "--" + OPT_DIRECTION + "=" + "BUY",
                "--" + OPT_CURRENCY_CODE + "=" + "EUR",
                "--" + OPT_AMOUNT + "=" + "0.125",
                "--" + OPT_MKT_PRICE_MARGIN + "=" + "0.0",
                "--" + OPT_SECURITY_DEPOSIT + "=" + "25.0"
        };
        CreateOfferOptionParser parser = new CreateOfferOptionParser(args).parse();
        assertEquals("abc-payment-acct-id-123", parser.getPaymentAccountId());
        assertEquals("BUY", parser.getDirection());
        assertEquals("EUR", parser.getCurrencyCode());
        assertEquals("0.125", parser.getAmount());
        assertEquals("0.0", parser.getMktPriceMargin());
        assertEquals("25.0", parser.getSecurityDeposit());
    }

    // createpaymentacct opt parser tests

    @Test
    public void testCreatePaymentAcctOptParserWithMissingPaymentFormOptShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                createpaymentacct.name()
                // OPT_PAYMENT_ACCOUNT_FORM
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new CreatePaymentAcctOptionParser(args).parse());
        assertEquals("no path to json payment account form specified", exception.getMessage());
    }

    @Test
    public void testCreatePaymentAcctOptParserWithMissingPaymentFormOptValueShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                createpaymentacct.name(),
                "--" + OPT_PAYMENT_ACCOUNT_FORM + "="
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new CreatePaymentAcctOptionParser(args).parse());
        assertEquals("no path to json payment account form specified", exception.getMessage());
    }

    @Test
    public void testCreatePaymentAcctOptParserWithInvalidPaymentFormOptValueShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                createpaymentacct.name(),
                "--" + OPT_PAYMENT_ACCOUNT_FORM + "=" + "/tmp/milkyway/solarsystem/mars"
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new CreatePaymentAcctOptionParser(args).parse());
        if (System.getProperty("os.name").toLowerCase().indexOf("win") >= 0)
            assertEquals("json payment account form '\\tmp\\milkyway\\solarsystem\\mars' could not be found",
                    exception.getMessage());
        else
            assertEquals("json payment account form '/tmp/milkyway/solarsystem/mars' could not be found",
                    exception.getMessage());
    }

    // createcryptopaymentacct parser tests

    @Test
    public void testCreateCryptoCurrencyPaymentAcctOptionParserWithMissingAcctNameOptShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                createcryptopaymentacct.name()
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new CreateCryptoCurrencyPaymentAcctOptionParser(args).parse());
        assertEquals("no payment account name specified", exception.getMessage());
    }

    @Test
    public void testCreateCryptoCurrencyPaymentAcctOptionParserWithEmptyAcctNameOptShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                createcryptopaymentacct.name(),
                "--" + OPT_ACCOUNT_NAME
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new CreateCryptoCurrencyPaymentAcctOptionParser(args).parse());
        assertEquals("account-name requires an argument", exception.getMessage());
    }

    @Test
    public void testCreateCryptoCurrencyPaymentAcctOptionParserWithMissingCurrencyCodeOptShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                createcryptopaymentacct.name(),
                "--" + OPT_ACCOUNT_NAME + "=" + "bsq payment account"
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new CreateCryptoCurrencyPaymentAcctOptionParser(args).parse());
        assertEquals("no currency code specified", exception.getMessage());
    }

    @Test
    public void testCreateCryptoCurrencyPaymentAcctOptionParserWithInvalidCurrencyCodeOptShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                createcryptopaymentacct.name(),
                "--" + OPT_ACCOUNT_NAME + "=" + "bsq payment account",
                "--" + OPT_CURRENCY_CODE + "=" + "xmr"
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new CreateCryptoCurrencyPaymentAcctOptionParser(args).parse());
        assertEquals("api only supports bsq crypto currency payment accounts", exception.getMessage());
    }

    @Test
    public void testCreateCryptoCurrencyPaymentAcctOptionParserWithMissingAddressOptShouldThrowException() {
        String[] args = new String[]{
                PASSWORD_OPT,
                createcryptopaymentacct.name(),
                "--" + OPT_ACCOUNT_NAME + "=" + "bsq payment account",
                "--" + OPT_CURRENCY_CODE + "=" + "bsq"
        };
        Throwable exception = assertThrows(RuntimeException.class, () ->
                new CreateCryptoCurrencyPaymentAcctOptionParser(args).parse());
        assertEquals("no bsq address specified", exception.getMessage());
    }

    @Test
    public void testCreateCryptoCurrencyPaymentAcctOptionParser() {
        var acctName = "bsq payment account";
        var currencyCode = "bsq";
        var address = "B1nXyZ"; // address is validated on server
        String[] args = new String[]{
                PASSWORD_OPT,
                createcryptopaymentacct.name(),
                "--" + OPT_ACCOUNT_NAME + "=" + acctName,
                "--" + OPT_CURRENCY_CODE + "=" + currencyCode,
                "--" + OPT_ADDRESS + "=" + address
        };
        var parser = new CreateCryptoCurrencyPaymentAcctOptionParser(args).parse();
        assertEquals(acctName, parser.getAccountName());
        assertEquals(currencyCode, parser.getCurrencyCode());
        assertEquals(address, parser.getAddress());
    }
}
