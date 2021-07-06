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

package bisq.core.dao.node.full;

import bisq.core.dao.node.BsqNode;
import bisq.core.dao.node.explorer.ExportJsonFilesService;
import bisq.core.dao.node.full.network.FullNodeNetworkService;
import bisq.core.dao.node.full.rpc.NotificationHandlerException;
import bisq.core.dao.node.parser.BlockParser;
import bisq.core.dao.node.parser.exceptions.BlockHashNotConnectingException;
import bisq.core.dao.node.parser.exceptions.BlockHeightNotConnectingException;
import bisq.core.dao.node.parser.exceptions.RequiredReorgFromSnapshotException;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.DaoStateSnapshotService;
import bisq.core.dao.state.model.blockchain.Block;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.network.ConnectionState;

import bisq.common.UserThread;
import bisq.common.handlers.ResultHandler;

import javax.inject.Inject;

import java.net.ConnectException;

import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

/**
 * Main class for a full node which have Bitcoin Core with rpc running and does the blockchain lookup itself.
 * It also provides the BSQ transactions to lite nodes on request and broadcasts new BSQ blocks.
 * <p>
 * TODO request p2p network data again after parsing is complete to be sure that in case we missed data during parsing
 * we get it added.
 */
@Slf4j
public class FullNode extends BsqNode {

