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

package bisq.desktop.main.offer.offerbook;

import bisq.desktop.Navigation;
import bisq.desktop.common.view.ActivatableViewAndModel;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.AccountStatusTooltipLabel;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipSlideToggleButton;
import bisq.desktop.components.AutoTooltipTableColumn;
import bisq.desktop.components.AutocompleteComboBox;
import bisq.desktop.components.ColoredDecimalPlacesWithZerosText;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.InfoAutoTooltipLabel;
import bisq.desktop.components.PeerInfoIconTrading;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.MainView;
import bisq.desktop.main.account.AccountView;
import bisq.desktop.main.account.content.fiataccounts.FiatAccountsView;
import bisq.desktop.main.funds.FundsView;
import bisq.desktop.main.funds.withdrawal.WithdrawalView;
import bisq.desktop.main.offer.OfferView;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.overlays.windows.OfferDetailsWindow;
import bisq.desktop.util.CssTheme;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.account.sign.SignedWitnessService;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.alert.PrivateNotificationManager;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.monetary.Price;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferFilter;
import bisq.core.offer.OfferPayload;
import bisq.core.offer.OfferRestrictions;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.user.DontShowAgainLookup;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.p2p.NodeAddress;

import bisq.common.app.DevEnv;
import bisq.common.config.Config;
import bisq.common.util.Tuple3;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import javax.inject.Named;

import de.jensd.fx.fontawesome.AwesomeIcon;
import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;

import javafx.scene.canvas.Canvas;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.fxmisc.easybind.monadic.MonadicBinding;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

import javafx.collections.ListChangeListener;

import javafx.util.Callback;
import javafx.util.StringConverter;

import java.util.Comparator;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;

