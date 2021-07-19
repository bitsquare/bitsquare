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

import bisq.core.filter.FilterManager;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferBookService;
import bisq.core.offer.OfferRestrictions;
import bisq.core.trade.TradeManager;

import bisq.network.utils.Utils;

import javax.inject.Inject;
import javax.inject.Singleton;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.offer.OfferPayload.Direction.BUY;

/**
 * Holds and manages the unsorted and unfiltered offerbook list (except for banned offers) of both buy and sell offers.
 * It is handled as singleton by Guice and is used by 2 instances of OfferBookDataModel (one for Buy one for Sell).
 * As it is used only by the Buy and Sell UIs we treat it as local UI model.
 * It also use OfferRepository.Listener as the lists items class and we don't want to get any dependency out of the
 * package for that.
 */
@Singleton
@Slf4j
public class OfferBook {
    private final OfferBookService offerBookService;
    private final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
    private final Map<String, Integer> buyOfferCountMap = new HashMap<>();
    private final Map<String, Integer> sellOfferCountMap = new HashMap<>();
    private final FilterManager filterManager;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    OfferBook(OfferBookService offerBookService, TradeManager tradeManager, FilterManager filterManager) {
        this.offerBookService = offerBookService;
        this.filterManager = filterManager;

        offerBookService.addOfferBookChangedListener(new OfferBookService.OfferBookChangedListener() {
            @Override
            public void onAdded(Offer offer) {
                // We get onAdded called every time a new ProtectedStorageEntry is received.
                // Mostly it is the same OfferPayload but the ProtectedStorageEntry is different.
                // We filter here to only add new offers if the same offer (using equals) was not already added and it
                // is not banned.

                if (filterManager.isOfferIdBanned(offer.getId())) {
                    log.debug("Ignored banned offer. ID={}", offer.getId());
                    return;
                }

                if (OfferRestrictions.requiresNodeAddressUpdate() && !Utils.isV3Address(offer.getMakerNodeAddress().getHostName())) {
                    log.debug("Ignored offer with Tor v2 node address. ID={}", offer.getId());
                    return;
                }

                boolean hasSameOffer = offerBookListItems.stream()
                        .anyMatch(item -> item.getOffer().equals(offer));
                if (!hasSameOffer) {
                    OfferBookListItem offerBookListItem = new OfferBookListItem(offer);
                    // We don't use the contains method as the equals method in Offer takes state and errorMessage into account.
                    // If we have an offer with same ID we remove it and add the new offer as it might have a changed state.
                    Optional<OfferBookListItem> candidateWithSameId = offerBookListItems.stream()
                            .filter(item -> item.getOffer().getId().equals(offer.getId()))
                            .findAny();
                    if (candidateWithSameId.isPresent()) {
                        log.warn("We had an old offer in the list with the same Offer ID. We remove the old one. " +
                                "old offerBookListItem={}, new offerBookListItem={}", candidateWithSameId.get(), offerBookListItem);
                        offerBookListItems.remove(candidateWithSameId.get());
                    }

                    offerBookListItems.add(offerBookListItem);
                } else {
                    log.debug("We have the exact same offer already in our list and ignore the onAdded call. ID={}", offer.getId());
                }
            }

            @Override
            public void onRemoved(Offer offer) {
                removeOffer(offer, tradeManager);
            }
        });
    }

    public void removeOffer(Offer offer, TradeManager tradeManager) {
        // Update state in case that that offer is used in the take offer screen, so it gets updated correctly
        offer.setState(Offer.State.REMOVED);

        offer.cancelAvailabilityRequest();
        // We don't use the contains method as the equals method in Offer takes state and errorMessage into account.
        Optional<OfferBookListItem> candidateToRemove = offerBookListItems.stream()
                .filter(item -> item.getOffer().getId().equals(offer.getId()))
                .findAny();
        candidateToRemove.ifPresent((item) -> {
            offerBookListItems.remove(item);
            // When the API is used to edit an offer, the daemon's P2PDataStorage will
            // send onRemoved(offer) and onAdded(offer) events 1-3 ms apart, and those
            // events may arrive at a peer's UI in the wrong order (onAdded, onRemoved),
            // resulting in an edited offer disappearing from the UI's OfferBook.
            // Reloading the offerBookListItems works around the problem.  Reloading
            // 100 offers usually takes less then 1 ms.
            fillOfferBookListItems();
        });
    }

    public ObservableList<OfferBookListItem> getOfferBookListItems() {
        return offerBookListItems;
    }

    public void fillOfferBookListItems() {
        try {
            // setAll causes sometimes an UnsupportedOperationException
            // Investigate why....
            offerBookListItems.clear();
            offerBookListItems.addAll(offerBookService.getOffers().stream()
                    .filter(o -> !filterManager.isOfferIdBanned(o.getId()))
                    .filter(o -> !OfferRestrictions.requiresNodeAddressUpdate() || Utils.isV3Address(o.getMakerNodeAddress().getHostName()))
                    .map(OfferBookListItem::new)
                    .collect(Collectors.toList()));

            log.debug("offerBookListItems.size {}", offerBookListItems.size());
            fillOfferCountMaps();
        } catch (Throwable t) {
            t.printStackTrace();
            log.error("Error at fillOfferBookListItems: " + t.toString());
        }
    }

    public Map<String, Integer> getBuyOfferCountMap() {
        return buyOfferCountMap;
    }

    public Map<String, Integer> getSellOfferCountMap() {
        return sellOfferCountMap;
    }

    private void fillOfferCountMaps() {
        buyOfferCountMap.clear();
        sellOfferCountMap.clear();
        final String[] ccyCode = new String[1];
        final int[] offerCount = new int[1];
        offerBookListItems.forEach(o -> {
            ccyCode[0] = o.getOffer().getCurrencyCode();
            if (o.getOffer().getDirection() == BUY) {
                offerCount[0] = buyOfferCountMap.getOrDefault(ccyCode[0], 0) + 1;
                buyOfferCountMap.put(ccyCode[0], offerCount[0]);
            } else {
                offerCount[0] = sellOfferCountMap.getOrDefault(ccyCode[0], 0) + 1;
                sellOfferCountMap.put(ccyCode[0], offerCount[0]);
            }
        });
        log.debug("buyOfferCountMap.size {}   sellOfferCountMap.size {}",
                buyOfferCountMap.size(), sellOfferCountMap.size());
    }
}
