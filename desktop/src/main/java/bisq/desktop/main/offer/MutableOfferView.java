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

package bisq.desktop.main.offer;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.components.AddressTextField;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipSlideToggleButton;
import bisq.desktop.components.BalanceTextField;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.components.FundsTextField;
import bisq.desktop.components.InfoInputTextField;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.MainView;
import bisq.desktop.main.account.AccountView;
import bisq.desktop.main.account.content.fiataccounts.FiatAccountsView;
import bisq.desktop.main.dao.DaoView;
import bisq.desktop.main.dao.wallet.BsqWalletView;
import bisq.desktop.main.dao.wallet.receive.BsqReceiveView;
import bisq.desktop.main.overlays.notifications.Notification;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.OfferDetailsWindow;
import bisq.desktop.main.overlays.windows.QRCodeWindow;
import bisq.desktop.main.portfolio.PortfolioView;
import bisq.desktop.main.portfolio.openoffer.OpenOffersView;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.payment.FasterPaymentsAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.user.DontShowAgainLookup;
import bisq.core.user.Preferences;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import net.glxn.qrgen.QRCode;
import net.glxn.qrgen.image.ImageType;

import de.jensd.fx.fontawesome.AwesomeIcon;
import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javafx.beans.value.ChangeListener;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;

import javafx.collections.FXCollections;

import javafx.util.StringConverter;

import java.net.URI;

import java.io.ByteArrayInputStream;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.NotNull;

import static bisq.desktop.util.FormBuilder.*;
import static javafx.beans.binding.Bindings.createStringBinding;

public abstract class MutableOfferView<M extends MutableOfferViewModel<?>> extends ActivatableViewAndModel<AnchorPane, M> {
    protected final Navigation navigation;
    private final Preferences preferences;
    private final OfferDetailsWindow offerDetailsWindow;
    private final CoinFormatter btcFormatter;
    private final BsqFormatter bsqFormatter;

    private ScrollPane scrollPane;
    protected GridPane gridPane;
    private TitledGroupBg payFundsTitledGroupBg, setDepositTitledGroupBg, paymentTitledGroupBg;
    protected TitledGroupBg amountTitledGroupBg;
    private BusyAnimation waitingForFundsSpinner;
    private AutoTooltipButton nextButton, cancelButton1, cancelButton2, placeOfferButton, buyBsqButton;
    private Button priceTypeToggleButton;
    private InputTextField fixedPriceTextField, marketBasedPriceTextField, triggerPriceInputTextField;
    protected InputTextField amountTextField, minAmountTextField, volumeTextField, buyerSecurityDepositInputTextField;
    private TextField currencyTextField;
    private AddressTextField addressTextField;
    private BalanceTextField balanceTextField;
    private FundsTextField totalToPayTextField;
    private Label amountDescriptionLabel, priceCurrencyLabel, priceDescriptionLabel, volumeDescriptionLabel,
            waitingForFundsLabel, marketBasedPriceLabel, percentagePriceDescriptionLabel, tradeFeeDescriptionLabel,
            resultLabel, tradeFeeInBtcLabel, tradeFeeInBsqLabel, xLabel, fakeXLabel, buyerSecurityDepositLabel,
            buyerSecurityDepositPercentageLabel, triggerPriceCurrencyLabel, triggerPriceDescriptionLabel;
    protected Label amountBtcLabel, volumeCurrencyLabel, minAmountBtcLabel;
    private ComboBox<PaymentAccount> paymentAccountsComboBox;
    private ComboBox<TradeCurrency> currencyComboBox;
    private ImageView qrCodeImageView;
    private VBox currencySelection, fixedPriceBox, percentagePriceBox, currencyTextFieldBox, triggerPriceVBox;
    private HBox fundingHBox, firstRowHBox, secondRowHBox, placeOfferBox, amountValueCurrencyBox,
            priceAsPercentageValueCurrencyBox, volumeValueCurrencyBox, priceValueCurrencyBox,
            minAmountValueCurrencyBox, advancedOptionsBox, triggerPriceHBox;

    private Subscription isWaitingForFundsSubscription, balanceSubscription;
    private ChangeListener<Boolean> amountFocusedListener, minAmountFocusedListener, volumeFocusedListener,
            buyerSecurityDepositFocusedListener, priceFocusedListener, placeOfferCompletedListener,
            priceAsPercentageFocusedListener, getShowWalletFundedNotificationListener,
            tradeFeeInBtcToggleListener, tradeFeeInBsqToggleListener, tradeFeeVisibleListener,
            isMinBuyerSecurityDepositListener, triggerPriceFocusedListener;
    private ChangeListener<Coin> missingCoinListener;
    private ChangeListener<String> tradeCurrencyCodeListener, errorMessageListener,
            marketPriceMarginListener, volumeListener, buyerSecurityDepositInBTCListener;
    private ChangeListener<Number> marketPriceAvailableListener;
    private EventHandler<ActionEvent> currencyComboBoxSelectionHandler, paymentAccountsComboBoxSelectionHandler;
    private OfferView.CloseHandler closeHandler;

    protected int gridRow = 0;
    private final List<Node> editOfferElements = new ArrayList<>();
    private boolean clearXchangeWarningDisplayed, fasterPaymentsWarningDisplayed, isActivated;
    private InfoInputTextField marketBasedPriceInfoInputTextField, volumeInfoInputTextField,
            buyerSecurityDepositInfoInputTextField, triggerPriceInfoInputTextField;
    private AutoTooltipSlideToggleButton tradeFeeInBtcToggle, tradeFeeInBsqToggle;
    private Text xIcon, fakeXIcon;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public MutableOfferView(M model,
                            Navigation navigation,
                            Preferences preferences,
                            OfferDetailsWindow offerDetailsWindow,
                            CoinFormatter btcFormatter,
                            BsqFormatter bsqFormatter) {
        super(model);

        this.navigation = navigation;
        this.preferences = preferences;
        this.offerDetailsWindow = offerDetailsWindow;
        this.btcFormatter = btcFormatter;
        this.bsqFormatter = bsqFormatter;
    }

    @Override
    protected void initialize() {
        addScrollPane();
        addGridPane();
        addPaymentGroup();
        addAmountPriceGroup();
        addOptionsGroup();
        addFundingGroup();

        createListeners();

        balanceTextField.setFormatter(model.getBtcFormatter());

        paymentAccountsComboBox.setConverter(GUIUtil.getPaymentAccountsComboBoxStringConverter());
        paymentAccountsComboBox.setButtonCell(GUIUtil.getComboBoxButtonCell(Res.get("shared.selectTradingAccount"),
                paymentAccountsComboBox, false));
        paymentAccountsComboBox.setCellFactory(model.getPaymentAccountListCellFactory(paymentAccountsComboBox));

        doSetFocus();
    }

    protected void doSetFocus() {
        GUIUtil.focusWhenAddedToScene(amountTextField);
    }

    @Override
    protected void activate() {
        if (model.getDataModel().isTabSelected)
            doActivate();
    }

    protected void doActivate() {
        if (!isActivated) {
            isActivated = true;
            currencyComboBox.setPrefWidth(250);
            paymentAccountsComboBox.setPrefWidth(250);

            addBindings();
            addListeners();
            addSubscriptions();

            // temporarily disabled due to high CPU usage (per issue #4649)
            // if (waitingForFundsSpinner != null)
            //     waitingForFundsSpinner.play();

            amountDescriptionLabel.setText(model.getAmountDescription());
            addressTextField.setAddress(model.getAddressAsString());
            addressTextField.setPaymentLabel(model.getPaymentLabel());

            paymentAccountsComboBox.setItems(model.getDataModel().getPaymentAccounts());
            paymentAccountsComboBox.getSelectionModel().select(model.getPaymentAccount());
            currencyComboBox.getSelectionModel().select(model.getTradeCurrency());

            onPaymentAccountsComboBoxSelected();

            balanceTextField.setTargetAmount(model.getDataModel().totalToPayAsCoinProperty().get());
            updatePriceToggle();

            boolean currencyForMakerFeeBtc = model.getDataModel().isCurrencyForMakerFeeBtc();
            tradeFeeInBtcToggle.setSelected(currencyForMakerFeeBtc);
            tradeFeeInBsqToggle.setSelected(!currencyForMakerFeeBtc);

            if (!DevEnv.isDaoActivated()) {
                tradeFeeInBtcToggle.setVisible(false);
                tradeFeeInBtcToggle.setManaged(false);
                tradeFeeInBsqToggle.setVisible(false);
                tradeFeeInBsqToggle.setManaged(false);
                buyBsqButton.setVisible(false);
                buyBsqButton.setManaged(false);
            }

            Label popOverLabel = OfferViewUtil.createPopOverLabel(Res.get("createOffer.triggerPrice.tooltip"));
            triggerPriceInfoInputTextField.setContentForPopOver(popOverLabel, AwesomeIcon.SHIELD);
        }
    }

