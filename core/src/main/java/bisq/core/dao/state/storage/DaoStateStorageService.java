/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.state.storage;

import bisq.core.dao.monitoring.DaoStateMonitoringService;
import bisq.core.dao.monitoring.model.DaoStateHash;
import bisq.core.dao.state.model.DaoState;

import bisq.network.p2p.storage.persistence.ResourceDataStoreService;
import bisq.network.p2p.storage.persistence.StoreService;

import bisq.common.app.DevEnv;
import bisq.common.config.Config;
import bisq.common.file.FileUtil;
import bisq.common.persistence.PersistenceManager;

import javax.inject.Inject;
import javax.inject.Named;

import java.io.File;
import java.io.IOException;

import java.util.LinkedList;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages persistence of the daoState.
 */
@Slf4j
public class DaoStateStorageService extends StoreService<DaoStateStore> {
    private static final String FILE_NAME = "DaoStateStore";

    private final DaoState daoState;
    private final DaoStateMonitoringService daoStateMonitoringService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DaoStateStorageService(ResourceDataStoreService resourceDataStoreService,
                                  DaoState daoState,
                                  DaoStateMonitoringService daoStateMonitoringService,
                                  @Named(Config.STORAGE_DIR) File storageDir,
                                  PersistenceManager<DaoStateStore> persistenceManager) {
        super(storageDir, persistenceManager);
        this.daoState = daoState;
        this.daoStateMonitoringService = daoStateMonitoringService;

        if (DevEnv.isDaoActivated()) {
            resourceDataStoreService.addService(this);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String getFileName() {
        return FILE_NAME;
    }

    public void requestPersistence(DaoState daoState, LinkedList<DaoStateHash> daoStateHashChain) {
        store.setDaoState(daoState);
        store.setDaoStateHashChain(daoStateHashChain);
        persistenceManager.requestPersistence();
    }

    public DaoState getPersistedBsqState() {
        return store.getDaoState();
    }

    public LinkedList<DaoStateHash> getPersistedDaoStateHashChain() {
        return store.getDaoStateHashChain();
    }

    public void resyncDaoStateFromGenesis(Runnable resultHandler) {
        store.setDaoState(new DaoState());
        store.setDaoStateHashChain(new LinkedList<>());
        persistenceManager.persistNow(resultHandler);
    }

    public void resyncDaoStateFromResources(File storageDir) throws IOException {
        // We delete all DAO consensus payload data and remove the daoState so it will rebuild from latest
        // resource files.
        long currentTime = System.currentTimeMillis();
        String backupDirName = "out_of_sync_dao_data";
        String newFileName = "BlindVoteStore_" + currentTime;
        FileUtil.removeAndBackupFile(storageDir, new File(storageDir, "BlindVoteStore"), newFileName, backupDirName);

        newFileName = "ProposalStore_" + currentTime;
        FileUtil.removeAndBackupFile(storageDir, new File(storageDir, "ProposalStore"), newFileName, backupDirName);

        // We also need to remove ballot list as it contains the proposals as well. It will be recreated at resync
        newFileName = "BallotList_" + currentTime;
        FileUtil.removeAndBackupFile(storageDir, new File(storageDir, "BallotList"), newFileName, backupDirName);

        newFileName = "UnconfirmedBsqChangeOutputList_" + currentTime;
        FileUtil.removeAndBackupFile(storageDir, new File(storageDir, "UnconfirmedBsqChangeOutputList"), newFileName, backupDirName);

        newFileName = "DaoStateStore_" + currentTime;
        FileUtil.removeAndBackupFile(storageDir, new File(storageDir, "DaoStateStore"), newFileName, backupDirName);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected DaoStateStore createStore() {
        return new DaoStateStore(DaoState.getClone(daoState), new LinkedList<>(daoStateMonitoringService.getDaoStateHashChain()));
    }

    @Override
    protected void initializePersistenceManager() {
        persistenceManager.initialize(store, PersistenceManager.Source.NETWORK);
    }
}
