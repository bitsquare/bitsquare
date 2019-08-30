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

package bisq.core.dispute.arbitration;

import bisq.core.app.AppOptionKeys;
import bisq.core.dispute.DisputeResolverManager;
import bisq.core.filter.FilterManager;
import bisq.core.user.User;

import bisq.network.p2p.storage.payload.ProtectedStorageEntry;

import bisq.common.crypto.KeyRing;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ArbitratorManager extends DisputeResolverManager<Arbitrator> {

    @Inject
    public ArbitratorManager(KeyRing keyRing,
                             ArbitratorService disputeResolverService,
                             User user,
                             FilterManager filterManager,
                             @Named(AppOptionKeys.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys) {
        super(keyRing, disputeResolverService, user, filterManager, useDevPrivilegeKeys);
    }

    @Override
    protected boolean isExpectedInstance(ProtectedStorageEntry data) {
        return data.getProtectedStoragePayload() instanceof Arbitrator;
    }

    @Override
    protected void addAcceptedDisputeResolverToUser(Arbitrator disputeResolver) {
        user.addAcceptedArbitrator(disputeResolver);
    }

    @Override
    protected void removeAcceptedDisputeResolverFromUser(ProtectedStorageEntry data) {
        user.removeAcceptedArbitrator((Arbitrator) data.getProtectedStoragePayload());
    }

    @Override
    protected List<Arbitrator> getAcceptedDisputeResolversFromUser() {
        return user.getAcceptedArbitrators();
    }

    @Override
    protected void clearAcceptedDisputeResolversAtUser() {
        user.clearAcceptedArbitrators();
    }

    @Override
    protected Arbitrator getRegisteredDisputeResolverFromUser() {
        return user.getRegisteredArbitrator();
    }

    @Override
    protected void setRegisteredDisputeResolverAtUser(Arbitrator disputeResolver) {
        user.setRegisteredArbitrator(disputeResolver);
    }
}
