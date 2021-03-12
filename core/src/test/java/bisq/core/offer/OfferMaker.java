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

import com.natpryce.makeiteasy.Instantiator;
import com.natpryce.makeiteasy.Maker;
import com.natpryce.makeiteasy.Property;

import static com.natpryce.makeiteasy.MakeItEasy.a;

public class OfferMaker {

    public static final Property<Offer, Long> price = new Property<>();
    public static final Property<Offer, Long> minAmount = new Property<>();
    public static final Property<Offer, Long> amount = new Property<>();
    public static final Property<Offer, String> baseCurrencyCode = new Property<>();
    public static final Property<Offer, String> counterCurrencyCode = new Property<>();
    public static final Property<Offer, OfferPayload.Direction> direction = new Property<>();
    public static final Property<Offer, Boolean> useMarketBasedPrice = new Property<>();
    public static final Property<Offer, Double> marketPriceMargin = new Property<>();
    public static final Property<Offer, String> id = new Property<>();

    public static final Instantiator<Offer> Offer = lookup -> new Offer(
            new FeeTxOfferPayload(lookup.valueOf(id, "1234"),
                    0L,
                    null,
                    null,
                    lookup.valueOf(direction, OfferPayload.Direction.BUY),
                    lookup.valueOf(price, 100000L),
                    lookup.valueOf(marketPriceMargin, 0.0),
                    lookup.valueOf(useMarketBasedPrice, false),
                    lookup.valueOf(amount, 100000L),
                    lookup.valueOf(minAmount, 100000L),
                    lookup.valueOf(baseCurrencyCode, "BTC"),
                    lookup.valueOf(counterCurrencyCode, "USD"),
                    null,
                    null,
                    "SEPA",
                    "",
                    null,
                    null,
                    null,
                    null,
                    null,
                    "",
                    0L,
                    0L,
                    0L,
                    false,
                    0L,
                    0L,
                    0L,
                    0L,
                    false,
                    false,
                    0L,
                    0L,
                    false,
                    null,
                    null,
                    0));

    public static final Maker<Offer> btcUsdOffer = a(Offer);
}
