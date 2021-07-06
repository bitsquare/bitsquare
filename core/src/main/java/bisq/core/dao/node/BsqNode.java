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

package bisq.core.dao.node;

import bisq.core.dao.DaoSetupService;
import bisq.core.dao.node.explorer.ExportJsonFilesService;
import bisq.core.dao.node.full.RawBlock;
import bisq.core.dao.node.parser.BlockParser;
import bisq.core.dao.node.parser.exceptions.BlockHashNotConnectingException;
import bisq.core.dao.node.parser.exceptions.BlockHeightNotConnectingException;
import bisq.core.dao.node.parser.exceptions.RequiredReorgFromSnapshotException;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.DaoStateSnapshotService;
import bisq.core.dao.state.model.blockchain.Block;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.P2PServiceListener;

import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Base class for the lite and full node.
 * It is responsible or the setup of the parser and snapshot management.
 */
@Slf4j
public abstract class BsqNode implements DaoSetupService {
    private final BlockParser blockParser;
    private final P2PService p2PService;
    protected final DaoStateService daoStateService;
    private final String genesisTxId;
    private final int genesisBlockHeight;
    protected final ExportJsonFilesService exportJsonFilesService;
    private final DaoStateSnapshotService daoStateSnapshotService;
    private final P2PServiceListener p2PServiceListener;
    protected boolean parseBlockchainComplete;
    protected boolean p2pNetworkReady;
    @Nullable
    protected Consumer<String> errorMessageHandler;
    @Nullable
    protected Consumer<String> warnMessageHandler;
    private final List<RawBlock> pendingBlocks = new ArrayList<>();

    // The chain height of the latest Block we either get reported by Bitcoin Core or from the seed node
    // This property should not be used in consensus code but only for retrieving blocks as it is not in sync with the
    // parsing and the daoState. It also does not represent the latest blockHeight but the currently received
    // (not parsed) block.
    @Getter
    protected int chainTipHeight;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BsqNode(BlockParser blockParser,
                   DaoStateService daoStateService,
                   DaoStateSnapshotService daoStateSnapshotService,
                   P2PService p2PService,
                   ExportJsonFilesService exportJsonFilesService) {
        this.blockParser = blockParser;
        this.daoStateService = daoStateService;
        this.daoStateSnapshotService = daoStateSnapshotService;
        this.p2PService = p2PService;
        this.exportJsonFilesService = exportJsonFilesService;

        genesisTxId = daoStateService.getGenesisTxId();
        genesisBlockHeight = daoStateService.getGenesisBlockHeight();

        p2PServiceListener = new P2PServiceListener() {
            @Override
            public void onTorNodeReady() {
            }

            @Override
            public void onHiddenServicePublished() {
            }

            @Override
            public void onSetupFailed(Throwable throwable) {
            }

            @Override
            public void onRequestCustomBridges() {
            }

            @Override
            public void onDataReceived() {
            }

            @Override
            public void onNoSeedNodeAvailable() {
                onP2PNetworkReady();
            }

            @Override
            public void onNoPeersAvailable() {
            }

            @Override
            public void onUpdatedDataReceived() {
                onP2PNetworkReady();
            }
        };
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
    }

    @Override
    public abstract void start();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setErrorMessageHandler(@SuppressWarnings("NullableProblems") Consumer<String> errorMessageHandler) {
        this.errorMessageHandler = errorMessageHandler;
    }

    public void setWarnMessageHandler(@SuppressWarnings("NullableProblems") Consumer<String> warnMessageHandler) {
        this.warnMessageHandler = warnMessageHandler;
    }

