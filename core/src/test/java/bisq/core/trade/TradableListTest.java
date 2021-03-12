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

package bisq.core.trade;

import bisq.core.offer.FeeTxOfferPayload;
import bisq.core.offer.Offer;
import bisq.core.offer.OpenOffer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static protobuf.PersistableEnvelope.MessageCase.TRADABLE_LIST;

public class TradableListTest {

    @Test
    public void protoTesting() {
        FeeTxOfferPayload offerPayload = mock(FeeTxOfferPayload.class, RETURNS_DEEP_STUBS);
        TradableList<OpenOffer> openOfferTradableList = new TradableList<>();
        protobuf.PersistableEnvelope message = (protobuf.PersistableEnvelope) openOfferTradableList.toProtoMessage();
        assertEquals(message.getMessageCase(), TRADABLE_LIST);

        // test adding an OpenOffer and convert toProto
        Offer offer = new Offer(offerPayload);
        OpenOffer openOffer = new OpenOffer(offer);
        openOfferTradableList.add(openOffer);
        message = (protobuf.PersistableEnvelope) openOfferTradableList.toProtoMessage();
        assertEquals(message.getMessageCase(), TRADABLE_LIST);
        assertEquals(1, message.getTradableList().getTradableList().size());
    }
}
