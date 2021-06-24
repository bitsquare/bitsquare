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

package bisq.desktop.main.offer.takeoffer;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.FxmlView;
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
import bisq.desktop.main.dao.DaoView;
import bisq.desktop.main.dao.wallet.BsqWalletView;
import bisq.desktop.main.dao.wallet.receive.BsqReceiveView;
import bisq.desktop.main.funds.FundsView;
import bisq.desktop.main.funds.withdrawal.WithdrawalView;
import bisq.desktop.main.offer.OfferView;
import bisq.desktop.main.offer.OfferViewUtil;
import bisq.desktop.main.overlays.notifications.Notification;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.GenericMessageWindow;
import bisq.desktop.main.overlays.windows.OfferDetailsWindow;
import bisq.desktop.main.overlays.windows.QRCodeWindow;
import bisq.desktop.main.portfolio.PortfolioView;
import bisq.desktop.main.portfolio.pendingtrades.PendingTradesView;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.Transitions;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.payment.FasterPaymentsAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.user.DontShowAgainLookup;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;
import bisq.common.util.Tuple4;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import net.glxn.qrgen.QRCode;
import net.glxn.qrgen.image.ImageType;

import javax.inject.Inject;
import javax.inject.Named;

import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;

import com.jfoenix.controls.JFXTextField;

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

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;

import java.net.URI;

import java.io.ByteArrayInputStream;

import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.NotNull;

import static bisq.desktop.util.FormBuilder.*;
import static javafx.beans.binding.Bindings.createStringBinding;

@FxmlView
public class TakeOfferView extends ActivatableViewAndModel<AnchorPane, TakeOfferViewModel> {
    private final Navigation navigation;
    private final CoinFormatter formatter;
    private final BsqFormatter bsqFormatter;
    private final OfferDetailsWindow offerDetailsWindow;
    private final Transitions transitions;

    private ScrollPane scrollPane;
    private GridPane gridPane;
    private TitledGroupBg payFundsTitledGroupBg, paymentAccountTitledGroupBg, advancedOptionsGroup;
    private VBox priceAsPercentageInputBox, amountRangeBox;
    private HBox fundingHBox, amountValueCurrencyBox, priceValueCurrencyBox, volumeValueCurrencyBox,
            priceAsPercentageValueCurrencyBox, minAmountValueCurrencyBox, advancedOptionsBox,
            takeOfferBox, buttonBox, firstRowHBox;
    private ComboBox<PaymentAccount> paymentAccountsComboBox;
    private Label amountDescriptionLabel,
            paymentMethodLabel,
            priceCurrencyLabel, priceAsPercentageLabel,
            volumeCurrencyLabel, priceDescriptionLabel, volumeDescriptionLabel,
            waitingForFundsLabel, offerAvailabilityLabel, priceAsPercentageDescription,
            tradeFeeDescriptionLabel, resultLabel, tradeFeeInBtcLabel, tradeFeeInBsqLabel, xLabel,
            fakeXLabel;
    private InputTextField amountTextField;
    private TextField paymentMethodTextField, currencyTextField, priceTextField, priceAsPercentageTextField,
            volumeTextField, amountRangeTextField;
    private FundsTextField totalToPayTextField;
    private AddressTextField addressTextField;
    private BalanceTextField balanceTextField;
    private Text xIcon, fakeXIcon;
    private Button nextButton, cancelButton1, cancelButton2;
    private AutoTooltipButton takeOfferButton;
    private ImageView qrCodeImageView;
    private BusyAnimation waitingForFundsBusyAnimation, offerAvailabilityBusyAnimation;
    private Notification walletFundedNotification;
    private OfferView.CloseHandler closeHandler;
    private Subscription balanceSubscription,
            showTransactionPublishedScreenSubscription, showWarningInvalidBtcDecimalPlacesSubscription,
            isWaitingForFundsSubscription, offerWarningSubscription, errorMessageSubscription,
            isOfferAvailableSubscription;

    private int gridRow = 0;
    private boolean offerDetailsWindowDisplayed, clearXchangeWarningDisplayed, fasterPaymentsWarningDisplayed,
            takeOfferFromUnsignedAccountWarningDisplayed, cashByMailWarningDisplayed;
    private SimpleBooleanProperty errorPopupDisplayed;
    private ChangeListener<Boolean> amountFocusedListener, getShowWalletFundedNotificationListener;

    private InfoInputTextField volumeInfoTextField;
    private AutoTooltipSlideToggleButton tradeFeeInBtcToggle, tradeFeeInBsqToggle;
    private ChangeListener<Boolean> tradeFeeInBtcToggleListener, tradeFeeInBsqToggleListener,
            tradeFeeVisibleListener;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private TakeOfferView(TakeOfferViewModel model,
                          Navigation navigation,
                          @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter formatter,
                          BsqFormatter bsqFormatter,
                          OfferDetailsWindow offerDetailsWindow,
                          Transitions transitions) {
        super(model);

        this.navigation = navigation;
        this.formatter = formatter;
        this.bsqFormatter = bsqFormatter;
        this.offerDetailsWindow = offerDetailsWindow;
        this.transitions = transitions;
    }

    @Override
    protected void initialize() {
        addScrollPane();
        addGridPane();
        addPaymentGroup();
        addAmountPriceGroup();
        addOptionsGroup();

        addButtons();
        addOfferAvailabilityLabel();
        addFundingGroup();

        balanceTextField.setFormatter(model.getBtcFormatter());

        amountFocusedListener = (o, oldValue, newValue) -> {
            model.onFocusOutAmountTextField(oldValue, newValue, amountTextField.getText());
            amountTextField.setText(model.amount.get());
        };

        getShowWalletFundedNotificationListener = (observable, oldValue, newValue) -> {
            if (newValue) {
                Notification walletFundedNotification = new Notification()
                        .headLine(Res.get("notification.walletUpdate.headline"))
                        .notification(Res.get("notification.walletUpdate.msg", formatter.formatCoinWithCode(model.dataModel.getTotalToPayAsCoin().get())))
                        .autoClose();

                walletFundedNotification.show();
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
            }
        };

        GUIUtil.focusWhenAddedToScene(amountTextField);
    }