    public void shutDown() {
        exportJsonFilesService.shutDown();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    protected void onInitialized() {
        daoStateSnapshotService.applySnapshot(false);

        if (p2PService.isBootstrapped()) {
            log.info("onAllServicesInitialized: isBootstrapped");
            onP2PNetworkReady();
        } else {
            p2PService.addP2PServiceListener(p2PServiceListener);
        }
    }

    @SuppressWarnings("WeakerAccess")
    protected void onP2PNetworkReady() {
        p2pNetworkReady = true;
        p2PService.removeP2PServiceListener(p2PServiceListener);
    }

    @SuppressWarnings("WeakerAccess")
    protected int getStartBlockHeight() {
        int chainHeight = daoStateService.getChainHeight();
        int startBlockHeight = chainHeight;
        if (chainHeight > genesisBlockHeight)
            startBlockHeight = chainHeight + 1;

        log.info("getStartBlockHeight:\n" +
                        "   Start block height={}\n" +
                        "   Genesis txId={}\n" +
                        "   Genesis block height={}\n" +
                        "   Block height={}\n",
                startBlockHeight,
                genesisTxId,
                genesisBlockHeight,
                chainHeight);

        return startBlockHeight;
    }

    protected abstract void startParseBlocks();

    protected void onParseBlockChainComplete() {
        log.info("onParseBlockChainComplete");
        parseBlockchainComplete = true;
        daoStateService.onParseBlockChainComplete();

        exportJsonFilesService.onParseBlockChainComplete();
    }

    @SuppressWarnings("WeakerAccess")
    protected void startReOrgFromLastSnapshot() {
        exportJsonFilesService.onReOrg(daoStateSnapshotService.getChainHeightOfPersistedState());
        daoStateSnapshotService.applySnapshot(true);
    }

    protected Optional<Block> doParseBlock(RawBlock rawBlock) throws RequiredReorgFromSnapshotException {
        // We check if we have a block with that height. If so we return. We do not use the chainHeight as with genesis
        // height we have no block but chainHeight is initially set to genesis height (bad design ;-( but a bit tricky
        // to change now as it used in many areas.)
        int blockHeight = rawBlock.getHeight();
        if (daoStateService.getBlockAtHeight(blockHeight).isPresent()) {
            log.info("We have already a block with the height of the new block. Height of new block={}", blockHeight);
            return Optional.empty();
        }

        try {
            Block block = blockParser.parseBlock(rawBlock);

            pendingBlocks.remove(rawBlock);

            // After parsing we check if we have pending blocks we might have received earlier but which have been
            // not connecting from the latest height we had. The list is sorted by height
            if (!pendingBlocks.isEmpty()) {
                // We take only first element after sorting (so it is the block with the next height) to avoid that
                // we would repeat calls in recursions in case we would iterate the list.
                pendingBlocks.sort(Comparator.comparing(RawBlock::getHeight));
                RawBlock nextPending = pendingBlocks.get(0);
                if (nextPending.getHeight() == daoStateService.getChainHeight() + 1)
                    doParseBlock(nextPending);
            }

            return Optional.of(block);
        } catch (BlockHeightNotConnectingException e) {
            // There is no guaranteed order how we receive blocks. We could have received block 102 before 101.
            // If block is in the future we move the block to the pendingBlocks list. At next block we look up the
            // list if there is any potential candidate with the correct height and if so we remove that from that list.

            int heightForNextBlock = daoStateService.getChainHeight() + 1;
            if (blockHeight > heightForNextBlock) {
                if (!pendingBlocks.contains(rawBlock)) {
                    pendingBlocks.add(rawBlock);
                    log.info("We received a block with a future block height. We store it as pending and try to apply " +
                            "it at the next block. rawBlock: height/hash={}/{}", blockHeight, rawBlock.getHash());
                } else {
                    log.warn("We received a block with a future block height but we had it already added to our pendingBlocks.");
                }
            } else if (blockHeight >= daoStateService.getGenesisBlockHeight()) {
                // We received an older block. We compare if we have it in our chain.
                Optional<Block> optionalBlock = daoStateService.getBlockAtHeight(blockHeight);
                if (optionalBlock.isPresent()) {
                    if (optionalBlock.get().getHash().equals(rawBlock.getPreviousBlockHash())) {
                        log.info("We received an old block we have already parsed and added. We ignore it.");
                    } else {
                        log.info("We received an old block with a different hash. We ignore it. Hash={}", rawBlock.getHash());
                    }
                } else {
                    log.info("In case we have reset from genesis height we would not find the block");
                }
            } else {
                log.info("We ignore it as it was before genesis height");
            }
        } catch (BlockHashNotConnectingException throwable) {
            Optional<Block> lastBlock = daoStateService.getLastBlock();
            log.warn("Block not connecting:\n" +
                            "New block height/hash/previousBlockHash={}/{}/{}, latest block height/hash={}/{}",
                    blockHeight,
                    rawBlock.getHash(),
                    rawBlock.getPreviousBlockHash(),
                    lastBlock.isPresent() ? lastBlock.get().getHeight() : "lastBlock not present",
                    lastBlock.isPresent() ? lastBlock.get().getHash() : "lastBlock not present");

            pendingBlocks.clear();
            startReOrgFromLastSnapshot();
            throw new RequiredReorgFromSnapshotException(rawBlock);
        }
        return Optional.empty();
    }
}