    @Override
    protected void deactivate() {
        if (isActivated) {
            isActivated = false;
            removeBindings();
            removeListeners();
            removeSubscriptions();

            // temporarily disabled due to high CPU usage (per issue #4649)
            //if (waitingForFundsSpinner != null)
            //    waitingForFundsSpinner.stop();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onTabSelected(boolean isSelected) {
        if (isSelected && !model.getDataModel().isTabSelected)
            doActivate();
        else
            deactivate();

        isActivated = isSelected;
        model.getDataModel().onTabSelected(isSelected);
    }

    public void initWithData(OfferPayload.Direction direction, TradeCurrency tradeCurrency) {
        boolean result = model.initWithData(direction, tradeCurrency);

        if (!result) {
            new Popup().headLine(Res.get("popup.warning.noTradingAccountSetup.headline"))
                    .instruction(Res.get("popup.warning.noTradingAccountSetup.msg"))
                    .actionButtonTextWithGoTo("navigation.account")
                    .onAction(() -> {
                        navigation.setReturnPath(navigation.getCurrentPath());
                        navigation.navigateTo(MainView.class, AccountView.class, FiatAccountsView.class);
                    }).show();
        }

        if (direction == OfferPayload.Direction.BUY) {
            placeOfferButton.setId("buy-button-big");
            placeOfferButton.updateText(Res.get("createOffer.placeOfferButton", Res.get("shared.buy")));
        } else {
            placeOfferButton.setId("sell-button-big");
            placeOfferButton.updateText(Res.get("createOffer.placeOfferButton", Res.get("shared.sell")));
        }

        updatePriceToggle();

        if (!model.getDataModel().isMakerFeeValid() && model.getDataModel().getMakerFee() != null)
            showInsufficientBsqFundsForBtcFeePaymentPopup();
    }

    // called form parent as the view does not get notified when the tab is closed
    public void onClose() {
        // we use model.placeOfferCompleted to not react on close which was triggered by a successful placeOffer
        if (model.getDataModel().getBalance().get().isPositive() && !model.placeOfferCompleted.get()) {
            model.getDataModel().swapTradeToSavings();
        }
    }

    public void setCloseHandler(OfferView.CloseHandler closeHandler) {
        this.closeHandler = closeHandler;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onPlaceOffer() {
        if (model.getDataModel().canPlaceOffer()) {
            if (model.getDataModel().isMakerFeeValid()) {
                Offer offer = model.createAndGetOffer();
                if (!DevEnv.isDevMode()) {
                    offerDetailsWindow.onPlaceOffer(() ->
                            model.onPlaceOffer(offer, offerDetailsWindow::hide))
                            .show(offer);
                } else {
                    balanceSubscription.unsubscribe();
                    model.onPlaceOffer(offer, () -> {
                    });
                }
            } else {
                showInsufficientBsqFundsForBtcFeePaymentPopup();
            }
        }
    }

    private void showInsufficientBsqFundsForBtcFeePaymentPopup() {
        Coin makerFee = model.getDataModel().getMakerFee(false);
        String message = null;
        if (makerFee != null) {
            message = Res.get("popup.warning.insufficientBsqFundsForBtcFeePayment",
                    bsqFormatter.formatCoinWithCode(makerFee.subtract(model.getDataModel().getUsableBsqBalance())));

        } else if (model.getDataModel().getUsableBsqBalance().isZero())
            message = Res.get("popup.warning.noBsqFundsForBtcFeePayment");

        if (message != null)
            new Popup().warning(message)
                    .actionButtonTextWithGoTo("navigation.dao.wallet.receive")
                    .onAction(() -> navigation.navigateTo(MainView.class, DaoView.class, BsqWalletView.class, BsqReceiveView.class))
                    .show();
    }

    private void onShowPayFundsScreen() {
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        nextButton.setVisible(false);
        nextButton.setManaged(false);
        nextButton.setOnAction(null);
        cancelButton1.setVisible(false);
        cancelButton1.setManaged(false);
        cancelButton1.setOnAction(null);
        buyBsqButton.setVisible(false);
        buyBsqButton.setManaged(false);
        buyBsqButton.setOnAction(null);

        tradeFeeInBtcToggle.setMouseTransparent(true);
        tradeFeeInBsqToggle.setMouseTransparent(true);

        setDepositTitledGroupBg.setVisible(false);
        setDepositTitledGroupBg.setManaged(false);

        advancedOptionsBox.setVisible(false);
        advancedOptionsBox.setManaged(false);

        model.onShowPayFundsScreen(() -> {
            if (!DevEnv.isDevMode()) {
                String key = "createOfferFundWalletInfo";
                String tradeAmountText = model.isSellOffer() ?
                        Res.get("createOffer.createOfferFundWalletInfo.tradeAmount", model.getTradeAmount()) : "";

                String message = Res.get("createOffer.createOfferFundWalletInfo.msg",
                        model.getTotalToPayInfo(),
                        tradeAmountText,
                        model.getSecurityDepositInfo(),
                        model.getTradeFee(),
                        model.getTxFee()
                );
                new Popup().headLine(Res.get("createOffer.createOfferFundWalletInfo.headline"))
                        .instruction(message)
                        .dontShowAgainId(key)
                        .show();
            }

            totalToPayTextField.setFundsStructure(model.getFundsStructure());
            totalToPayTextField.setContentForInfoPopOver(createInfoPopover());
        });

        paymentAccountsComboBox.setDisable(true);

        editOfferElements.forEach(node -> {
            node.setMouseTransparent(true);
            node.setFocusTraversable(false);
        });

        updateOfferElementsStyle();

        if (triggerPriceInputTextField.getText().isEmpty()) {
            triggerPriceVBox.setVisible(false);
        }

        balanceTextField.setTargetAmount(model.getDataModel().totalToPayAsCoinProperty().get());

        if (!DevEnv.isDevMode()) {
            String key = "securityDepositInfo";
            new Popup().backgroundInfo(Res.get("popup.info.securityDepositInfo"))
                    .actionButtonText(Res.get("shared.faq"))
                    .onAction(() -> GUIUtil.openWebPage("https://bisq.wiki/Frequently_asked_questions#Why_does_Bisq_require_a_security_deposit_in_BTC.3F"))
                    .useIUnderstandButton()
                    .dontShowAgainId(key)
                    .show();
        }

        // temporarily disabled due to high CPU usage (per issue #4649)
        // waitingForFundsSpinner.play();

        payFundsTitledGroupBg.setVisible(true);
        totalToPayTextField.setVisible(true);
        addressTextField.setVisible(true);
        qrCodeImageView.setVisible(true);
        balanceTextField.setVisible(true);
        cancelButton2.setVisible(true);
    }

    private void updateOfferElementsStyle() {
        GridPane.setColumnSpan(firstRowHBox, 2);

        String activeInputStyle = "input-with-border";
        String readOnlyInputStyle = "input-with-border-readonly";
        amountValueCurrencyBox.getStyleClass().remove(activeInputStyle);
        amountValueCurrencyBox.getStyleClass().add(readOnlyInputStyle);
        priceAsPercentageValueCurrencyBox.getStyleClass().remove(activeInputStyle);
        priceAsPercentageValueCurrencyBox.getStyleClass().add(readOnlyInputStyle);
        volumeValueCurrencyBox.getStyleClass().remove(activeInputStyle);
        volumeValueCurrencyBox.getStyleClass().add(readOnlyInputStyle);
        priceValueCurrencyBox.getStyleClass().remove(activeInputStyle);
        priceValueCurrencyBox.getStyleClass().add(readOnlyInputStyle);
        minAmountValueCurrencyBox.getStyleClass().remove(activeInputStyle);
        minAmountValueCurrencyBox.getStyleClass().add(readOnlyInputStyle);
        triggerPriceHBox.getStyleClass().remove(activeInputStyle);
        triggerPriceHBox.getStyleClass().add(readOnlyInputStyle);

        GridPane.setColumnSpan(secondRowHBox, 1);
        priceTypeToggleButton.setVisible(false);
        HBox.setMargin(priceTypeToggleButton, new Insets(16, -14, 0, 0));

        resultLabel.getStyleClass().add("small");
        xLabel.getStyleClass().add("small");
        xIcon.setStyle(String.format("-fx-font-family: %s; -fx-font-size: %s;", MaterialDesignIcon.CLOSE.fontFamily(), "1em"));
        fakeXIcon.setStyle(String.format("-fx-font-family: %s; -fx-font-size: %s;", MaterialDesignIcon.CLOSE.fontFamily(), "1em"));
        fakeXLabel.getStyleClass().add("small");
    }

    private void maybeShowClearXchangeWarning(PaymentAccount paymentAccount) {
        if (paymentAccount.getPaymentMethod().getId().equals(PaymentMethod.CLEAR_X_CHANGE_ID) &&
                !clearXchangeWarningDisplayed) {
            clearXchangeWarningDisplayed = true;
            UserThread.runAfter(GUIUtil::showClearXchangeWarning, 500, TimeUnit.MILLISECONDS);
        }
    }

    private void maybeShowFasterPaymentsWarning(PaymentAccount paymentAccount) {
        if (paymentAccount.getPaymentMethod().getId().equals(PaymentMethod.FASTER_PAYMENTS_ID) &&
                ((FasterPaymentsAccount) paymentAccount).getHolderName().isEmpty() &&
                !fasterPaymentsWarningDisplayed) {
            fasterPaymentsWarningDisplayed = true;
            UserThread.runAfter(() -> GUIUtil.showFasterPaymentsWarning(navigation), 500, TimeUnit.MILLISECONDS);
        }
    }

    protected void onPaymentAccountsComboBoxSelected() {
        // Temporary deactivate handler as the payment account change can populate a new currency list and causes
        // unwanted selection events (item 0)
        currencyComboBox.setOnAction(null);

        PaymentAccount paymentAccount = paymentAccountsComboBox.getSelectionModel().getSelectedItem();
        if (paymentAccount != null) {
            maybeShowClearXchangeWarning(paymentAccount);
            maybeShowFasterPaymentsWarning(paymentAccount);

            currencySelection.setVisible(paymentAccount.hasMultipleCurrencies());
            currencySelection.setManaged(paymentAccount.hasMultipleCurrencies());
            currencyTextFieldBox.setVisible(!paymentAccount.hasMultipleCurrencies());
            if (paymentAccount.hasMultipleCurrencies()) {
                final List<TradeCurrency> tradeCurrencies = paymentAccount.getTradeCurrencies();
                currencyComboBox.setItems(FXCollections.observableArrayList(tradeCurrencies));
                model.onPaymentAccountSelected(paymentAccount);
            } else {
                TradeCurrency singleTradeCurrency = paymentAccount.getSingleTradeCurrency();
                if (singleTradeCurrency != null)
                    currencyTextField.setText(singleTradeCurrency.getNameAndCode());
                model.onPaymentAccountSelected(paymentAccount);
                model.onCurrencySelected(model.getDataModel().getTradeCurrency());
            }
        } else {
            currencySelection.setVisible(false);
            currencySelection.setManaged(false);
            currencyTextFieldBox.setVisible(true);

            currencyTextField.setText("");
        }

        currencyComboBox.setOnAction(currencyComboBoxSelectionHandler);

        updatePriceToggle();
    }

    private void onCurrencyComboBoxSelected() {
        model.onCurrencySelected(currencyComboBox.getSelectionModel().getSelectedItem());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void close() {
        if (closeHandler != null)
            closeHandler.close();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bindings, Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addBindings() {
        priceCurrencyLabel.textProperty().bind(createStringBinding(() -> CurrencyUtil.getCounterCurrency(model.tradeCurrencyCode.get()), model.tradeCurrencyCode));
        triggerPriceCurrencyLabel.textProperty().bind(createStringBinding(() ->
                CurrencyUtil.getCounterCurrency(model.tradeCurrencyCode.get()), model.tradeCurrencyCode));
        triggerPriceDescriptionLabel.textProperty().bind(model.triggerPriceDescription);
        percentagePriceDescriptionLabel.textProperty().bind(model.percentagePriceDescription);
        marketBasedPriceLabel.prefWidthProperty().bind(priceCurrencyLabel.widthProperty());
        volumeCurrencyLabel.textProperty().bind(model.tradeCurrencyCode);
        priceDescriptionLabel.textProperty().bind(createStringBinding(() -> CurrencyUtil.getPriceWithCurrencyCode(model.tradeCurrencyCode.get(), "shared.fixedPriceInCurForCur"), model.tradeCurrencyCode));
        volumeDescriptionLabel.textProperty().bind(createStringBinding(model.volumeDescriptionLabel::get, model.tradeCurrencyCode, model.volumeDescriptionLabel));
        amountTextField.textProperty().bindBidirectional(model.amount);
        minAmountTextField.textProperty().bindBidirectional(model.minAmount);
        fixedPriceTextField.textProperty().bindBidirectional(model.price);
        triggerPriceInputTextField.textProperty().bindBidirectional(model.triggerPrice);
        marketBasedPriceTextField.textProperty().bindBidirectional(model.marketPriceMargin);
        volumeTextField.textProperty().bindBidirectional(model.volume);
        volumeTextField.promptTextProperty().bind(model.volumePromptLabel);
        totalToPayTextField.textProperty().bind(model.totalToPay);
        addressTextField.amountAsCoinProperty().bind(model.getDataModel().getMissingCoin());
        buyerSecurityDepositInputTextField.textProperty().bindBidirectional(model.buyerSecurityDeposit);
        buyerSecurityDepositLabel.textProperty().bind(model.buyerSecurityDepositLabel);
        tradeFeeInBtcLabel.textProperty().bind(model.tradeFeeInBtcWithFiat);
        tradeFeeInBsqLabel.textProperty().bind(model.tradeFeeInBsqWithFiat);
        tradeFeeDescriptionLabel.textProperty().bind(model.tradeFeeDescription);
        tradeFeeInBtcLabel.visibleProperty().bind(model.isTradeFeeVisible);
        tradeFeeInBsqLabel.visibleProperty().bind(model.isTradeFeeVisible);
        tradeFeeDescriptionLabel.visibleProperty().bind(model.isTradeFeeVisible);

        // Validation
        amountTextField.validationResultProperty().bind(model.amountValidationResult);
        minAmountTextField.validationResultProperty().bind(model.minAmountValidationResult);
        fixedPriceTextField.validationResultProperty().bind(model.priceValidationResult);
        triggerPriceInputTextField.validationResultProperty().bind(model.triggerPriceValidationResult);
        volumeTextField.validationResultProperty().bind(model.volumeValidationResult);
        buyerSecurityDepositInputTextField.validationResultProperty().bind(model.buyerSecurityDepositValidationResult);

        // funding
        fundingHBox.visibleProperty().bind(model.getDataModel().getIsBtcWalletFunded().not().and(model.showPayFundsScreenDisplayed));
        fundingHBox.managedProperty().bind(model.getDataModel().getIsBtcWalletFunded().not().and(model.showPayFundsScreenDisplayed));
        waitingForFundsLabel.textProperty().bind(model.waitingForFundsText);
        placeOfferBox.visibleProperty().bind(model.getDataModel().getIsBtcWalletFunded().and(model.showPayFundsScreenDisplayed));
        placeOfferBox.managedProperty().bind(model.getDataModel().getIsBtcWalletFunded().and(model.showPayFundsScreenDisplayed));
        placeOfferButton.disableProperty().bind(model.isPlaceOfferButtonDisabled);
        cancelButton2.disableProperty().bind(model.cancelButtonDisabled);

        // trading account
        paymentAccountsComboBox.managedProperty().bind(paymentAccountsComboBox.visibleProperty());
        paymentTitledGroupBg.managedProperty().bind(paymentTitledGroupBg.visibleProperty());
        currencyComboBox.prefWidthProperty().bind(paymentAccountsComboBox.widthProperty());
        currencyComboBox.managedProperty().bind(currencyComboBox.visibleProperty());
        currencyTextFieldBox.managedProperty().bind(currencyTextFieldBox.visibleProperty());
    }

    private void removeBindings() {
        priceCurrencyLabel.textProperty().unbind();
        triggerPriceCurrencyLabel.textProperty().unbind();
        triggerPriceDescriptionLabel.textProperty().unbind();
        percentagePriceDescriptionLabel.textProperty().unbind();
        volumeCurrencyLabel.textProperty().unbind();
        priceDescriptionLabel.textProperty().unbind();
        volumeDescriptionLabel.textProperty().unbind();
        amountTextField.textProperty().unbindBidirectional(model.amount);
        minAmountTextField.textProperty().unbindBidirectional(model.minAmount);
        fixedPriceTextField.textProperty().unbindBidirectional(model.price);
        triggerPriceInputTextField.textProperty().unbindBidirectional(model.triggerPrice);
        marketBasedPriceTextField.textProperty().unbindBidirectional(model.marketPriceMargin);
        marketBasedPriceLabel.prefWidthProperty().unbind();
        volumeTextField.textProperty().unbindBidirectional(model.volume);
        volumeTextField.promptTextProperty().unbindBidirectional(model.volume);
        totalToPayTextField.textProperty().unbind();
        addressTextField.amountAsCoinProperty().unbind();
        buyerSecurityDepositInputTextField.textProperty().unbindBidirectional(model.buyerSecurityDeposit);
        buyerSecurityDepositLabel.textProperty().unbind();
        tradeFeeInBtcLabel.textProperty().unbind();
        tradeFeeInBsqLabel.textProperty().unbind();
        tradeFeeDescriptionLabel.textProperty().unbind();
        tradeFeeInBtcLabel.visibleProperty().unbind();
        tradeFeeInBsqLabel.visibleProperty().unbind();
        tradeFeeDescriptionLabel.visibleProperty().unbind();

        // Validation
        amountTextField.validationResultProperty().unbind();
        minAmountTextField.validationResultProperty().unbind();
        fixedPriceTextField.validationResultProperty().unbind();
        triggerPriceInputTextField.validationResultProperty().unbind();
        volumeTextField.validationResultProperty().unbind();
        buyerSecurityDepositInputTextField.validationResultProperty().unbind();

        // funding
        fundingHBox.visibleProperty().unbind();
        fundingHBox.managedProperty().unbind();
        waitingForFundsLabel.textProperty().unbind();
        placeOfferBox.visibleProperty().unbind();
        placeOfferBox.managedProperty().unbind();
        placeOfferButton.disableProperty().unbind();
        cancelButton2.disableProperty().unbind();

        // trading account
        paymentTitledGroupBg.managedProperty().unbind();
        paymentAccountsComboBox.managedProperty().unbind();
        currencyComboBox.managedProperty().unbind();
        currencyComboBox.prefWidthProperty().unbind();
        currencyTextFieldBox.managedProperty().unbind();
    }

    private void addSubscriptions() {
        isWaitingForFundsSubscription = EasyBind.subscribe(model.isWaitingForFunds, isWaitingForFunds -> {

            // temporarily disabled due to high CPU usage (per issue #4649)
            //if (isWaitingForFunds) {
            //    waitingForFundsSpinner.play();
            //} else {
            //    waitingForFundsSpinner.stop();
            //}

            waitingForFundsLabel.setVisible(isWaitingForFunds);
            waitingForFundsLabel.setManaged(isWaitingForFunds);
        });

        balanceSubscription = EasyBind.subscribe(model.getDataModel().getBalance(), balanceTextField::setBalance);
    }

    private void removeSubscriptions() {
        isWaitingForFundsSubscription.unsubscribe();
        balanceSubscription.unsubscribe();
    }

    private void createListeners() {
        amountFocusedListener = (o, oldValue, newValue) -> {
            model.onFocusOutAmountTextField(oldValue, newValue);
            amountTextField.setText(model.amount.get());
        };
        minAmountFocusedListener = (o, oldValue, newValue) -> {
            model.onFocusOutMinAmountTextField(oldValue, newValue);
            minAmountTextField.setText(model.minAmount.get());
        };
        priceFocusedListener = (o, oldValue, newValue) -> {
            model.onFocusOutPriceTextField(oldValue, newValue);
            fixedPriceTextField.setText(model.price.get());
        };
        priceAsPercentageFocusedListener = (o, oldValue, newValue) -> {
            model.onFocusOutPriceAsPercentageTextField(oldValue, newValue);
            marketBasedPriceTextField.setText(model.marketPriceMargin.get());
        };
        volumeFocusedListener = (o, oldValue, newValue) -> {
            model.onFocusOutVolumeTextField(oldValue, newValue);
            volumeTextField.setText(model.volume.get());
        };
        buyerSecurityDepositFocusedListener = (o, oldValue, newValue) -> {
            model.onFocusOutBuyerSecurityDepositTextField(oldValue, newValue);
            buyerSecurityDepositInputTextField.setText(model.buyerSecurityDeposit.get());
        };

        triggerPriceFocusedListener = (o, oldValue, newValue) -> {
            model.onFocusOutTriggerPriceTextField(oldValue, newValue);
            triggerPriceInputTextField.setText(model.triggerPrice.get());
        };

        errorMessageListener = (o, oldValue, newValue) -> {
            if (newValue != null)
                UserThread.runAfter(() -> new Popup().error(Res.get("createOffer.amountPriceBox.error.message", model.errorMessage.get()))
                        .show(), 100, TimeUnit.MILLISECONDS);
        };

        paymentAccountsComboBoxSelectionHandler = e -> onPaymentAccountsComboBoxSelected();
        currencyComboBoxSelectionHandler = e -> onCurrencyComboBoxSelected();

        tradeCurrencyCodeListener = (observable, oldValue, newValue) -> {
            fixedPriceTextField.clear();
            marketBasedPriceTextField.clear();
            volumeTextField.clear();
            triggerPriceInputTextField.clear();
        };

        placeOfferCompletedListener = (o, oldValue, newValue) -> {
            if (DevEnv.isDevMode()) {
                close();
            } else if (newValue) {
                // We need a bit of delay to avoid issues with fade out/fade in of 2 popups
                String key = "createOfferSuccessInfo";
                if (DontShowAgainLookup.showAgain(key)) {
                    UserThread.runAfter(() -> new Popup().headLine(Res.get("createOffer.success.headline"))
                                    .feedback(Res.get("createOffer.success.info"))
                                    .dontShowAgainId(key)
                                    .actionButtonTextWithGoTo("navigation.portfolio.myOpenOffers")
                                    .onAction(() -> {
                                        UserThread.runAfter(() ->
                                                        navigation.navigateTo(MainView.class, PortfolioView.class,
                                                                OpenOffersView.class),
                                                100, TimeUnit.MILLISECONDS);
                                        close();
                                    })
                                    .onClose(this::close)
                                    .show(),
                            1);
                } else {
                    close();
                }
            }
        };

        marketPriceAvailableListener = (observable, oldValue, newValue) -> updatePriceToggle();

        getShowWalletFundedNotificationListener = (observable, oldValue, newValue) -> {
            if (newValue) {
                Notification walletFundedNotification = new Notification()
                        .headLine(Res.get("notification.walletUpdate.headline"))
                        .notification(Res.get("notification.walletUpdate.msg", btcFormatter.formatCoinWithCode(model.getDataModel().getTotalToPayAsCoin().get())))
                        .autoClose();

                walletFundedNotification.show();
            }
        };

        buyerSecurityDepositInBTCListener = (observable, oldValue, newValue) -> {
            if (!newValue.equals("")) {
                Label depositInBTCInfo = OfferViewUtil.createPopOverLabel(model.getSecurityDepositPopOverLabel(newValue));
                buyerSecurityDepositInfoInputTextField.setContentForInfoPopOver(depositInBTCInfo);
            } else {
                buyerSecurityDepositInfoInputTextField.setContentForInfoPopOver(null);
            }
        };

        volumeListener = (observable, oldValue, newValue) -> {
            if (!newValue.equals("") && CurrencyUtil.isFiatCurrency(model.tradeCurrencyCode.get())) {
                Label popOverLabel = OfferViewUtil.createPopOverLabel(Res.get("offerbook.info.roundedFiatVolume"));
                volumeInfoInputTextField.setContentForPrivacyPopOver(popOverLabel);
            } else {
                volumeInfoInputTextField.hideIcon();
            }
        };

        missingCoinListener = (observable, oldValue, newValue) -> {
            if (!newValue.toString().equals("")) {
                final byte[] imageBytes = QRCode
                        .from(getBitcoinURI())
                        .withSize(98, 98) // code has 41 elements 8 px is border with 98 we get double scale and min. border
                        .to(ImageType.PNG)
                        .stream()
                        .toByteArray();
                Image qrImage = new Image(new ByteArrayInputStream(imageBytes));
                qrCodeImageView.setImage(qrImage);
            }
        };

        marketPriceMarginListener = (observable, oldValue, newValue) -> {
            if (marketBasedPriceInfoInputTextField != null) {
                String tooltip;
                if (newValue.equals("0.00")) {
                    if (model.isSellOffer()) {
                        tooltip = Res.get("createOffer.info.sellAtMarketPrice");
                    } else {
                        tooltip = Res.get("createOffer.info.buyAtMarketPrice");
                    }
                    final Label atMarketPriceLabel = OfferViewUtil.createPopOverLabel(tooltip);
                    marketBasedPriceInfoInputTextField.setContentForInfoPopOver(atMarketPriceLabel);
                } else if (newValue.contains("-")) {
                    if (model.isSellOffer()) {
                        tooltip = Res.get("createOffer.warning.sellBelowMarketPrice", newValue.substring(1));
                    } else {
                        tooltip = Res.get("createOffer.warning.buyAboveMarketPrice", newValue.substring(1));
                    }
                    final Label negativePercentageLabel = OfferViewUtil.createPopOverLabel(tooltip);
                    marketBasedPriceInfoInputTextField.setContentForWarningPopOver(negativePercentageLabel);
                } else if (!newValue.equals("")) {
                    if (model.isSellOffer()) {
                        tooltip = Res.get("createOffer.info.sellAboveMarketPrice", newValue);
                    } else {
                        tooltip = Res.get("createOffer.info.buyBelowMarketPrice", newValue);
                    }
                    Label positivePercentageLabel = OfferViewUtil.createPopOverLabel(tooltip);
                    marketBasedPriceInfoInputTextField.setContentForInfoPopOver(positivePercentageLabel);
                }
            }
        };


        tradeFeeInBtcToggleListener = (observable, oldValue, newValue) -> {
            if (newValue && tradeFeeInBsqToggle.isSelected())
                tradeFeeInBsqToggle.setSelected(false);

            if (!newValue && !tradeFeeInBsqToggle.isSelected())
                tradeFeeInBsqToggle.setSelected(true);

            setIsCurrencyForMakerFeeBtc(newValue);
        };
        tradeFeeInBsqToggleListener = (observable, oldValue, newValue) -> {
            if (newValue && tradeFeeInBtcToggle.isSelected())
                tradeFeeInBtcToggle.setSelected(false);

            if (!newValue && !tradeFeeInBtcToggle.isSelected())
                tradeFeeInBtcToggle.setSelected(true);

            setIsCurrencyForMakerFeeBtc(!newValue);
        };

        tradeFeeVisibleListener = (observable, oldValue, newValue) -> {
            if (DevEnv.isDaoActivated()) {
                tradeFeeInBtcToggle.setVisible(newValue);
                tradeFeeInBsqToggle.setVisible(newValue);
                buyBsqButton.setVisible(newValue);
            }
        };

        isMinBuyerSecurityDepositListener = ((observable, oldValue, newValue) -> {
            if (newValue) {
                // show BTC
                buyerSecurityDepositPercentageLabel.setText(Res.getBaseCurrencyCode());
                buyerSecurityDepositInputTextField.setDisable(true);
            } else {
                // show %
                buyerSecurityDepositPercentageLabel.setText("%");
                buyerSecurityDepositInputTextField.setDisable(false);
            }
        });
    }

    private void setIsCurrencyForMakerFeeBtc(boolean isCurrencyForMakerFeeBtc) {
        model.setIsCurrencyForMakerFeeBtc(isCurrencyForMakerFeeBtc);
        if (DevEnv.isDaoActivated()) {
            tradeFeeInBtcLabel.setOpacity(isCurrencyForMakerFeeBtc ? 1 : 0.3);
            tradeFeeInBsqLabel.setOpacity(isCurrencyForMakerFeeBtc ? 0.3 : 1);
        }
    }

    protected void updatePriceToggle() {
        int marketPriceAvailableValue = model.marketPriceAvailableProperty.get();
        if (marketPriceAvailableValue > -1) {
            boolean showPriceToggle = marketPriceAvailableValue == 1 &&
                    !model.getDataModel().paymentAccount.isHalCashAccount();
            percentagePriceBox.setVisible(showPriceToggle);
            priceTypeToggleButton.setVisible(showPriceToggle);
            boolean fixedPriceSelected = !model.getDataModel().getUseMarketBasedPrice().get() || !showPriceToggle;
            updatePriceToggleButtons(fixedPriceSelected);
        }
    }

    private void addListeners() {
        model.tradeCurrencyCode.addListener(tradeCurrencyCodeListener);
        model.marketPriceAvailableProperty.addListener(marketPriceAvailableListener);
        model.marketPriceMargin.addListener(marketPriceMarginListener);
        model.volume.addListener(volumeListener);
        model.getDataModel().missingCoin.addListener(missingCoinListener);
        model.isTradeFeeVisible.addListener(tradeFeeVisibleListener);
        model.buyerSecurityDepositInBTC.addListener(buyerSecurityDepositInBTCListener);
        model.isMinBuyerSecurityDeposit.addListener(isMinBuyerSecurityDepositListener);

        tradeFeeInBtcToggle.selectedProperty().addListener(tradeFeeInBtcToggleListener);
        tradeFeeInBsqToggle.selectedProperty().addListener(tradeFeeInBsqToggleListener);

        // focus out
        amountTextField.focusedProperty().addListener(amountFocusedListener);
        minAmountTextField.focusedProperty().addListener(minAmountFocusedListener);
        fixedPriceTextField.focusedProperty().addListener(priceFocusedListener);
        triggerPriceInputTextField.focusedProperty().addListener(triggerPriceFocusedListener);
        marketBasedPriceTextField.focusedProperty().addListener(priceAsPercentageFocusedListener);
        volumeTextField.focusedProperty().addListener(volumeFocusedListener);
        buyerSecurityDepositInputTextField.focusedProperty().addListener(buyerSecurityDepositFocusedListener);

        // notifications
        model.getDataModel().getShowWalletFundedNotification().addListener(getShowWalletFundedNotificationListener);

        // warnings
        model.errorMessage.addListener(errorMessageListener);
        // model.getDataModel().feeFromFundingTxProperty.addListener(feeFromFundingTxListener);

        model.placeOfferCompleted.addListener(placeOfferCompletedListener);

        // UI actions
        paymentAccountsComboBox.setOnAction(paymentAccountsComboBoxSelectionHandler);
        currencyComboBox.setOnAction(currencyComboBoxSelectionHandler);
    }

    private void removeListeners() {
        model.tradeCurrencyCode.removeListener(tradeCurrencyCodeListener);
        model.marketPriceAvailableProperty.removeListener(marketPriceAvailableListener);
        model.marketPriceMargin.removeListener(marketPriceMarginListener);
        model.volume.removeListener(volumeListener);
        model.getDataModel().missingCoin.removeListener(missingCoinListener);
        model.isTradeFeeVisible.removeListener(tradeFeeVisibleListener);
        model.buyerSecurityDepositInBTC.removeListener(buyerSecurityDepositInBTCListener);
        tradeFeeInBtcToggle.selectedProperty().removeListener(tradeFeeInBtcToggleListener);
        tradeFeeInBsqToggle.selectedProperty().removeListener(tradeFeeInBsqToggleListener);
        model.isMinBuyerSecurityDeposit.removeListener(isMinBuyerSecurityDepositListener);

        // focus out
        amountTextField.focusedProperty().removeListener(amountFocusedListener);
        minAmountTextField.focusedProperty().removeListener(minAmountFocusedListener);
        fixedPriceTextField.focusedProperty().removeListener(priceFocusedListener);
        triggerPriceInputTextField.focusedProperty().removeListener(triggerPriceFocusedListener);
        marketBasedPriceTextField.focusedProperty().removeListener(priceAsPercentageFocusedListener);
        volumeTextField.focusedProperty().removeListener(volumeFocusedListener);
        buyerSecurityDepositInputTextField.focusedProperty().removeListener(buyerSecurityDepositFocusedListener);

        // notifications
        model.getDataModel().getShowWalletFundedNotification().removeListener(getShowWalletFundedNotificationListener);

        // warnings
        model.errorMessage.removeListener(errorMessageListener);
        // model.getDataModel().feeFromFundingTxProperty.removeListener(feeFromFundingTxListener);

        model.placeOfferCompleted.removeListener(placeOfferCompletedListener);

        // UI actions
        paymentAccountsComboBox.setOnAction(null);
        currencyComboBox.setOnAction(null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Build UI elements
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addScrollPane() {
        scrollPane = new ScrollPane();
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        AnchorPane.setLeftAnchor(scrollPane, 0d);
        AnchorPane.setTopAnchor(scrollPane, 0d);
        AnchorPane.setRightAnchor(scrollPane, 0d);
        AnchorPane.setBottomAnchor(scrollPane, 0d);
        root.getChildren().add(scrollPane);
    }

    private void addGridPane() {
        gridPane = new GridPane();
        gridPane.getStyleClass().add("content-pane");
        gridPane.setPadding(new Insets(30, 25, -1, 25));
        gridPane.setHgap(5);
        gridPane.setVgap(5);
        ColumnConstraints columnConstraints1 = new ColumnConstraints();
        columnConstraints1.setHalignment(HPos.RIGHT);
        columnConstraints1.setHgrow(Priority.NEVER);
        columnConstraints1.setMinWidth(200);
        ColumnConstraints columnConstraints2 = new ColumnConstraints();
        columnConstraints2.setHgrow(Priority.ALWAYS);
        gridPane.getColumnConstraints().addAll(columnConstraints1, columnConstraints2);
        scrollPane.setContent(gridPane);
    }

    private void addPaymentGroup() {
        paymentTitledGroupBg = addTitledGroupBg(gridPane, gridRow, 1, Res.get("shared.selectTradingAccount"));
        GridPane.setColumnSpan(paymentTitledGroupBg, 2);

        HBox paymentGroupBox = new HBox();
        paymentGroupBox.setAlignment(Pos.CENTER_LEFT);
        paymentGroupBox.setSpacing(12);
        paymentGroupBox.setPadding(new Insets(10, 0, 18, 0));

        final Tuple3<VBox, Label, ComboBox<PaymentAccount>> tradingAccountBoxTuple = addTopLabelComboBox(
                Res.get("shared.tradingAccount"), Res.get("shared.selectTradingAccount"));
        final Tuple3<VBox, Label, ComboBox<TradeCurrency>> currencyBoxTuple = addTopLabelComboBox(
                Res.get("shared.currency"), Res.get("list.currency.select"));

        currencySelection = currencyBoxTuple.first;
        paymentGroupBox.getChildren().addAll(tradingAccountBoxTuple.first, currencySelection);

        GridPane.setRowIndex(paymentGroupBox, gridRow);
        GridPane.setColumnSpan(paymentGroupBox, 2);
        GridPane.setMargin(paymentGroupBox, new Insets(Layout.FIRST_ROW_DISTANCE, 0, 0, 0));
        gridPane.getChildren().add(paymentGroupBox);

        tradingAccountBoxTuple.first.setMinWidth(800);
        paymentAccountsComboBox = tradingAccountBoxTuple.third;
        paymentAccountsComboBox.setMinWidth(tradingAccountBoxTuple.first.getMinWidth());
        paymentAccountsComboBox.setPrefWidth(tradingAccountBoxTuple.first.getMinWidth());
        editOfferElements.add(tradingAccountBoxTuple.first);

        // we display either currencyComboBox (multi currency account) or currencyTextField (single)
        currencyComboBox = currencyBoxTuple.third;
        currencyComboBox.setMaxWidth(tradingAccountBoxTuple.first.getMinWidth() / 2);
        editOfferElements.add(currencySelection);
        currencyComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(TradeCurrency tradeCurrency) {
                return tradeCurrency.getNameAndCode();
            }

            @Override
            public TradeCurrency fromString(String s) {
                return null;
            }
        });

        final Tuple3<Label, TextField, VBox> currencyTextFieldTuple = addTopLabelTextField(gridPane, gridRow, Res.get("shared.currency"), "", 5d);
        currencyTextField = currencyTextFieldTuple.second;
        currencyTextFieldBox = currencyTextFieldTuple.third;
        currencyTextFieldBox.setVisible(false);
        editOfferElements.add(currencyTextFieldBox);

        paymentGroupBox.getChildren().add(currencyTextFieldBox);
    }

    private void addAmountPriceGroup() {
        amountTitledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 2,
                Res.get("createOffer.setAmountPrice"), Layout.COMPACT_GROUP_DISTANCE);
        GridPane.setColumnSpan(amountTitledGroupBg, 2);

        addAmountPriceFields();
        addSecondRow();
    }

    private void addOptionsGroup() {
        setDepositTitledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 1,
                Res.get("shared.advancedOptions"), Layout.COMPACT_GROUP_DISTANCE);

        advancedOptionsBox = new HBox();
        advancedOptionsBox.setSpacing(40);

        GridPane.setRowIndex(advancedOptionsBox, gridRow);
        GridPane.setColumnIndex(advancedOptionsBox, 0);
        GridPane.setHalignment(advancedOptionsBox, HPos.LEFT);
        GridPane.setMargin(advancedOptionsBox, new Insets(Layout.COMPACT_FIRST_ROW_AND_GROUP_DISTANCE, 0, 0, 0));
        gridPane.getChildren().add(advancedOptionsBox);

        Tuple2<AutoTooltipButton, VBox> buyBsqButtonBox = OfferViewUtil.createBuyBsqButtonBox(navigation, preferences);
        buyBsqButton = buyBsqButtonBox.first;
        buyBsqButton.setVisible(false);

        advancedOptionsBox.getChildren().addAll(getBuyerSecurityDepositBox(), getTradeFeeFieldsBox(), buyBsqButtonBox.second);

        Tuple2<Button, Button> tuple = add2ButtonsAfterGroup(gridPane, ++gridRow,
                Res.get("shared.nextStep"), Res.get("shared.cancel"));
        nextButton = (AutoTooltipButton) tuple.first;
        nextButton.setMaxWidth(200);
        editOfferElements.add(nextButton);
        nextButton.disableProperty().bind(model.isNextButtonDisabled);
        cancelButton1 = (AutoTooltipButton) tuple.second;
        cancelButton1.setMaxWidth(200);
        editOfferElements.add(cancelButton1);
        cancelButton1.setDefaultButton(false);
        cancelButton1.setOnAction(e -> {
            close();
            model.getDataModel().swapTradeToSavings();
        });

        nextButton.setOnAction(e -> {
            if (model.isPriceInRange()) {
                if (DevEnv.isDaoTradingActivated())
                    showFeeOption();
                else
                    onShowPayFundsScreen();
            }
        });
    }

    protected void hideOptionsGroup() {
        setDepositTitledGroupBg.setVisible(false);
        setDepositTitledGroupBg.setManaged(false);
        nextButton.setVisible(false);
        nextButton.setManaged(false);
        cancelButton1.setVisible(false);
        cancelButton1.setManaged(false);
        advancedOptionsBox.setVisible(false);
        advancedOptionsBox.setManaged(false);
    }

    private void showFeeOption() {
        boolean isPreferredFeeCurrencyBtc = model.getDataModel().isPreferredFeeCurrencyBtc();
        boolean isBsqForFeeAvailable = model.getDataModel().isBsqForFeeAvailable();
        if (!isPreferredFeeCurrencyBtc && !isBsqForFeeAvailable) {
            Coin makerFee = model.getDataModel().getMakerFee(false);
            String missingBsq = null;
            if (makerFee != null) {
                missingBsq = Res.get("popup.warning.insufficientBsqFundsForBtcFeePayment",
                        bsqFormatter.formatCoinWithCode(makerFee.subtract(model.getDataModel().getUsableBsqBalance())));

            } else if (model.getDataModel().getUsableBsqBalance().isZero()) {
                missingBsq = Res.get("popup.warning.noBsqFundsForBtcFeePayment");
            }

            if (missingBsq != null) {
                new Popup().warning(missingBsq)
                        .actionButtonText(Res.get("feeOptionWindow.useBTC"))
                        .onAction(() -> {
                            tradeFeeInBtcToggle.setSelected(true);
                            onShowPayFundsScreen();
                        })
                        .show();
            } else {
                onShowPayFundsScreen();
            }
        } else {
            onShowPayFundsScreen();
        }
    }

    private VBox getBuyerSecurityDepositBox() {
        Tuple3<HBox, InfoInputTextField, Label> tuple = getEditableValueBoxWithInfo(
                Res.get("createOffer.securityDeposit.prompt"));
        buyerSecurityDepositInfoInputTextField = tuple.second;
        buyerSecurityDepositInputTextField = buyerSecurityDepositInfoInputTextField.getInputTextField();
        buyerSecurityDepositPercentageLabel = tuple.third;
        // getEditableValueBox delivers BTC, so we overwrite it with %
        buyerSecurityDepositPercentageLabel.setText("%");

        Tuple2<Label, VBox> tradeInputBoxTuple = getTradeInputBox(tuple.first, model.getSecurityDepositLabel());
        VBox depositBox = tradeInputBoxTuple.second;
        buyerSecurityDepositLabel = tradeInputBoxTuple.first;
        depositBox.setMaxWidth(310);

        editOfferElements.add(buyerSecurityDepositInputTextField);
        editOfferElements.add(buyerSecurityDepositPercentageLabel);

        return depositBox;
    }

    private void addFundingGroup() {
        // don't increase gridRow as we removed button when this gets visible
        payFundsTitledGroupBg = addTitledGroupBg(gridPane, gridRow, 3,
                Res.get("createOffer.fundsBox.title"), Layout.COMPACT_GROUP_DISTANCE);
        payFundsTitledGroupBg.getStyleClass().add("last");
        GridPane.setColumnSpan(payFundsTitledGroupBg, 2);
        payFundsTitledGroupBg.setVisible(false);

        totalToPayTextField = addFundsTextfield(gridPane, gridRow,
                Res.get("shared.totalsNeeded"), Layout.COMPACT_FIRST_ROW_AND_GROUP_DISTANCE);
        totalToPayTextField.setVisible(false);

        qrCodeImageView = new ImageView();
        qrCodeImageView.setVisible(false);
        qrCodeImageView.setFitHeight(150);
        qrCodeImageView.setFitWidth(150);
        qrCodeImageView.getStyleClass().add("qr-code");
        Tooltip.install(qrCodeImageView, new Tooltip(Res.get("shared.openLargeQRWindow")));
        qrCodeImageView.setOnMouseClicked(e -> GUIUtil.showFeeInfoBeforeExecute(
                () -> UserThread.runAfter(
                        () -> new QRCodeWindow(getBitcoinURI()).show(),
                        200, TimeUnit.MILLISECONDS)));
        GridPane.setRowIndex(qrCodeImageView, gridRow);
        GridPane.setColumnIndex(qrCodeImageView, 1);
        GridPane.setRowSpan(qrCodeImageView, 3);
        GridPane.setValignment(qrCodeImageView, VPos.BOTTOM);
        GridPane.setMargin(qrCodeImageView, new Insets(Layout.FIRST_ROW_DISTANCE - 9, 0, 0, 10));
        gridPane.getChildren().add(qrCodeImageView);

        addressTextField = addAddressTextField(gridPane, ++gridRow,
                Res.get("shared.tradeWalletAddress"));
        addressTextField.setVisible(false);

        balanceTextField = addBalanceTextField(gridPane, ++gridRow,
                Res.get("shared.tradeWalletBalance"));
        balanceTextField.setVisible(false);

        fundingHBox = new HBox();
        fundingHBox.setVisible(false);
        fundingHBox.setManaged(false);
        fundingHBox.setSpacing(10);
        Button fundFromSavingsWalletButton = new AutoTooltipButton(Res.get("shared.fundFromSavingsWalletButton"));
        fundFromSavingsWalletButton.setDefaultButton(true);
        fundFromSavingsWalletButton.getStyleClass().add("action-button");
        fundFromSavingsWalletButton.setOnAction(e -> model.fundFromSavingsWallet());
        Label label = new AutoTooltipLabel(Res.get("shared.OR"));
        label.setPadding(new Insets(5, 0, 0, 0));
        Button fundFromExternalWalletButton = new AutoTooltipButton(Res.get("shared.fundFromExternalWalletButton"));
        fundFromExternalWalletButton.setDefaultButton(false);
        fundFromExternalWalletButton.setOnAction(e -> GUIUtil.showFeeInfoBeforeExecute(this::openWallet));
        waitingForFundsSpinner = new BusyAnimation(false);
        waitingForFundsLabel = new AutoTooltipLabel();
        waitingForFundsLabel.setPadding(new Insets(5, 0, 0, 0));

        fundingHBox.getChildren().addAll(fundFromSavingsWalletButton,
                label,
                fundFromExternalWalletButton,
                waitingForFundsSpinner,
                waitingForFundsLabel);

        GridPane.setRowIndex(fundingHBox, ++gridRow);
        GridPane.setColumnSpan(fundingHBox, 2);
        GridPane.setMargin(fundingHBox, new Insets(5, 0, 0, 0));
        gridPane.getChildren().add(fundingHBox);

        placeOfferBox = new HBox();
        placeOfferBox.setSpacing(10);
        GridPane.setRowIndex(placeOfferBox, gridRow);
        GridPane.setColumnSpan(placeOfferBox, 2);
        GridPane.setMargin(placeOfferBox, new Insets(5, 20, 0, 0));
        gridPane.getChildren().add(placeOfferBox);

        placeOfferButton = new AutoTooltipButton();
        placeOfferButton.setOnAction(e -> onPlaceOffer());
        placeOfferButton.setMinHeight(40);
        placeOfferButton.setPadding(new Insets(0, 20, 0, 20));

        placeOfferBox.getChildren().add(placeOfferButton);
        placeOfferBox.visibleProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                fundingHBox.getChildren().remove(cancelButton2);
                placeOfferBox.getChildren().add(cancelButton2);
            } else if (!fundingHBox.getChildren().contains(cancelButton2)) {
                placeOfferBox.getChildren().remove(cancelButton2);
                fundingHBox.getChildren().add(cancelButton2);
            }
        });

