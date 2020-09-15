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

package bisq.core.trade.failed;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.DumpDelayedPayoutTx;
import bisq.core.trade.TradableList;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeUtils;

import bisq.common.crypto.KeyRing;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.persistable.PersistedDataHost;

import com.google.inject.Inject;

import javafx.collections.ObservableList;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Setter;

public class FailedTradesManager implements PersistedDataHost {
    private static final Logger log = LoggerFactory.getLogger(FailedTradesManager.class);
    private final TradableList<Trade> failedTrades = new TradableList<>();
    private final KeyRing keyRing;
    private final PriceFeedService priceFeedService;
    private final BtcWalletService btcWalletService;
    private final PersistenceManager<TradableList<Trade>> persistenceManager;
    private final DumpDelayedPayoutTx dumpDelayedPayoutTx;
    @Setter
    private Function<Trade, Boolean> unfailTradeCallback;

    @Inject
    public FailedTradesManager(KeyRing keyRing,
                               PriceFeedService priceFeedService,
                               BtcWalletService btcWalletService,
                               PersistenceManager<TradableList<Trade>> persistenceManager,
                               DumpDelayedPayoutTx dumpDelayedPayoutTx) {
        this.keyRing = keyRing;
        this.priceFeedService = priceFeedService;
        this.btcWalletService = btcWalletService;
        this.dumpDelayedPayoutTx = dumpDelayedPayoutTx;
        this.persistenceManager = persistenceManager;
        this.persistenceManager.initialize(failedTrades);
    }

    @Override
    public void readPersisted() {
        TradableList<Trade> persisted = persistenceManager.getPersisted("FailedTrades");
        if (persisted != null) {
            failedTrades.setAll(persisted.getList());
        }

        failedTrades.forEach(trade -> {
            if (trade.getOffer() != null) {
                trade.getOffer().setPriceFeedService(priceFeedService);
            }
        });

        dumpDelayedPayoutTx.maybeDumpDelayedPayoutTxs(failedTrades, "delayed_payout_txs_failed");
    }

    public void add(Trade trade) {
        if (failedTrades.add(trade)) {
            persistenceManager.requestPersistence();
        }
    }

    public boolean wasMyOffer(Offer offer) {
        return offer.isMyOffer(keyRing);
    }

    public ObservableList<Trade> getFailedTrades() {
        return failedTrades.getList();
    }

    public Optional<Trade> getTradeById(String id) {
        return failedTrades.stream().filter(e -> e.getId().equals(id)).findFirst();
    }

    public Stream<Trade> getTradesStreamWithFundsLockedIn() {
        return failedTrades.stream()
                .filter(Trade::isFundsLockedIn);
    }

    public void unfailTrade(Trade trade) {
        if (unfailTradeCallback == null)
            return;

        if (unfailTradeCallback.apply(trade)) {
            log.info("Unfailing trade {}", trade.getId());
            if (failedTrades.remove(trade)) {
                persistenceManager.requestPersistence();
            }
        }
    }

    public String checkUnfail(Trade trade) {
        var addresses = TradeUtils.getTradeAddresses(trade, btcWalletService, keyRing);
        if (addresses == null) {
            return "Addresses not found";
        }
        StringBuilder blockingTrades = new StringBuilder();
        for (var entry : btcWalletService.getAddressEntryListAsImmutableList()) {
            if (entry.getContext() == AddressEntry.Context.AVAILABLE)
                continue;
            if (entry.getAddressString() != null &&
                    (entry.getAddressString().equals(addresses.first) ||
                            entry.getAddressString().equals(addresses.second))) {
                blockingTrades.append(entry.getOfferId()).append(", ");
            }
        }
        return blockingTrades.toString();
    }
}
