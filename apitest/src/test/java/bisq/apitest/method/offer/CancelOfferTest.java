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

import bisq.core.payment.PaymentAccount;

import bisq.proto.grpc.OfferInfo;

import java.util.List;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static bisq.apitest.config.ApiTestConfig.BSQ;
import static bisq.core.btc.wallet.Restrictions.getDefaultBuyerSecurityDepositAsPercent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static protobuf.OfferPayload.Direction.BUY;

@Disabled
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CancelOfferTest extends AbstractOfferTest {

    private static final String DIRECTION = BUY.name();
    private static final String CURRENCY_CODE = "cad";
    private static final int MAX_OFFERS = 3;

    private final Consumer<String> createOfferToCancel = (paymentAccountId) -> {
        aliceClient.createMarketBasedPricedOffer(DIRECTION,
                CURRENCY_CODE,
                10000000L,
                10000000L,
                0.00,
                getDefaultBuyerSecurityDepositAsPercent(),
                paymentAccountId,
                BSQ,
                NO_TRIGGER_PRICE);
    };

    @Test
    @Order(1)
    public void testCancelOffer() {
        PaymentAccount cadAccount = createDummyF2FAccount(aliceClient, "CA");

        // Create some offers.
        for (int i = 1; i <= MAX_OFFERS; i++) {
            createOfferToCancel.accept(cadAccount.getId());
            // Wait for Alice's AddToOfferBook task.
            // Wait times vary;  my logs show >= 2 second delay.
            sleep(2500);
        }

        List<OfferInfo> offers = aliceClient.getMyOffersSortedByDate(DIRECTION, CURRENCY_CODE);
        assertEquals(MAX_OFFERS, offers.size());

        // Cancel the offers, checking the open offer count after each offer removal.
        for (int i = 1; i <= MAX_OFFERS; i++) {
            aliceClient.cancelOffer(offers.remove(0).getId());
            offers = aliceClient.getMyOffersSortedByDate(DIRECTION, CURRENCY_CODE);
            assertEquals(MAX_OFFERS - i, offers.size());
        }

        sleep(1000);  // wait for offer removal

        offers = aliceClient.getMyOffersSortedByDate(DIRECTION, CURRENCY_CODE);
        assertEquals(0, offers.size());
    }
}
