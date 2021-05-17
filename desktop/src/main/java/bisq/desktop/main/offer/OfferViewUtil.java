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
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.main.MainView;
import bisq.desktop.main.offer.offerbook.OfferBookView;
import bisq.desktop.main.overlays.popups.Popup;

import bisq.core.locale.Res;
import bisq.core.offer.OfferPayload;
import bisq.core.user.Preferences;

import bisq.common.util.Tuple2;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

import lombok.extern.slf4j.Slf4j;

/**
 * Reusable methods for CreateOfferView, TakeOfferView or other related views
 */
@Slf4j
public class OfferViewUtil {
    public static Label createPopOverLabel(String text) {
        final Label label = new Label(text);
        label.setPrefWidth(300);
        label.setWrapText(true);
        label.setLineSpacing(1);
        label.setPadding(new Insets(10));
        return label;
    }

    public interface DirectionClosure {
        OfferPayload.Direction getDirection();
    }

    public static Tuple2<AutoTooltipButton, VBox> createBuyBsqButtonBox(Navigation navigation,
                                                                        Preferences preferences,
                                                                        DirectionClosure directionClosure) {
        String buyBsqText = Res.get("shared.buyCurrency", "BSQ");
        var buyBsqButton = new AutoTooltipButton(buyBsqText);
        buyBsqButton.getStyleClass().add("action-button");
        buyBsqButton.getStyleClass().add("tiny-button");
        buyBsqButton.setOnAction(e -> new Popup().headLine(buyBsqText)
                .information(Res.get("createOffer.buyBsq.popupMessage"))
                .actionButtonText(buyBsqText)
                .buttonAlignment(HPos.CENTER)
                .onAction(() -> {
                    preferences.setSellScreenCurrencyCode("BSQ");
                    navigation.navigateToWithData(
                            // FIXME: replace "closeOfferView" with a more unique object?
                            directionClosure.getDirection() == OfferPayload.Direction.SELL ? "closeOfferView" : null,
                            MainView.class, SellOfferView.class, OfferBookView.class);
                }).show());

        final VBox buyBsqButtonVBox = new VBox(buyBsqButton);
        buyBsqButtonVBox.setAlignment(Pos.BOTTOM_LEFT);
        buyBsqButtonVBox.setPadding(new Insets(0, 0, 0, -20));
        VBox.setMargin(buyBsqButton, new Insets(0, 0, 4, 0));

        return new Tuple2<>(buyBsqButton, buyBsqButtonVBox);
    }
}