import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class OfferBookView extends ActivatableViewAndModel<GridPane, OfferBookViewModel> {

    private final Navigation navigation;
    private final OfferDetailsWindow offerDetailsWindow;
    private final CoinFormatter formatter;
    private final PrivateNotificationManager privateNotificationManager;
    private final boolean useDevPrivilegeKeys;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final SignedWitnessService signedWitnessService;

    private AutocompleteComboBox<TradeCurrency> currencyComboBox;
    private AutocompleteComboBox<PaymentMethod> paymentMethodComboBox;
    private AutoTooltipButton createOfferButton;
    private AutoTooltipSlideToggleButton matchingOffersToggle;
    private AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> amountColumn, volumeColumn, marketColumn,
            priceColumn, paymentMethodColumn, depositColumn, signingStateColumn, avatarColumn;
    private TableView<OfferBookListItem> tableView;

    private OfferView.OfferActionHandler offerActionHandler;
    private int gridRow = 0;
    private Label nrOfOffersLabel;
    private ListChangeListener<OfferBookListItem> offerListListener;
    private ChangeListener<Number> priceFeedUpdateCounterListener;
    private Subscription currencySelectionSubscriber;
    private static final int SHOW_ALL = 0;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    OfferBookView(OfferBookViewModel model,
                  Navigation navigation,
                  OfferDetailsWindow offerDetailsWindow,
                  @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter formatter,
                  PrivateNotificationManager privateNotificationManager,
                  @Named(Config.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys,
                  AccountAgeWitnessService accountAgeWitnessService,
                  SignedWitnessService signedWitnessService) {
        super(model);

        this.navigation = navigation;
        this.offerDetailsWindow = offerDetailsWindow;
        this.formatter = formatter;
        this.privateNotificationManager = privateNotificationManager;
        this.useDevPrivilegeKeys = useDevPrivilegeKeys;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.signedWitnessService = signedWitnessService;
    }

    @Override
    public void initialize() {
        root.setPadding(new Insets(15, 15, 5, 15));

        final TitledGroupBg titledGroupBg = addTitledGroupBg(root, gridRow, 2, Res.get("offerbook.availableOffers"));
        titledGroupBg.getStyleClass().add("last");

        HBox offerToolsBox = new HBox();
        offerToolsBox.setAlignment(Pos.BOTTOM_LEFT);
        offerToolsBox.setSpacing(10);
        offerToolsBox.setPadding(new Insets(10, 0, 0, 0));

        Tuple3<VBox, Label, AutocompleteComboBox<TradeCurrency>> currencyBoxTuple = FormBuilder.addTopLabelAutocompleteComboBox(
                Res.get("offerbook.filterByCurrency"));
        currencyComboBox = currencyBoxTuple.third;
        currencyComboBox.setPrefWidth(270);

        Tuple3<VBox, Label, AutocompleteComboBox<PaymentMethod>> paymentBoxTuple = FormBuilder.addTopLabelAutocompleteComboBox(
                Res.get("offerbook.filterByPaymentMethod"));
        paymentMethodComboBox = paymentBoxTuple.third;
        paymentMethodComboBox.setCellFactory(GUIUtil.getPaymentMethodCellFactory());
        paymentMethodComboBox.setPrefWidth(270);

        matchingOffersToggle = new AutoTooltipSlideToggleButton();
        matchingOffersToggle.setText(Res.get("offerbook.matchingOffers"));
        HBox.setMargin(matchingOffersToggle, new Insets(7, 0, -9, -15));


        createOfferButton = new AutoTooltipButton();
        createOfferButton.setMinHeight(40);
        createOfferButton.setGraphicTextGap(10);

        offerToolsBox.getChildren().addAll(currencyBoxTuple.first, paymentBoxTuple.first,
                matchingOffersToggle, getSpacer(), createOfferButton);

        GridPane.setHgrow(offerToolsBox, Priority.ALWAYS);
        GridPane.setRowIndex(offerToolsBox, gridRow);
        GridPane.setColumnSpan(offerToolsBox, 2);
        GridPane.setMargin(offerToolsBox, new Insets(Layout.FIRST_ROW_DISTANCE, 0, 0, 0));
        root.getChildren().add(offerToolsBox);

        tableView = new TableView<>();

        GridPane.setRowIndex(tableView, ++gridRow);
        GridPane.setColumnIndex(tableView, 0);
        GridPane.setColumnSpan(tableView, 2);
        GridPane.setMargin(tableView, new Insets(10, 0, -10, 0));
        GridPane.setVgrow(tableView, Priority.ALWAYS);
        root.getChildren().add(tableView);

        marketColumn = getMarketColumn();

        priceColumn = getPriceColumn();
        tableView.getColumns().add(priceColumn);
        amountColumn = getAmountColumn();
        tableView.getColumns().add(amountColumn);
        volumeColumn = getVolumeColumn();
        tableView.getColumns().add(volumeColumn);
        paymentMethodColumn = getPaymentMethodColumn();
        tableView.getColumns().add(paymentMethodColumn);
        depositColumn = getDepositColumn();
        tableView.getColumns().add(depositColumn);
        signingStateColumn = getSigningStateColumn();
        tableView.getColumns().add(signingStateColumn);
        avatarColumn = getAvatarColumn();
        tableView.getColumns().add(getActionColumn());
        tableView.getColumns().add(avatarColumn);

        tableView.getSortOrder().add(priceColumn);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Label placeholder = new AutoTooltipLabel(Res.get("table.placeholder.noItems", Res.get("shared.multipleOffers")));
        placeholder.setWrapText(true);
        tableView.setPlaceholder(placeholder);

        marketColumn.setComparator(Comparator.comparing(
                o -> CurrencyUtil.getCurrencyPair(o.getOffer().getCurrencyCode()),
                Comparator.nullsFirst(Comparator.naturalOrder())
        ));

        // We sort by % so we can also sort if SHOW ALL is selected
        Comparator<OfferBookListItem> marketBasedPriceComparator = (o1, o2) -> {
            Optional<Double> marketBasedPrice1 = model.getMarketBasedPrice(o1.getOffer());
            Optional<Double> marketBasedPrice2 = model.getMarketBasedPrice(o2.getOffer());
            if (marketBasedPrice1.isPresent() && marketBasedPrice2.isPresent()) {
                return Double.compare(marketBasedPrice1.get(), marketBasedPrice2.get());
            } else {
                return 0;
            }
        };
        // If we do not have a % price we use only fix price and sort by that
        priceColumn.setComparator(marketBasedPriceComparator.thenComparing((o1, o2) -> {
            Price price2 = o2.getOffer().getPrice();
            Price price1 = o1.getOffer().getPrice();
            if (price2 == null || price1 == null) {
                return 0;
            }
            if (model.getDirection() == OfferPayload.Direction.SELL) {
                return price1.compareTo(price2);
            } else {
                return price2.compareTo(price1);
            }
        }));

        amountColumn.setComparator(Comparator.comparing(o -> o.getOffer().getMinAmount()));
        volumeColumn.setComparator(Comparator.comparing(o -> o.getOffer().getMinVolume(), Comparator.nullsFirst(Comparator.naturalOrder())));
        paymentMethodColumn.setComparator(Comparator.comparing(o -> Res.get(o.getOffer().getPaymentMethod().getId())));
        avatarColumn.setComparator(Comparator.comparing(o -> model.getNumTrades(o.getOffer())));
        depositColumn.setComparator(Comparator.comparing(item -> {
            boolean isSellOffer = item.getOffer().getDirection() == OfferPayload.Direction.SELL;
            Coin deposit = isSellOffer ?
                    item.getOffer().getBuyerSecurityDeposit() :
                    item.getOffer().getSellerSecurityDeposit();

            double amountValue = item.getOffer().getAmount().getValue();
            if ((deposit == null || amountValue == 0)) {
                return 0d;
            } else {
                return deposit.getValue() / amountValue;
            }

        }, Comparator.nullsFirst(Comparator.naturalOrder())));

        signingStateColumn.setComparator(Comparator.comparing(e -> e.getWitnessAgeData(accountAgeWitnessService, signedWitnessService), Comparator.nullsFirst(Comparator.naturalOrder())));

        nrOfOffersLabel = new AutoTooltipLabel("");
        nrOfOffersLabel.setId("num-offers");
        GridPane.setHalignment(nrOfOffersLabel, HPos.LEFT);
        GridPane.setVgrow(nrOfOffersLabel, Priority.NEVER);
        GridPane.setValignment(nrOfOffersLabel, VPos.TOP);
        GridPane.setRowIndex(nrOfOffersLabel, ++gridRow);
        GridPane.setColumnIndex(nrOfOffersLabel, 0);
        GridPane.setMargin(nrOfOffersLabel, new Insets(10, 0, 0, 0));
        root.getChildren().add(nrOfOffersLabel);

        offerListListener = c -> nrOfOffersLabel.setText(Res.get("offerbook.nrOffers", model.getOfferList().size()));

        // Fixes incorrect ordering of Available offers:
        // https://github.com/bisq-network/bisq-desktop/issues/588
        priceFeedUpdateCounterListener = (observable, oldValue, newValue) -> tableView.sort();
    }

    @Override
    protected void activate() {
        currencyComboBox.setCellFactory(GUIUtil.getTradeCurrencyCellFactory(Res.get("shared.oneOffer"),
                Res.get("shared.multipleOffers"),
                (model.getDirection() == OfferPayload.Direction.BUY ? model.getSellOfferCounts() : model.getBuyOfferCounts())));

        currencyComboBox.setConverter(new CurrencyStringConverter(currencyComboBox));
        currencyComboBox.getEditor().getStyleClass().add("combo-box-editor-bold");

        currencyComboBox.setAutocompleteItems(model.getTradeCurrencies());
        currencyComboBox.setVisibleRowCount(Math.min(currencyComboBox.getItems().size(), 10));

        currencyComboBox.setOnChangeConfirmed(e -> {
            if (currencyComboBox.getEditor().getText().isEmpty())
                currencyComboBox.getSelectionModel().select(SHOW_ALL);
            model.onSetTradeCurrency(currencyComboBox.getSelectionModel().getSelectedItem());
        });
        updateCurrencyComboBoxFromModel();

        currencyComboBox.getEditor().setText(new CurrencyStringConverter(currencyComboBox).toString(currencyComboBox.getSelectionModel().getSelectedItem()));

        matchingOffersToggle.setSelected(model.useOffersMatchingMyAccountsFilter);
        matchingOffersToggle.disableProperty().bind(model.disableMatchToggle);
        matchingOffersToggle.setOnAction(e -> model.onShowOffersMatchingMyAccounts(matchingOffersToggle.isSelected()));

        volumeColumn.sortableProperty().bind(model.showAllTradeCurrenciesProperty.not());
        model.getOfferList().comparatorProperty().bind(tableView.comparatorProperty());

        amountColumn.sortTypeProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == TableColumn.SortType.DESCENDING) {
                amountColumn.setComparator(Comparator.comparing(o -> o.getOffer().getAmount(), Comparator.nullsFirst(Comparator.naturalOrder())));
            } else {
                amountColumn.setComparator(Comparator.comparing(o -> o.getOffer().getMinAmount(), Comparator.nullsFirst(Comparator.naturalOrder())));
            }
        });
        volumeColumn.sortTypeProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == TableColumn.SortType.DESCENDING) {
                volumeColumn.setComparator(Comparator.comparing(o -> o.getOffer().getVolume(), Comparator.nullsFirst(Comparator.naturalOrder())));
            } else {
                volumeColumn.setComparator(Comparator.comparing(o -> o.getOffer().getMinVolume(), Comparator.nullsFirst(Comparator.naturalOrder())));
            }
        });

        paymentMethodComboBox.setConverter(new PaymentMethodStringConverter(paymentMethodComboBox));
        paymentMethodComboBox.getEditor().getStyleClass().add("combo-box-editor-bold");

        paymentMethodComboBox.setAutocompleteItems(model.getPaymentMethods());
        paymentMethodComboBox.setVisibleRowCount(Math.min(paymentMethodComboBox.getItems().size(), 10));

        paymentMethodComboBox.setOnChangeConfirmed(e -> {
            if (paymentMethodComboBox.getEditor().getText().isEmpty())
                paymentMethodComboBox.getSelectionModel().select(SHOW_ALL);
            model.onSetPaymentMethod(paymentMethodComboBox.getSelectionModel().getSelectedItem());
            updateCurrencyComboBoxFromModel();
            updateSigningStateColumn();
        });

        if (model.showAllPaymentMethods)
            paymentMethodComboBox.getSelectionModel().select(SHOW_ALL);
        else
            paymentMethodComboBox.getSelectionModel().select(model.selectedPaymentMethod);
        paymentMethodComboBox.getEditor().setText(new PaymentMethodStringConverter(paymentMethodComboBox).toString(paymentMethodComboBox.getSelectionModel().getSelectedItem()));

        createOfferButton.setOnAction(e -> onCreateOffer());

        MonadicBinding<Void> currencySelectionBinding = EasyBind.combine(
                model.showAllTradeCurrenciesProperty, model.tradeCurrencyCode,
                (showAll, code) -> {
                    setDirectionTitles();
                    if (showAll) {
                        volumeColumn.setTitleWithHelpText(Res.get("shared.amountMinMax"), Res.get("shared.amountHelp"));
                        priceColumn.setTitle(Res.get("shared.price"));
                        priceColumn.getStyleClass().remove("first-column");

                        if (!tableView.getColumns().contains(marketColumn))
                            tableView.getColumns().add(0, marketColumn);
                    } else {
                        volumeColumn.setTitleWithHelpText(Res.get("offerbook.volume", code), Res.get("shared.amountHelp"));
                        priceColumn.setTitle(CurrencyUtil.getPriceWithCurrencyCode(code));
                        priceColumn.getStyleClass().add("first-column");

                        tableView.getColumns().remove(marketColumn);
                    }

                    updateSigningStateColumn();

                    return null;
                });

        currencySelectionSubscriber = currencySelectionBinding.subscribe((observable, oldValue, newValue) -> {
        });

        tableView.setItems(model.getOfferList());

        model.getOfferList().addListener(offerListListener);
        nrOfOffersLabel.setText(Res.get("offerbook.nrOffers", model.getOfferList().size()));

        model.priceFeedService.updateCounterProperty().addListener(priceFeedUpdateCounterListener);
    }

    private void updateCurrencyComboBoxFromModel() {
        if (model.showAllTradeCurrenciesProperty.get()) {
            currencyComboBox.getSelectionModel().select(SHOW_ALL);
        } else {
            currencyComboBox.getSelectionModel().select(model.getSelectedTradeCurrency());
        }
    }

    private void updateSigningStateColumn() {
        if (model.hasSelectionAccountSigning()) {
            if (!tableView.getColumns().contains(signingStateColumn)) {
                tableView.getColumns().add(tableView.getColumns().indexOf(depositColumn) + 1, signingStateColumn);
            }
        } else {
            tableView.getColumns().remove(signingStateColumn);
        }
    }

    @Override
    protected void deactivate() {
        createOfferButton.setOnAction(null);
        matchingOffersToggle.setOnAction(null);
        matchingOffersToggle.disableProperty().unbind();
        model.getOfferList().comparatorProperty().unbind();

        volumeColumn.sortableProperty().unbind();
        priceColumn.sortableProperty().unbind();
        amountColumn.sortableProperty().unbind();
        model.getOfferList().comparatorProperty().unbind();

        model.getOfferList().removeListener(offerListListener);
        model.priceFeedService.updateCounterProperty().removeListener(priceFeedUpdateCounterListener);

        currencySelectionSubscriber.unsubscribe();
    }

    static class CurrencyStringConverter extends StringConverter<TradeCurrency> {
        private final ComboBox<TradeCurrency> comboBox;

        CurrencyStringConverter(ComboBox<TradeCurrency> comboBox) {
            this.comboBox = comboBox;
        }

        @Override
        public String toString(TradeCurrency item) {
            return item != null ? asString(item) : "";
        }

        @Override
        public TradeCurrency fromString(String query) {
            if (comboBox.getItems().isEmpty())
                return null;
            if (query.isEmpty())
                return specialShowAllItem();
            return comboBox.getItems().stream().
                    filter(item -> asString(item).equals(query)).
                    findAny().orElse(null);
        }

        private String asString(TradeCurrency item) {
            if (isSpecialShowAllItem(item))
                return Res.get(GUIUtil.SHOW_ALL_FLAG);
            if (isSpecialEditItem(item))
                return Res.get(GUIUtil.EDIT_FLAG);
            return item.getCode() + "  -  " + item.getName();
        }

        private boolean isSpecialShowAllItem(TradeCurrency item) {
            return item.getCode().equals(GUIUtil.SHOW_ALL_FLAG);
        }

        private boolean isSpecialEditItem(TradeCurrency item) {
            return item.getCode().equals(GUIUtil.EDIT_FLAG);
        }

        private TradeCurrency specialShowAllItem() {
            return comboBox.getItems().get(SHOW_ALL);
        }
    }

    static class PaymentMethodStringConverter extends StringConverter<PaymentMethod> {
        private final ComboBox<PaymentMethod> comboBox;

        PaymentMethodStringConverter(ComboBox<PaymentMethod> comboBox) {
            this.comboBox = comboBox;
        }

        @Override
        public String toString(PaymentMethod item) {
            return item != null ? asString(item) : "";
        }

        @Override
        public PaymentMethod fromString(String query) {
            if (comboBox.getItems().isEmpty())
                return null;
            if (query.isEmpty())
                return specialShowAllItem();
            return comboBox.getItems().stream().
                    filter(item -> asString(item).equals(query)).
                    findAny().orElse(null);
        }

        private String asString(PaymentMethod item) {
            if (isSpecialShowAllItem(item))
                return Res.get(GUIUtil.SHOW_ALL_FLAG);
            return Res.get(item.getId());
        }

        private boolean isSpecialShowAllItem(PaymentMethod item) {
            return item.getId().equals(GUIUtil.SHOW_ALL_FLAG);
        }

        private PaymentMethod specialShowAllItem() {
            return comboBox.getItems().get(SHOW_ALL);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void enableCreateOfferButton() {
        createOfferButton.setDisable(false);
    }

    public void setDirection(OfferPayload.Direction direction) {
        model.initWithDirection(direction);
        ImageView iconView = new ImageView();

        createOfferButton.setGraphic(iconView);
        iconView.setId(direction == OfferPayload.Direction.SELL ? "image-sell-white" : "image-buy-white");
        createOfferButton.setId(direction == OfferPayload.Direction.SELL ? "sell-button-big" : "buy-button-big");
        avatarColumn.setTitle(direction == OfferPayload.Direction.SELL ? Res.get("shared.buyerUpperCase") : Res.get("shared.sellerUpperCase"));
        setDirectionTitles();
    }

    private void setDirectionTitles() {
        TradeCurrency selectedTradeCurrency = model.getSelectedTradeCurrency();
        if (selectedTradeCurrency != null) {
            OfferPayload.Direction direction = model.getDirection();
            String offerButtonText;
            String code = selectedTradeCurrency.getCode();

            if (model.showAllTradeCurrenciesProperty.get()) {
                offerButtonText = direction == OfferPayload.Direction.BUY ?
                        Res.get("offerbook.createOfferToBuy",
                                Res.getBaseCurrencyCode()) :
                        Res.get("offerbook.createOfferToSell",
                                Res.getBaseCurrencyCode());
            } else if (selectedTradeCurrency instanceof FiatCurrency) {
                offerButtonText = direction == OfferPayload.Direction.BUY ?
                        Res.get("offerbook.createOfferToBuy.withFiat",
                                Res.getBaseCurrencyCode(), code) :
                        Res.get("offerbook.createOfferToSell.forFiat", Res.getBaseCurrencyCode(), code);

            } else {
                offerButtonText = direction == OfferPayload.Direction.BUY ?
                        Res.get("offerbook.createOfferToBuy.withCrypto",
                                code, Res.getBaseCurrencyCode()) :
                        Res.get("offerbook.createOfferToSell.forCrypto", code, Res.getBaseCurrencyCode());
            }
            createOfferButton.updateText(offerButtonText);
        }
    }

    public void setOfferActionHandler(OfferView.OfferActionHandler offerActionHandler) {
        this.offerActionHandler = offerActionHandler;
    }

    public void onTabSelected(boolean isSelected) {
        model.onTabSelected(isSelected);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI actions
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onCreateOffer() {
        if (model.canCreateOrTakeOffer()) {
            if (!model.hasPaymentAccountForCurrency()) {
                new Popup().headLine(Res.get("offerbook.warning.noTradingAccountForCurrency.headline"))
                        .instruction(Res.get("offerbook.warning.noTradingAccountForCurrency.msg"))
                        .actionButtonText(Res.get("offerbook.yesCreateOffer"))
                        .onAction(() -> {
                            createOfferButton.setDisable(true);
                            offerActionHandler.onCreateOffer(model.getSelectedTradeCurrency());
                        })
                        .secondaryActionButtonText(Res.get("offerbook.setupNewAccount"))
                        .onSecondaryAction(() -> {
                            navigation.setReturnPath(navigation.getCurrentPath());
                            navigation.navigateTo(MainView.class, AccountView.class, FiatAccountsView.class);
                        })
                        .width(725)
                        .show();
                return;
            }

            createOfferButton.setDisable(true);
            offerActionHandler.onCreateOffer(model.getSelectedTradeCurrency());
        }
    }

    private void onShowInfo(Offer offer, OfferFilter.Result result) {
        switch (result) {
            case VALID:
                break;
            case API_DISABLED:
                DevEnv.logErrorAndThrowIfDevMode("We are in desktop and in the taker position " +
                        "viewing offers, so it cannot be that we got that result as we are not an API user.");
                break;
            case HAS_NO_PAYMENT_ACCOUNT_VALID_FOR_OFFER:
                openPopupForMissingAccountSetup(Res.get("offerbook.warning.noMatchingAccount.headline"),
                        Res.get("offerbook.warning.noMatchingAccount.msg"),
                        FiatAccountsView.class,
                        "navigation.account");
                break;
            case HAS_NOT_SAME_PROTOCOL_VERSION:
                new Popup().warning(Res.get("offerbook.warning.wrongTradeProtocol")).show();
                break;
            case IS_IGNORED:
                new Popup().warning(Res.get("offerbook.warning.userIgnored")).show();
                break;
            case IS_OFFER_BANNED:
                new Popup().warning(Res.get("offerbook.warning.offerBlocked")).show();
                break;
            case IS_CURRENCY_BANNED:
                new Popup().warning(Res.get("offerbook.warning.currencyBanned")).show();
                break;
            case IS_PAYMENT_METHOD_BANNED:
                new Popup().warning(Res.get("offerbook.warning.paymentMethodBanned")).show();
                break;
            case IS_NODE_ADDRESS_BANNED:
                new Popup().warning(Res.get("offerbook.warning.nodeBlocked")).show();
                break;
            case REQUIRE_UPDATE_TO_NEW_VERSION:
                new Popup().warning(Res.get("offerbook.warning.requireUpdateToNewVersion")).show();
                break;
            case IS_INSUFFICIENT_COUNTERPARTY_TRADE_LIMIT:
                new Popup().warning(Res.get("offerbook.warning.counterpartyTradeRestrictions")).show();
                break;
            case IS_MY_INSUFFICIENT_TRADE_LIMIT:
                Optional<PaymentAccount> account = model.getMostMaturePaymentAccountForOffer(offer);
                if (account.isPresent()) {
                    long tradeLimit = model.accountAgeWitnessService.getMyTradeLimit(account.get(),
                            offer.getCurrencyCode(), offer.getMirroredDirection());
                    new Popup()
                            .warning(Res.get("popup.warning.tradeLimitDueAccountAgeRestriction.buyer",
                                    formatter.formatCoinWithCode(Coin.valueOf(tradeLimit)),
                                    Res.get("offerbook.warning.newVersionAnnouncement")))
                            .show();
                } else {
                    DevEnv.logErrorAndThrowIfDevMode("We don't found a payment account but got called the " +
                            "isInsufficientTradeLimit case.");
                }
                break;
            default:
                break;
        }
    }

    private void onTakeOffer(Offer offer) {
        if (model.canCreateOrTakeOffer()) {
            if (offer.getDirection() == OfferPayload.Direction.SELL &&
                    offer.getPaymentMethod().getId().equals(PaymentMethod.CASH_DEPOSIT.getId())) {
                new Popup().confirmation(Res.get("popup.info.cashDepositInfo", offer.getBankId()))
                        .actionButtonText(Res.get("popup.info.cashDepositInfo.confirm"))
                        .onAction(() -> offerActionHandler.onTakeOffer(offer))
                        .show();
            } else {
                offerActionHandler.onTakeOffer(offer);
            }
        }
    }

    private void onRemoveOpenOffer(Offer offer) {
        if (model.isBootstrappedOrShowPopup()) {
            String key = "RemoveOfferWarning";
            if (DontShowAgainLookup.showAgain(key)) {
                new Popup().warning(Res.get("popup.warning.removeOffer", model.getMakerFeeAsString(offer)))
                        .actionButtonText(Res.get("shared.removeOffer"))
                        .onAction(() -> doRemoveOffer(offer))
                        .closeButtonText(Res.get("shared.dontRemoveOffer"))
                        .dontShowAgainId(key)
                        .show();
            } else {
                doRemoveOffer(offer);
            }
        }
    }

    private void doRemoveOffer(Offer offer) {
        String key = "WithdrawFundsAfterRemoveOfferInfo";
        model.onRemoveOpenOffer(offer,
                () -> {
                    log.debug(Res.get("offerbook.removeOffer.success"));
                    if (DontShowAgainLookup.showAgain(key))
                        new Popup().instruction(Res.get("offerbook.withdrawFundsHint", Res.get("navigation.funds.availableForWithdrawal")))
                                .actionButtonTextWithGoTo("navigation.funds.availableForWithdrawal")
                                .onAction(() -> navigation.navigateTo(MainView.class, FundsView.class, WithdrawalView.class))
                                .dontShowAgainId(key)
                                .show();
                },
                (message) -> {
                    log.error(message);
                    new Popup().warning(Res.get("offerbook.removeOffer.failed", message)).show();
                });
    }

    private void openPopupForMissingAccountSetup(String headLine, String message, Class target, String targetAsString) {
        new Popup().headLine(headLine)
                .instruction(message)
                .actionButtonTextWithGoTo(targetAsString)
                .onAction(() -> {
                    navigation.setReturnPath(navigation.getCurrentPath());
                    navigation.navigateTo(MainView.class, AccountView.class, target);
                }).show();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Table
    ///////////////////////////////////////////////////////////////////////////////////////////

    private AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> getAmountColumn() {
        AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> column = new AutoTooltipTableColumn<>(Res.get("shared.BTCMinMax"), Res.get("shared.amountHelp"));
        column.setMinWidth(100);
        column.getStyleClass().add("number-column");
        column.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OfferBookListItem, OfferBookListItem> call(
                            TableColumn<OfferBookListItem, OfferBookListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final OfferBookListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty)
                                    setGraphic(new ColoredDecimalPlacesWithZerosText(model.getAmount(item), GUIUtil.AMOUNT_DECIMALS_WITH_ZEROS));
                                else
                                    setGraphic(null);
                            }
                        };
                    }
                });
        return column;
    }

    private AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> getMarketColumn() {
        AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> column = new AutoTooltipTableColumn<>(Res.get("shared.market")) {
            {
                setMinWidth(40);
            }
        };
        column.getStyleClass().addAll("number-column", "first-column");
        column.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OfferBookListItem, OfferBookListItem> call(
                            TableColumn<OfferBookListItem, OfferBookListItem> column) {
                        return new TableCell<>() {

                            @Override
                            public void updateItem(final OfferBookListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty)
                                    setText(CurrencyUtil.getCurrencyPair(item.getOffer().getCurrencyCode()));
                                else
                                    setText("");
                            }
                        };
                    }
                });
        return column;
    }

    private ObservableValue<OfferBookListItem> asPriceDependentObservable(OfferBookListItem item) {
        return item.getOffer().isUseMarketBasedPrice()
                ? EasyBind.map(model.priceFeedService.updateCounterProperty(), n -> item)
                : new ReadOnlyObjectWrapper<>(item);
    }

    private AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> getPriceColumn() {
        AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> column = new AutoTooltipTableColumn<>("") {
            {
                setMinWidth(130);
            }
        };
        column.getStyleClass().add("number-column");
        column.setCellValueFactory(offer -> asPriceDependentObservable(offer.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OfferBookListItem, OfferBookListItem> call(
                            TableColumn<OfferBookListItem, OfferBookListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final OfferBookListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    setGraphic(getPriceAndPercentage(item));
                                } else {
                                    setGraphic(null);
                                }
                            }

                            private HBox getPriceAndPercentage(OfferBookListItem item) {
                                Offer offer = item.getOffer();
                                boolean useMarketBasedPrice = offer.isUseMarketBasedPrice();
                                MaterialDesignIcon icon = useMarketBasedPrice ? MaterialDesignIcon.CHART_LINE : MaterialDesignIcon.LOCK;
                                String info;

                                if (useMarketBasedPrice) {
                                    double marketPriceMargin = offer.getMarketPriceMargin();
                                    if (marketPriceMargin == 0) {
                                        if (offer.isBuyOffer()) {
                                            info = Res.get("offerbook.info.sellAtMarketPrice");
                                        } else {
                                            info = Res.get("offerbook.info.buyAtMarketPrice");
                                        }
                                    } else {
                                        String absolutePriceMargin = model.getAbsolutePriceMargin(offer);
                                        if (marketPriceMargin > 0) {
                                            if (offer.isBuyOffer()) {
                                                info = Res.get("offerbook.info.sellBelowMarketPrice", absolutePriceMargin);
                                            } else {
                                                info = Res.get("offerbook.info.buyAboveMarketPrice", absolutePriceMargin);
                                            }
                                        } else {
                                            if (offer.isBuyOffer()) {
                                                info = Res.get("offerbook.info.sellAboveMarketPrice", absolutePriceMargin);
                                            } else {
                                                info = Res.get("offerbook.info.buyBelowMarketPrice", absolutePriceMargin);
                                            }
                                        }
                                    }
                                } else {
                                    if (offer.isBuyOffer()) {
                                        info = Res.get("offerbook.info.sellAtFixedPrice");
                                    } else {
                                        info = Res.get("offerbook.info.buyAtFixedPrice");
                                    }
                                }
                                InfoAutoTooltipLabel priceLabel = new InfoAutoTooltipLabel(model.getPrice(item),
                                        icon, ContentDisplay.RIGHT, info);
                                priceLabel.setTextAlignment(TextAlignment.RIGHT);
                                AutoTooltipLabel percentageLabel = new AutoTooltipLabel(model.getPriceAsPercentage(item));
                                percentageLabel.setOpacity(useMarketBasedPrice ? 1 : 0.4);

                                HBox hBox = new HBox();
                                hBox.setSpacing(5);
                                hBox.getChildren().addAll(priceLabel, percentageLabel);
                                hBox.setPadding(new Insets(7, 0, 0, 0));
                                return hBox;
                            }
                        };
                    }
                });
        return column;
    }

    private AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> getVolumeColumn() {
        AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> column = new AutoTooltipTableColumn<>("") {
            {
                setMinWidth(125);
            }
        };
        column.getStyleClass().add("number-column");
        column.setCellValueFactory(offer -> asPriceDependentObservable(offer.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OfferBookListItem, OfferBookListItem> call(
                            TableColumn<OfferBookListItem, OfferBookListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final OfferBookListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {
                                    if (item.getOffer().getPrice() == null) {
                                        setText(Res.get("shared.na"));
                                        setGraphic(null);
                                    } else {
                                        setText("");
                                        setGraphic(new ColoredDecimalPlacesWithZerosText(model.getVolume(item),
                                                model.getNumberOfDecimalsForVolume(item)));
                                    }
                                } else {
                                    setText("");
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
        return column;
    }

    private AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> getPaymentMethodColumn() {
        AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> column = new AutoTooltipTableColumn<>(Res.get("shared.paymentMethod")) {
            {
                setMinWidth(80);
            }
        };

        column.getStyleClass().add("number-column");
        column.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OfferBookListItem, OfferBookListItem> call(TableColumn<OfferBookListItem, OfferBookListItem> column) {
                        return new TableCell<>() {
                            private HyperlinkWithIcon field;

                            @Override
                            public void updateItem(final OfferBookListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                if (item != null && !empty) {

                                    if (model.isOfferBanned(item.getOffer())) {
                                        setGraphic(new AutoTooltipLabel(model.getPaymentMethod(item)));
                                    } else {
                                        if (item.getOffer().isXmrAutoConf()) {
                                            field = new HyperlinkWithIcon(model.getPaymentMethod(item), AwesomeIcon.ROCKET);
                                        } else {
                                            field = new HyperlinkWithIcon(model.getPaymentMethod(item));
                                        }
                                        field.setOnAction(event -> offerDetailsWindow.show(item.getOffer()));
                                        field.setTooltip(new Tooltip(model.getPaymentMethodToolTip(item)));
                                        setGraphic(field);
                                    }
                                } else {
                                    setGraphic(null);
                                    if (field != null)
                                        field.setOnAction(null);
                                }
                            }
                        };
                    }
                });
        return column;
    }


    private AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> getDepositColumn() {
        AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> column = new AutoTooltipTableColumn<>(
                Res.get("offerbook.deposit"),
                Res.get("offerbook.deposit.help")) {
            {
                setMinWidth(70);
                setSortable(true);
            }
        };

        column.getStyleClass().add("number-column");
        column.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        column.setCellFactory(
                new Callback<>() {
                    @Override
                    public TableCell<OfferBookListItem, OfferBookListItem> call(
                            TableColumn<OfferBookListItem, OfferBookListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final OfferBookListItem item, boolean empty) {
                                super.updateItem(item, empty);
                                if (item != null && !empty) {
                                    var isSellOffer = item.getOffer().getDirection() == OfferPayload.Direction.SELL;
                                    var deposit = isSellOffer ? item.getOffer().getBuyerSecurityDeposit() :
                                            item.getOffer().getSellerSecurityDeposit();
                                    if (deposit == null) {
                                        setText(Res.get("shared.na"));
                                        setGraphic(null);
                                    } else {
                                        setText("");
                                        setGraphic(new ColoredDecimalPlacesWithZerosText(model.formatDepositString(
                                                deposit, item.getOffer().getAmount().getValue()),
                                                GUIUtil.AMOUNT_DECIMALS_WITH_ZEROS));
                                    }
                                } else {
                                    setText("");
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
        return column;
    }

    private TableColumn<OfferBookListItem, OfferBookListItem> getActionColumn() {
        TableColumn<OfferBookListItem, OfferBookListItem> column = new AutoTooltipTableColumn<>(Res.get("shared.actions")) {
            {
                setMinWidth(180);
                setSortable(false);
            }
        };
        column.getStyleClass().addAll("last-column", "avatar-column");
        column.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        column.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<OfferBookListItem, OfferBookListItem> call(TableColumn<OfferBookListItem, OfferBookListItem> column) {
                        return new TableCell<>() {
                            final ImageView iconView = new ImageView();
                            final AutoTooltipButton button = new AutoTooltipButton();
                            OfferFilter.Result canTakeOfferResult = null;

                            {
                                button.setGraphic(iconView);
                                button.setMinWidth(200);
                                button.setMaxWidth(200);
                                button.setGraphicTextGap(10);
                            }

                            @Override
                            public void updateItem(final OfferBookListItem item, boolean empty) {
                                super.updateItem(item, empty);

                                TableRow<OfferBookListItem> tableRow = getTableRow();
                                if (item != null && !empty) {
                                    Offer offer = item.getOffer();
                                    boolean myOffer = model.isMyOffer(offer);

                                    if (tableRow != null) {
                                        canTakeOfferResult = model.offerFilter.canTakeOffer(offer, false);
                                        tableRow.setOpacity(canTakeOfferResult.isValid() || myOffer ? 1 : 0.4);

                                        if (myOffer) {
                                            button.setDefaultButton(false);
                                            tableRow.setOnMousePressed(null);
                                        } else if (canTakeOfferResult.isValid()) {
                                            // set first row button as default
                                            button.setDefaultButton(getIndex() == 0);
                                            tableRow.setOnMousePressed(null);
                                        } else {
                                            button.setDefaultButton(false);
                                            if (!myOffer) {
                                                tableRow.setOnMousePressed(e -> {
                                                    // ugly hack to get the icon clickable when deactivated
                                                    if (!(e.getTarget() instanceof ImageView || e.getTarget() instanceof Canvas))
                                                        onShowInfo(offer, canTakeOfferResult);
                                                });
                                            }
                                        }
                                    }

                                    String title;
                                    if (myOffer) {
                                        iconView.setId("image-remove");
                                        title = Res.get("shared.remove");
                                        button.setId(null);
                                        button.setStyle(CssTheme.isDarkTheme() ? "-fx-text-fill: white" : "-fx-text-fill: #444444");
                                        button.setOnAction(e -> onRemoveOpenOffer(offer));
                                    } else {
                                        boolean isSellOffer = offer.getDirection() == OfferPayload.Direction.SELL;
                                        iconView.setId(isSellOffer ? "image-buy-white" : "image-sell-white");
                                        button.setId(isSellOffer ? "buy-button" : "sell-button");
                                        button.setStyle("-fx-text-fill: white");
                                        if (isSellOffer) {
                                            title = CurrencyUtil.isFiatCurrency(offer.getCurrencyCode()) ?
                                                    Res.get("offerbook.takeOfferToBuy", offer.getOfferPayload().getBaseCurrencyCode()) :
                                                    Res.get("offerbook.takeOfferToSell", offer.getCurrencyCode());
                                        } else {
                                            title = CurrencyUtil.isFiatCurrency(offer.getCurrencyCode()) ?
                                                    Res.get("offerbook.takeOfferToSell", offer.getOfferPayload().getBaseCurrencyCode()) :
                                                    Res.get("offerbook.takeOfferToBuy", offer.getCurrencyCode());
                                        }
                                        button.setTooltip(new Tooltip(Res.get("offerbook.takeOfferButton.tooltip", model.getDirectionLabelTooltip(offer))));
                                        button.setOnAction(e -> onTakeOffer(offer));
                                    }

                                    if (!myOffer) {
                                        if (canTakeOfferResult == null) {
                                            canTakeOfferResult = model.offerFilter.canTakeOffer(offer, false);
                                        }

                                        if (!canTakeOfferResult.isValid()) {
                                            button.setOnAction(e -> onShowInfo(offer, canTakeOfferResult));
                                        }
                                    }

                                    button.updateText(title);
                                    setPadding(new Insets(0, 15, 0, 0));
                                    setGraphic(button);
                                } else {
                                    setGraphic(null);
                                    button.setOnAction(null);
                                    if (tableRow != null) {
                                        tableRow.setOpacity(1);
                                        tableRow.setOnMousePressed(null);
                                    }
                                }
                            }
                        };
                    }
                });
        return column;
    }

    private AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> getSigningStateColumn() {
        AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> column = new AutoTooltipTableColumn<>(
                Res.get("offerbook.timeSinceSigning"),
                Res.get("offerbook.timeSinceSigning.help",
                        SignedWitnessService.SIGNER_AGE_DAYS,
                        formatter.formatCoinWithCode(OfferRestrictions.TOLERATED_SMALL_TRADE_AMOUNT))) {
            {
                setMinWidth(60);
                setSortable(true);
            }
        };

        column.getStyleClass().add("number-column");
        column.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<OfferBookListItem, OfferBookListItem> call(TableColumn<OfferBookListItem, OfferBookListItem> column) {
                return new TableCell<>() {
                    @Override
                    public void updateItem(final OfferBookListItem item, boolean empty) {
                        super.updateItem(item, empty);

                        if (item != null && !empty) {
                            var witnessAgeData = item.getWitnessAgeData(accountAgeWitnessService, signedWitnessService);
                            var label = witnessAgeData.isSigningRequired()
                                    ? new AccountStatusTooltipLabel(witnessAgeData, formatter)
                                    : new InfoAutoTooltipLabel(witnessAgeData.getDisplayString(), witnessAgeData.getIcon(), ContentDisplay.RIGHT, witnessAgeData.getInfo());
                            setGraphic(label);
                        } else {
                            setGraphic(null);
                        }
                    }
                };
            }
        });
        return column;
    }

    private AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> getAvatarColumn() {
        AutoTooltipTableColumn<OfferBookListItem, OfferBookListItem> column = new AutoTooltipTableColumn<>(Res.get("offerbook.trader")) {
            {
                setMinWidth(60);
                setMaxWidth(60);
                setSortable(true);
            }
        };
        column.getStyleClass().addAll("last-column", "avatar-column");
        column.setCellValueFactory((offer) -> new ReadOnlyObjectWrapper<>(offer.getValue()));
        column.setCellFactory(
                new Callback<>() {

                    @Override
                    public TableCell<OfferBookListItem, OfferBookListItem> call(TableColumn<OfferBookListItem, OfferBookListItem> column) {
                        return new TableCell<>() {
                            @Override
                            public void updateItem(final OfferBookListItem newItem, boolean empty) {
                                super.updateItem(newItem, empty);
                                if (newItem != null && !empty) {
                                    final Offer offer = newItem.getOffer();
                                    final NodeAddress makersNodeAddress = offer.getOwnerNodeAddress();
                                    String role = Res.get("peerInfoIcon.tooltip.maker");
                                    int numTrades = model.getNumTrades(offer);
                                    PeerInfoIconTrading peerInfoIcon = new PeerInfoIconTrading(makersNodeAddress,
                                            role,
                                            numTrades,
                                            privateNotificationManager,
                                            offer,
                                            model.preferences,
                                            model.accountAgeWitnessService,
                                            useDevPrivilegeKeys);
                                    setGraphic(peerInfoIcon);
                                } else {
                                    setGraphic(null);
                                }
                            }
                        };
                    }
                });
        return column;
    }

    @NotNull
    private Region getSpacer() {
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}
