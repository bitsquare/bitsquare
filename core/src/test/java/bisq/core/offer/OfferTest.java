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

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OfferTest {

    @Test
    public void testHasNoRange() {
        FeeTxOfferPayload payload = mock(FeeTxOfferPayload.class);
        when(payload.getMinAmount()).thenReturn(1000L);
        when(payload.getAmount()).thenReturn(1000L);

        Offer offer = new Offer(payload);
        assertFalse(offer.isRange());
    }

    @Test
    public void testHasRange() {
        FeeTxOfferPayload payload = mock(FeeTxOfferPayload.class);
        when(payload.getMinAmount()).thenReturn(1000L);
        when(payload.getAmount()).thenReturn(2000L);

        Offer offer = new Offer(payload);
        assertTrue(offer.isRange());
    }
}
