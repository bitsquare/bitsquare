package bisq.desktop.util;

import bisq.core.locale.Res;
import bisq.core.monetary.Volume;
import bisq.core.offer.FeeTxOfferPayload;
import bisq.core.offer.Offer;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.coin.ImmutableCoinFormatter;

import bisq.common.config.Config;

import org.bitcoinj.core.Coin;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import static bisq.desktop.maker.OfferMaker.btcUsdOffer;
import static bisq.desktop.maker.VolumeMaker.usdVolume;
import static bisq.desktop.maker.VolumeMaker.volumeString;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DisplayUtilsTest {
    private final CoinFormatter formatter = new ImmutableCoinFormatter(Config.baseCurrencyNetworkParameters().getMonetaryFormat());

    @Before
    public void setUp() {
        Locale.setDefault(new Locale("en", "US"));
        Res.setBaseCurrencyCode("BTC");
        Res.setBaseCurrencyName("Bitcoin");
    }

    @Test
    public void testFormatAccountAge() {
        assertEquals("0 days", DisplayUtils.formatAccountAge(TimeUnit.HOURS.toMillis(23)));
        assertEquals("0 days", DisplayUtils.formatAccountAge(0));
        assertEquals("0 days", DisplayUtils.formatAccountAge(-1));
        assertEquals("1 day", DisplayUtils.formatAccountAge(TimeUnit.DAYS.toMillis(1)));
        assertEquals("2 days", DisplayUtils.formatAccountAge(TimeUnit.DAYS.toMillis(2)));
        assertEquals("30 days", DisplayUtils.formatAccountAge(TimeUnit.DAYS.toMillis(30)));
        assertEquals("60 days", DisplayUtils.formatAccountAge(TimeUnit.DAYS.toMillis(60)));
    }

    @Test
    public void testFormatVolume() {
        assertEquals("1", DisplayUtils.formatVolume(make(btcUsdOffer), true, 4));
        assertEquals("100", DisplayUtils.formatVolume(make(usdVolume)));
        assertEquals("1775", DisplayUtils.formatVolume(make(usdVolume.but(with(volumeString, "1774.62")))));
    }

    @Test
    public void testFormatSameVolume() {
        Offer offer = mock(Offer.class);
        Volume btc = Volume.parse("0.10", "BTC");
        when(offer.getMinVolume()).thenReturn(btc);
        when(offer.getVolume()).thenReturn(btc);

        assertEquals("0.10000000", DisplayUtils.formatVolume(offer.getVolume()));
    }

    @Test
    public void testFormatDifferentVolume() {
        Offer offer = mock(Offer.class);
        Volume btcMin = Volume.parse("0.10", "BTC");
        Volume btcMax = Volume.parse("0.25", "BTC");
        when(offer.isRange()).thenReturn(true);
        when(offer.getMinVolume()).thenReturn(btcMin);
        when(offer.getVolume()).thenReturn(btcMax);

        assertEquals("0.10000000 - 0.25000000", DisplayUtils.formatVolume(offer, false, 0));
    }

    @Test
    public void testFormatNullVolume() {
        Offer offer = mock(Offer.class);
        when(offer.getMinVolume()).thenReturn(null);
        when(offer.getVolume()).thenReturn(null);

        assertEquals("", DisplayUtils.formatVolume(offer.getVolume()));
    }

    @Test
    public void testFormatSameAmount() {
        Offer offer = mock(Offer.class);
        when(offer.getMinAmount()).thenReturn(Coin.valueOf(10000000));
        when(offer.getAmount()).thenReturn(Coin.valueOf(10000000));

        assertEquals("0.10", DisplayUtils.formatAmount(offer, formatter));
    }

    @Test
    public void testFormatDifferentAmount() {
        FeeTxOfferPayload offerPayload = mock(FeeTxOfferPayload.class);
        Offer offer = new Offer(offerPayload);
        when(offerPayload.getMinAmount()).thenReturn(10000000L);
        when(offerPayload.getAmount()).thenReturn(20000000L);

        assertEquals("0.10 - 0.20", DisplayUtils.formatAmount(offer, formatter));
    }

    @Test
    public void testFormatAmountWithAlignmenWithDecimals() {
        FeeTxOfferPayload offerPayload = mock(FeeTxOfferPayload.class);
        Offer offer = new Offer(offerPayload);
        when(offerPayload.getMinAmount()).thenReturn(10000000L);
        when(offerPayload.getAmount()).thenReturn(20000000L);

        assertEquals("0.1000 - 0.2000", DisplayUtils.formatAmount(offer, 4, true, 15, formatter));
    }

    @Test
    public void testFormatAmountWithAlignmenWithDecimalsNoRange() {
        FeeTxOfferPayload offerPayload = mock(FeeTxOfferPayload.class);
        Offer offer = new Offer(offerPayload);
        when(offerPayload.getMinAmount()).thenReturn(10000000L);
        when(offerPayload.getAmount()).thenReturn(10000000L);

        assertEquals("0.1000", DisplayUtils.formatAmount(offer, 4, true, 15, formatter));
    }

    @Test
    public void testFormatNullAmount() {
        Offer offer = mock(Offer.class);
        when(offer.getMinAmount()).thenReturn(null);
        when(offer.getAmount()).thenReturn(null);

        assertEquals("", DisplayUtils.formatAmount(offer, formatter));
    }
}