        cancelButton2 = new AutoTooltipButton(Res.get("shared.cancel"));

        fundingHBox.getChildren().add(cancelButton2);

        cancelButton2.setOnAction(e -> {
            String key = "CreateOfferCancelAndFunded";
            if (model.getDataModel().getIsBtcWalletFunded().get() &&
                    preferences.showAgain(key)) {
                new Popup().backgroundInfo(Res.get("createOffer.warnCancelOffer"))
                        .closeButtonText(Res.get("shared.no"))
                        .actionButtonText(Res.get("shared.yesCancel"))
                        .onAction(() -> {
                            close();
                            model.getDataModel().swapTradeToSavings();
                        })
                        .dontShowAgainId(key)
                        .show();
            } else {
                close();
                model.getDataModel().swapTradeToSavings();
            }
        });
        cancelButton2.setDefaultButton(false);
        cancelButton2.setVisible(false);
    }

    private void openWallet() {
        try {
            Utilities.openURI(URI.create(getBitcoinURI()));
        } catch (Exception ex) {
            log.warn(ex.getMessage());
            new Popup().warning(Res.get("shared.openDefaultWalletFailed")).show();
        }
    }

    @NotNull
    private String getBitcoinURI() {
        return GUIUtil.getBitcoinURI(addressTextField.getAddress(), model.getDataModel().getMissingCoin().get(),
                model.getPaymentLabel());
    }

    private void addAmountPriceFields() {
        // amountBox
        Tuple3<HBox, InputTextField, Label> amountValueCurrencyBoxTuple = getEditableValueBox(Res.get("createOffer.amount.prompt"));
        amountValueCurrencyBox = amountValueCurrencyBoxTuple.first;
        amountTextField = amountValueCurrencyBoxTuple.second;
        editOfferElements.add(amountTextField);
        amountBtcLabel = amountValueCurrencyBoxTuple.third;
        editOfferElements.add(amountBtcLabel);
        Tuple2<Label, VBox> amountInputBoxTuple = getTradeInputBox(amountValueCurrencyBox, model.getAmountDescription());
        amountDescriptionLabel = amountInputBoxTuple.first;
        editOfferElements.add(amountDescriptionLabel);
        VBox amountBox = amountInputBoxTuple.second;

        // x
        xLabel = new Label();
        xIcon = getIconForLabel(MaterialDesignIcon.CLOSE, "2em", xLabel);
        xIcon.getStyleClass().add("opaque-icon");
        xLabel.getStyleClass().add("opaque-icon-character");

        // price as percent
        Tuple3<HBox, InfoInputTextField, Label> priceAsPercentageTuple = getEditableValueBoxWithInfo(Res.get("createOffer.price.prompt"));

        priceAsPercentageValueCurrencyBox = priceAsPercentageTuple.first;
        marketBasedPriceInfoInputTextField = priceAsPercentageTuple.second;
        marketBasedPriceTextField = marketBasedPriceInfoInputTextField.getInputTextField();
        editOfferElements.add(marketBasedPriceTextField);
        marketBasedPriceLabel = priceAsPercentageTuple.third;
        editOfferElements.add(marketBasedPriceLabel);
        Tuple2<Label, VBox> priceAsPercentageInputBoxTuple = getTradeInputBox(priceAsPercentageValueCurrencyBox,
                model.getPercentagePriceDescription());
        percentagePriceDescriptionLabel = priceAsPercentageInputBoxTuple.first;

        getSmallIconForLabel(MaterialDesignIcon.CHART_LINE, percentagePriceDescriptionLabel, "small-icon-label");

        percentagePriceBox = priceAsPercentageInputBoxTuple.second;

        // =
        resultLabel = new AutoTooltipLabel("=");
        resultLabel.getStyleClass().add("opaque-icon-character");

        // volume
        Tuple3<HBox, InfoInputTextField, Label> volumeValueCurrencyBoxTuple = getEditableValueBoxWithInfo(Res.get("createOffer.volume.prompt"));
        volumeValueCurrencyBox = volumeValueCurrencyBoxTuple.first;
        volumeInfoInputTextField = volumeValueCurrencyBoxTuple.second;
        volumeTextField = volumeInfoInputTextField.getInputTextField();
        editOfferElements.add(volumeTextField);
        volumeCurrencyLabel = volumeValueCurrencyBoxTuple.third;
        editOfferElements.add(volumeCurrencyLabel);
        Tuple2<Label, VBox> volumeInputBoxTuple = getTradeInputBox(volumeValueCurrencyBox, model.volumeDescriptionLabel.get());
        volumeDescriptionLabel = volumeInputBoxTuple.first;
        editOfferElements.add(volumeDescriptionLabel);
        VBox volumeBox = volumeInputBoxTuple.second;

        firstRowHBox = new HBox();
        firstRowHBox.setSpacing(5);
        firstRowHBox.setAlignment(Pos.CENTER_LEFT);
        firstRowHBox.getChildren().addAll(amountBox, xLabel, percentagePriceBox, resultLabel, volumeBox);
        GridPane.setColumnSpan(firstRowHBox, 2);
        GridPane.setRowIndex(firstRowHBox, gridRow);
        GridPane.setMargin(firstRowHBox, new Insets(Layout.COMPACT_FIRST_ROW_AND_GROUP_DISTANCE, 10, 0, 0));
        gridPane.getChildren().add(firstRowHBox);
    }

    private void updatePriceToggleButtons(boolean fixedPriceSelected) {
        int marketPriceAvailable = model.marketPriceAvailableProperty.get();
        fixedPriceSelected = fixedPriceSelected || (marketPriceAvailable == 0);

        if (marketPriceAvailable == 1) {
            model.getDataModel().setUseMarketBasedPrice(!fixedPriceSelected);
        }

        percentagePriceBox.setDisable(fixedPriceSelected);
        fixedPriceBox.setDisable(!fixedPriceSelected);

        if (fixedPriceSelected) {
            firstRowHBox.getChildren().remove(percentagePriceBox);
            secondRowHBox.getChildren().remove(fixedPriceBox);

            if (!firstRowHBox.getChildren().contains(fixedPriceBox))
                firstRowHBox.getChildren().add(2, fixedPriceBox);
            if (!secondRowHBox.getChildren().contains(percentagePriceBox))
                secondRowHBox.getChildren().add(2, percentagePriceBox);
        } else {
            firstRowHBox.getChildren().remove(fixedPriceBox);
            secondRowHBox.getChildren().remove(percentagePriceBox);

            if (!firstRowHBox.getChildren().contains(percentagePriceBox))
                firstRowHBox.getChildren().add(2, percentagePriceBox);
            if (!secondRowHBox.getChildren().contains(fixedPriceBox))
                secondRowHBox.getChildren().add(2, fixedPriceBox);
        }

        triggerPriceVBox.setVisible(!fixedPriceSelected);
        model.onFixPriceToggleChange(fixedPriceSelected);
    }

    private void addSecondRow() {
        // price as fiat
        Tuple3<HBox, InputTextField, Label> priceValueCurrencyBoxTuple = getEditableValueBox(
                Res.get("createOffer.price.prompt"));
        priceValueCurrencyBox = priceValueCurrencyBoxTuple.first;
        fixedPriceTextField = priceValueCurrencyBoxTuple.second;
        editOfferElements.add(fixedPriceTextField);
        priceCurrencyLabel = priceValueCurrencyBoxTuple.third;
        editOfferElements.add(priceCurrencyLabel);
        Tuple2<Label, VBox> priceInputBoxTuple = getTradeInputBox(priceValueCurrencyBox, "");
        priceDescriptionLabel = priceInputBoxTuple.first;

        getSmallIconForLabel(MaterialDesignIcon.LOCK, priceDescriptionLabel, "small-icon-label");

        editOfferElements.add(priceDescriptionLabel);
        fixedPriceBox = priceInputBoxTuple.second;

        marketBasedPriceTextField.setPromptText(Res.get("shared.enterPercentageValue"));
        marketBasedPriceLabel.setText("%");

        Tuple3<HBox, InputTextField, Label> amountValueCurrencyBoxTuple = getEditableValueBox(Res.get("createOffer.amount.prompt"));
        minAmountValueCurrencyBox = amountValueCurrencyBoxTuple.first;
        minAmountTextField = amountValueCurrencyBoxTuple.second;
        editOfferElements.add(minAmountTextField);
        minAmountBtcLabel = amountValueCurrencyBoxTuple.third;
        editOfferElements.add(minAmountBtcLabel);

        Tuple2<Label, VBox> amountInputBoxTuple = getTradeInputBox(minAmountValueCurrencyBox, Res.get("createOffer.amountPriceBox.minAmountDescription"));

        fakeXLabel = new Label();
        fakeXIcon = getIconForLabel(MaterialDesignIcon.CLOSE, "2em", fakeXLabel);
        fakeXLabel.getStyleClass().add("opaque-icon-character");
        fakeXLabel.setVisible(false); // we just use it to get the same layout as the upper row

        // Fixed/Percentage toggle
        priceTypeToggleButton = getIconButton(MaterialDesignIcon.SWAP_VERTICAL);
        editOfferElements.add(priceTypeToggleButton);
        HBox.setMargin(priceTypeToggleButton, new Insets(16, 1.5, 0, 0));
        priceTypeToggleButton.setOnAction((actionEvent) ->
                updatePriceToggleButtons(model.getDataModel().getUseMarketBasedPrice().getValue()));

        // triggerPrice
        Tuple3<HBox, InfoInputTextField, Label> triggerPriceTuple3 = getEditableValueBoxWithInfo(Res.get("createOffer.triggerPrice.prompt"));
        triggerPriceHBox = triggerPriceTuple3.first;
        triggerPriceInfoInputTextField = triggerPriceTuple3.second;
        editOfferElements.add(triggerPriceInfoInputTextField);
        triggerPriceInputTextField = triggerPriceInfoInputTextField.getInputTextField();
        triggerPriceCurrencyLabel = triggerPriceTuple3.third;
        editOfferElements.add(triggerPriceCurrencyLabel);
        Tuple2<Label, VBox> triggerPriceTuple2 = getTradeInputBox(triggerPriceHBox, model.getTriggerPriceDescriptionLabel());
        triggerPriceDescriptionLabel = triggerPriceTuple2.first;
        triggerPriceDescriptionLabel.setPrefWidth(290);
        triggerPriceVBox = triggerPriceTuple2.second;

        secondRowHBox = new HBox();
        secondRowHBox.setSpacing(5);
        secondRowHBox.setAlignment(Pos.CENTER_LEFT);
        secondRowHBox.getChildren().addAll(amountInputBoxTuple.second, fakeXLabel, fixedPriceBox, priceTypeToggleButton, triggerPriceVBox);
        GridPane.setColumnSpan(secondRowHBox, 2);
        GridPane.setRowIndex(secondRowHBox, ++gridRow);
        GridPane.setColumnIndex(secondRowHBox, 0);
        GridPane.setMargin(secondRowHBox, new Insets(0, 10, 10, 0));
        gridPane.getChildren().add(secondRowHBox);
    }

    private VBox getTradeFeeFieldsBox() {
        tradeFeeInBtcLabel = new Label();
        tradeFeeInBtcLabel.setMouseTransparent(true);
        tradeFeeInBtcLabel.setId("trade-fee-textfield");

        tradeFeeInBsqLabel = new Label();
        tradeFeeInBsqLabel.setMouseTransparent(true);
        tradeFeeInBsqLabel.setId("trade-fee-textfield");

        VBox vBox = new VBox();
        vBox.setSpacing(6);
        vBox.setMaxWidth(300);
        vBox.setAlignment(DevEnv.isDaoActivated() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        vBox.getChildren().addAll(tradeFeeInBtcLabel, tradeFeeInBsqLabel);

        tradeFeeInBtcToggle = new AutoTooltipSlideToggleButton();
        tradeFeeInBtcToggle.setText("BTC");
        tradeFeeInBtcToggle.setVisible(false);
        tradeFeeInBtcToggle.setPadding(new Insets(-8, 5, -10, 5));

        tradeFeeInBsqToggle = new AutoTooltipSlideToggleButton();
        tradeFeeInBsqToggle.setText("BSQ");
        tradeFeeInBsqToggle.setVisible(false);
        tradeFeeInBsqToggle.setPadding(new Insets(-9, 5, -9, 5));

        VBox tradeFeeToggleButtonBox = new VBox();
        tradeFeeToggleButtonBox.getChildren().addAll(tradeFeeInBtcToggle, tradeFeeInBsqToggle);

        HBox hBox = new HBox();
        hBox.getChildren().addAll(vBox, tradeFeeToggleButtonBox);
        hBox.setMinHeight(47);
        hBox.setMaxHeight(hBox.getMinHeight());
        HBox.setHgrow(vBox, Priority.ALWAYS);
        HBox.setHgrow(tradeFeeToggleButtonBox, Priority.NEVER);

        final Tuple2<Label, VBox> tradeInputBox = getTradeInputBox(hBox, Res.get("createOffer.tradeFee.descriptionBSQEnabled"));

        tradeFeeDescriptionLabel = tradeInputBox.first;

        return tradeInputBox.second;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PayInfo
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GridPane createInfoPopover() {
        GridPane infoGridPane = new GridPane();
        infoGridPane.setHgap(5);
        infoGridPane.setVgap(5);
        infoGridPane.setPadding(new Insets(10, 10, 10, 10));

        int i = 0;
        if (model.isSellOffer())
            addPayInfoEntry(infoGridPane, i++, Res.getWithCol("shared.tradeAmount"), model.tradeAmount.get());

        addPayInfoEntry(infoGridPane, i++, Res.getWithCol("shared.yourSecurityDeposit"), model.getSecurityDepositInfo());
        addPayInfoEntry(infoGridPane, i++, Res.get("createOffer.fundsBox.offerFee"), model.getTradeFee());
        addPayInfoEntry(infoGridPane, i++, Res.get("createOffer.fundsBox.networkFee"), model.getTxFee());
        Separator separator = new Separator();
        separator.setOrientation(Orientation.HORIZONTAL);
        separator.getStyleClass().add("offer-separator");
        GridPane.setConstraints(separator, 1, i++);
        infoGridPane.getChildren().add(separator);
        addPayInfoEntry(infoGridPane, i, Res.getWithCol("shared.total"), model.getTotalToPayInfo());
        return infoGridPane;
    }

    private void addPayInfoEntry(GridPane infoGridPane, int row, String labelText, String value) {
        Label label = new AutoTooltipLabel(labelText);
        TextField textField = new TextField(value);
        textField.setMinWidth(500);
        textField.setEditable(false);
        textField.setFocusTraversable(false);
        textField.setId("payment-info");
        GridPane.setConstraints(label, 0, row, 1, 1, HPos.RIGHT, VPos.CENTER);
        GridPane.setConstraints(textField, 1, row);
        infoGridPane.getChildren().addAll(label, textField);
    }
}