    private void setIsCurrencyForMakerFeeBtc(boolean isCurrencyForMakerFeeBtc) {
        model.setIsCurrencyForTakerFeeBtc(isCurrencyForMakerFeeBtc);
        if (DevEnv.isDaoActivated()) {
            tradeFeeInBtcLabel.setOpacity(isCurrencyForMakerFeeBtc ? 1 : 0.3);
            tradeFeeInBsqLabel.setOpacity(isCurrencyForMakerFeeBtc ? 0.3 : 1);
        }
    }

    @Override
    protected void activate() {
        addBindings();
        addSubscriptions();
        addListeners();

        if (offerAvailabilityBusyAnimation != null && !model.showPayFundsScreenDisplayed.get()) {
            // temporarily disabled due to high CPU usage (per issue #4649)
            //    offerAvailabilityBusyAnimation.play();
            offerAvailabilityLabel.setVisible(true);
            offerAvailabilityLabel.setManaged(true);
        } else {
            offerAvailabilityLabel.setVisible(false);
            offerAvailabilityLabel.setManaged(false);
        }

        if (waitingForFundsBusyAnimation != null && model.isWaitingForFunds.get()) {
            // temporarily disabled due to high CPU usage (per issue #4649)
            //    waitingForFundsBusyAnimation.play();
            waitingForFundsLabel.setVisible(true);
            waitingForFundsLabel.setManaged(true);
        } else {
            waitingForFundsLabel.setVisible(false);
            waitingForFundsLabel.setManaged(false);
        }

        String currencyCode = model.dataModel.getCurrencyCode();
        volumeCurrencyLabel.setText(currencyCode);
        priceDescriptionLabel.setText(CurrencyUtil.getPriceWithCurrencyCode(currencyCode));
        volumeDescriptionLabel.setText(model.volumeDescriptionLabel.get());

        PaymentAccount lastPaymentAccount = model.getLastSelectedPaymentAccount();

        if (model.getPossiblePaymentAccounts().size() > 1) {
            new Popup().headLine(Res.get("popup.info.multiplePaymentAccounts.headline"))
                    .information(Res.get("popup.info.multiplePaymentAccounts.msg"))
                    .dontShowAgainId("MultiplePaymentAccountsAvailableWarning")
                    .show();

            paymentAccountsComboBox.setItems(model.getPossiblePaymentAccounts());
            paymentAccountsComboBox.getSelectionModel().select(lastPaymentAccount);
            model.onPaymentAccountSelected(lastPaymentAccount);
            paymentAccountTitledGroupBg.setText(Res.get("shared.selectTradingAccount"));
        }

        balanceTextField.setTargetAmount(model.dataModel.getTotalToPayAsCoin().get());

        maybeShowTakeOfferFromUnsignedAccountWarning(model.dataModel.getOffer());
        maybeShowClearXchangeWarning(lastPaymentAccount);
        maybeShowFasterPaymentsWarning(lastPaymentAccount);
        maybeShowCashByMailWarning(lastPaymentAccount, model.dataModel.getOffer());

        if (!DevEnv.isDaoActivated() && !model.isRange()) {
            nextButton.setVisible(false);
            cancelButton1.setVisible(false);
            if (model.isOfferAvailable.get())
                showNextStepAfterAmountIsSet();
        }

        boolean currencyForMakerFeeBtc = model.dataModel.isCurrencyForTakerFeeBtc();
        tradeFeeInBtcToggle.setSelected(currencyForMakerFeeBtc);
        tradeFeeInBsqToggle.setSelected(!currencyForMakerFeeBtc);

        if (!DevEnv.isDaoActivated()) {
            tradeFeeInBtcToggle.setVisible(false);
            tradeFeeInBtcToggle.setManaged(false);
            tradeFeeInBsqToggle.setVisible(false);
            tradeFeeInBsqToggle.setManaged(false);
        }
    }