    private final RpcService rpcService;
    private final FullNodeNetworkService fullNodeNetworkService;
    private boolean addBlockHandlerAdded;
    private int blocksToParseInBatch;
    private long parseInBatchStartTime;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private FullNode(BlockParser blockParser,
                     DaoStateService daoStateService,
                     DaoStateSnapshotService daoStateSnapshotService,
                     P2PService p2PService,
                     RpcService rpcService,
                     ExportJsonFilesService exportJsonFilesService,
                     FullNodeNetworkService fullNodeNetworkService) {
        super(blockParser, daoStateService, daoStateSnapshotService, p2PService, exportJsonFilesService);
        this.rpcService = rpcService;

        this.fullNodeNetworkService = fullNodeNetworkService;
        ConnectionState.setExpectedRequests(5);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void start() {
        fullNodeNetworkService.start();

        rpcService.setup(() -> {
                    super.onInitialized();
                    startParseBlocks();
                },
                this::handleError);
    }

    public void shutDown() {
        super.shutDown();
        fullNodeNetworkService.shutDown();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void startParseBlocks() {
        requestChainHeadHeightAndParseBlocks(getStartBlockHeight());
    }

    @Override
    protected void startReOrgFromLastSnapshot() {
        super.startReOrgFromLastSnapshot();

        int startBlockHeight = getStartBlockHeight();
        rpcService.requestChainHeadHeight(chainHeight -> parseBlocksOnHeadHeight(startBlockHeight, chainHeight),
                this::handleError);
    }

    @Override
    protected void onP2PNetworkReady() {
        super.onP2PNetworkReady();

        if (parseBlockchainComplete) {
            addBlockHandler();
            int blockHeightOfLastBlock = daoStateService.getBlockHeightOfLastBlock();
            log.info("onP2PNetworkReady: We run parseBlocksIfNewBlockAvailable with latest block height {}.", blockHeightOfLastBlock);
            parseBlocksIfNewBlockAvailable(blockHeightOfLastBlock);
        }
    }

    @Override
    protected void onParseBlockChainComplete() {
        super.onParseBlockChainComplete();

        if (p2pNetworkReady)
            addBlockHandler();
        else
            log.info("onParseBlockChainComplete but P2P network is not ready yet.");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addBlockHandler() {
        if (!addBlockHandlerAdded) {
            addBlockHandlerAdded = true;
            rpcService.addNewDtoBlockHandler(rawBlock -> {
                        try {
                            // We need to call that before parsing to have set the chain tip correctly for clients
                            // which might listen for new blocks on daoStateService. DaoStateListener.onNewBlockHeight
                            // is called before the doParseBlock returns.

                            // We only update chainTipHeight if we get a newer block
                            int blockHeight = rawBlock.getHeight();
                            if (blockHeight > chainTipHeight)
                                chainTipHeight = blockHeight;

                            doParseBlock(rawBlock).ifPresent(this::onNewBlock);
                        } catch (RequiredReorgFromSnapshotException ignore) {
                        }
                    },
                    this::handleError);
        }
    }

    private void onNewBlock(Block block) {
        exportJsonFilesService.onNewBlock(block);

        if (p2pNetworkReady && parseBlockchainComplete) {
            fullNodeNetworkService.publishNewBlock(block);
        }
    }

    private void parseBlocksIfNewBlockAvailable(int chainHeight) {
        rpcService.requestChainHeadHeight(newChainHeight -> {
                    if (newChainHeight > chainHeight) {
                        log.info("During parsing new blocks have arrived. We parse again with those missing blocks." +
                                "ChainHeadHeight={}, newChainHeadHeight={}", chainHeight, newChainHeight);
                        parseBlocksOnHeadHeight(chainHeight + 1, newChainHeight);
                    } else {
                        log.info("parseBlocksIfNewBlockAvailable did not result in a new block, so we complete.");
                        log.info("parse {} blocks took {} seconds", blocksToParseInBatch, (System.currentTimeMillis() - parseInBatchStartTime) / 1000d);
                        if (!parseBlockchainComplete) {
                            onParseBlockChainComplete();
                        }
                    }
                },
                this::handleError);
    }

    private void requestChainHeadHeightAndParseBlocks(int startBlockHeight) {
        log.info("requestChainHeadHeightAndParseBlocks with startBlockHeight={}", startBlockHeight);
        rpcService.requestChainHeadHeight(chainHeight -> parseBlocksOnHeadHeight(startBlockHeight, chainHeight),
                this::handleError);
    }

    private void parseBlocksOnHeadHeight(int startBlockHeight, int chainHeight) {
        if (startBlockHeight <= chainHeight) {
            blocksToParseInBatch = chainHeight - startBlockHeight;
            parseInBatchStartTime = System.currentTimeMillis();
            log.info("parse {} blocks with startBlockHeight={} and chainHeight={}", blocksToParseInBatch, startBlockHeight, chainHeight);
            chainTipHeight = chainHeight;
            parseBlocks(startBlockHeight,
                    chainHeight,
                    this::onNewBlock,
                    () -> {
                        // We are done but it might be that new blocks have arrived in the meantime,
                        // so we try again with startBlockHeight set to current chainHeight
                        // We also set up the listener in the else main branch where we check
                        // if we are at chainTip, so do not include here another check as it would
                        // not trigger the listener registration.
                        parseBlocksIfNewBlockAvailable(chainHeight);
                    }, this::handleError);
        } else {
            log.warn("We are trying to start with a block which is above the chain height of Bitcoin Core. " +
                    "We need probably wait longer until Bitcoin Core has fully synced. " +
                    "We try again after a delay of 1 min.");
            UserThread.runAfter(() -> requestChainHeadHeightAndParseBlocks(startBlockHeight), 60);
        }
    }

    private void parseBlocks(int startBlockHeight,
                             int chainHeight,
                             Consumer<Block> newBlockHandler,
                             ResultHandler resultHandler,
                             Consumer<Throwable> errorHandler) {
        parseBlockRecursively(startBlockHeight, chainHeight, newBlockHandler, resultHandler, errorHandler);
    }

    private void parseBlockRecursively(int blockHeight,
                                       int chainHeight,
                                       Consumer<Block> newBlockHandler,
                                       ResultHandler resultHandler,
                                       Consumer<Throwable> errorHandler) {
        rpcService.requestDtoBlock(blockHeight,
                rawBlock -> {
                    try {
                        doParseBlock(rawBlock).ifPresent(newBlockHandler);

                        // Increment blockHeight and recursively call parseBlockAsync until we reach chainHeight
                        if (blockHeight < chainHeight) {
                            int newBlockHeight = blockHeight + 1;
                            parseBlockRecursively(newBlockHeight, chainHeight, newBlockHandler, resultHandler, errorHandler);
                        } else {
                            // We are done
                            resultHandler.handleResult();
                        }
                    } catch (RequiredReorgFromSnapshotException ignore) {
                        // If we get a reorg we don't continue to call parseBlockRecursively
                    }
                },
                errorHandler);
    }

    private void handleError(Throwable throwable) {
        if (throwable instanceof BlockHashNotConnectingException || throwable instanceof BlockHeightNotConnectingException) {
            // We do not escalate that exception as it is handled with the snapshot manager to recover its state.
            log.warn(throwable.toString());
        } else {
            String errorMessage = "An error occurred: Error=" + throwable.toString();
            log.error(errorMessage);
            throwable.printStackTrace();

            if (throwable instanceof RpcException) {
                Throwable cause = throwable.getCause();
                if (cause != null) {
                    if (cause instanceof ConnectException) {
                        if (warnMessageHandler != null)
                            warnMessageHandler.accept("You have configured Bisq to run as DAO full node but there is no " +
                                    "localhost Bitcoin Core node detected. You need to have Bitcoin Core started and synced before " +
                                    "starting Bisq. Please restart Bisq with proper DAO full node setup or switch to lite node mode.");
                        return;
                    } else if (cause instanceof NotificationHandlerException) {
                        log.error("Error from within block notification daemon: {}", cause.getCause().toString());
                        startReOrgFromLastSnapshot();
                        return;
                    } else if (cause instanceof Error) {
                        throw (Error) cause;
                    }
                }
            }

            if (errorMessageHandler != null)
                errorMessageHandler.accept(errorMessage);
        }
    }
}
