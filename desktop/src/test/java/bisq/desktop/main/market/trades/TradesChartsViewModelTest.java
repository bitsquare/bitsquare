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

package bisq.desktop.main.market.trades;

import bisq.desktop.Navigation;
import bisq.desktop.main.market.trades.charts.CandleData;

import bisq.core.locale.FiatCurrency;
import bisq.core.monetary.Price;
import bisq.core.offer.FeeTxOfferPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.TradeStatistics3;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class TradesChartsViewModelTest {
    TradesChartsViewModel model;
    TradeStatisticsManager tradeStatisticsManager;

    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    private File dir;
    FeeTxOfferPayload offer = new FeeTxOfferPayload(null,
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
            "EUR",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
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
            1
    );

    @Before
    public void setup() throws IOException {
        tradeStatisticsManager = mock(TradeStatisticsManager.class);
        model = new TradesChartsViewModel(tradeStatisticsManager, mock(Preferences.class), mock(PriceFeedService.class),
                mock(Navigation.class));
        dir = File.createTempFile("temp_tests1", "");
        //noinspection ResultOfMethodCallIgnored
        dir.delete();
        //noinspection ResultOfMethodCallIgnored
        dir.mkdir();
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testGetCandleData() {
        model.selectedTradeCurrencyProperty.setValue(new FiatCurrency("EUR"));

        long low = Fiat.parseFiat("EUR", "500").value;
        long open = Fiat.parseFiat("EUR", "520").value;
        long close = Fiat.parseFiat("EUR", "580").value;
        long high = Fiat.parseFiat("EUR", "600").value;
        long average = Fiat.parseFiat("EUR", "550").value;
        long median = Fiat.parseFiat("EUR", "550").value;
        long amount = Coin.parseCoin("4").value;
        long volume = Fiat.parseFiat("EUR", "2200").value;
        boolean isBullish = true;

        Set<TradeStatistics3> set = new HashSet<>();
        final Date now = new Date();

        set.add(new TradeStatistics3(offer.getCurrencyCode(),
                Price.parse("EUR", "520").getValue(),
                Coin.parseCoin("1").getValue(),
                PaymentMethod.BLOCK_CHAINS_ID,
                now.getTime(),
                null,
                null,
                null,
                null));
        set.add(new TradeStatistics3(offer.getCurrencyCode(),
                Price.parse("EUR", "500").getValue(),
                Coin.parseCoin("1").getValue(),
                PaymentMethod.BLOCK_CHAINS_ID,
                now.getTime() + 100,
                null,
                null,
                null,
                null));
        set.add(new TradeStatistics3(offer.getCurrencyCode(),
                Price.parse("EUR", "600").getValue(),
                Coin.parseCoin("1").getValue(),
                PaymentMethod.BLOCK_CHAINS_ID,
                now.getTime() + 200,
                null,
                null,
                null,
                null));
        set.add(new TradeStatistics3(offer.getCurrencyCode(),
                Price.parse("EUR", "580").getValue(),
                Coin.parseCoin("1").getValue(),
                PaymentMethod.BLOCK_CHAINS_ID,
                now.getTime() + 300,
                null,
                null,
                null,
                null));

        CandleData candleData = model.getCandleData(model.roundToTick(now, TradesChartsViewModel.TickUnit.DAY).getTime(), set, 0);
        assertEquals(open, candleData.open);
        assertEquals(close, candleData.close);
        assertEquals(high, candleData.high);
        assertEquals(low, candleData.low);
        assertEquals(average, candleData.average);
        assertEquals(median, candleData.median);
        assertEquals(amount, candleData.accumulatedAmount);
        assertEquals(volume, candleData.accumulatedVolume);
        assertEquals(isBullish, candleData.isBullish);
    }

    // TODO JMOCKIT
    @Ignore
    @Test
    public void testItemLists() throws ParseException {
        // Helper class to add historic trades
        class Trade {
            Trade(String date, String size, String price, String cc) {
                try {
                    this.date = dateFormat.parse(date);
                } catch (ParseException p) {
                    this.date = new Date();
                }
                this.size = size;
                this.price = price;
                this.cc = cc;
            }

            Date date;
            String size;
            String price;
            String cc;
        }

        // Trade EUR
        model.selectedTradeCurrencyProperty.setValue(new FiatCurrency("EUR"));

        ArrayList<Trade> trades = new ArrayList<>();

        // Set predetermined time to use as "now" during test
/*        new MockUp<System>() {
            @Mock
            long currentTimeMillis() {
                return test_time.getTime();
            }
        };*/

        // Two trades 10 seconds apart, different YEAR, MONTH, WEEK, DAY, HOUR, MINUTE_10
        trades.add(new Trade("2017-12-31T23:59:52", "1", "100", "EUR"));
        trades.add(new Trade("2018-01-01T00:00:02", "1", "110", "EUR"));
        Set<TradeStatistics3> set = new HashSet<>();
        trades.forEach(t ->
                set.add(new TradeStatistics3(offer.getCurrencyCode(),
                        Price.parse(t.cc, t.price).getValue(),
                        Coin.parseCoin(t.size).getValue(),
                        PaymentMethod.BLOCK_CHAINS_ID,
                        t.date.getTime(),
                        null,
                        null,
                        null,
                        null))
        );
        ObservableSet<TradeStatistics3> tradeStats = FXCollections.observableSet(set);

        // Run test for each tick type
        for (TradesChartsViewModel.TickUnit tick : TradesChartsViewModel.TickUnit.values()) {
/*            new Expectations() {{
                tradeStatisticsManager.getObservableTradeStatisticsSet();
                result = tradeStats;
            }};*/

            // Trigger chart update
            model.setTickUnit(tick);
            assertEquals(model.selectedTradeCurrencyProperty.get().getCode(), tradeStats.iterator().next().getCurrency());
            assertEquals(2, model.priceItems.size());
            assertEquals(2, model.volumeItems.size());
        }
    }
}
