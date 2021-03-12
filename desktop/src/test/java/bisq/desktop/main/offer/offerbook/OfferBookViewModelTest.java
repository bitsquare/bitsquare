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

import bisq.desktop.main.PriceUtil;

import bisq.core.locale.Country;
import bisq.core.locale.CryptoCurrency;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.GlobalSettings;
import bisq.core.locale.Res;
import bisq.core.offer.FeeTxOfferPayload;
import bisq.core.offer.Offer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.AliPayAccount;
import bisq.core.payment.CountryBasedPaymentAccount;
import bisq.core.payment.CryptoCurrencyAccount;
import bisq.core.payment.NationalBankAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PaymentAccountUtil;
import bisq.core.payment.SameBankAccount;
import bisq.core.payment.SepaAccount;
import bisq.core.payment.SpecificBanksAccount;
import bisq.core.payment.payload.NationalBankAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.payment.payload.SameBankAccountPayload;
import bisq.core.payment.payload.SepaAccountPayload;
import bisq.core.payment.payload.SpecificBanksAccountPayload;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.coin.ImmutableCoinFormatter;

import bisq.common.config.Config;

import javafx.beans.property.SimpleIntegerProperty;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.Instant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.natpryce.makeiteasy.Maker;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static bisq.desktop.main.offer.offerbook.OfferBookListItemMaker.*;
import static bisq.desktop.maker.PreferenceMakers.empty;
import static bisq.desktop.maker.TradeCurrencyMakers.usd;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OfferBookViewModelTest {
    private final CoinFormatter coinFormatter = new ImmutableCoinFormatter(Config.baseCurrencyNetworkParameters().getMonetaryFormat());
    private static final Logger log = LoggerFactory.getLogger(OfferBookViewModelTest.class);

    @Before
    public void setUp() {
        GlobalSettings.setDefaultTradeCurrency(usd);
        Res.setBaseCurrencyCode(usd.getCode());
        Res.setBaseCurrencyName(usd.getName());
    }

    private PriceUtil getPriceUtil() {
        PriceFeedService priceFeedService = mock(PriceFeedService.class);
        TradeStatisticsManager tradeStatisticsManager = mock(TradeStatisticsManager.class);
        when(tradeStatisticsManager.getObservableTradeStatisticsSet()).thenReturn(FXCollections.observableSet());
        return new PriceUtil(priceFeedService, tradeStatisticsManager, empty);
    }

    @Ignore("PaymentAccountPayload needs to be set (has been changed with PB changes)")
    public void testIsAnyPaymentAccountValidForOffer() {
        Collection<PaymentAccount> paymentAccounts;
        paymentAccounts = new ArrayList<>(FXCollections.singletonObservableList(getSepaAccount("EUR", "DE", "1212324",
                new ArrayList<>(Arrays.asList("AT", "DE")))));
        assertTrue(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getSEPAPaymentMethod("EUR", "AT", new ArrayList<>(Arrays.asList("AT", "DE")), "PSK"), paymentAccounts));


        // empty paymentAccounts
        paymentAccounts = new ArrayList<>();
        assertFalse(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(getSEPAPaymentMethod("EUR", "AT",
                new ArrayList<>(Arrays.asList("AT", "DE")), "PSK"), paymentAccounts));

        // simple cases: same payment methods

        // offer: alipay paymentAccount: alipay - same country, same currency
        paymentAccounts = new ArrayList<>(FXCollections.singletonObservableList(getAliPayAccount("CNY")));
        assertTrue(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getAliPayPaymentMethod("EUR"), paymentAccounts));

        // offer: ether paymentAccount: ether - same country, same currency
        paymentAccounts = new ArrayList<>(FXCollections.singletonObservableList(getCryptoAccount("ETH")));
        assertTrue(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getBlockChainsPaymentMethod("ETH"), paymentAccounts));

        // offer: sepa paymentAccount: sepa - same country, same currency
        paymentAccounts = new ArrayList<>(FXCollections.singletonObservableList(getSepaAccount("EUR", "AT", "1212324",
                new ArrayList<>(Arrays.asList("AT", "DE")))));
        assertTrue(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getSEPAPaymentMethod("EUR", "AT", new ArrayList<>(Arrays.asList("AT", "DE")), "PSK"), paymentAccounts));

        // offer: nationalBank paymentAccount: nationalBank - same country, same currency
        paymentAccounts = new ArrayList<>(FXCollections.singletonObservableList(getNationalBankAccount("EUR", "AT", "PSK")));
        assertTrue(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getNationalBankPaymentMethod("EUR", "AT", "PSK"), paymentAccounts));

        // offer: SameBank paymentAccount: SameBank - same country, same currency
        paymentAccounts = new ArrayList<>(FXCollections.singletonObservableList(getSameBankAccount("EUR", "AT", "PSK")));
        assertTrue(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getSameBankPaymentMethod("EUR", "AT", "PSK"), paymentAccounts));

        // offer: sepa paymentAccount: sepa - diff. country, same currency
        paymentAccounts = new ArrayList<>(FXCollections.singletonObservableList(getSepaAccount("EUR", "DE", "1212324", new ArrayList<>(Arrays.asList("AT", "DE")))));
        assertTrue(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getSEPAPaymentMethod("EUR", "AT", new ArrayList<>(Arrays.asList("AT", "DE")), "PSK"), paymentAccounts));


        //////

        // offer: sepa paymentAccount: sepa - same country, same currency
        paymentAccounts = new ArrayList<>(FXCollections.singletonObservableList(getSepaAccount("EUR", "AT", "1212324", new ArrayList<>(Arrays.asList("AT", "DE")))));
        assertTrue(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getSEPAPaymentMethod("EUR", "AT", new ArrayList<>(Arrays.asList("AT", "DE")), "PSK"), paymentAccounts));


        // offer: sepa paymentAccount: nationalBank - same country, same currency
        // wrong method
        paymentAccounts = new ArrayList<>(FXCollections.singletonObservableList(getNationalBankAccount("EUR", "AT", "PSK")));
        assertFalse(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getSEPAPaymentMethod("EUR", "AT", new ArrayList<>(Arrays.asList("AT", "DE")), "PSK"), paymentAccounts));

        // wrong currency
        paymentAccounts = new ArrayList<>(FXCollections.singletonObservableList(getNationalBankAccount("USD", "US", "XXX")));
        assertFalse(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getNationalBankPaymentMethod("EUR", "AT", "PSK"), paymentAccounts));

        // wrong country
        paymentAccounts = new ArrayList<>(FXCollections.singletonObservableList(getNationalBankAccount("EUR", "FR", "PSK")));
        assertFalse(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getNationalBankPaymentMethod("EUR", "AT", "PSK"), paymentAccounts));

        // sepa wrong country
        paymentAccounts = new ArrayList<>(FXCollections.singletonObservableList(getNationalBankAccount("EUR", "CH", "PSK")));
        assertFalse(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getSEPAPaymentMethod("EUR", "AT", new ArrayList<>(Arrays.asList("AT", "DE")), "PSK"), paymentAccounts));

        // sepa wrong currency
        paymentAccounts = new ArrayList<>(FXCollections.singletonObservableList(getNationalBankAccount("CHF", "DE", "PSK")));
        assertFalse(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getSEPAPaymentMethod("EUR", "AT", new ArrayList<>(Arrays.asList("AT", "DE")), "PSK"), paymentAccounts));


        // same bank
        paymentAccounts = new ArrayList<>(FXCollections.singletonObservableList(getSameBankAccount("EUR", "AT", "PSK")));
        assertTrue(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getNationalBankPaymentMethod("EUR", "AT", "PSK"), paymentAccounts));

        // not same bank
        paymentAccounts = new ArrayList<>(FXCollections.singletonObservableList(getSameBankAccount("EUR", "AT", "Raika")));
        assertFalse(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getNationalBankPaymentMethod("EUR", "AT", "PSK"), paymentAccounts));

        // same bank, wrong country
        paymentAccounts = new ArrayList<>(FXCollections.singletonObservableList(getSameBankAccount("EUR", "DE", "PSK")));
        assertFalse(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getNationalBankPaymentMethod("EUR", "AT", "PSK"), paymentAccounts));

        // same bank, wrong currency
        paymentAccounts = new ArrayList<>(FXCollections.singletonObservableList(getSameBankAccount("USD", "AT", "PSK")));
        assertFalse(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getNationalBankPaymentMethod("EUR", "AT", "PSK"), paymentAccounts));

        // spec. bank
        paymentAccounts = new ArrayList<>(FXCollections.singletonObservableList(getSpecificBanksAccount("EUR", "AT", "PSK",
                new ArrayList<>(Arrays.asList("PSK", "Raika")))));
        assertTrue(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getNationalBankPaymentMethod("EUR", "AT", "PSK"), paymentAccounts));

        // spec. bank, missing bank
        paymentAccounts = new ArrayList<>(FXCollections.singletonObservableList(getSpecificBanksAccount("EUR", "AT", "PSK",
                new ArrayList<>(FXCollections.singletonObservableList("Raika")))));
        assertFalse(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getNationalBankPaymentMethod("EUR", "AT", "PSK"), paymentAccounts));

        // spec. bank, wrong country
        paymentAccounts = new ArrayList<>(FXCollections.singletonObservableList(getSpecificBanksAccount("EUR", "FR", "PSK",
                new ArrayList<>(Arrays.asList("PSK", "Raika")))));
        assertFalse(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getNationalBankPaymentMethod("EUR", "AT", "PSK"), paymentAccounts));

        // spec. bank, wrong currency
        paymentAccounts = new ArrayList<>(FXCollections.singletonObservableList(getSpecificBanksAccount("USD", "AT", "PSK",
                new ArrayList<>(Arrays.asList("PSK", "Raika")))));
        assertFalse(PaymentAccountUtil.isAnyPaymentAccountValidForOffer(
                getNationalBankPaymentMethod("EUR", "AT", "PSK"), paymentAccounts));

        //TODO add more tests

    }

    @Test
    public void testMaxCharactersForAmountWithNoOffers() {
        OfferBook offerBook = mock(OfferBook.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookViewModel model = new OfferBookViewModel(null, null, offerBook, empty, null, null, null,
                null, null, null, getPriceUtil(), null, coinFormatter, new BsqFormatter());
        assertEquals(0, model.maxPlacesForAmount.intValue());
    }

    @Test
    public void testMaxCharactersForAmount() {
        OfferBook offerBook = mock(OfferBook.class);
        OpenOfferManager openOfferManager = mock(OpenOfferManager.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        offerBookListItems.addAll(make(OfferBookListItemMaker.btcBuyItem));

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookViewModel model = new OfferBookViewModel(null, openOfferManager, offerBook, empty, null, null, null,
                null, null, null, getPriceUtil(), null, coinFormatter, new BsqFormatter());
        model.activate();

        assertEquals(6, model.maxPlacesForAmount.intValue());
        offerBookListItems.addAll(make(btcBuyItem.but(with(amount, 2000000000L))));
        assertEquals(7, model.maxPlacesForAmount.intValue());
    }

    @Test
    public void testMaxCharactersForAmountRange() {
        OfferBook offerBook = mock(OfferBook.class);
        OpenOfferManager openOfferManager = mock(OpenOfferManager.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        offerBookListItems.addAll(make(OfferBookListItemMaker.btcItemWithRange));

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookViewModel model = new OfferBookViewModel(null, openOfferManager, offerBook, empty, null, null, null,
                null, null, null, getPriceUtil(), null, coinFormatter, new BsqFormatter());
        model.activate();

        assertEquals(15, model.maxPlacesForAmount.intValue());
        offerBookListItems.addAll(make(btcItemWithRange.but(with(amount, 2000000000L))));
        assertEquals(16, model.maxPlacesForAmount.intValue());
        offerBookListItems.addAll(make(btcItemWithRange.but(with(minAmount, 30000000000L),
                with(amount, 30000000000L))));
        assertEquals(19, model.maxPlacesForAmount.intValue());
    }

    @Test
    public void testMaxCharactersForVolumeWithNoOffers() {
        OfferBook offerBook = mock(OfferBook.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookViewModel model = new OfferBookViewModel(null, null, offerBook, empty, null, null, null,
                null, null, null, getPriceUtil(), null, coinFormatter, new BsqFormatter());
        assertEquals(0, model.maxPlacesForVolume.intValue());
    }

    @Test
    public void testMaxCharactersForVolume() {
        OfferBook offerBook = mock(OfferBook.class);
        OpenOfferManager openOfferManager = mock(OpenOfferManager.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        offerBookListItems.addAll(make(OfferBookListItemMaker.btcBuyItem));

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookViewModel model = new OfferBookViewModel(null, openOfferManager, offerBook, empty, null, null, null,
                null, null, null, getPriceUtil(), null, coinFormatter, new BsqFormatter());
        model.activate();

        assertEquals(5, model.maxPlacesForVolume.intValue());
        offerBookListItems.addAll(make(btcBuyItem.but(with(amount, 2000000000L))));
        assertEquals(7, model.maxPlacesForVolume.intValue());
    }

    @Test
    public void testMaxCharactersForVolumeRange() {
        OfferBook offerBook = mock(OfferBook.class);
        OpenOfferManager openOfferManager = mock(OpenOfferManager.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        offerBookListItems.addAll(make(OfferBookListItemMaker.btcItemWithRange));

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookViewModel model = new OfferBookViewModel(null, openOfferManager, offerBook, empty, null, null, null,
                null, null, null, getPriceUtil(), null, coinFormatter, new BsqFormatter());
        model.activate();

        assertEquals(9, model.maxPlacesForVolume.intValue());
        offerBookListItems.addAll(make(btcItemWithRange.but(with(amount, 2000000000L))));
        assertEquals(11, model.maxPlacesForVolume.intValue());
        offerBookListItems.addAll(make(btcItemWithRange.but(with(minAmount, 30000000000L),
                with(amount, 30000000000L))));
        assertEquals(19, model.maxPlacesForVolume.intValue());
    }

    @Test
    public void testMaxCharactersForPriceWithNoOffers() {
        OfferBook offerBook = mock(OfferBook.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookViewModel model = new OfferBookViewModel(null, null, offerBook, empty, null, null, null,
                null, null, null, getPriceUtil(), null, coinFormatter, new BsqFormatter());
        assertEquals(0, model.maxPlacesForPrice.intValue());
    }

    @Test
    public void testMaxCharactersForPrice() {
        OfferBook offerBook = mock(OfferBook.class);
        OpenOfferManager openOfferManager = mock(OpenOfferManager.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        offerBookListItems.addAll(make(OfferBookListItemMaker.btcBuyItem));

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookViewModel model = new OfferBookViewModel(null, openOfferManager, offerBook, empty, null, null, null,
                null, null, null, getPriceUtil(), null, coinFormatter, new BsqFormatter());
        model.activate();

        assertEquals(7, model.maxPlacesForPrice.intValue());
        offerBookListItems.addAll(make(btcBuyItem.but(with(price, 149558240L)))); //14955.8240
        assertEquals(10, model.maxPlacesForPrice.intValue());
        offerBookListItems.addAll(make(btcBuyItem.but(with(price, 14955824L)))); //1495.58240
        assertEquals(10, model.maxPlacesForPrice.intValue());
    }

    @Test
    public void testMaxCharactersForPriceDistanceWithNoOffers() {
        OfferBook offerBook = mock(OfferBook.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookViewModel model = new OfferBookViewModel(null, null, offerBook, empty, null, null, null,
                null, null, null, getPriceUtil(), null, coinFormatter, new BsqFormatter());
        assertEquals(0, model.maxPlacesForMarketPriceMargin.intValue());
    }

    @Test
    public void testMaxCharactersForPriceDistance() {
        OfferBook offerBook = mock(OfferBook.class);
        OpenOfferManager openOfferManager = mock(OpenOfferManager.class);
        PriceFeedService priceFeedService = mock(PriceFeedService.class);

        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        final Maker<OfferBookListItem> item = btcBuyItem.but(with(useMarketBasedPrice, true));

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);
        when(priceFeedService.getMarketPrice(anyString())).thenReturn(null);
        when(priceFeedService.updateCounterProperty()).thenReturn(new SimpleIntegerProperty());

        final OfferBookListItem item1 = make(item);
        item1.getOffer().setPriceFeedService(priceFeedService);
        final OfferBookListItem item2 = make(item.but(with(marketPriceMargin, 0.0197)));
        item2.getOffer().setPriceFeedService(priceFeedService);
        final OfferBookListItem item3 = make(item.but(with(marketPriceMargin, 0.1)));
        item3.getOffer().setPriceFeedService(priceFeedService);
        final OfferBookListItem item4 = make(item.but(with(marketPriceMargin, -0.1)));
        item4.getOffer().setPriceFeedService(priceFeedService);
        offerBookListItems.addAll(item1, item2);

        final OfferBookViewModel model = new OfferBookViewModel(null, openOfferManager, offerBook, empty, null, null, priceFeedService,
                null, null, null, getPriceUtil(), null, coinFormatter, new BsqFormatter());
        model.activate();

        assertEquals(8, model.maxPlacesForMarketPriceMargin.intValue()); //" (1.97%)"
        offerBookListItems.addAll(item3);
        assertEquals(9, model.maxPlacesForMarketPriceMargin.intValue()); //" (10.00%)"
        offerBookListItems.addAll(item4);
        assertEquals(10, model.maxPlacesForMarketPriceMargin.intValue()); //" (-10.00%)"
    }

    @Test
    public void testGetPrice() {
        OfferBook offerBook = mock(OfferBook.class);
        OpenOfferManager openOfferManager = mock(OpenOfferManager.class);
        PriceFeedService priceFeedService = mock(PriceFeedService.class);

        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);
        when(priceFeedService.getMarketPrice(anyString())).thenReturn(new MarketPrice("USD", 12684.0450, Instant.now().getEpochSecond(), true));

        final OfferBookViewModel model = new OfferBookViewModel(null, openOfferManager, offerBook, empty, null, null, null,
                null, null, null, getPriceUtil(), null, coinFormatter, new BsqFormatter());

        final OfferBookListItem item = make(btcBuyItem.but(
                with(useMarketBasedPrice, true),
                with(marketPriceMargin, -0.12)));

        final OfferBookListItem lowItem = make(btcBuyItem.but(
                with(useMarketBasedPrice, true),
                with(marketPriceMargin, 0.01)));

        final OfferBookListItem fixedItem = make(btcBuyItem);

        item.getOffer().setPriceFeedService(priceFeedService);
        lowItem.getOffer().setPriceFeedService(priceFeedService);
        offerBookListItems.addAll(lowItem, fixedItem);
        model.activate();


        assertEquals("12557.2046", model.getPrice(lowItem));
        assertEquals("(1.00%)", model.getPriceAsPercentage(lowItem));
        assertEquals("10.0000", model.getPrice(fixedItem));
        offerBookListItems.addAll(item);
        assertEquals("14206.1304", model.getPrice(item));
        assertEquals("(-12.00%)", model.getPriceAsPercentage(item));
        assertEquals("12557.2046", model.getPrice(lowItem));
        assertEquals("(1.00%)", model.getPriceAsPercentage(lowItem));
    }

    private PaymentAccount getAliPayAccount(String currencyCode) {
        PaymentAccount paymentAccount = new AliPayAccount();
        paymentAccount.setSelectedTradeCurrency(new FiatCurrency(currencyCode));
        return paymentAccount;
    }

    private PaymentAccount getCryptoAccount(String currencyCode) {
        PaymentAccount paymentAccount = new CryptoCurrencyAccount();
        paymentAccount.addCurrency(new CryptoCurrency(currencyCode, null));
        return paymentAccount;
    }

    private PaymentAccount getSepaAccount(String currencyCode,
                                          String countryCode,
                                          String bic,
                                          ArrayList<String> countryCodes) {
        CountryBasedPaymentAccount paymentAccount = new SepaAccount();
        paymentAccount.setSingleTradeCurrency(new FiatCurrency(currencyCode));
        paymentAccount.setCountry(new Country(countryCode, null, null));
        ((SepaAccountPayload) paymentAccount.getPaymentAccountPayload()).setBic(bic);
        countryCodes.forEach(((SepaAccountPayload) paymentAccount.getPaymentAccountPayload())::addAcceptedCountry);
        return paymentAccount;
    }

    private PaymentAccount getNationalBankAccount(String currencyCode, String countryCode, String bankId) {
        CountryBasedPaymentAccount paymentAccount = new NationalBankAccount();
        paymentAccount.setSingleTradeCurrency(new FiatCurrency(currencyCode));
        paymentAccount.setCountry(new Country(countryCode, null, null));
        ((NationalBankAccountPayload) paymentAccount.getPaymentAccountPayload()).setBankId(bankId);
        return paymentAccount;
    }

    private PaymentAccount getSameBankAccount(String currencyCode, String countryCode, String bankId) {
        SameBankAccount paymentAccount = new SameBankAccount();
        paymentAccount.setSingleTradeCurrency(new FiatCurrency(currencyCode));
        paymentAccount.setCountry(new Country(countryCode, null, null));
        ((SameBankAccountPayload) paymentAccount.getPaymentAccountPayload()).setBankId(bankId);
        return paymentAccount;
    }

    private PaymentAccount getSpecificBanksAccount(String currencyCode,
                                                   String countryCode,
                                                   String bankId,
                                                   ArrayList<String> bankIds) {
        SpecificBanksAccount paymentAccount = new SpecificBanksAccount();
        paymentAccount.setSingleTradeCurrency(new FiatCurrency(currencyCode));
        paymentAccount.setCountry(new Country(countryCode, null, null));
        ((SpecificBanksAccountPayload) paymentAccount.getPaymentAccountPayload()).setBankId(bankId);
        bankIds.forEach(((SpecificBanksAccountPayload) paymentAccount.getPaymentAccountPayload())::addAcceptedBank);
        return paymentAccount;
    }


    private Offer getBlockChainsPaymentMethod(String currencyCode) {
        return getOffer(currencyCode,
                PaymentMethod.BLOCK_CHAINS_ID,
                null,
                null,
                null,
                null);
    }

    private Offer getAliPayPaymentMethod(String currencyCode) {
        return getOffer(currencyCode,
                PaymentMethod.ALI_PAY_ID,
                null,
                null,
                null,
                null);
    }

    private Offer getSEPAPaymentMethod(String currencyCode,
                                       String countryCode,
                                       ArrayList<String> countryCodes,
                                       String bankId) {
        return getPaymentMethod(currencyCode,
                PaymentMethod.SEPA_ID,
                countryCode,
                countryCodes,
                bankId,
                null);
    }

    private Offer getNationalBankPaymentMethod(String currencyCode, String countryCode, String bankId) {
        return getPaymentMethod(currencyCode,
                PaymentMethod.NATIONAL_BANK_ID,
                countryCode,
                new ArrayList<>(FXCollections.singletonObservableList(countryCode)),
                bankId,
                null);
    }

    private Offer getSameBankPaymentMethod(String currencyCode, String countryCode, String bankId) {
        return getPaymentMethod(currencyCode,
                PaymentMethod.SAME_BANK_ID,
                countryCode,
                new ArrayList<>(FXCollections.singletonObservableList(countryCode)),
                bankId,
                new ArrayList<>(FXCollections.singletonObservableList(bankId)));
    }

    private Offer getSpecificBanksPaymentMethod(String currencyCode,
                                                String countryCode,
                                                String bankId,
                                                ArrayList<String> bankIds) {
        return getPaymentMethod(currencyCode,
                PaymentMethod.SPECIFIC_BANKS_ID,
                countryCode,
                new ArrayList<>(FXCollections.singletonObservableList(countryCode)),
                bankId,
                bankIds);
    }

    private Offer getPaymentMethod(String currencyCode,
                                   String paymentMethodId,
                                   String countryCode,
                                   ArrayList<String> countryCodes,
                                   String bankId,
                                   ArrayList<String> bankIds) {
        return getOffer(currencyCode,
                paymentMethodId,
                countryCode,
                countryCodes,
                bankId,
                bankIds);
    }


    private Offer getOffer(String tradeCurrencyCode,
                           String paymentMethodId,
                           String countryCode,
                           ArrayList<String> acceptedCountryCodes,
                           String bankId,
                           ArrayList<String> acceptedBanks) {
        return new Offer(new FeeTxOfferPayload(null,
                0,
                null,
                null,
                null,
                0,
                0,
                false,
                0,
                0,
                "BTC",
                tradeCurrencyCode,
                null,
                null,
                paymentMethodId,
                null,
                null,
                countryCode,
                acceptedCountryCodes,
                bankId,
                acceptedBanks,
                null,
                0,
                0,
                0,
                false,
                0,
                0,
                0,
                0,
                false,
                false,
                0,
                0,
                false,
                null,
                null,
                1));
    }
}
