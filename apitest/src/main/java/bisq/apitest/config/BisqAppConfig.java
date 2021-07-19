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

package bisq.apitest.config;

import bisq.seednode.SeedNodeMain;

import bisq.desktop.app.BisqAppMain;



import bisq.daemon.app.BisqDaemonMain;

/**
 Some non user configurable Bisq seednode, arb node, bob and alice daemon option values.
 @see <a href="https://github.com/bisq-network/bisq/blob/master/docs/dev-setup.md">dev-setup.md</a>
 @see <a href="https://github.com/bisq-network/bisq/blob/master/docs/dao-setup.md">dao-setup.md</a>
 */
public enum BisqAppConfig {

    seednode("bisq-BTC_REGTEST_Seed_2002",
            "bisq-seednode",
            "-XX:MaxRAM=2g -Dlogback.configurationFile=apitest/build/resources/main/logback.xml",
            SeedNodeMain.class.getName(),
            2002,
            5120,
            -1,
            49996),
    arbdaemon("bisq-BTC_REGTEST_Arb_dao",
            "bisq-daemon",
            "-XX:MaxRAM=2g -Dlogback.configurationFile=apitest/build/resources/main/logback.xml",
            BisqDaemonMain.class.getName(),
            4444,
            5121,
            9997,
            49997),
    arbdesktop("bisq-BTC_REGTEST_Arb_dao",
            "bisq-desktop",
            "-XX:MaxRAM=3g -Dlogback.configurationFile=apitest/build/resources/main/logback.xml",
            BisqAppMain.class.getName(),
            4444,
            5121,
            -1,
            49997),
    alicedaemon("bisq-BTC_REGTEST_Alice_dao",
            "bisq-daemon",
            "-XX:MaxRAM=2g -Dlogback.configurationFile=apitest/build/resources/main/logback.xml",
            BisqDaemonMain.class.getName(),
            7777,
            5122,
            9998,
            49998),
    alicedesktop("bisq-BTC_REGTEST_Alice_dao",
            "bisq-desktop",
            "-XX:MaxRAM=6g -Dlogback.configurationFile=apitest/build/resources/main/logback.xml",
            BisqAppMain.class.getName(),
            7777,
            5122,
            -1,
            49998),
    bobdaemon("bisq-BTC_REGTEST_Bob_dao",
            "bisq-daemon",
            "-XX:MaxRAM=2g -Dlogback.configurationFile=apitest/build/resources/main/logback.xml",
            BisqDaemonMain.class.getName(),
            8888,
            5123,
            9999,
            49999),
    bobdesktop("bisq-BTC_REGTEST_Bob_dao",
            "bisq-desktop",
            "-XX:MaxRAM=6g -Dlogback.configurationFile=apitest/build/resources/main/logback.xml",
            BisqAppMain.class.getName(),
            8888,
            5123,
            -1,
            49999);

    public final String appName;
    public final String startupScript;
    public final String javaOpts;
    public final String mainClassName;
    public final int nodePort;
    public final int rpcBlockNotificationPort;
    // Daemons can use a global gRPC password, but each needs a unique apiPort.
    public final int apiPort;
    public final int remoteDebugPort;

    BisqAppConfig(String appName,
                  String startupScript,
                  String javaOpts,
                  String mainClassName,
                  int nodePort,
                  int rpcBlockNotificationPort,
                  int apiPort,
                  int remoteDebugPort) {
        this.appName = appName;
        this.startupScript = startupScript;
        this.javaOpts = javaOpts;
        this.mainClassName = mainClassName;
        this.nodePort = nodePort;
        this.rpcBlockNotificationPort = rpcBlockNotificationPort;
        this.apiPort = apiPort;
        this.remoteDebugPort = remoteDebugPort;
    }

    @Override
    public String toString() {
        return "BisqAppConfig{" + "\n" +
                "  appName='" + appName + '\'' + "\n" +
                ", startupScript='" + startupScript + '\'' + "\n" +
                ", javaOpts='" + javaOpts + '\'' + "\n" +
                ", mainClassName='" + mainClassName + '\'' + "\n" +
                ", nodePort=" + nodePort + "\n" +
                ", rpcBlockNotificationPort=" + rpcBlockNotificationPort + "\n" +
                ", apiPort=" + apiPort + "\n" +
                ", remoteDebugPort=" + remoteDebugPort + "\n" +
                '}';
    }
}
