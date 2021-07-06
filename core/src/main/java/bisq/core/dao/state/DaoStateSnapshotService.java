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

package bisq.core.dao.state;

import bisq.core.dao.monitoring.DaoStateMonitoringService;
import bisq.core.dao.monitoring.model.DaoStateHash;
import bisq.core.dao.state.model.DaoState;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.storage.DaoStateStorageService;

import bisq.common.config.Config;

import javax.inject.Inject;
import javax.inject.Named;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;

import java.util.LinkedList;
import java.util.Optional;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Manages periodical snapshots of the DaoState.
 * At startup we apply a snapshot if available.
 * At each trigger height we persist the latest snapshot candidate and set the current daoState as new candidate.
 * The trigger height is determined by the SNAPSHOT_GRID. The latest persisted snapshot is min. the height of
 * SNAPSHOT_GRID old not less than 2 times the SNAPSHOT_GRID old.
 */
@Slf4j
public class DaoStateSnapshotService {
    private static final int SNAPSHOT_GRID = 20;

    private final DaoStateService daoStateService;
    private final GenesisTxInfo genesisTxInfo;
    private final DaoStateStorageService daoStateStorageService;
    private final DaoStateMonitoringService daoStateMonitoringService;
    private final File storageDir;

    private DaoState daoStateSnapshotCandidate;
    private LinkedList<DaoStateHash> daoStateHashChainSnapshotCandidate = new LinkedList<>();
    private int chainHeightOfLastApplySnapshot;
    @Setter
    @Nullable
    private Runnable daoRequiresRestartHandler;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DaoStateSnapshotService(DaoStateService daoStateService,
                                   GenesisTxInfo genesisTxInfo,
                                   DaoStateStorageService daoStateStorageService,
                                   DaoStateMonitoringService daoStateMonitoringService,
                                   @Named(Config.STORAGE_DIR) File storageDir) {
        this.daoStateService = daoStateService;
        this.genesisTxInfo = genesisTxInfo;
        this.daoStateStorageService = daoStateStorageService;
        this.daoStateMonitoringService = daoStateMonitoringService;
        this.storageDir = storageDir;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We do not use DaoStateListener.onDaoStateChanged but let the DaoEventCoordinator call maybeCreateSnapshot to ensure the
    // correct order of execution.
    // We need to process during batch processing as well to write snapshots during that process.
    public void maybeCreateSnapshot(Block block) {
        int chainHeight = block.getHeight();

        // Either we don't have a snapshot candidate yet, or if we have one the height at that snapshot candidate must be
        // different to our current height.
        boolean noSnapshotCandidateOrDifferentHeight = daoStateSnapshotCandidate == null ||
                daoStateSnapshotCandidate.getChainHeight() != chainHeight;
        if (isSnapshotHeight(chainHeight) &&
                !daoStateService.getBlocks().isEmpty() &&
                isValidHeight(daoStateService.getBlockHeightOfLastBlock()) &&
                noSnapshotCandidateOrDifferentHeight) {
            // At trigger event we store the latest snapshotCandidate to disc
            long ts = System.currentTimeMillis();
            if (daoStateSnapshotCandidate != null) {
                // Serialisation happens on the userThread so we do not need to clone the data. Write to disk happens
                // in a thread but does not interfere with our objects as they got already serialized when passed to the
                // write thread. We use requestPersistence so we do not write immediately but at next scheduled interval.
                // This avoids frequent write at dao sync and better performance.
                daoStateStorageService.requestPersistence(daoStateSnapshotCandidate, daoStateHashChainSnapshotCandidate);
                log.info("Serializing snapshotCandidate for writing to Disc with height {} at height {} took {} ms",
                        daoStateSnapshotCandidate.getChainHeight(), chainHeight, System.currentTimeMillis() - ts);
            }

            ts = System.currentTimeMillis();
            // Now we clone and keep it in memory for the next trigger event
            daoStateSnapshotCandidate = daoStateService.getClone();
            daoStateHashChainSnapshotCandidate = new LinkedList<>(daoStateMonitoringService.getDaoStateHashChain());

            log.debug("Cloned new snapshotCandidate at height {} took {} ms", chainHeight, System.currentTimeMillis() - ts);
        }
    }

    public Optional<Integer> getChainHeightOfPersistedState() {
        DaoState state = daoStateStorageService.getPersistedBsqState();
        return state != null ? Optional.of(state.getChainHeight()) : Optional.empty();
    }

    public void applySnapshot(boolean fromReorg) {
        DaoState persistedBsqState = daoStateStorageService.getPersistedBsqState();
        LinkedList<DaoStateHash> persistedDaoStateHashChain = daoStateStorageService.getPersistedDaoStateHashChain();
        if (persistedBsqState != null) {
            int chainHeightOfPersisted = persistedBsqState.getChainHeight();
            if (!persistedBsqState.getBlocks().isEmpty()) {
                int heightOfLastBlock = persistedBsqState.getLastBlock().getHeight();
                log.debug("applySnapshot from persistedBsqState daoState with height of last block {}", heightOfLastBlock);
                if (isValidHeight(heightOfLastBlock)) {
                    if (chainHeightOfLastApplySnapshot != chainHeightOfPersisted) {
                        chainHeightOfLastApplySnapshot = chainHeightOfPersisted;
                        daoStateService.applySnapshot(persistedBsqState);
                        daoStateMonitoringService.applySnapshot(persistedDaoStateHashChain);
                    } else {
                        // The reorg might have been caused by the previous parsing which might contains a range of
                        // blocks.
                        log.warn("We applied already a snapshot with chainHeight {}. " +
                                        "We remove all dao store files and shutdown. After a restart resource files will " +
                                        "be applied if available.",
                                chainHeightOfLastApplySnapshot);
                        resyncDaoStateFromResources();
                    }
                }
            } else if (fromReorg) {
                log.info("We got a reorg and we want to apply the snapshot but it is empty. " +
                        "That is expected in the first blocks until the first snapshot has been created. " +
                        "We remove all dao store files and shutdown. " +
                        "After a restart resource files will be applied if available.");
                resyncDaoStateFromResources();
            }
        } else {
            log.info("Try to apply snapshot but no stored snapshot available. That is expected at first blocks.");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean isValidHeight(int heightOfLastBlock) {
        return heightOfLastBlock >= genesisTxInfo.getGenesisBlockHeight();
    }

    private void resyncDaoStateFromResources() {
        log.info("resyncDaoStateFromResources called");
        try {
            daoStateStorageService.resyncDaoStateFromResources(storageDir);

            if (daoRequiresRestartHandler != null) {
                daoRequiresRestartHandler.run();
            }
        } catch (IOException e) {
            log.error("Error at resyncDaoStateFromResources: {}", e.toString());
        }
    }

    @VisibleForTesting
    int getSnapshotHeight(int genesisHeight, int height, int grid) {
        return Math.round(Math.max(genesisHeight + 3 * grid, height) / grid) * grid - grid;
    }

    @VisibleForTesting
    boolean isSnapshotHeight(int genesisHeight, int height, int grid) {
        return height % grid == 0 && height >= getSnapshotHeight(genesisHeight, height, grid);
    }

    private boolean isSnapshotHeight(int height) {
        return isSnapshotHeight(genesisTxInfo.getGenesisBlockHeight(), height, SNAPSHOT_GRID);
    }
}