    @Override
    protected void deactivate() {
        removeBindings();
        removeSubscriptions();
        removeListeners();

        if (offerAvailabilityBusyAnimation != null)
            offerAvailabilityBusyAnimation.stop();

        if (waitingForFundsBusyAnimation != null)
            waitingForFundsBusyAnimation.stop();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initWithData(Offer offer) {
        model.initWithData(offer);
        priceAsPercentageInputBox.setVisible(offer.isUseMarketBasedPrice());

        if (model.getOffer().getDirection() == OfferPayload.Direction.SELL) {
            takeOfferButton.setId("buy-button-big");
            takeOfferButton.updateText(Res.get("takeOffer.takeOfferButton", Res.get("shared.buy")));
            nextButton.setId("buy-button");
        } else {
            takeOfferButton.setId("sell-button-big");
            nextButton.setId("sell-button");
            takeOfferButton.updateText(Res.get("takeOffer.takeOfferButton", Res.get("shared.sell")));
        }
        priceAsPercentageDescription.setText(model.getPercentagePriceDescription());

        boolean showComboBox = model.getPossiblePaymentAccounts().size() > 1;
        paymentAccountsComboBox.setVisible(showComboBox);
        paymentAccountsComboBox.setManaged(showComboBox);
        paymentAccountsComboBox.setMouseTransparent(!showComboBox);
        paymentMethodTextField.setVisible(!showComboBox);
        paymentMethodTextField.setManaged(!showComboBox);
        paymentMethodLabel.setVisible(!showComboBox);
        paymentMethodLabel.setManaged(!showComboBox);

        if (!showComboBox) {
            paymentMethodTextField.setText(model.getPossiblePaymentAccounts().get(0).getAccountName());
        }

        currencyTextField.setText(model.dataModel.getCurrencyNameAndCode());
        amountDescriptionLabel.setText(model.getAmountDescription());

        if (model.isRange()) {
            amountRangeTextField.setText(model.getAmountRange());
            amountRangeBox.setVisible(true);
        } else {
            amountTextField.setDisable(true);
        }

        priceTextField.setText(model.getPrice());
        priceAsPercentageTextField.setText(model.marketPriceMargin);
        addressTextField.setPaymentLabel(model.getPaymentLabel());
        addressTextField.setAddress(model.dataModel.getAddressEntry().getAddressString());

        if (CurrencyUtil.isFiatCurrency(offer.getCurrencyCode())) {
            Label popOverLabel = OfferViewUtil.createPopOverLabel(Res.get("offerbook.info.roundedFiatVolume"));
            volumeInfoTextField.setContentForPrivacyPopOver(popOverLabel);
        }

        if (offer.getPrice() == null)
            new Popup().warning(Res.get("takeOffer.noPriceFeedAvailable"))
                    .onClose(() -> close(false))
                    .show();
    }

    public void setCloseHandler(OfferView.CloseHandler closeHandler) {
        this.closeHandler = closeHandler;
    }

    // Called from parent as the view does not get notified when the tab is closed
    public void onClose() {
        Coin balance = model.dataModel.getBalance().get();
        if (balance != null && balance.isPositive() && !model.takeOfferCompleted.get() && !DevEnv.isDevMode()) {
            model.dataModel.swapTradeToSavings();
        }
    }

    public void onTabSelected(boolean isSelected) {
        model.dataModel.onTabSelected(isSelected);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onTakeOffer() {
        if (!model.dataModel.canTakeOffer()) {
            return;
        }

        if (!model.dataModel.isTakerFeeValid()) {
            showInsufficientBsqFundsForBtcFeePaymentPopup();
            return;
        }

        if (DevEnv.isDevMode()) {
            balanceSubscription.unsubscribe();
            model.onTakeOffer(() -> {
            });
            return;
        }

        offerDetailsWindow.onTakeOffer(() ->
                model.onTakeOffer(() -> {
                    offerDetailsWindow.hide();
                    offerDetailsWindowDisplayed = false;
                })
        ).show(model.getOffer(),
                model.dataModel.getAmount().get(),
                model.dataModel.tradePrice);

        offerDetailsWindowDisplayed = true;
    }

    private void onShowPayFundsScreen() {
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        nextButton.setVisible(false);
        nextButton.setManaged(false);
        nextButton.setOnAction(null);
        cancelButton1.setVisible(false);
        cancelButton1.setManaged(false);
        cancelButton1.setOnAction(null);
        offerAvailabilityBusyAnimation.stop();
        offerAvailabilityBusyAnimation.setVisible(false);
        offerAvailabilityBusyAnimation.setManaged(false);
        offerAvailabilityLabel.setVisible(false);
        offerAvailabilityLabel.setManaged(false);

        tradeFeeInBtcToggle.setMouseTransparent(true);
        tradeFeeInBsqToggle.setMouseTransparent(true);

        int delay = 500;
        int diff = 100;

        transitions.fadeOutAndRemove(advancedOptionsGroup, delay, (event) -> {
        });
        delay -= diff;
        transitions.fadeOutAndRemove(advancedOptionsBox, delay);

        model.onShowPayFundsScreen();

        if (DevEnv.isDaoActivated()) {
            paymentAccountsComboBox.setMouseTransparent(true);
            paymentAccountsComboBox.setDisable(true);
            paymentAccountsComboBox.setFocusTraversable(false);
        }

        amountTextField.setMouseTransparent(true);
        amountTextField.setDisable(false);
        amountTextField.setFocusTraversable(false);

        amountRangeTextField.setMouseTransparent(true);
        amountRangeTextField.setDisable(false);
        amountRangeTextField.setFocusTraversable(false);

        priceTextField.setMouseTransparent(true);
        priceTextField.setDisable(false);
        priceTextField.setFocusTraversable(false);

        priceAsPercentageTextField.setMouseTransparent(true);
        priceAsPercentageTextField.setDisable(false);
        priceAsPercentageTextField.setFocusTraversable(false);

        volumeTextField.setMouseTransparent(true);
        volumeTextField.setDisable(false);
        volumeTextField.setFocusTraversable(false);

        updateOfferElementsStyle();

        balanceTextField.setTargetAmount(model.dataModel.getTotalToPayAsCoin().get());

        if (!DevEnv.isDevMode()) {
            String key = "securityDepositInfo";
            new Popup().backgroundInfo(Res.get("popup.info.securityDepositInfo"))
                    .actionButtonText(Res.get("shared.faq"))
                    .onAction(() -> GUIUtil.openWebPage("https://bisq.wiki/Frequently_asked_questions#Why_does_Bisq_require_a_security_deposit_in_BTC.3F"))
                    .useIUnderstandButton()
                    .dontShowAgainId(key)
                    .show();


            String tradeAmountText = model.isSeller() ? Res.get("takeOffer.takeOfferFundWalletInfo.tradeAmount", model.getTradeAmount()) : "";
            String message = Res.get("takeOffer.takeOfferFundWalletInfo.msg",
                    model.getTotalToPayInfo(),
                    tradeAmountText,
                    model.getSecurityDepositInfo(),
                    model.getTradeFee(),
                    model.getTxFee()
            );
            key = "takeOfferFundWalletInfo";
            new Popup().headLine(Res.get("takeOffer.takeOfferFundWalletInfo.headline"))
                    .instruction(message)
                    .dontShowAgainId(key)
                    .show();
        }

        cancelButton2.setVisible(true);

        // temporarily disabled due to high CPU usage (per issue #4649)
        //waitingForFundsBusyAnimation.play();

        payFundsTitledGroupBg.setVisible(true);
        totalToPayTextField.setVisible(true);
        addressTextField.setVisible(true);
        qrCodeImageView.setVisible(true);
        balanceTextField.setVisible(true);

        totalToPayTextField.setFundsStructure(Res.get("takeOffer.fundsBox.fundsStructure",
                model.getSecurityDepositWithCode(), model.getTakerFeePercentage(), model.getTxFeePercentage()));
        totalToPayTextField.setContentForInfoPopOver(createInfoPopover());

        if (model.dataModel.getIsBtcWalletFunded().get()) {
            if (walletFundedNotification == null) {
                walletFundedNotification = new Notification()
                        .headLine(Res.get("notification.walletUpdate.headline"))
                        .notification(Res.get("notification.takeOffer.walletUpdate.msg", formatter.formatCoinWithCode(model.dataModel.getTotalToPayAsCoin().get())))
                        .autoClose();
                walletFundedNotification.show();
            }
        }

        final byte[] imageBytes = QRCode
                .from(getBitcoinURI())
                .withSize(98, 98) // code has 41 elements 8 px is border with 98 we get double scale and min. border
                .to(ImageType.PNG)
                .stream()
                .toByteArray();
        Image qrImage = new Image(new ByteArrayInputStream(imageBytes));
        qrCodeImageView.setImage(qrImage);
    }

    private void updateOfferElementsStyle() {
        GridPane.setColumnSpan(firstRowHBox, 1);

        final String activeInputStyle = "input-with-border";
        final String readOnlyInputStyle = "input-with-border-readonly";
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

        resultLabel.getStyleClass().add("small");
        xLabel.getStyleClass().add("small");
        xIcon.setStyle(String.format("-fx-font-family: %s; -fx-font-size: %s;", MaterialDesignIcon.CLOSE.fontFamily(), "1em"));
        fakeXIcon.setStyle(String.format("-fx-font-family: %s; -fx-font-size: %s;", MaterialDesignIcon.CLOSE.fontFamily(), "1em"));
        fakeXLabel.getStyleClass().add("small");
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Navigation
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void close() {
        close(true);
    }

    private void close(boolean removeOffer) {
        model.dataModel.onClose(removeOffer);
        if (closeHandler != null)
            closeHandler.close();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bindings, Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addBindings() {
        amountTextField.textProperty().bindBidirectional(model.amount);
        volumeTextField.textProperty().bindBidirectional(model.volume);
        totalToPayTextField.textProperty().bind(model.totalToPay);
        addressTextField.amountAsCoinProperty().bind(model.dataModel.getMissingCoin());
        amountTextField.validationResultProperty().bind(model.amountValidationResult);
        priceCurrencyLabel.textProperty().bind(createStringBinding(() -> CurrencyUtil.getCounterCurrency(model.dataModel.getCurrencyCode())));
        priceAsPercentageLabel.prefWidthProperty().bind(priceCurrencyLabel.widthProperty());
        nextButton.disableProperty().bind(model.isNextButtonDisabled);
        tradeFeeInBtcLabel.textProperty().bind(model.tradeFeeInBtcWithFiat);
        tradeFeeInBsqLabel.textProperty().bind(model.tradeFeeInBsqWithFiat);
        tradeFeeDescriptionLabel.textProperty().bind(model.tradeFeeDescription);
        tradeFeeInBtcLabel.visibleProperty().bind(model.isTradeFeeVisible);
        tradeFeeInBsqLabel.visibleProperty().bind(model.isTradeFeeVisible);
        tradeFeeDescriptionLabel.visibleProperty().bind(model.isTradeFeeVisible);
        tradeFeeDescriptionLabel.managedProperty().bind(tradeFeeDescriptionLabel.visibleProperty());

        // funding
        fundingHBox.visibleProperty().bind(model.dataModel.getIsBtcWalletFunded().not().and(model.showPayFundsScreenDisplayed));
        fundingHBox.managedProperty().bind(model.dataModel.getIsBtcWalletFunded().not().and(model.showPayFundsScreenDisplayed));
        waitingForFundsLabel.textProperty().bind(model.spinnerInfoText);
        takeOfferBox.visibleProperty().bind(model.dataModel.getIsBtcWalletFunded().and(model.showPayFundsScreenDisplayed));
        takeOfferBox.managedProperty().bind(model.dataModel.getIsBtcWalletFunded().and(model.showPayFundsScreenDisplayed));
        takeOfferButton.disableProperty().bind(model.isTakeOfferButtonDisabled);
    }

    private void removeBindings() {
        amountTextField.textProperty().unbindBidirectional(model.amount);
        volumeTextField.textProperty().unbindBidirectional(model.volume);
        totalToPayTextField.textProperty().unbind();
        addressTextField.amountAsCoinProperty().unbind();
        amountTextField.validationResultProperty().unbind();
        priceCurrencyLabel.textProperty().unbind();
        priceAsPercentageLabel.prefWidthProperty().unbind();
        nextButton.disableProperty().unbind();
        tradeFeeInBtcLabel.textProperty().unbind();
        tradeFeeInBsqLabel.textProperty().unbind();
        tradeFeeDescriptionLabel.textProperty().unbind();
        tradeFeeInBtcLabel.visibleProperty().unbind();
        tradeFeeInBsqLabel.visibleProperty().unbind();
        tradeFeeDescriptionLabel.visibleProperty().unbind();
        tradeFeeDescriptionLabel.managedProperty().unbind();

        // funding
        fundingHBox.visibleProperty().unbind();
        fundingHBox.managedProperty().unbind();
        waitingForFundsLabel.textProperty().unbind();
        takeOfferBox.visibleProperty().unbind();
        takeOfferBox.managedProperty().unbind();
        takeOfferButton.disableProperty().unbind();
    }

    private void addSubscriptions() {
        errorPopupDisplayed = new SimpleBooleanProperty();
        offerWarningSubscription = EasyBind.subscribe(model.offerWarning, newValue -> {
            if (newValue != null) {
                if (offerDetailsWindowDisplayed)
                    offerDetailsWindow.hide();

                UserThread.runAfter(() -> new Popup().warning(newValue + "\n\n" +
                        Res.get("takeOffer.alreadyPaidInFunds"))
                        .actionButtonTextWithGoTo("navigation.funds.availableForWithdrawal")
                        .onAction(() -> {
                            errorPopupDisplayed.set(true);
                            model.resetOfferWarning();
                            close();
                            navigation.navigateTo(MainView.class, FundsView.class, WithdrawalView.class);
                        })
                        .onClose(() -> {
                            errorPopupDisplayed.set(true);
                            model.resetOfferWarning();
                            close();
                        })
                        .show(), 100, TimeUnit.MILLISECONDS);
            }
        });

        errorMessageSubscription = EasyBind.subscribe(model.errorMessage, newValue -> {
            if (newValue != null) {
                new Popup().error(Res.get("takeOffer.error.message", model.errorMessage.get()) +
                        Res.get("popup.error.tryRestart"))
                        .onClose(() -> {
                            errorPopupDisplayed.set(true);
                            model.resetErrorMessage();
                            close();
                        })
                        .show();
            }
        });

        isOfferAvailableSubscription = EasyBind.subscribe(model.isOfferAvailable, isOfferAvailable -> {
            if (isOfferAvailable) {
                offerAvailabilityBusyAnimation.stop();
                offerAvailabilityBusyAnimation.setVisible(false);
                if (!DevEnv.isDaoActivated() && !model.isRange() && !model.showPayFundsScreenDisplayed.get())
                    showNextStepAfterAmountIsSet();
            }

            offerAvailabilityLabel.setVisible(!isOfferAvailable);
            offerAvailabilityLabel.setManaged(!isOfferAvailable);
        });

        isWaitingForFundsSubscription = EasyBind.subscribe(model.isWaitingForFunds, isWaitingForFunds -> {
            // temporarily disabled due to high CPU usage (per issue #4649)
            //  waitingForFundsBusyAnimation.play();
            waitingForFundsLabel.setVisible(isWaitingForFunds);
            waitingForFundsLabel.setManaged(isWaitingForFunds);
        });

        showWarningInvalidBtcDecimalPlacesSubscription = EasyBind.subscribe(model.showWarningInvalidBtcDecimalPlaces, newValue -> {
            if (newValue) {
                new Popup().warning(Res.get("takeOffer.amountPriceBox.warning.invalidBtcDecimalPlaces")).show();
                model.showWarningInvalidBtcDecimalPlaces.set(false);
            }
        });

        showTransactionPublishedScreenSubscription = EasyBind.subscribe(model.showTransactionPublishedScreen, newValue -> {
            if (newValue && DevEnv.isDevMode()) {
                close();
            } else if (newValue && model.getTrade() != null && !model.getTrade().hasFailed()) {
                String key = "takeOfferSuccessInfo";
                if (DontShowAgainLookup.showAgain(key)) {
                    UserThread.runAfter(() -> new Popup().headLine(Res.get("takeOffer.success.headline"))
                            .feedback(Res.get("takeOffer.success.info"))
                            .actionButtonTextWithGoTo("navigation.portfolio.pending")
                            .dontShowAgainId(key)
                            .onAction(() -> {
                                UserThread.runAfter(
                                        () -> navigation.navigateTo(MainView.class, PortfolioView.class, PendingTradesView.class)
                                        , 100, TimeUnit.MILLISECONDS);
                                close();
                            })
                            .onClose(this::close)
                            .show(), 1);
                } else {
                    close();
                }
            }
        });

        balanceSubscription = EasyBind.subscribe(model.dataModel.getBalance(), balanceTextField::setBalance);
    }

    private void removeSubscriptions() {
        offerWarningSubscription.unsubscribe();
        errorMessageSubscription.unsubscribe();
        isOfferAvailableSubscription.unsubscribe();
        isWaitingForFundsSubscription.unsubscribe();
        showWarningInvalidBtcDecimalPlacesSubscription.unsubscribe();
        showTransactionPublishedScreenSubscription.unsubscribe();
        // noSufficientFeeSubscription.unsubscribe();
        balanceSubscription.unsubscribe();
    }

    private void addListeners() {
        amountTextField.focusedProperty().addListener(amountFocusedListener);
        model.dataModel.getShowWalletFundedNotification().addListener(getShowWalletFundedNotificationListener);
        model.isTradeFeeVisible.addListener(tradeFeeVisibleListener);
        tradeFeeInBtcToggle.selectedProperty().addListener(tradeFeeInBtcToggleListener);
        tradeFeeInBsqToggle.selectedProperty().addListener(tradeFeeInBsqToggleListener);
    }

    private void removeListeners() {
        amountTextField.focusedProperty().removeListener(amountFocusedListener);
        model.dataModel.getShowWalletFundedNotification().removeListener(getShowWalletFundedNotificationListener);
        model.isTradeFeeVisible.removeListener(tradeFeeVisibleListener);
        tradeFeeInBtcToggle.selectedProperty().removeListener(tradeFeeInBtcToggleListener);
        tradeFeeInBsqToggle.selectedProperty().removeListener(tradeFeeInBsqToggleListener);
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
        gridPane.setPadding(new Insets(15, 15, -1, 15));
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
        paymentAccountTitledGroupBg = addTitledGroupBg(gridPane, gridRow, 1, Res.get("takeOffer.paymentInfo"));
        GridPane.setColumnSpan(paymentAccountTitledGroupBg, 2);

        final Tuple4<ComboBox<PaymentAccount>, Label, TextField, HBox> paymentAccountTuple = addComboBoxTopLabelTextField(gridPane,
                gridRow, Res.get("shared.selectTradingAccount"),
                Res.get("shared.paymentMethod"), Layout.FIRST_ROW_DISTANCE);

        paymentAccountsComboBox = paymentAccountTuple.first;
        HBox.setMargin(paymentAccountsComboBox, new Insets(Layout.FLOATING_LABEL_DISTANCE, 0, 0, 0));
        paymentAccountsComboBox.setConverter(GUIUtil.getPaymentAccountsComboBoxStringConverter());
        paymentAccountsComboBox.setCellFactory(model.getPaymentAccountListCellFactory(paymentAccountsComboBox));
        paymentAccountsComboBox.setVisible(false);
        paymentAccountsComboBox.setManaged(false);
        paymentAccountsComboBox.setOnAction(e -> {
            PaymentAccount paymentAccount = paymentAccountsComboBox.getSelectionModel().getSelectedItem();
            if (paymentAccount != null) {
                maybeShowClearXchangeWarning(paymentAccount);
                maybeShowFasterPaymentsWarning(paymentAccount);
            }
            model.onPaymentAccountSelected(paymentAccount);
        });

        paymentMethodLabel = paymentAccountTuple.second;
        paymentMethodTextField = paymentAccountTuple.third;
        paymentMethodTextField.setMinWidth(250);
        paymentMethodTextField.setEditable(false);
        paymentMethodTextField.setMouseTransparent(true);
        paymentMethodTextField.setFocusTraversable(false);

        currencyTextField = new JFXTextField();
        currencyTextField.setMinWidth(250);
        currencyTextField.setEditable(false);
        currencyTextField.setMouseTransparent(true);
        currencyTextField.setFocusTraversable(false);

        final Tuple2<Label, VBox> tradeCurrencyTuple = getTopLabelWithVBox(Res.get("shared.tradeCurrency"), currencyTextField);
        HBox.setMargin(tradeCurrencyTuple.second, new Insets(5, 0, 0, 0));

        final HBox hBox = paymentAccountTuple.fourth;
        hBox.setSpacing(30);
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.setPadding(new Insets(10, 0, 18, 0));

        hBox.getChildren().add(tradeCurrencyTuple.second);
    }

    private void addAmountPriceGroup() {
        TitledGroupBg titledGroupBg = addTitledGroupBg(gridPane, ++gridRow, 2,
                Res.get("takeOffer.setAmountPrice"), Layout.COMPACT_GROUP_DISTANCE);
        GridPane.setColumnSpan(titledGroupBg, 2);

        addAmountPriceFields();
        addSecondRow();
    }

    private void addOptionsGroup() {
        advancedOptionsGroup = addTitledGroupBg(gridPane, ++gridRow, 1, Res.get("shared.advancedOptions"), Layout.COMPACT_GROUP_DISTANCE);

        advancedOptionsBox = new HBox();
        advancedOptionsBox.setSpacing(40);

        GridPane.setRowIndex(advancedOptionsBox, gridRow);
        GridPane.setColumnIndex(advancedOptionsBox, 0);
        GridPane.setHalignment(advancedOptionsBox, HPos.LEFT);
        GridPane.setMargin(advancedOptionsBox, new Insets(Layout.COMPACT_FIRST_ROW_AND_GROUP_DISTANCE, 0, 0, 0));
        gridPane.getChildren().add(advancedOptionsBox);

        Tuple2<AutoTooltipButton, VBox> buyBsqButtonBox = OfferViewUtil.createBuyBsqButtonBox(
                navigation, model.dataModel.preferences, model.dataModel::getDirection);

        advancedOptionsBox.getChildren().addAll(getTradeFeeFieldsBox(), buyBsqButtonBox.second);
    }

    private void addButtons() {
        Tuple3<Button, Button, HBox> tuple = add2ButtonsWithBox(gridPane, ++gridRow,
                Res.get("shared.nextStep"), Res.get("shared.cancel"), 15, true);

        buttonBox = tuple.third;

        nextButton = tuple.first;
        nextButton.setMaxWidth(200);
        nextButton.setDefaultButton(true);
        nextButton.setOnAction(e -> nextStepCheckMakerTx());

        cancelButton1 = tuple.second;
        cancelButton1.setMaxWidth(200);
        cancelButton1.setDefaultButton(false);
        cancelButton1.setOnAction(e -> {
            model.dataModel.swapTradeToSavings();
            close(false);
        });
    }

    private void nextStepCheckMakerTx() {
        // the tx validation check has had plenty of time to complete, but if for some reason it has not returned
        // we continue anyway since the check is not crucial.
        // note, it would be great if there was a real tri-state boolean we could use here, instead of -1, 0, and 1
        int result = model.dataModel.mempoolStatus.get();
        if (result == 0) {
            new Popup().warning(Res.get("popup.warning.makerTxInvalid") + model.dataModel.getMempoolStatusText())
                    .onClose(() -> {
                        cancelButton1.fire();
                    })
                    .show();
        } else {
            if (result == -1) {
                log.warn("Fee check has not returned a result yet. We optimistically assume all is ok and continue.");
            }
            showNextStepAfterAmountIsSet();
        }
    }

    private void showNextStepAfterAmountIsSet() {
        if (DevEnv.isDaoTradingActivated())
            showFeeOption();
        else
            onShowPayFundsScreen();
    }

    private void showFeeOption() {
        boolean isPreferredFeeCurrencyBtc = model.dataModel.isPreferredFeeCurrencyBtc();
        boolean isBsqForFeeAvailable = model.dataModel.isBsqForFeeAvailable();
        if (!isPreferredFeeCurrencyBtc && !isBsqForFeeAvailable) {
            Coin takerFee = model.dataModel.getTakerFee(false);
            String missingBsq = null;
            if (takerFee != null) {
                missingBsq = Res.get("popup.warning.insufficientBsqFundsForBtcFeePayment",
                        bsqFormatter.formatCoinWithCode(takerFee.subtract(model.dataModel.getUsableBsqBalance())));

            } else if (model.dataModel.getUsableBsqBalance().isZero()) {
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

    private void addOfferAvailabilityLabel() {
        offerAvailabilityBusyAnimation = new BusyAnimation(false);
        offerAvailabilityLabel = new AutoTooltipLabel(Res.get("takeOffer.fundsBox.isOfferAvailable"));
        buttonBox.getChildren().addAll(offerAvailabilityBusyAnimation, offerAvailabilityLabel);
    }

    private void addFundingGroup() {
        // don't increase gridRow as we removed button when this gets visible
        payFundsTitledGroupBg = addTitledGroupBg(gridPane, gridRow, 3,
                Res.get("takeOffer.fundsBox.title"), Layout.COMPACT_GROUP_DISTANCE);
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

        addressTextField = addAddressTextField(gridPane, ++gridRow, Res.get("shared.tradeWalletAddress"));
        addressTextField.setVisible(false);

        balanceTextField = addBalanceTextField(gridPane, ++gridRow, Res.get("shared.tradeWalletBalance"));
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
        waitingForFundsBusyAnimation = new BusyAnimation(false);
        waitingForFundsLabel = new AutoTooltipLabel();
        waitingForFundsLabel.setPadding(new Insets(5, 0, 0, 0));
        fundingHBox.getChildren().addAll(fundFromSavingsWalletButton,
                label,
                fundFromExternalWalletButton,
                waitingForFundsBusyAnimation,
                waitingForFundsLabel);

        GridPane.setRowIndex(fundingHBox, ++gridRow);
        GridPane.setMargin(fundingHBox, new Insets(5, 0, 0, 0));
        gridPane.getChildren().add(fundingHBox);

        takeOfferBox = new HBox();
        takeOfferBox.setSpacing(10);
        GridPane.setRowIndex(takeOfferBox, gridRow);
        GridPane.setColumnSpan(takeOfferBox, 2);
        GridPane.setMargin(takeOfferBox, new Insets(5, 20, 0, 0));
        gridPane.getChildren().add(takeOfferBox);

        takeOfferButton = new AutoTooltipButton();
        takeOfferButton.setOnAction(e -> onTakeOffer());
        takeOfferButton.setMinHeight(40);
        takeOfferButton.setPadding(new Insets(0, 20, 0, 20));

        takeOfferBox.getChildren().add(takeOfferButton);
        takeOfferBox.visibleProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                fundingHBox.getChildren().remove(cancelButton2);
                takeOfferBox.getChildren().add(cancelButton2);
            } else if (!fundingHBox.getChildren().contains(cancelButton2)) {
                takeOfferBox.getChildren().remove(cancelButton2);
                fundingHBox.getChildren().add(cancelButton2);
            }
        });

        cancelButton2 = new AutoTooltipButton(Res.get("shared.cancel"));

        fundingHBox.getChildren().add(cancelButton2);

        cancelButton2.setOnAction(e -> {
            String key = "CreateOfferCancelAndFunded";
            if (model.dataModel.getIsBtcWalletFunded().get() &&
                    model.dataModel.preferences.showAgain(key)) {
                new Popup().backgroundInfo(Res.get("takeOffer.alreadyFunded.askCancel"))
                        .closeButtonText(Res.get("shared.no"))
                        .actionButtonText(Res.get("shared.yesCancel"))
                        .onAction(() -> {
                            model.dataModel.swapTradeToSavings();
                            close(false);
                        })
                        .dontShowAgainId(key)
                        .show();
            } else {
                close(false);
                model.dataModel.swapTradeToSavings();
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
        return GUIUtil.getBitcoinURI(model.dataModel.getAddressEntry().getAddressString(),
                model.dataModel.getMissingCoin().get(),
                model.getPaymentLabel());
    }

    private void addAmountPriceFields() {
        // amountBox
        Tuple3<HBox, InputTextField, Label> amountValueCurrencyBoxTuple = getEditableValueBox(Res.get("takeOffer.amount.prompt"));
        amountValueCurrencyBox = amountValueCurrencyBoxTuple.first;
        amountTextField = amountValueCurrencyBoxTuple.second;
        Tuple2<Label, VBox> amountInputBoxTuple = getTradeInputBox(amountValueCurrencyBox, model.getAmountDescription());
        amountDescriptionLabel = amountInputBoxTuple.first;
        VBox amountBox = amountInputBoxTuple.second;

        // x
        xLabel = new Label();
        xIcon = getIconForLabel(MaterialDesignIcon.CLOSE, "2em", xLabel);
        xIcon.getStyleClass().add("opaque-icon");
        xLabel.getStyleClass().addAll("opaque-icon-character");

        // price
        Tuple3<HBox, TextField, Label> priceValueCurrencyBoxTuple = getNonEditableValueBox();
        priceValueCurrencyBox = priceValueCurrencyBoxTuple.first;
        priceTextField = priceValueCurrencyBoxTuple.second;
        priceCurrencyLabel = priceValueCurrencyBoxTuple.third;
        Tuple2<Label, VBox> priceInputBoxTuple = getTradeInputBox(priceValueCurrencyBox,
                Res.get("takeOffer.amountPriceBox.priceDescription"));
        priceDescriptionLabel = priceInputBoxTuple.first;

        getSmallIconForLabel(MaterialDesignIcon.LOCK, priceDescriptionLabel, "small-icon-label");

        VBox priceBox = priceInputBoxTuple.second;

        // =
        resultLabel = new AutoTooltipLabel("=");
        resultLabel.getStyleClass().addAll("opaque-icon-character");

        // volume
        Tuple3<HBox, InfoInputTextField, Label> volumeValueCurrencyBoxTuple = getNonEditableValueBoxWithInfo();
        volumeValueCurrencyBox = volumeValueCurrencyBoxTuple.first;

        volumeInfoTextField = volumeValueCurrencyBoxTuple.second;
        volumeTextField = volumeInfoTextField.getInputTextField();
        volumeCurrencyLabel = volumeValueCurrencyBoxTuple.third;
        Tuple2<Label, VBox> volumeInputBoxTuple = getTradeInputBox(volumeValueCurrencyBox, model.volumeDescriptionLabel.get());
        volumeDescriptionLabel = volumeInputBoxTuple.first;
        VBox volumeBox = volumeInputBoxTuple.second;

        firstRowHBox = new HBox();
        firstRowHBox.setSpacing(5);
        firstRowHBox.setAlignment(Pos.CENTER_LEFT);
        firstRowHBox.getChildren().addAll(amountBox, xLabel, priceBox, resultLabel, volumeBox);
        GridPane.setColumnSpan(firstRowHBox, 2);
        GridPane.setRowIndex(firstRowHBox, gridRow);
        GridPane.setMargin(firstRowHBox, new Insets(Layout.COMPACT_FIRST_ROW_AND_GROUP_DISTANCE, 10, 0, 0));
        gridPane.getChildren().add(firstRowHBox);
    }

    private void addSecondRow() {
        Tuple3<HBox, TextField, Label> priceAsPercentageTuple = getNonEditableValueBox();
        priceAsPercentageValueCurrencyBox = priceAsPercentageTuple.first;
        priceAsPercentageTextField = priceAsPercentageTuple.second;
        priceAsPercentageLabel = priceAsPercentageTuple.third;

        Tuple2<Label, VBox> priceAsPercentageInputBoxTuple = getTradeInputBox(priceAsPercentageValueCurrencyBox, "");
        priceAsPercentageDescription = priceAsPercentageInputBoxTuple.first;

        getSmallIconForLabel(MaterialDesignIcon.CHART_LINE, priceAsPercentageDescription, "small-icon-label");

        priceAsPercentageInputBox = priceAsPercentageInputBoxTuple.second;

        priceAsPercentageLabel.setText("%");

        Tuple3<HBox, TextField, Label> amountValueCurrencyBoxTuple = getNonEditableValueBox();
        amountRangeTextField = amountValueCurrencyBoxTuple.second;

        minAmountValueCurrencyBox = amountValueCurrencyBoxTuple.first;
        Tuple2<Label, VBox> amountInputBoxTuple = getTradeInputBox(minAmountValueCurrencyBox,
                Res.get("takeOffer.amountPriceBox.amountRangeDescription"));

        amountRangeBox = amountInputBoxTuple.second;
        amountRangeBox.setVisible(false);

        fakeXLabel = new Label();
        fakeXIcon = getIconForLabel(MaterialDesignIcon.CLOSE, "2em", fakeXLabel);
        fakeXLabel.setVisible(false); // we just use it to get the same layout as the upper row
        fakeXLabel.getStyleClass().add("opaque-icon-character");

        HBox hBox = new HBox();
        hBox.setSpacing(5);
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.getChildren().addAll(amountRangeBox, fakeXLabel, priceAsPercentageInputBox);

        GridPane.setRowIndex(hBox, ++gridRow);
        GridPane.setMargin(hBox, new Insets(0, 10, 10, 0));
        gridPane.getChildren().add(hBox);
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
        tradeFeeInBtcToggle.setPadding(new Insets(-8, 5, -10, 5));

        tradeFeeInBsqToggle = new AutoTooltipSlideToggleButton();
        tradeFeeInBsqToggle.setText("BSQ");
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
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////


    private void showInsufficientBsqFundsForBtcFeePaymentPopup() {
        Coin takerFee = model.dataModel.getTakerFee(false);
        String message = null;
        if (takerFee != null)
            message = Res.get("popup.warning.insufficientBsqFundsForBtcFeePayment",
                    bsqFormatter.formatCoinWithCode(takerFee.subtract(model.dataModel.getUsableBsqBalance())));

        else if (model.dataModel.getUsableBsqBalance().isZero())
            message = Res.get("popup.warning.noBsqFundsForBtcFeePayment");

        if (message != null)
            new Popup().warning(message)
                    .actionButtonTextWithGoTo("navigation.dao.wallet.receive")
                    .onAction(() -> navigation.navigateTo(MainView.class, DaoView.class, BsqWalletView.class, BsqReceiveView.class))
                    .show();
    }

    private void maybeShowTakeOfferFromUnsignedAccountWarning(Offer offer) {
        // warn if you are selling BTC to unsigned account (#5343)
        if (model.isSellingToAnUnsignedAccount(offer) && !takeOfferFromUnsignedAccountWarningDisplayed) {
            takeOfferFromUnsignedAccountWarningDisplayed = true;
            UserThread.runAfter(GUIUtil::showTakeOfferFromUnsignedAccountWarning, 500, TimeUnit.MILLISECONDS);
        }
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

    private void maybeShowCashByMailWarning(PaymentAccount paymentAccount, Offer offer) {
        if (paymentAccount.getPaymentMethod().getId().equals(PaymentMethod.CASH_BY_MAIL_ID) &&
                !cashByMailWarningDisplayed && !offer.getExtraInfo().isEmpty()) {
            cashByMailWarningDisplayed = true;
            UserThread.runAfter(() -> {
                new GenericMessageWindow()
                        .preamble(Res.get("payment.cashByMail.tradingRestrictions"))
                        .instruction(offer.getExtraInfo())
                        .actionButtonText(Res.get("shared.iConfirm"))
                        .closeButtonText(Res.get("shared.close"))
                        .width(Layout.INITIAL_WINDOW_WIDTH)
                        .onClose(() -> close(false))
                        .show();
            }, 500, TimeUnit.MILLISECONDS);
        }
    }

    private Tuple2<Label, VBox> getTradeInputBox(HBox amountValueBox, String promptText) {
        Label descriptionLabel = new AutoTooltipLabel(promptText);
        descriptionLabel.setId("input-description-label");
        descriptionLabel.setPrefWidth(170);

        VBox box = new VBox();
        box.setPadding(new Insets(10, 0, 0, 0));
        box.setSpacing(2);
        box.getChildren().addAll(descriptionLabel, amountValueBox);
        return new Tuple2<>(descriptionLabel, box);
    }

    // As we don't use binding here we need to recreate it on mouse over to reflect the current state
    private GridPane createInfoPopover() {
        GridPane infoGridPane = new GridPane();
        infoGridPane.setHgap(5);
        infoGridPane.setVgap(5);
        infoGridPane.setPadding(new Insets(10, 10, 10, 10));

        int i = 0;
        if (model.isSeller())
            addPayInfoEntry(infoGridPane, i++, Res.get("takeOffer.fundsBox.tradeAmount"), model.getTradeAmount());

        addPayInfoEntry(infoGridPane, i++, Res.getWithCol("shared.yourSecurityDeposit"), model.getSecurityDepositInfo());
        addPayInfoEntry(infoGridPane, i++, Res.get("takeOffer.fundsBox.offerFee"), model.getTradeFee());
        addPayInfoEntry(infoGridPane, i++, Res.get("takeOffer.fundsBox.networkFee"), model.getTxFee());
        Separator separator = new Separator();
        separator.setOrientation(Orientation.HORIZONTAL);
        separator.getStyleClass().add("offer-separator");
        GridPane.setConstraints(separator, 1, i++);
        infoGridPane.getChildren().add(separator);
        addPayInfoEntry(infoGridPane, i, Res.getWithCol("shared.total"),
                model.getTotalToPayInfo());

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

