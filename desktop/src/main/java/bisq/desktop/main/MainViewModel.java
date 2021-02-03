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
import bisq.desktop.common.model.ViewModel;
import bisq.desktop.components.BalanceWithConfirmationTextField;
import bisq.desktop.components.TxIdTextField;
import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.main.overlays.notifications.NotificationCenter;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.DisplayAlertMessageWindow;
import bisq.desktop.main.overlays.windows.TacWindow;
import bisq.desktop.main.overlays.windows.TorNetworkSettingsWindow;
import bisq.desktop.main.overlays.windows.UpdateRevolutAccountWindow;
import bisq.desktop.main.overlays.windows.WalletPasswordWindow;
import bisq.desktop.main.overlays.windows.downloadupdate.DisplayUpdateDownloadWindow;
import bisq.desktop.main.presentation.AccountPresentation;
import bisq.desktop.main.presentation.DaoPresentation;
import bisq.desktop.main.presentation.MarketPricePresentation;
import bisq.desktop.main.presentation.SettingsPresentation;
import bisq.desktop.main.shared.PriceFeedComboBoxItem;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.GUIUtil;

import bisq.core.account.sign.SignedWitnessService;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.alert.PrivateNotificationManager;
import bisq.core.app.BisqSetup;
import bisq.core.btc.nodes.LocalBitcoinNode;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.locale.CryptoCurrency;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.AliPayAccount;
import bisq.core.payment.CryptoCurrencyAccount;
import bisq.core.payment.RevolutAccount;
import bisq.core.payment.payload.AssetsAccountPayload;
import bisq.core.presentation.BalancePresentation;
import bisq.core.presentation.SupportTicketsPresentation;
import bisq.core.presentation.TradePresentation;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.TradeManager;
import bisq.core.user.DontShowAgainLookup;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import bisq.network.p2p.BootstrapListener;
import bisq.network.p2p.P2PService;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.app.Version;
import bisq.common.config.Config;
import bisq.common.file.CorruptedStorageFileHandler;
import bisq.common.util.Tuple2;

