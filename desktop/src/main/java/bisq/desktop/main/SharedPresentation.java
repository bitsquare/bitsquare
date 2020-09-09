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

package bisq.desktop.main;

import bisq.desktop.app.BisqApp;
import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.btc.wallet.WalletsManager;
import bisq.core.locale.Res;

import bisq.common.UserThread;
import bisq.common.storage.FileUtil;

import org.bitcoinj.wallet.DeterministicSeed;

import java.io.File;

import lombok.extern.slf4j.Slf4j;

/**
 * This serves as shared space for static methods used from different views where no common parent view would fit as
 * owner of that code. We keep it strictly static. It should replace GUIUtil for those methods which are not utility
 * methods.
 */
@Slf4j
public class SharedPresentation {
    public static void restoreSeedWords(DeterministicSeed seed, WalletsManager walletsManager, File storageDir) {
        try {
            FileUtil.renameFile(new File(storageDir, "AddressEntryList"), new File(storageDir, "AddressEntryList_wallet_restore_" + System.currentTimeMillis()));
        } catch (Throwable t) {
            new Popup().error(Res.get("error.deleteAddressEntryListFailed", t)).show();
        }
        walletsManager.restoreSeedWords(
                seed,
                () -> UserThread.execute(() -> {
                    log.info("Wallets restored with seed words");
                    new Popup().feedback(Res.get("seed.restore.success")).hideCloseButton().show();
                    BisqApp.getShutDownHandler().run();
                }),
                throwable -> UserThread.execute(() -> {
                    log.error(throwable.toString());
                    new Popup().error(Res.get("seed.restore.error", Res.get("shared.errorMessageInline", throwable)))
                            .show();
                }));
    }
}