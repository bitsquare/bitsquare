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

package bisq.apitest.method.offer;

import protobuf.PaymentAccount;

import java.math.BigDecimal;

import java.util.function.BiFunction;
import java.util.function.Function;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import static bisq.apitest.Scaffold.BitcoinCoreApp.bitcoind;
import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.apitest.config.BisqAppConfig.alicedaemon;
import static bisq.apitest.config.BisqAppConfig.arbdaemon;
import static bisq.apitest.config.BisqAppConfig.bobdaemon;
import static bisq.apitest.config.BisqAppConfig.seednode;
import static bisq.common.util.MathUtils.exactMultiply;



import bisq.apitest.method.MethodTest;

@Slf4j
public abstract class AbstractOfferTest extends MethodTest {

    protected static final int ACTIVATE_OFFER = 1;
    protected static final int DEACTIVATE_OFFER = 0;
    protected static final long NO_TRIGGER_PRICE = 0;

    @Setter
    protected static boolean isLongRunningTest;

    protected static PaymentAccount alicesBsqAcct;
    protected static PaymentAccount bobsBsqAcct;

    @BeforeAll
    public static void setUp() {
        startSupportingApps(true,
                true,
                bitcoind,
                seednode,
                arbdaemon,
                alicedaemon,
                bobdaemon);
    }


    // Mkt Price Margin value of offer returned from server is scaled down by 10^-2.
    protected final Function<Double, Double> scaledDownMktPriceMargin = (mktPriceMargin) ->
            exactMultiply(mktPriceMargin, 0.01);

    // Price value of offer returned from server is scaled up by 10^4.
    protected final Function<BigDecimal, Long> scaledUpFiatPrice = (price) -> {
        BigDecimal factor = new BigDecimal(10).pow(4);
        return price.multiply(factor).longValue();
    };

    protected final BiFunction<Double, Double, Long> calcTriggerPriceAsLong = (base, delta) -> {
        var triggerPriceAsDouble = new BigDecimal(base).add(new BigDecimal(delta)).doubleValue();
        return Double.valueOf(exactMultiply(triggerPriceAsDouble, 10_000)).longValue();
    };

    protected final BiFunction<Double, Double, String> calcFixedPriceAsString = (base, delta) -> {
        var fixedPriceAsBigDecimal = new BigDecimal(Double.toString(base))
                .add(new BigDecimal(Double.toString(delta)));
        return fixedPriceAsBigDecimal.toPlainString();
    };

    public static void createBsqPaymentAccounts() {
        alicesBsqAcct = aliceClient.createCryptoCurrencyPaymentAccount("Alice's BSQ Account",
                BSQ,
                aliceClient.getUnusedBsqAddress(),
                false);
        bobsBsqAcct = bobClient.createCryptoCurrencyPaymentAccount("Bob's BSQ Account",
                BSQ,
                bobClient.getUnusedBsqAddress(),
                false);
    }

    @AfterAll
    public static void tearDown() {
        tearDownScaffold();
    }
}
