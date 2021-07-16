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

package bisq.desktop.components;

import bisq.desktop.components.controlsfx.control.PopOver;
import bisq.desktop.main.offer.offerbook.OfferBookListItem;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;

import bisq.core.account.sign.SignedWitnessService;
import bisq.core.locale.Res;
import bisq.core.offer.OfferRestrictions;
import bisq.core.util.coin.CoinFormatter;

import bisq.common.UserThread;

import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;

import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

import java.util.concurrent.TimeUnit;


public class AccountStatusTooltipLabel extends AutoTooltipLabel {

    public static final int DEFAULT_WIDTH = 300;
    private final Node textIcon;
    private final OfferBookListItem.WitnessAgeData witnessAgeData;
    private final String popupTitle;
    private PopOver popOver;
    private boolean keepPopOverVisible = false;

    public AccountStatusTooltipLabel(OfferBookListItem.WitnessAgeData witnessAgeData,
                                     CoinFormatter formatter) {
        super(witnessAgeData.getDisplayString());
        this.witnessAgeData = witnessAgeData;
        this.textIcon = FormBuilder.getIcon(witnessAgeData.getIcon());
        this.popupTitle = witnessAgeData.isLimitLifted()
                ? Res.get("offerbook.timeSinceSigning.tooltip.accountLimitLifted")
                : Res.get("offerbook.timeSinceSigning.tooltip.accountLimit", formatter.formatCoinWithCode(OfferRestrictions.TOLERATED_SMALL_TRADE_AMOUNT));

        positionAndActivateIcon();
    }

    private void positionAndActivateIcon() {
        textIcon.setOpacity(0.4);
        textIcon.getStyleClass().add("tooltip-icon");
        popOver = createPopOver();
        textIcon.setOnMouseEntered(e -> showPopup(textIcon));

        textIcon.setOnMouseExited(e -> UserThread.runAfter(() -> {
                    if (!keepPopOverVisible) {
                        popOver.hide();
                    }
                }, 200, TimeUnit.MILLISECONDS)
        );

        setGraphic(textIcon);
        setContentDisplay(ContentDisplay.RIGHT);
    }

    private PopOver createPopOver() {
        Label titleLabel = new Label(popupTitle);
        titleLabel.setMaxWidth(DEFAULT_WIDTH);
        titleLabel.setWrapText(true);
        titleLabel.setPadding(new Insets(10, 10, 0, 10));
        titleLabel.getStyleClass().add("account-status-title");

        Label infoLabel = new Label(witnessAgeData.getInfo());
        infoLabel.setMaxWidth(DEFAULT_WIDTH);
        infoLabel.setWrapText(true);
        infoLabel.setPadding(new Insets(0, 10, 4, 10));
        infoLabel.getStyleClass().add("small-text");

        Label buyLabel = createDetailsItem(
                Res.get("offerbook.timeSinceSigning.tooltip.checkmark.buyBtc"),
                witnessAgeData.isAccountSigned()
        );
        Label waitLabel = createDetailsItem(
                Res.get("offerbook.timeSinceSigning.tooltip.checkmark.wait", SignedWitnessService.SIGNER_AGE_DAYS),
                witnessAgeData.isLimitLifted()
        );

        Hyperlink learnMoreLink = new ExternalHyperlink(Res.get("offerbook.timeSinceSigning.tooltip.learnMore"),
                null,
                "0.769em");
        learnMoreLink.setMaxWidth(DEFAULT_WIDTH);
        learnMoreLink.setWrapText(true);
        learnMoreLink.setPadding(new Insets(10, 10, 2, 10));
        learnMoreLink.getStyleClass().addAll("very-small-text");
        learnMoreLink.setOnAction((e) -> GUIUtil.openWebPage("https://bisq.wiki/Account_limits"));

        VBox vBox = new VBox(2, titleLabel, infoLabel, buyLabel, waitLabel, learnMoreLink);
        vBox.setPadding(new Insets(2, 0, 2, 0));
        vBox.setAlignment(Pos.CENTER_LEFT);


        PopOver popOver = new PopOver(vBox);
        popOver.setArrowLocation(PopOver.ArrowLocation.LEFT_CENTER);

        vBox.setOnMouseEntered(mouseEvent -> keepPopOverVisible = true);

        vBox.setOnMouseExited(mouseEvent -> {
            keepPopOverVisible = false;
            popOver.hide();
        });

        return popOver;
    }

    private void showPopup(Node textIcon) {
        Bounds bounds = textIcon.localToScreen(textIcon.getBoundsInLocal());
        popOver.show(textIcon, bounds.getMaxX() + 10, (bounds.getMinY() + bounds.getHeight() / 2) - 10);
    }

    private Label createDetailsItem(String text, boolean active) {
        Label label = new Label(text);
        label.setMaxWidth(DEFAULT_WIDTH);
        label.setWrapText(true);
        label.setPadding(new Insets(0, 10, 0, 10));
        label.getStyleClass().add("small-text");
        if (active) {
            label.setStyle("-fx-text-fill: -fx-accent");
        } else {
            label.setStyle("-fx-text-fill: -bs-color-gray-dim");
        }

        Text icon = FormBuilder.getSmallIconForLabel(active ?
                MaterialDesignIcon.CHECKBOX_MARKED_CIRCLE : MaterialDesignIcon.CLOSE_CIRCLE, label);
        icon.setLayoutY(4);

        if (active) {
            icon.getStyleClass().add("account-status-active-info-item");
        } else {
            icon.getStyleClass().add("account-status-inactive-info-item");
        }

        return label;
    }
}
