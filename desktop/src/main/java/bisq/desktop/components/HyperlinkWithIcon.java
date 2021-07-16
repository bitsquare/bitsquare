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

import bisq.desktop.util.FormBuilder;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import de.jensd.fx.glyphs.GlyphIcons;

import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.text.Text;

import javafx.geometry.Insets;

import lombok.Getter;

public class HyperlinkWithIcon extends Hyperlink {
    @Getter
    private Node icon;

    public HyperlinkWithIcon(String text) {
        this(text, AwesomeIcon.INFO_SIGN);
    }

    public HyperlinkWithIcon(String text, AwesomeIcon awesomeIcon) {
        super(text);

        Label icon = new Label();
        AwesomeDude.setIcon(icon, awesomeIcon);
        icon.setMinWidth(20);
        icon.setOpacity(0.7);
        icon.getStyleClass().addAll("hyperlink", "no-underline");
        setPadding(new Insets(0));
        icon.setPadding(new Insets(0));

        setIcon(icon);
    }

    public HyperlinkWithIcon(String text, GlyphIcons icon) {
        this(text, icon, null);
    }

    public HyperlinkWithIcon(String text, GlyphIcons icon, String style, String iconSize) {
        super(text);

        Text textIcon = FormBuilder.getIcon(icon, iconSize);
        textIcon.setOpacity(0.7);
        textIcon.getStyleClass().addAll("hyperlink", "no-underline");

        if (style != null) {
            textIcon.getStyleClass().add(style);
            getStyleClass().add(style);
        }

        setPadding(new Insets(0));

        setIcon(textIcon);
    }

    public HyperlinkWithIcon(String text, GlyphIcons icon, String style) {
        this(text, icon, style, "1.231em");
    }

    public void hideIcon() {
        setGraphic(null);
    }

    public void setIcon(Node icon) {
        this.icon = icon;
        setGraphic(icon);

        setContentDisplay(ContentDisplay.RIGHT);
        setGraphicTextGap(7.0);
    }

    public void clear() {
        setText("");
        setGraphic(null);
    }
}
