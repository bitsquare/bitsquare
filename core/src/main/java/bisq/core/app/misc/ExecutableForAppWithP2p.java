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

package bisq.core.app.misc;

import bisq.core.app.BisqExecutable;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.OpenOfferManager;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;
import bisq.common.config.Config;
import bisq.common.handlers.ResultHandler;
import bisq.common.setup.GracefulShutDownHandler;
import bisq.common.setup.UncaughtExceptionHandler;
import bisq.common.util.Profiler;
import bisq.common.util.RestartUtil;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import java.io.IOException;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ExecutableForAppWithP2p extends BisqExecutable implements UncaughtExceptionHandler {
    private static final long CHECK_MEMORY_PERIOD_SEC = 300;
    private static final long CHECK_SHUTDOWN_SEC = TimeUnit.HOURS.toSeconds(1);
    private static final long SHUTDOWN_INTERVAL = TimeUnit.HOURS.toMillis(24);
    private volatile boolean stopped;
    private final long startTime = System.currentTimeMillis();

    public ExecutableForAppWithP2p(String fullName, String scriptName, String appName, String version) {
        super(fullName, scriptName, appName, version);
    }

    @Override
    protected void configUserThread() {
        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat(this.getClass().getSimpleName())
                .setDaemon(true)
                .build();
        UserThread.setExecutor(Executors.newSingleThreadExecutor(threadFactory));
    }

    @Override
    public void onSetupComplete() {
        log.info("onSetupComplete");
    }

    // We don't use the gracefulShutDown implementation of the super class as we have a limited set of modules
    @Override
    public void gracefulShutDown(ResultHandler resultHandler) {
        log.debug("gracefulShutDown");
        try {
            if (injector != null) {
                injector.getInstance(ArbitratorManager.class).shutDown();
                injector.getInstance(OpenOfferManager.class).shutDown(() -> injector.getInstance(P2PService.class).shutDown(() -> {
                    injector.getInstance(WalletsSetup.class).shutDownComplete.addListener((ov, o, n) -> {
                        module.close(injector);
                        resultHandler.handleResult();
                        log.info("Graceful shutdown completed");
                        System.exit(0);
                    });
                    injector.getInstance(WalletsSetup.class).shutDown();
                    injector.getInstance(BtcWalletService.class).shutDown();
                    injector.getInstance(BsqWalletService.class).shutDown();
                }));
                // we wait max 5 sec.
                UserThread.runAfter(() -> {
                    resultHandler.handleResult();
                    System.exit(0);
                }, 5);
            } else {
                UserThread.runAfter(() -> {
                    resultHandler.handleResult();
                    System.exit(0);
                }, 1);
            }
        } catch (Throwable t) {
            log.debug("App shutdown failed with exception");
            t.printStackTrace();
            System.exit(1);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UncaughtExceptionHandler implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void handleUncaughtException(Throwable throwable, boolean doShutDown) {
        log.error(throwable.toString());

        if (doShutDown)
            gracefulShutDown(() -> log.info("gracefulShutDown complete"));
    }

    @SuppressWarnings("InfiniteLoopStatement")
    protected void keepRunning() {
        while (true) {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException ignore) {
            }
        }
    }

    protected void startShutDownInterval(GracefulShutDownHandler gracefulShutDownHandler) {
        if (config.seedNodeRestartTime < 0 || config.seedNodeRestartTime > 23) {
            // -1 is default value which means not defined. Valid values are 0-23, anything else is ignored.
            // We restart 24 hours after started. There might be some risk for restart of multiple seed nodes around the
            // same time which can lead to lost data.
            UserThread.runPeriodically(() -> {
                if (System.currentTimeMillis() - startTime > SHUTDOWN_INTERVAL) {
                    log.warn("\n\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n" +
                                    "Shut down as node was running longer as {} hours" +
                                    "\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n\n",
                            SHUTDOWN_INTERVAL / 3600000);

                    shutDown(gracefulShutDownHandler);
                }
            }, CHECK_SHUTDOWN_SEC);
        } else {
            // We interpret the value as hour of day (0-23). If all seeds have updated clocks and a different hour for
            // the restart we avoid the risk of a restart of multiple nodes.

            // We wrap our periodic check in a delay of 2 hours to avoid that we get
            // triggered multiple times after a restart while being in the same hour
            UserThread.runAfter(() -> {
                // We check every hour if we are in the target hour.
                UserThread.runPeriodically(() -> {
                    int currentHour = ZonedDateTime.ofInstant(Instant.now(), ZoneId.of("GMT0")).getHour();
                    if (currentHour == config.seedNodeRestartTime) {
                        shutDown(gracefulShutDownHandler);
                    }
                }, TimeUnit.MINUTES.toSeconds(10));
            }, TimeUnit.HOURS.toSeconds(2));
        }
    }

    protected void checkMemory(Config config, GracefulShutDownHandler gracefulShutDownHandler) {
        int maxMemory = config.maxMemory;
        UserThread.runPeriodically(() -> {
            Profiler.printSystemLoad(log);
            if (!stopped) {
                long usedMemoryInMB = Profiler.getUsedMemoryInMB();
                double warningTrigger = maxMemory * 0.8;
                if (usedMemoryInMB > warningTrigger) {
                    log.warn("\n\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n" +
                                    "We are over 80% of our memory limit ({}) and call the GC. usedMemory: {} MB. freeMemory: {} MB" +
                                    "\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n\n",
                            (int) warningTrigger, usedMemoryInMB, Profiler.getFreeMemoryInMB());
                    System.gc();
                    Profiler.printSystemLoad(log);
                }

                UserThread.runAfter(() -> {
                    log.info("Memory 2 sec. after calling the GC. usedMemory: {} MB. freeMemory: {} MB",
                            Profiler.getUsedMemoryInMB(), Profiler.getFreeMemoryInMB());
                }, 2);

                UserThread.runAfter(() -> {
                    long usedMemory = Profiler.getUsedMemoryInMB();
                    if (usedMemory > maxMemory) {
                        log.warn("\n\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n" +
                                        "We are over our memory limit ({}) and trigger a shutdown. usedMemory: {} MB. freeMemory: {} MB" +
                                        "\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n\n",
                                (int) maxMemory, usedMemory, Profiler.getFreeMemoryInMB());
                        shutDown(gracefulShutDownHandler);
                    }
                }, 5);
            }
        }, CHECK_MEMORY_PERIOD_SEC);
    }

    protected void shutDown(GracefulShutDownHandler gracefulShutDownHandler) {
        stopped = true;
        gracefulShutDownHandler.gracefulShutDown(() -> {
            log.info("Shutdown complete");
            System.exit(1);
        });
    }

    protected void restart(Config config, GracefulShutDownHandler gracefulShutDownHandler) {
        stopped = true;
        gracefulShutDownHandler.gracefulShutDown(() -> {
            //noinspection finally
            try {
                final String[] tokens = config.appDataDir.getPath().split("_");
                String logPath = "error_" + (tokens.length > 1 ? tokens[tokens.length - 2] : "") + ".log";
                RestartUtil.restartApplication(logPath);
            } catch (IOException e) {
                log.error(e.toString());
                e.printStackTrace();
            } finally {
                log.warn("Shutdown complete");
                System.exit(0);
            }
        });
    }
}
