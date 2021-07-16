/*
 * Copyright (c) 2013 - 2015, ControlsFX
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *     * Neither the name of ControlsFX, any associated website, nor the
 * names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL CONTROLSFX BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package bisq.desktop.components.controlsfx.skin;

import bisq.desktop.components.controlsfx.control.PopOver;
import bisq.desktop.components.controlsfx.control.PopOver.ArrowLocation;

import javafx.stage.Window;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.HLineTo;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.QuadCurveTo;
import javafx.scene.shape.VLineTo;

import javafx.geometry.Point2D;
import javafx.geometry.Pos;

import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

import javafx.event.EventHandler;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bisq.desktop.components.controlsfx.control.PopOver.ArrowLocation.*;
import static java.lang.Double.MAX_VALUE;
import static javafx.geometry.Pos.CENTER_LEFT;
import static javafx.scene.control.ContentDisplay.GRAPHIC_ONLY;
import static javafx.scene.paint.Color.YELLOW;

public class PopOverSkin implements Skin<PopOver> {

    private static final String DETACHED_STYLE_CLASS = "detached"; //$NON-NLS-1$

    private double xOffset;
    private double yOffset;

    private boolean tornOff;

    private final Path path;
    private final Path clip;

    private final BorderPane content;
    private final StackPane titlePane;
    private final StackPane stackPane;

    private Point2D dragStartLocation;

    private final PopOver popOver;

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    public PopOverSkin(final PopOver popOver) {

        this.popOver = popOver;

        stackPane = popOver.getRoot();
        stackPane.setPickOnBounds(false);

        Bindings.bindContent(stackPane.getStyleClass(), popOver.getStyleClass());

        /*
         * The min width and height equal 2 * corner radius + 2 * arrow indent +
         * 2 * arrow size.
         */
        stackPane.minWidthProperty().bind(
                Bindings.add(Bindings.multiply(2, popOver.arrowSizeProperty()),
                        Bindings.add(
                                Bindings.multiply(2,
                                        popOver.cornerRadiusProperty()),
                                Bindings.multiply(2,
                                        popOver.arrowIndentProperty()))));

        stackPane.minHeightProperty().bind(stackPane.minWidthProperty());

        Label title = new Label();
        title.textProperty().bind(popOver.titleProperty());
        title.setMaxSize(MAX_VALUE, MAX_VALUE);
        title.setAlignment(Pos.CENTER);
        title.getStyleClass().add("text"); //$NON-NLS-1$

        Label closeIcon = new Label();
        closeIcon.setGraphic(createCloseIcon());
        closeIcon.setMaxSize(MAX_VALUE, MAX_VALUE);
        closeIcon.setContentDisplay(GRAPHIC_ONLY);
        closeIcon.visibleProperty().bind(
                popOver.closeButtonEnabledProperty().and(
                        popOver.detachedProperty().or(popOver.headerAlwaysVisibleProperty())));
        closeIcon.getStyleClass().add("icon"); //$NON-NLS-1$
        closeIcon.setAlignment(CENTER_LEFT);
        closeIcon.getGraphic().setOnMouseClicked(evt -> popOver.hide());

        titlePane = new StackPane();
        titlePane.getChildren().add(title);
        titlePane.getChildren().add(closeIcon);
        titlePane.getStyleClass().add("title"); //$NON-NLS-1$

        content = new BorderPane();
        content.setCenter(popOver.getContentNode());
        content.getStyleClass().add("content"); //$NON-NLS-1$

        if (popOver.isDetached() || popOver.isHeaderAlwaysVisible()) {
            content.setTop(titlePane);
        }

        if (popOver.isDetached()) {
            popOver.getStyleClass().add(DETACHED_STYLE_CLASS);
            content.getStyleClass().add(DETACHED_STYLE_CLASS);
        }

        popOver.headerAlwaysVisibleProperty().addListener((o, oV, isVisible) -> {
            if (isVisible) {
                content.setTop(titlePane);
            } else if (!popOver.isDetached()) {
                content.setTop(null);
            }
        });

        InvalidationListener updatePathListener = observable -> updatePath();
        getPopupWindow().xProperty().addListener(updatePathListener);
        getPopupWindow().yProperty().addListener(updatePathListener);
        popOver.arrowLocationProperty().addListener(updatePathListener);
        popOver.contentNodeProperty().addListener(
                (value, oldContent, newContent) -> content
                        .setCenter(newContent));
        popOver.detachedProperty()
                .addListener((value, oldDetached, newDetached) -> {

                    if (newDetached) {
                        popOver.getStyleClass().add(DETACHED_STYLE_CLASS);
                        content.getStyleClass().add(DETACHED_STYLE_CLASS);
                        content.setTop(titlePane);

                        switch (getSkinnable().getArrowLocation()) {
                            case LEFT_TOP:
                            case LEFT_CENTER:
                            case LEFT_BOTTOM:
                                popOver.setAnchorX(
                                        popOver.getAnchorX() + popOver.getArrowSize());
                                break;
                            case TOP_LEFT:
                            case TOP_CENTER:
                            case TOP_RIGHT:
                                popOver.setAnchorY(
                                        popOver.getAnchorY() + popOver.getArrowSize());
                                break;
                            default:
                                break;
                        }
                    } else {
                        popOver.getStyleClass().remove(DETACHED_STYLE_CLASS);
                        content.getStyleClass().remove(DETACHED_STYLE_CLASS);

                        if (!popOver.isHeaderAlwaysVisible()) {
                            content.setTop(null);
                        }
                    }

                    popOver.sizeToScene();

                    updatePath();
                });

        path = new Path();
        path.getStyleClass().add("border"); //$NON-NLS-1$
        path.setManaged(false);

        clip = new Path();

        /*
         * The clip is a path and the path has to be filled with a color.
         * Otherwise clipping will not work.
         */
        clip.setFill(YELLOW);

        createPathElements();
        updatePath();

        final EventHandler<MouseEvent> mousePressedHandler = evt -> {
            log.info("mousePressed:" + popOver.isDetachable() + "," + popOver.isDetached());
            if (popOver.isDetachable() || popOver.isDetached()) {
                tornOff = false;

                xOffset = evt.getScreenX();
                yOffset = evt.getScreenY();

                dragStartLocation = new Point2D(xOffset, yOffset);
            }
        };

        final EventHandler<MouseEvent> mouseReleasedHandler = evt -> {
            log.info("mouseReleased:tornOff" + tornOff + ", " + !getSkinnable().isDetached());
            if (tornOff && !getSkinnable().isDetached()) {
                tornOff = false;
                getSkinnable().detach();
            }
        };

        final EventHandler<MouseEvent> mouseDragHandler = evt -> {
            log.info("mouseDrag:" + popOver.isDetachable() + "," + popOver.isDetached());
            if (popOver.isDetachable() || popOver.isDetached()) {
                double deltaX = evt.getScreenX() - xOffset;
                double deltaY = evt.getScreenY() - yOffset;

                Window window = getSkinnable().getScene().getWindow();

                window.setX(window.getX() + deltaX);
                window.setY(window.getY() + deltaY);

                xOffset = evt.getScreenX();
                yOffset = evt.getScreenY();

                if (dragStartLocation.distance(xOffset, yOffset) > 20) {
                    tornOff = true;
                    updatePath();
                } else if (tornOff) {
                    tornOff = false;
                    updatePath();
                }
            }
        };

        stackPane.setOnMousePressed(mousePressedHandler);
        stackPane.setOnMouseDragged(mouseDragHandler);
        stackPane.setOnMouseReleased(mouseReleasedHandler);

        stackPane.getChildren().add(path);
        stackPane.getChildren().add(content);

        content.setClip(clip);
    }

    @Override
    public Node getNode() {
        return stackPane;
    }

    @Override
    public PopOver getSkinnable() {
        return popOver;
    }

    @Override
    public void dispose() {
    }

    private Node createCloseIcon() {
        Group group = new Group();
        group.getStyleClass().add("graphics"); //$NON-NLS-1$

        Circle circle = new Circle();
        circle.getStyleClass().add("circle"); //$NON-NLS-1$
        circle.setRadius(6);
        circle.setCenterX(6);
        circle.setCenterY(6);
        group.getChildren().add(circle);

        Line line1 = new Line();
        line1.getStyleClass().add("line"); //$NON-NLS-1$
        line1.setStartX(4);
        line1.setStartY(4);
        line1.setEndX(8);
        line1.setEndY(8);
        group.getChildren().add(line1);

        Line line2 = new Line();
        line2.getStyleClass().add("line"); //$NON-NLS-1$
        line2.setStartX(8);
        line2.setStartY(4);
        line2.setEndX(4);
        line2.setEndY(8);
        group.getChildren().add(line2);

        return group;
    }

    private MoveTo moveTo;

    private QuadCurveTo topCurveTo, rightCurveTo, bottomCurveTo, leftCurveTo;

    private HLineTo lineBTop, lineETop, lineHTop, lineKTop;
    private LineTo lineCTop, lineDTop, lineFTop, lineGTop, lineITop, lineJTop;

    private VLineTo lineBRight, lineERight, lineHRight, lineKRight;
    private LineTo lineCRight, lineDRight, lineFRight, lineGRight, lineIRight,
            lineJRight;

    private HLineTo lineBBottom, lineEBottom, lineHBottom, lineKBottom;
    private LineTo lineCBottom, lineDBottom, lineFBottom, lineGBottom,
            lineIBottom, lineJBottom;

    private VLineTo lineBLeft, lineELeft, lineHLeft, lineKLeft;
    private LineTo lineCLeft, lineDLeft, lineFLeft, lineGLeft, lineILeft,
            lineJLeft;

    private void createPathElements() {
        DoubleProperty centerYProperty = new SimpleDoubleProperty();
        DoubleProperty centerXProperty = new SimpleDoubleProperty();

        DoubleProperty leftEdgeProperty = new SimpleDoubleProperty();
        DoubleProperty leftEdgePlusRadiusProperty = new SimpleDoubleProperty();

        DoubleProperty topEdgeProperty = new SimpleDoubleProperty();
        DoubleProperty topEdgePlusRadiusProperty = new SimpleDoubleProperty();

        DoubleProperty rightEdgeProperty = new SimpleDoubleProperty();
        DoubleProperty rightEdgeMinusRadiusProperty = new SimpleDoubleProperty();

        DoubleProperty bottomEdgeProperty = new SimpleDoubleProperty();
        DoubleProperty bottomEdgeMinusRadiusProperty = new SimpleDoubleProperty();

        DoubleProperty cornerProperty = getSkinnable().cornerRadiusProperty();

        DoubleProperty arrowSizeProperty = getSkinnable().arrowSizeProperty();
        DoubleProperty arrowIndentProperty = getSkinnable()
                .arrowIndentProperty();

        centerYProperty.bind(Bindings.divide(stackPane.heightProperty(), 2));
        centerXProperty.bind(Bindings.divide(stackPane.widthProperty(), 2));

        leftEdgePlusRadiusProperty.bind(Bindings.add(leftEdgeProperty,
                getSkinnable().cornerRadiusProperty()));

        topEdgePlusRadiusProperty.bind(Bindings.add(topEdgeProperty,
                getSkinnable().cornerRadiusProperty()));

        rightEdgeProperty.bind(stackPane.widthProperty());
        rightEdgeMinusRadiusProperty.bind(Bindings.subtract(rightEdgeProperty,
                getSkinnable().cornerRadiusProperty()));

        bottomEdgeProperty.bind(stackPane.heightProperty());
        bottomEdgeMinusRadiusProperty.bind(Bindings.subtract(
                bottomEdgeProperty, getSkinnable().cornerRadiusProperty()));

        // INIT
        moveTo = new MoveTo();
        moveTo.xProperty().bind(leftEdgePlusRadiusProperty);
        moveTo.yProperty().bind(topEdgeProperty);

        //
        // TOP EDGE
        //
        lineBTop = new HLineTo();
        lineBTop.xProperty().bind(
                Bindings.add(leftEdgePlusRadiusProperty, arrowIndentProperty));

        lineCTop = new LineTo();
        lineCTop.xProperty().bind(
                Bindings.add(lineBTop.xProperty(), arrowSizeProperty));
        lineCTop.yProperty().bind(
                Bindings.subtract(topEdgeProperty, arrowSizeProperty));

        lineDTop = new LineTo();
        lineDTop.xProperty().bind(
                Bindings.add(lineCTop.xProperty(), arrowSizeProperty));
        lineDTop.yProperty().bind(topEdgeProperty);

        lineETop = new HLineTo();
        lineETop.xProperty().bind(
                Bindings.subtract(centerXProperty, arrowSizeProperty));

        lineFTop = new LineTo();
        lineFTop.xProperty().bind(centerXProperty);
        lineFTop.yProperty().bind(
                Bindings.subtract(topEdgeProperty, arrowSizeProperty));

        lineGTop = new LineTo();
        lineGTop.xProperty().bind(
                Bindings.add(centerXProperty, arrowSizeProperty));
        lineGTop.yProperty().bind(topEdgeProperty);

        lineHTop = new HLineTo();
        lineHTop.xProperty().bind(
                Bindings.subtract(Bindings.subtract(
                        rightEdgeMinusRadiusProperty, arrowIndentProperty),
                        Bindings.multiply(arrowSizeProperty, 2)));

        lineITop = new LineTo();
        lineITop.xProperty().bind(
                Bindings.subtract(Bindings.subtract(
                        rightEdgeMinusRadiusProperty, arrowIndentProperty),
                        arrowSizeProperty));
        lineITop.yProperty().bind(
                Bindings.subtract(topEdgeProperty, arrowSizeProperty));

        lineJTop = new LineTo();
        lineJTop.xProperty().bind(
                Bindings.subtract(rightEdgeMinusRadiusProperty,
                        arrowIndentProperty));
        lineJTop.yProperty().bind(topEdgeProperty);

        lineKTop = new HLineTo();
        lineKTop.xProperty().bind(rightEdgeMinusRadiusProperty);

        //
        // RIGHT EDGE
        //
        rightCurveTo = new QuadCurveTo();
        rightCurveTo.xProperty().bind(rightEdgeProperty);
        rightCurveTo.yProperty().bind(
                Bindings.add(topEdgeProperty, cornerProperty));
        rightCurveTo.controlXProperty().bind(rightEdgeProperty);
        rightCurveTo.controlYProperty().bind(topEdgeProperty);

        lineBRight = new VLineTo();
        lineBRight.yProperty().bind(
                Bindings.add(topEdgePlusRadiusProperty, arrowIndentProperty));

        lineCRight = new LineTo();
        lineCRight.xProperty().bind(
                Bindings.add(rightEdgeProperty, arrowSizeProperty));
        lineCRight.yProperty().bind(
                Bindings.add(lineBRight.yProperty(), arrowSizeProperty));

        lineDRight = new LineTo();
        lineDRight.xProperty().bind(rightEdgeProperty);
        lineDRight.yProperty().bind(
                Bindings.add(lineCRight.yProperty(), arrowSizeProperty));

        lineERight = new VLineTo();
        lineERight.yProperty().bind(
                Bindings.subtract(centerYProperty, arrowSizeProperty));

        lineFRight = new LineTo();
        lineFRight.xProperty().bind(
                Bindings.add(rightEdgeProperty, arrowSizeProperty));
        lineFRight.yProperty().bind(centerYProperty);

        lineGRight = new LineTo();
        lineGRight.xProperty().bind(rightEdgeProperty);
        lineGRight.yProperty().bind(
                Bindings.add(centerYProperty, arrowSizeProperty));

        lineHRight = new VLineTo();
        lineHRight.yProperty().bind(
                Bindings.subtract(Bindings.subtract(
                        bottomEdgeMinusRadiusProperty, arrowIndentProperty),
                        Bindings.multiply(arrowSizeProperty, 2)));

        lineIRight = new LineTo();
        lineIRight.xProperty().bind(
                Bindings.add(rightEdgeProperty, arrowSizeProperty));
        lineIRight.yProperty().bind(
                Bindings.subtract(Bindings.subtract(
                        bottomEdgeMinusRadiusProperty, arrowIndentProperty),
                        arrowSizeProperty));

        lineJRight = new LineTo();
        lineJRight.xProperty().bind(rightEdgeProperty);
        lineJRight.yProperty().bind(
                Bindings.subtract(bottomEdgeMinusRadiusProperty,
                        arrowIndentProperty));

        lineKRight = new VLineTo();
        lineKRight.yProperty().bind(bottomEdgeMinusRadiusProperty);

        //
        // BOTTOM EDGE
        //

        bottomCurveTo = new QuadCurveTo();
        bottomCurveTo.xProperty().bind(rightEdgeMinusRadiusProperty);
        bottomCurveTo.yProperty().bind(bottomEdgeProperty);
        bottomCurveTo.controlXProperty().bind(rightEdgeProperty);
        bottomCurveTo.controlYProperty().bind(bottomEdgeProperty);

        lineBBottom = new HLineTo();
        lineBBottom.xProperty().bind(
                Bindings.subtract(rightEdgeMinusRadiusProperty,
                        arrowIndentProperty));

        lineCBottom = new LineTo();
        lineCBottom.xProperty().bind(
                Bindings.subtract(lineBBottom.xProperty(), arrowSizeProperty));
        lineCBottom.yProperty().bind(
                Bindings.add(bottomEdgeProperty, arrowSizeProperty));

        lineDBottom = new LineTo();
        lineDBottom.xProperty().bind(
                Bindings.subtract(lineCBottom.xProperty(), arrowSizeProperty));
        lineDBottom.yProperty().bind(bottomEdgeProperty);

        lineEBottom = new HLineTo();
        lineEBottom.xProperty().bind(
                Bindings.add(centerXProperty, arrowSizeProperty));

        lineFBottom = new LineTo();
        lineFBottom.xProperty().bind(centerXProperty);
        lineFBottom.yProperty().bind(
                Bindings.add(bottomEdgeProperty, arrowSizeProperty));

        lineGBottom = new LineTo();
        lineGBottom.xProperty().bind(
                Bindings.subtract(centerXProperty, arrowSizeProperty));
        lineGBottom.yProperty().bind(bottomEdgeProperty);

        lineHBottom = new HLineTo();
        lineHBottom.xProperty().bind(
                Bindings.add(Bindings.add(leftEdgePlusRadiusProperty,
                        arrowIndentProperty), Bindings.multiply(
                        arrowSizeProperty, 2)));

        lineIBottom = new LineTo();
        lineIBottom.xProperty().bind(
                Bindings.add(Bindings.add(leftEdgePlusRadiusProperty,
                        arrowIndentProperty), arrowSizeProperty));
        lineIBottom.yProperty().bind(
                Bindings.add(bottomEdgeProperty, arrowSizeProperty));

        lineJBottom = new LineTo();
        lineJBottom.xProperty().bind(
                Bindings.add(leftEdgePlusRadiusProperty, arrowIndentProperty));
        lineJBottom.yProperty().bind(bottomEdgeProperty);

        lineKBottom = new HLineTo();
        lineKBottom.xProperty().bind(leftEdgePlusRadiusProperty);

        //
        // LEFT EDGE
        //
        leftCurveTo = new QuadCurveTo();
        leftCurveTo.xProperty().bind(leftEdgeProperty);
        leftCurveTo.yProperty().bind(
                Bindings.subtract(bottomEdgeProperty, cornerProperty));
        leftCurveTo.controlXProperty().bind(leftEdgeProperty);
        leftCurveTo.controlYProperty().bind(bottomEdgeProperty);

        lineBLeft = new VLineTo();
        lineBLeft.yProperty().bind(
                Bindings.subtract(bottomEdgeMinusRadiusProperty,
                        arrowIndentProperty));

        lineCLeft = new LineTo();
        lineCLeft.xProperty().bind(
                Bindings.subtract(leftEdgeProperty, arrowSizeProperty));
        lineCLeft.yProperty().bind(
                Bindings.subtract(lineBLeft.yProperty(), arrowSizeProperty));

        lineDLeft = new LineTo();
        lineDLeft.xProperty().bind(leftEdgeProperty);
        lineDLeft.yProperty().bind(
                Bindings.subtract(lineCLeft.yProperty(), arrowSizeProperty));

        lineELeft = new VLineTo();
        lineELeft.yProperty().bind(
                Bindings.add(centerYProperty, arrowSizeProperty));

        lineFLeft = new LineTo();
        lineFLeft.xProperty().bind(
                Bindings.subtract(leftEdgeProperty, arrowSizeProperty));
        lineFLeft.yProperty().bind(centerYProperty);

        lineGLeft = new LineTo();
        lineGLeft.xProperty().bind(leftEdgeProperty);
        lineGLeft.yProperty().bind(
                Bindings.subtract(centerYProperty, arrowSizeProperty));

        lineHLeft = new VLineTo();
        lineHLeft.yProperty().bind(
                Bindings.add(Bindings.add(topEdgePlusRadiusProperty,
                        arrowIndentProperty), Bindings.multiply(
                        arrowSizeProperty, 2)));

        lineILeft = new LineTo();
        lineILeft.xProperty().bind(
                Bindings.subtract(leftEdgeProperty, arrowSizeProperty));
        lineILeft.yProperty().bind(
                Bindings.add(Bindings.add(topEdgePlusRadiusProperty,
                        arrowIndentProperty), arrowSizeProperty));

        lineJLeft = new LineTo();
        lineJLeft.xProperty().bind(leftEdgeProperty);
        lineJLeft.yProperty().bind(
                Bindings.add(topEdgePlusRadiusProperty, arrowIndentProperty));

        lineKLeft = new VLineTo();
        lineKLeft.yProperty().bind(topEdgePlusRadiusProperty);

        topCurveTo = new QuadCurveTo();
        topCurveTo.xProperty().bind(leftEdgePlusRadiusProperty);
        topCurveTo.yProperty().bind(topEdgeProperty);
        topCurveTo.controlXProperty().bind(leftEdgeProperty);
        topCurveTo.controlYProperty().bind(topEdgeProperty);
    }

    private Window getPopupWindow() {
        return getSkinnable().getScene().getWindow();
    }

    private boolean showArrow(ArrowLocation location) {
        ArrowLocation arrowLocation = getSkinnable().getArrowLocation();
        return location.equals(arrowLocation) && !getSkinnable().isDetached()
                && !tornOff;
    }

    private void updatePath() {
        List<PathElement> elements = new ArrayList<>();
        elements.add(moveTo);

        if (showArrow(TOP_LEFT)) {
            elements.add(lineBTop);
            elements.add(lineCTop);
            elements.add(lineDTop);
        }
        if (showArrow(TOP_CENTER)) {
            elements.add(lineETop);
            elements.add(lineFTop);
            elements.add(lineGTop);
        }
        if (showArrow(TOP_RIGHT)) {
            elements.add(lineHTop);
            elements.add(lineITop);
            elements.add(lineJTop);
        }
        elements.add(lineKTop);
        elements.add(rightCurveTo);

        if (showArrow(RIGHT_TOP)) {
            elements.add(lineBRight);
            elements.add(lineCRight);
            elements.add(lineDRight);
        }
        if (showArrow(RIGHT_CENTER)) {
            elements.add(lineERight);
            elements.add(lineFRight);
            elements.add(lineGRight);
        }
        if (showArrow(RIGHT_BOTTOM)) {
            elements.add(lineHRight);
            elements.add(lineIRight);
            elements.add(lineJRight);
        }
        elements.add(lineKRight);
        elements.add(bottomCurveTo);

        if (showArrow(BOTTOM_RIGHT)) {
            elements.add(lineBBottom);
            elements.add(lineCBottom);
            elements.add(lineDBottom);
        }
        if (showArrow(BOTTOM_CENTER)) {
            elements.add(lineEBottom);
            elements.add(lineFBottom);
            elements.add(lineGBottom);
        }
        if (showArrow(BOTTOM_LEFT)) {
            elements.add(lineHBottom);
            elements.add(lineIBottom);
            elements.add(lineJBottom);
        }
        elements.add(lineKBottom);
        elements.add(leftCurveTo);

        if (showArrow(LEFT_BOTTOM)) {
            elements.add(lineBLeft);
            elements.add(lineCLeft);
            elements.add(lineDLeft);
        }
        if (showArrow(LEFT_CENTER)) {
            elements.add(lineELeft);
            elements.add(lineFLeft);
            elements.add(lineGLeft);
        }
        if (showArrow(LEFT_TOP)) {
            elements.add(lineHLeft);
            elements.add(lineILeft);
            elements.add(lineJLeft);
        }
        elements.add(lineKLeft);
        elements.add(topCurveTo);

        path.getElements().setAll(elements);
        clip.getElements().setAll(elements);
    }
}