import com.google.inject.Inject;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.monadic.MonadicBinding;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MainViewModel implements ViewModel, BisqSetup.BisqSetupListener {
    private final BisqSetup bisqSetup;
    private final WalletsSetup walletsSetup;
    private final User user;
    private final BalancePresentation balancePresentation;
    private final TradePresentation tradePresentation;
    private final SupportTicketsPresentation supportTicketsPresentation;
    private final MarketPricePresentation marketPricePresentation;
    private final DaoPresentation daoPresentation;
    private final AccountPresentation accountPresentation;
    private final SettingsPresentation settingsPresentation;
    private final P2PService p2PService;
    private final TradeManager tradeManager;
    private final OpenOfferManager openOfferManager;
    @Getter
    private final Preferences preferences;
    private final PrivateNotificationManager privateNotificationManager;
    private final WalletPasswordWindow walletPasswordWindow;
    private final NotificationCenter notificationCenter;
    private final TacWindow tacWindow;
    @Getter
    private final PriceFeedService priceFeedService;
    private final Config config;
    private final LocalBitcoinNode localBitcoinNode;
    private final AccountAgeWitnessService accountAgeWitnessService;
    @Getter
    private final TorNetworkSettingsWindow torNetworkSettingsWindow;
    private final CorruptedStorageFileHandler corruptedStorageFileHandler;

    @Getter
    private final BooleanProperty showAppScreen = new SimpleBooleanProperty();
    private final DoubleProperty combinedSyncProgress = new SimpleDoubleProperty(-1);
    private final BooleanProperty isSplashScreenRemoved = new SimpleBooleanProperty();
    private final StringProperty footerVersionInfo = new SimpleStringProperty();
    private Timer checkNumberOfBtcPeersTimer;
    private Timer checkNumberOfP2pNetworkPeersTimer;
    @SuppressWarnings("FieldCanBeLocal")
    private MonadicBinding<Boolean> tradesAndUIReady;
    private final Queue<Overlay<?>> popupQueue = new PriorityQueue<>(Comparator.comparing(Overlay::getDisplayOrderPriority));


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MainViewModel(BisqSetup bisqSetup,
                         WalletsSetup walletsSetup,
                         BtcWalletService btcWalletService,
                         User user,
                         BalancePresentation balancePresentation,
                         TradePresentation tradePresentation,
                         SupportTicketsPresentation supportTicketsPresentation,
                         MarketPricePresentation marketPricePresentation,
                         DaoPresentation daoPresentation,
                         AccountPresentation accountPresentation,
                         SettingsPresentation settingsPresentation,
                         P2PService p2PService,
                         TradeManager tradeManager,
                         OpenOfferManager openOfferManager,
                         Preferences preferences,
                         PrivateNotificationManager privateNotificationManager,
                         WalletPasswordWindow walletPasswordWindow,
                         NotificationCenter notificationCenter,
                         TacWindow tacWindow,
                         FeeService feeService,
                         PriceFeedService priceFeedService,
                         Config config,
                         LocalBitcoinNode localBitcoinNode,
                         AccountAgeWitnessService accountAgeWitnessService,
                         TorNetworkSettingsWindow torNetworkSettingsWindow,
                         CorruptedStorageFileHandler corruptedStorageFileHandler) {
        this.bisqSetup = bisqSetup;
        this.walletsSetup = walletsSetup;
        this.user = user;
        this.balancePresentation = balancePresentation;
        this.tradePresentation = tradePresentation;
        this.supportTicketsPresentation = supportTicketsPresentation;
        this.marketPricePresentation = marketPricePresentation;
        this.daoPresentation = daoPresentation;
        this.accountPresentation = accountPresentation;
        this.settingsPresentation = settingsPresentation;
        this.p2PService = p2PService;
        this.tradeManager = tradeManager;
        this.openOfferManager = openOfferManager;
        this.preferences = preferences;
        this.privateNotificationManager = privateNotificationManager;
        this.walletPasswordWindow = walletPasswordWindow;
        this.notificationCenter = notificationCenter;
        this.tacWindow = tacWindow;
        this.priceFeedService = priceFeedService;
        this.config = config;
        this.localBitcoinNode = localBitcoinNode;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.torNetworkSettingsWindow = torNetworkSettingsWindow;
        this.corruptedStorageFileHandler = corruptedStorageFileHandler;

        TxIdTextField.setPreferences(preferences);

        TxIdTextField.setWalletService(btcWalletService);
        BalanceWithConfirmationTextField.setWalletService(btcWalletService);

        GUIUtil.setFeeService(feeService);
        GUIUtil.setPreferences(preferences);

        setupHandlers();
        bisqSetup.addBisqSetupListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BisqSetupListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onSetupComplete() {
        // We handle the trade period here as we display a global popup if we reached dispute time
        tradesAndUIReady = EasyBind.combine(isSplashScreenRemoved, tradeManager.persistedTradesInitializedProperty(),
                (a, b) -> a && b);
        tradesAndUIReady.subscribe((observable, oldValue, newValue) -> {
            if (newValue) {
                tradeManager.applyTradePeriodState();

                tradeManager.getObservableList().forEach(trade -> {
                    Date maxTradePeriodDate = trade.getMaxTradePeriodDate();
                    String key;
                    switch (trade.getTradePeriodState()) {
                        case FIRST_HALF:
                            break;
                        case SECOND_HALF:
                            key = "displayHalfTradePeriodOver" + trade.getId();
                            if (DontShowAgainLookup.showAgain(key)) {
                                DontShowAgainLookup.dontShowAgain(key, true);
                                new Popup().warning(Res.get("popup.warning.tradePeriod.halfReached",
                                        trade.getShortId(),
                                        DisplayUtils.formatDateTime(maxTradePeriodDate)))
                                        .show();
                            }
                            break;
                        case TRADE_PERIOD_OVER:
                            key = "displayTradePeriodOver" + trade.getId();
                            if (DontShowAgainLookup.showAgain(key)) {
                                DontShowAgainLookup.dontShowAgain(key, true);
                                new Popup().warning(Res.get("popup.warning.tradePeriod.ended",
                                        trade.getShortId(),
                                        DisplayUtils.formatDateTime(maxTradePeriodDate)))
                                        .show();
                            }
                            break;
                    }
                });
            }
        });

        setupP2PNumPeersWatcher();
        setupBtcNumPeersWatcher();

        marketPricePresentation.setup();
        daoPresentation.setup();
        accountPresentation.setup();
        settingsPresentation.setup();

        if (DevEnv.isDevMode()) {
            preferences.setShowOwnOffersInOfferBook(true);
            setupDevDummyPaymentAccounts();
        }

        getShowAppScreen().set(true);

        // We only show the popup if the user has already set up any fiat account. For new users it is not a rule
        // change and for altcoins its not relevant.
        String key = "reasonForPaymentChange";
        boolean hasFiatAccount = user.getPaymentAccounts() != null &&
                user.getPaymentAccounts().stream()
                        .filter(e -> !(e.getPaymentAccountPayload() instanceof AssetsAccountPayload))
                        .findAny()
                        .isPresent();
        if (hasFiatAccount && DontShowAgainLookup.showAgain(key)) {
            UserThread.runAfter(() -> {
                new Popup().attention(Res.get("popup.attention.reasonForPaymentRuleChange")).
                        dontShowAgainId(key)
                        .closeButtonText(Res.get("shared.iUnderstand"))
                        .show();
            }, 1);
        } else {
            // If user add a fiat account later we don't show the popup as we assume it is a new user.
            DontShowAgainLookup.dontShowAgain(key, true);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    // After showAppScreen is set and splash screen is faded out
    void onSplashScreenRemoved() {
        isSplashScreenRemoved.set(true);

        // Delay that as we want to know what is the current path of the navigation which is set
        // in MainView showAppScreen handler
        notificationCenter.onAllServicesAndViewsInitialized();

        maybeShowPopupsFromQueue();
    }

    void onOpenDownloadWindow() {
        bisqSetup.displayAlertIfPresent(user.getDisplayedAlert(), true);
    }

    void setPriceFeedComboBoxItem(PriceFeedComboBoxItem item) {
        marketPricePresentation.setPriceFeedComboBoxItem(item);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupHandlers() {
        bisqSetup.setDisplayTacHandler(acceptedHandler -> UserThread.runAfter(() -> {
            //noinspection FunctionalExpressionCanBeFolded
            tacWindow.onAction(acceptedHandler::run).show();
        }, 1));

        bisqSetup.setDisplayTorNetworkSettingsHandler(show -> {
            if (show) {
                torNetworkSettingsWindow.show();
            } else if (torNetworkSettingsWindow.isDisplayed()) {
                torNetworkSettingsWindow.hide();
            }
        });
        bisqSetup.setSpvFileCorruptedHandler(msg -> new Popup().warning(msg)
                .actionButtonText(Res.get("settings.net.reSyncSPVChainButton"))
                .onAction(() -> GUIUtil.reSyncSPVChain(preferences))
                .show());
        bisqSetup.setVoteResultExceptionHandler(voteResultException -> log.warn(voteResultException.toString()));

        bisqSetup.setChainFileLockedExceptionHandler(msg -> new Popup().warning(msg)
                .useShutDownButton()
                .show());
        bisqSetup.setLockedUpFundsHandler(msg -> new Popup().width(850).warning(msg).show());
        bisqSetup.setShowFirstPopupIfResyncSPVRequestedHandler(this::showFirstPopupIfResyncSPVRequested);
        bisqSetup.setRequestWalletPasswordHandler(aesKeyHandler -> walletPasswordWindow
                .onAesKey(aesKeyHandler::accept)
                .onClose(() -> BisqApp.getShutDownHandler().run())
                .show());

        bisqSetup.setDisplayUpdateHandler((alert, key) -> new DisplayUpdateDownloadWindow(alert, config)
                .actionButtonText(Res.get("displayUpdateDownloadWindow.button.downloadLater"))
                .onAction(() -> {
                    preferences.dontShowAgain(key, false); // update later
                })
                .closeButtonText(Res.get("shared.cancel"))
                .onClose(() -> {
                    preferences.dontShowAgain(key, true); // ignore update
                })
                .show());
        bisqSetup.setDisplayAlertHandler(alert -> new DisplayAlertMessageWindow()
                .alertMessage(alert)
                .closeButtonText(Res.get("shared.close"))
                .onClose(() -> user.setDisplayedAlert(alert))
                .show());
        bisqSetup.setDisplayPrivateNotificationHandler(privateNotification ->
                new Popup().headLine(Res.get("popup.privateNotification.headline"))
                        .attention(privateNotification.getMessage())
                        .onClose(privateNotificationManager::removePrivateNotification)
                        .useIUnderstandButton()
                        .show());
        bisqSetup.setDaoErrorMessageHandler(errorMessage -> new Popup().error(errorMessage).show());
        bisqSetup.setDaoWarnMessageHandler(warnMessage -> new Popup().warning(warnMessage).show());
        bisqSetup.setDisplaySecurityRecommendationHandler(key ->
                new Popup().headLine(Res.get("popup.securityRecommendation.headline"))
                        .information(Res.get("popup.securityRecommendation.msg"))
                        .dontShowAgainId(key)
                        .show());
        bisqSetup.setDisplayLocalhostHandler(key -> {
            if (!DevEnv.isDevMode()) {
                Popup popup = new Popup().backgroundInfo(Res.get("popup.bitcoinLocalhostNode.msg"))
                        .dontShowAgainId(key);
                popup.setDisplayOrderPriority(5);
                popupQueue.add(popup);
            }
        });
        bisqSetup.setDisplaySignedByArbitratorHandler(key -> accountPresentation.showOneTimeAccountSigningPopup(
                key, "popup.accountSigning.signedByArbitrator"));
        bisqSetup.setDisplaySignedByPeerHandler(key -> accountPresentation.showOneTimeAccountSigningPopup(
                key, "popup.accountSigning.signedByPeer", String.valueOf(SignedWitnessService.SIGNER_AGE_DAYS)));
        bisqSetup.setDisplayPeerLimitLiftedHandler(key -> accountPresentation.showOneTimeAccountSigningPopup(
                key, "popup.accountSigning.peerLimitLifted"));
        bisqSetup.setDisplayPeerSignerHandler(key -> accountPresentation.showOneTimeAccountSigningPopup(
                key, "popup.accountSigning.peerSigner"));

        bisqSetup.setWrongOSArchitectureHandler(msg -> new Popup().warning(msg).show());

        bisqSetup.setRejectedTxErrorMessageHandler(msg -> new Popup().width(850).warning(msg).show());

        bisqSetup.setShowPopupIfInvalidBtcConfigHandler(this::showPopupIfInvalidBtcConfig);

        bisqSetup.setRevolutAccountsUpdateHandler(revolutAccountList -> {
            // We copy the array as we will mutate it later
            showRevolutAccountUpdateWindow(new ArrayList<>(revolutAccountList));
        });
        bisqSetup.setOsxKeyLoggerWarningHandler(() -> {
            String key = "osxKeyLoggerWarning";
            if (preferences.showAgain(key)) {
                new Popup().warning(Res.get("popup.warning.osxKeyLoggerWarning"))
                        .closeButtonText(Res.get("shared.iUnderstand"))
                        .dontShowAgainId(key)
                        .show();
            }
        });
        bisqSetup.setQubesOSInfoHandler(() -> {
            String key = "qubesOSSetupInfo";
            if (preferences.showAgain(key)) {
                new Popup().information(Res.get("popup.info.qubesOSSetupInfo"))
                        .closeButtonText(Res.get("shared.iUnderstand"))
                        .dontShowAgainId(key)
                        .show();
            }
        });

        bisqSetup.setDownGradePreventionHandler(lastVersion -> {
            new Popup().warning(Res.get("popup.warn.downGradePrevention", lastVersion, Version.VERSION))
                    .useShutDownButton()
                    .hideCloseButton()
                    .show();
        });

        bisqSetup.setDaoRequiresRestartHandler(() -> new Popup().warning("popup.warn.daoRequiresRestart")
                .useShutDownButton()
                .hideCloseButton()
                .show());

        corruptedStorageFileHandler.getFiles().ifPresent(files -> new Popup()
                .warning(Res.get("popup.warning.incompatibleDB", files.toString(), config.appDataDir))
                .useShutDownButton()
                .show());

        tradeManager.setTakeOfferRequestErrorMessageHandler(errorMessage -> new Popup()
                .warning(Res.get("popup.error.takeOfferRequestFailed", errorMessage))
                .show());

        bisqSetup.getBtcSyncProgress().addListener((observable, oldValue, newValue) -> updateBtcSyncProgress());
        daoPresentation.getBsqSyncProgress().addListener((observable, oldValue, newValue) -> updateBtcSyncProgress());

        bisqSetup.setFilterWarningHandler(warning -> new Popup().warning(warning).show());

        this.footerVersionInfo.setValue("v" + Version.VERSION);
        this.getNewVersionAvailableProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                this.footerVersionInfo.setValue("v" + Version.VERSION + " " + Res.get("mainView.version.update"));
            } else {
                this.footerVersionInfo.setValue("v" + Version.VERSION);
            }
        });

        if (p2PService.isBootstrapped()) {
            setupInvalidOpenOffersHandler();
        } else {
            p2PService.addP2PServiceListener(new BootstrapListener() {
                @Override
                public void onUpdatedDataReceived() {
                    setupInvalidOpenOffersHandler();
                }
            });
        }

        walletsSetup.setDisplayUserBtcNodeMisconfigurationHandler(
                (String peer) ->
                    new Popup()
                        .hideCloseButton()
                        .warning(Res.get("popup.warning.userBtcNodeMisconfigured.explanation", peer))
                        .useShutDownButton()
                        .show()
                );
    }

    private void showRevolutAccountUpdateWindow(List<RevolutAccount> revolutAccountList) {
        if (!revolutAccountList.isEmpty()) {
            RevolutAccount revolutAccount = revolutAccountList.get(0);
            revolutAccountList.remove(0);
            new UpdateRevolutAccountWindow(revolutAccount, user).onClose(() -> {
                // We delay a bit in case we have multiple account for better UX
                UserThread.runAfter(() -> showRevolutAccountUpdateWindow(revolutAccountList), 300, TimeUnit.MILLISECONDS);
            }).show();
        }
    }

    private void setupP2PNumPeersWatcher() {
        p2PService.getNumConnectedPeers().addListener((observable, oldValue, newValue) -> {
            int numPeers = (int) newValue;
            if ((int) oldValue > 0 && numPeers == 0) {
                // give a bit of tolerance
                if (checkNumberOfP2pNetworkPeersTimer != null)
                    checkNumberOfP2pNetworkPeersTimer.stop();

                checkNumberOfP2pNetworkPeersTimer = UserThread.runAfter(() -> {
                    // check again numPeers
                    if (p2PService.getNumConnectedPeers().get() == 0) {
                        getP2pNetworkWarnMsg().set(Res.get("mainView.networkWarning.allConnectionsLost", Res.get("shared.P2P")));
                        getP2pNetworkLabelId().set("splash-error-state-msg");
                    } else {
                        getP2pNetworkWarnMsg().set(null);
                        getP2pNetworkLabelId().set("footer-pane");
                    }
                }, 5);
            } else if ((int) oldValue == 0 && numPeers > 0) {
                if (checkNumberOfP2pNetworkPeersTimer != null)
                    checkNumberOfP2pNetworkPeersTimer.stop();

                getP2pNetworkWarnMsg().set(null);
                getP2pNetworkLabelId().set("footer-pane");
            }
        });
    }

    private void setupBtcNumPeersWatcher() {
        walletsSetup.numPeersProperty().addListener((observable, oldValue, newValue) -> {
            int numPeers = (int) newValue;
            if ((int) oldValue > 0 && numPeers == 0) {
                if (checkNumberOfBtcPeersTimer != null)
                    checkNumberOfBtcPeersTimer.stop();

                checkNumberOfBtcPeersTimer = UserThread.runAfter(() -> {
                    // check again numPeers
                    if (walletsSetup.numPeersProperty().get() == 0) {
                        if (localBitcoinNode.shouldBeUsed())
                            getWalletServiceErrorMsg().set(
                                    Res.get("mainView.networkWarning.localhostBitcoinLost",
                                            Res.getBaseCurrencyName().toLowerCase()));
                        else
                            getWalletServiceErrorMsg().set(
                                    Res.get("mainView.networkWarning.allConnectionsLost",
                                            Res.getBaseCurrencyName().toLowerCase()));
                    } else {
                        getWalletServiceErrorMsg().set(null);
                    }
                }, 5);
            } else if ((int) oldValue == 0 && numPeers > 0) {
                if (checkNumberOfBtcPeersTimer != null)
                    checkNumberOfBtcPeersTimer.stop();
                getWalletServiceErrorMsg().set(null);
            }
        });
    }

    private void showFirstPopupIfResyncSPVRequested() {
        Popup firstPopup = new Popup();
        firstPopup.information(Res.get("settings.net.reSyncSPVAfterRestart")).show();
        if (bisqSetup.getBtcSyncProgress().get() == 1) {
            showSecondPopupIfResyncSPVRequested(firstPopup);
        } else {
            bisqSetup.getBtcSyncProgress().addListener((observable, oldValue, newValue) -> {
                if ((double) newValue == 1)
                    showSecondPopupIfResyncSPVRequested(firstPopup);
            });
        }
    }

    private void showSecondPopupIfResyncSPVRequested(Popup firstPopup) {
        firstPopup.hide();
        preferences.setResyncSpvRequested(false);
        new Popup().information(Res.get("settings.net.reSyncSPVAfterRestartCompleted"))
                .hideCloseButton()
                .useShutDownButton()
                .show();
    }

    private void showPopupIfInvalidBtcConfig() {
        preferences.setBitcoinNodesOptionOrdinal(0);
        new Popup().warning(Res.get("settings.net.warn.invalidBtcConfig"))
                .hideCloseButton()
                .useShutDownButton()
                .show();
    }

    private void setupDevDummyPaymentAccounts() {
        if (user.getPaymentAccounts() != null && user.getPaymentAccounts().isEmpty()) {
            AliPayAccount aliPayAccount = new AliPayAccount();
            aliPayAccount.init();
            aliPayAccount.setAccountNr("dummy_" + new Random().nextInt(100));
            aliPayAccount.setAccountName("AliPayAccount dummy");// Don't translate only for dev
            user.addPaymentAccount(aliPayAccount);

            if (p2PService.isBootstrapped()) {
                accountAgeWitnessService.publishMyAccountAgeWitness(aliPayAccount.getPaymentAccountPayload());
            } else {
                p2PService.addP2PServiceListener(new BootstrapListener() {
                    @Override
                    public void onUpdatedDataReceived() {
                        accountAgeWitnessService.publishMyAccountAgeWitness(aliPayAccount.getPaymentAccountPayload());
                    }
                });
            }

            CryptoCurrencyAccount cryptoCurrencyAccount = new CryptoCurrencyAccount();
            cryptoCurrencyAccount.init();
            cryptoCurrencyAccount.setAccountName("ETH dummy");// Don't translate only for dev
            cryptoCurrencyAccount.setAddress("0x" + new Random().nextInt(1000000));
            Optional<CryptoCurrency> eth = CurrencyUtil.getCryptoCurrency("ETH");
            eth.ifPresent(cryptoCurrencyAccount::setSingleTradeCurrency);

            user.addPaymentAccount(cryptoCurrencyAccount);
        }
    }

    private void updateBtcSyncProgress() {
        final DoubleProperty btcSyncProgress = bisqSetup.getBtcSyncProgress();

        if (btcSyncProgress.doubleValue() < 1) {
            combinedSyncProgress.set(btcSyncProgress.doubleValue());
        } else {
            combinedSyncProgress.set(daoPresentation.getBsqSyncProgress().doubleValue());
        }
    }

    private void setupInvalidOpenOffersHandler() {
        openOfferManager.getInvalidOffers().addListener((ListChangeListener<Tuple2<OpenOffer, String>>) c -> {
            c.next();
            if (c.wasAdded()) {
                handleInvalidOpenOffers(c.getAddedSubList());
            }
        });
        handleInvalidOpenOffers(openOfferManager.getInvalidOffers());
    }

    private void handleInvalidOpenOffers(List<? extends Tuple2<OpenOffer, String>> list) {
        list.forEach(tuple2 -> {
            String errorMsg = tuple2.second;
            OpenOffer openOffer = tuple2.first;
            new Popup().warning(errorMsg)
                    .width(1000)
                    .actionButtonText(Res.get("shared.removeOffer"))
                    .onAction(() -> {
                        openOfferManager.removeOpenOffer(openOffer, () -> {
                            log.info("Invalid open offer with ID {} was successfully removed.", openOffer.getId());
                        }, log::error);

                    })
                    .hideCloseButton()
                    .show();
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MainView delegate getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    BooleanProperty getNewVersionAvailableProperty() {
        return bisqSetup.getNewVersionAvailableProperty();
    }

    StringProperty getNumOpenSupportTickets() {
        return supportTicketsPresentation.getNumOpenSupportTickets();
    }

    BooleanProperty getShowOpenSupportTicketsNotification() {
        return supportTicketsPresentation.getShowOpenSupportTicketsNotification();
    }

    BooleanProperty getShowPendingTradesNotification() {
        return tradePresentation.getShowPendingTradesNotification();
    }

    StringProperty getNumPendingTrades() {
        return tradePresentation.getNumPendingTrades();
    }

    StringProperty getAvailableBalance() {
        return balancePresentation.getAvailableBalance();
    }

    StringProperty getReservedBalance() {
        return balancePresentation.getReservedBalance();
    }

    StringProperty getLockedBalance() {
        return balancePresentation.getLockedBalance();
    }


    // Wallet
    StringProperty getBtcInfo() {
        final StringProperty combinedInfo = new SimpleStringProperty();
        combinedInfo.bind(bisqSetup.getBtcInfo());
        return combinedInfo;
    }

    StringProperty getCombinedFooterInfo() {
        final StringProperty combinedInfo = new SimpleStringProperty();
        combinedInfo.bind(Bindings.concat(this.footerVersionInfo, " ", daoPresentation.getBsqInfo()));
        return combinedInfo;
    }

    DoubleProperty getCombinedSyncProgress() {
        return combinedSyncProgress;
    }

    StringProperty getWalletServiceErrorMsg() {
        return bisqSetup.getWalletServiceErrorMsg();
    }

    StringProperty getBtcSplashSyncIconId() {
        return bisqSetup.getBtcSplashSyncIconId();
    }

    BooleanProperty getUseTorForBTC() {
        return bisqSetup.getUseTorForBTC();
    }

    // P2P
    StringProperty getP2PNetworkInfo() {
        return bisqSetup.getP2PNetworkInfo();
    }

    BooleanProperty getSplashP2PNetworkAnimationVisible() {
        return bisqSetup.getSplashP2PNetworkAnimationVisible();
    }

    StringProperty getP2pNetworkWarnMsg() {
        return bisqSetup.getP2pNetworkWarnMsg();
    }

    StringProperty getP2PNetworkIconId() {
        return bisqSetup.getP2PNetworkIconId();
    }

    BooleanProperty getUpdatedDataReceived() {
        return bisqSetup.getUpdatedDataReceived();
    }

    StringProperty getP2pNetworkLabelId() {
        return bisqSetup.getP2pNetworkLabelId();
    }

    // marketPricePresentation
    ObjectProperty<PriceFeedComboBoxItem> getSelectedPriceFeedComboBoxItemProperty() {
        return marketPricePresentation.getSelectedPriceFeedComboBoxItemProperty();
    }

    BooleanProperty getIsFiatCurrencyPriceFeedSelected() {
        return marketPricePresentation.getIsFiatCurrencyPriceFeedSelected();
    }

    BooleanProperty getIsExternallyProvidedPrice() {
        return marketPricePresentation.getIsExternallyProvidedPrice();
    }

    BooleanProperty getIsPriceAvailable() {
        return marketPricePresentation.getIsPriceAvailable();
    }

    IntegerProperty getMarketPriceUpdated() {
        return marketPricePresentation.getMarketPriceUpdated();
    }

    StringProperty getMarketPrice() {
        return marketPricePresentation.getMarketPrice();
    }

    StringProperty getMarketPrice(String currencyCode) {
        return marketPricePresentation.getMarketPrice(currencyCode);
    }

    public ObservableList<PriceFeedComboBoxItem> getPriceFeedComboBoxItems() {
        return marketPricePresentation.getPriceFeedComboBoxItems();
    }

    // We keep daoPresentation and accountPresentation support even it is not used atm. But if we add a new feature and
    // add a badge again it will be needed.
    @SuppressWarnings({"unused"})
    public BooleanProperty getShowDaoUpdatesNotification() {
        return daoPresentation.getShowDaoUpdatesNotification();
    }

    // We keep daoPresentation and accountPresentation support even it is not used atm. But if we add a new feature and
    // add a badge again it will be needed.
    @SuppressWarnings("unused")
    public BooleanProperty getShowAccountUpdatesNotification() {
        return accountPresentation.getShowAccountUpdatesNotification();
    }

    public BooleanProperty getShowSettingsUpdatesNotification() {
        return settingsPresentation.getShowSettingsUpdatesNotification();
    }

    private void maybeShowPopupsFromQueue() {
        if (!popupQueue.isEmpty()) {
            Overlay<?> overlay = popupQueue.poll();
            overlay.getIsHiddenProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue) {
                    UserThread.runAfter(this::maybeShowPopupsFromQueue, 2);
                }
            });
            overlay.show();
        }
    }
}
