/*
 * Copyright (c) 2013, 2016 ControlsFX
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

package bisq.desktop.components.controlsfx.control;

import bisq.desktop.components.controlsfx.skin.PopOverSkin;

import javafx.animation.FadeTransition;

import javafx.stage.Window;
import javafx.stage.WindowEvent;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.PopupControl;
import javafx.scene.control.Skin;
import javafx.scene.layout.StackPane;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;

import javafx.event.EventHandler;
import javafx.event.WeakEventHandler;

import javafx.util.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;
import static javafx.scene.input.MouseEvent.MOUSE_CLICKED;

/**
 * The PopOver control provides detailed information about an owning node in a
 * popup window. The popup window has a very lightweight appearance (no default
 * window decorations) and an arrow pointing at the owner. Due to the nature of
 * popup windows the PopOver will move around with the parent window when the
 * user drags it. <br>
 * <center> <img src="popover.png" alt="Screenshot of PopOver"> </center> <br>
 * The PopOver can be detached from the owning node by dragging it away from the
 * owner. It stops displaying an arrow and starts displaying a title and a close
 * icon. <br>
 * <br>
 * <center> <img src="popover-detached.png"
 * alt="Screenshot of a detached PopOver"> </center> <br>
 * The following image shows a popover with an accordion content node. PopOver
 * controls are automatically resizing themselves when the content node changes
 * its size.<br>
 * <br>
 * <center> <img src="popover-accordion.png"
 * alt="Screenshot of PopOver containing an Accordion"> </center> <br>
 * For styling apply stylesheets to the root pane of the PopOver.
 *
 * <h3>Example:</h3>
 *
 * <pre>
 * PopOver popOver = new PopOver();
 * popOver.getRoot().getStylesheets().add(...);
 * </pre>
 *
 */
public class PopOver extends PopupControl {

    private static final String DEFAULT_STYLE_CLASS = "popover"; //$NON-NLS-1$

    private static final Duration DEFAULT_FADE_DURATION = Duration.seconds(.2);

    private double targetX;

    private double targetY;

    private final SimpleBooleanProperty animated = new SimpleBooleanProperty(true);
    private final ObjectProperty<Duration> fadeInDuration = new SimpleObjectProperty<>(DEFAULT_FADE_DURATION);
    private final ObjectProperty<Duration> fadeOutDuration = new SimpleObjectProperty<>(DEFAULT_FADE_DURATION);

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    /**
     * Creates a pop over with a label as the content node.
     */
    public PopOver() {
        super();

        getStyleClass().add(DEFAULT_STYLE_CLASS);

        getRoot().getStylesheets().add(
                requireNonNull(PopOver.class.getResource("popover.css")).toExternalForm()); //$NON-NLS-1$

        setAnchorLocation(AnchorLocation.WINDOW_TOP_LEFT);
        setOnHiding(evt -> setDetached(false));

        /*
         * Create some initial content.
         */
        Label label = new Label("No content set"); //$NON-NLS-1$
        label.setPrefSize(200, 200);
        label.setPadding(new Insets(4));
        setContentNode(label);

        InvalidationListener repositionListener = observable -> {
            if (isShowing() && !isDetached()) {
                show(getOwnerNode(), targetX, targetY);
                adjustWindowLocation();
            }
        };

        arrowSize.addListener(repositionListener);
        cornerRadius.addListener(repositionListener);
        arrowLocation.addListener(repositionListener);
        arrowIndent.addListener(repositionListener);
        headerAlwaysVisible.addListener(repositionListener);

        /*
         * A detached popover should of course not automatically hide itself.
         */
        detached.addListener(it -> setAutoHide(!isDetached()));

        setAutoHide(true);
    }

    /**
     * Creates a pop over with the given node as the content node.
     *
     * @param content
     *            The content shown by the pop over
     */
    public PopOver(Node content) {
        this();

        setContentNode(content);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new PopOverSkin(this);
    }

    private final StackPane root = new StackPane();

    /**
     * The root pane stores the content node of the popover. It is accessible
     * via this method in order to support proper styling.
     *
     * <h3>Example:</h3>
     *
     * <pre>
     * PopOver popOver = new PopOver();
     * popOver.getRoot().getStylesheets().add(...);
     * </pre>
     *
     * @return the root pane
     */
    public final StackPane getRoot() {
        return root;
    }

    // Content support.

    private final ObjectProperty<Node> contentNode = new SimpleObjectProperty<>(
            this, "contentNode") { //$NON-NLS-1$
        @Override
        public void setValue(Node node) {
            if (node == null) {
                throw new IllegalArgumentException(
                        "content node can not be null"); //$NON-NLS-1$
            }
        }

    };

    /**
     * Returns the content shown by the pop over.
     *
     * @return the content node property
     */
    public final ObjectProperty<Node> contentNodeProperty() {
        return contentNode;
    }

    /**
     * Returns the value of the content property
     *
     * @return the content node
     *
     * @see #contentNodeProperty()
     */
    public final Node getContentNode() {
        return contentNodeProperty().get();
    }

    /**
     * Sets the value of the content property.
     *
     * @param content
     *            the new content node value
     *
     * @see #contentNodeProperty()
     */
    public final void setContentNode(Node content) {
        contentNodeProperty().set(content);
    }

    private final InvalidationListener hideListener = observable -> {
        if (!isDetached()) {
            hide(Duration.ZERO);
        }
    };

    private final WeakInvalidationListener weakHideListener = new WeakInvalidationListener(
            hideListener);

    private final ChangeListener<Number> xListener = (value, oldX, newX) -> {
        if (!isDetached()) {
            setAnchorX(getAnchorX() + (newX.doubleValue() - oldX.doubleValue()));
        }
    };

    private final WeakChangeListener<Number> weakXListener = new WeakChangeListener<>(
            xListener);

    private final ChangeListener<Number> yListener = (value, oldY, newY) -> {
        if (!isDetached()) {
            setAnchorY(getAnchorY() + (newY.doubleValue() - oldY.doubleValue()));
        }
    };

    private final WeakChangeListener<Number> weakYListener = new WeakChangeListener<>(
            yListener);

    private Window ownerWindow;
    private final EventHandler<WindowEvent> closePopOverOnOwnerWindowCloseLambda = event -> ownerWindowClosing();
    private final WeakEventHandler<WindowEvent> closePopOverOnOwnerWindowClose = new WeakEventHandler<>(closePopOverOnOwnerWindowCloseLambda);

    /**
     * Shows the pop over in a position relative to the edges of the given owner
     * node. The position is dependent on the arrow location. If the arrow is
     * pointing to the right then the pop over will be placed to the left of the
     * given owner. If the arrow points up then the pop over will be placed
     * below the given owner node. The arrow will slightly overlap with the
     * owner node.
     *
     * @param owner
     *            the owner of the pop over
     */
    public final void show(Node owner) {
        show(owner, 4);
    }

    /**
     * Shows the pop over in a position relative to the edges of the given owner
     * node. The position is dependent on the arrow location. If the arrow is
     * pointing to the right then the pop over will be placed to the left of the
     * given owner. If the arrow points up then the pop over will be placed
     * below the given owner node.
     *
     * @param owner
     *            the owner of the pop over
     * @param offset
     *            if negative specifies the distance to the owner node or when
     *            positive specifies the number of pixels that the arrow will
     *            overlap with the owner node (positive values are recommended)
     */
    public final void show(Node owner, double offset) {
        requireNonNull(owner);

        Bounds bounds = owner.localToScreen(owner.getBoundsInLocal());

        switch (getArrowLocation()) {
            case BOTTOM_CENTER:
            case BOTTOM_LEFT:
            case BOTTOM_RIGHT:
                show(owner, bounds.getMinX() + bounds.getWidth() / 2,
                        bounds.getMinY() + offset);
                break;
            case LEFT_BOTTOM:
            case LEFT_CENTER:
            case LEFT_TOP:
                show(owner, bounds.getMaxX() - offset,
                        bounds.getMinY() + bounds.getHeight() / 2);
                break;
            case RIGHT_BOTTOM:
            case RIGHT_CENTER:
            case RIGHT_TOP:
                show(owner, bounds.getMinX() + offset,
                        bounds.getMinY() + bounds.getHeight() / 2);
                break;
            case TOP_CENTER:
            case TOP_LEFT:
            case TOP_RIGHT:
                show(owner, bounds.getMinX() + bounds.getWidth() / 2,
                        bounds.getMinY() + bounds.getHeight() - offset);
                break;
            default:
                break;
        }
    }

    /** {@inheritDoc} */
    @Override
    public final void show(Window owner) {
        super.show(owner);
        ownerWindow = owner;

        if (isAnimated()) {
            showFadeInAnimation(getFadeInDuration());
        }

        ownerWindow.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST,
                closePopOverOnOwnerWindowClose);
        ownerWindow.addEventFilter(WindowEvent.WINDOW_HIDING,
                closePopOverOnOwnerWindowClose);
    }

    /** {@inheritDoc} */
    @Override
    public final void show(Window ownerWindow, double anchorX, double anchorY) {
        super.show(ownerWindow, anchorX, anchorY);
        this.ownerWindow = ownerWindow;

        if (isAnimated()) {
            showFadeInAnimation(getFadeInDuration());
        }

        ownerWindow.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST,
                closePopOverOnOwnerWindowClose);
        ownerWindow.addEventFilter(WindowEvent.WINDOW_HIDING,
                closePopOverOnOwnerWindowClose);
    }

    /**
     * Makes the pop over visible at the give location and associates it with
     * the given owner node. The x and y coordinate will be the target location
     * of the arrow of the pop over and not the location of the window.
     *
     * @param owner
     *            the owning node
     * @param x
     *            the x coordinate for the pop over arrow tip
     * @param y
     *            the y coordinate for the pop over arrow tip
     */
    @Override
    public final void show(Node owner, double x, double y) {
        show(owner, x, y, getFadeInDuration());
    }

    /**
     * Makes the pop over visible at the give location and associates it with
     * the given owner node. The x and y coordinate will be the target location
     * of the arrow of the pop over and not the location of the window.
     *
     * @param owner
     *            the owning node
     * @param x
     *            the x coordinate for the pop over arrow tip
     * @param y
     *            the y coordinate for the pop over arrow tip
     * @param fadeInDuration
     *            the time it takes for the pop over to be fully visible. This duration takes precedence over the fade-in property without setting.
     */
    public final void show(Node owner, double x, double y,
                           Duration fadeInDuration) {

        /*
         * Calling show() a second time without first closing the pop over
         * causes it to be placed at the wrong location.
         */
        if (ownerWindow != null && isShowing()) {
            super.hide();
        }

        targetX = x;
        targetY = y;

        if (owner == null) {
            throw new IllegalArgumentException("owner can not be null"); //$NON-NLS-1$
        }

        if (fadeInDuration == null) {
            fadeInDuration = DEFAULT_FADE_DURATION;
        }

        /*
         * This is all needed because children windows do not get their x and y
         * coordinate updated when the owning window gets moved by the user.
         */
        if (ownerWindow != null) {
            ownerWindow.xProperty().removeListener(weakXListener);
            ownerWindow.yProperty().removeListener(weakYListener);
            ownerWindow.widthProperty().removeListener(weakHideListener);
            ownerWindow.heightProperty().removeListener(weakHideListener);
        }

        ownerWindow = owner.getScene().getWindow();
        ownerWindow.xProperty().addListener(weakXListener);
        ownerWindow.yProperty().addListener(weakYListener);
        ownerWindow.widthProperty().addListener(weakHideListener);
        ownerWindow.heightProperty().addListener(weakHideListener);

        setOnShown(evt -> {

            /*
             * The user clicked somewhere into the transparent background. If
             * this is the case then hide the window (when attached).
             */
            getScene().addEventHandler(MOUSE_CLICKED, mouseEvent -> {
                if (mouseEvent.getTarget().equals(getScene().getRoot())) {
                    if (!isDetached()) {
                        hide();
                    }
                }
            });

            /*
             * Move the window so that the arrow will end up pointing at the
             * target coordinates.
             */
            adjustWindowLocation();
        });

        super.show(owner, x, y);

        if (isAnimated()) {
            showFadeInAnimation(fadeInDuration);
        }

        // Bug fix - close popup when owner window is closing
        ownerWindow.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST,
                closePopOverOnOwnerWindowClose);
        ownerWindow.addEventFilter(WindowEvent.WINDOW_HIDING,
                closePopOverOnOwnerWindowClose);
    }

    private void showFadeInAnimation(Duration fadeInDuration) {
        // Fade In
        Node skinNode = getSkin().getNode();
        skinNode.setOpacity(0);

        FadeTransition fadeIn = new FadeTransition(fadeInDuration, skinNode);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
    }

    private void ownerWindowClosing() {
        hide(Duration.ZERO);
    }

    /**
     * Hides the pop over by quickly changing its opacity to 0.
     *
     * @see #hide(Duration)
     */
    @Override
    public final void hide() {
        hide(getFadeOutDuration());
    }

    /**
     * Hides the pop over by quickly changing its opacity to 0.
     *
     * @param fadeOutDuration
     *            the duration of the fade transition that is being used to
     *            change the opacity of the pop over
     * @since 1.0
     */
    public final void hide(Duration fadeOutDuration) {
        log.info("hide:" + fadeOutDuration.toString());
        //We must remove EventFilter in order to prevent memory leak.
        if (ownerWindow != null) {
            ownerWindow.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST,
                    closePopOverOnOwnerWindowClose);
            ownerWindow.removeEventFilter(WindowEvent.WINDOW_HIDING,
                    closePopOverOnOwnerWindowClose);
        }
        if (fadeOutDuration == null) {
            fadeOutDuration = DEFAULT_FADE_DURATION;
        }

        if (isShowing()) {
            if (isAnimated()) {
                // Fade Out
                Node skinNode = getSkin().getNode();

                FadeTransition fadeOut = new FadeTransition(fadeOutDuration,
                        skinNode);
                fadeOut.setFromValue(skinNode.getOpacity());
                fadeOut.setToValue(0);
                fadeOut.setOnFinished(evt -> super.hide());
                fadeOut.play();
            } else {
                super.hide();
            }
        }
    }

    private void adjustWindowLocation() {
        Bounds bounds = PopOver.this.getSkin().getNode().getBoundsInParent();

        switch (getArrowLocation()) {
            case TOP_CENTER:
            case TOP_LEFT:
            case TOP_RIGHT:
                setAnchorX(getAnchorX() + bounds.getMinX() - computeXOffset());
                setAnchorY(getAnchorY() + bounds.getMinY() + getArrowSize());
                break;
            case LEFT_TOP:
            case LEFT_CENTER:
            case LEFT_BOTTOM:
                setAnchorX(getAnchorX() + bounds.getMinX() + getArrowSize());
                setAnchorY(getAnchorY() + bounds.getMinY() - computeYOffset());
                break;
            case BOTTOM_CENTER:
            case BOTTOM_LEFT:
            case BOTTOM_RIGHT:
                setAnchorX(getAnchorX() + bounds.getMinX() - computeXOffset());
                setAnchorY(getAnchorY() - bounds.getMinY() - bounds.getMaxY() - 1);
                break;
            case RIGHT_TOP:
            case RIGHT_BOTTOM:
            case RIGHT_CENTER:
                setAnchorX(getAnchorX() - bounds.getMinX() - bounds.getMaxX() - 1);
                setAnchorY(getAnchorY() + bounds.getMinY() - computeYOffset());
                break;
        }
    }

    private double computeXOffset() {
        switch (getArrowLocation()) {
            case TOP_LEFT:
            case BOTTOM_LEFT:
                return getCornerRadius() + getArrowIndent() + getArrowSize();
            case TOP_CENTER:
            case BOTTOM_CENTER:
                return getContentNode().prefWidth(-1) / 2;
            case TOP_RIGHT:
            case BOTTOM_RIGHT:
                return getContentNode().prefWidth(-1) - getArrowIndent()
                        - getCornerRadius() - getArrowSize();
            default:
                return 0;
        }
    }

    private double computeYOffset() {
        double prefContentHeight = getContentNode().prefHeight(-1);

        switch (getArrowLocation()) {
            case LEFT_TOP:
            case RIGHT_TOP:
                return getCornerRadius() + getArrowIndent() + getArrowSize();
            case LEFT_CENTER:
            case RIGHT_CENTER:
                return Math.max(prefContentHeight, 2 * (getCornerRadius()
                        + getArrowIndent() + getArrowSize())) / 2;
            case LEFT_BOTTOM:
            case RIGHT_BOTTOM:
                return Math.max(prefContentHeight - getCornerRadius()
                        - getArrowIndent() - getArrowSize(), getCornerRadius()
                        + getArrowIndent() + getArrowSize());
            default:
                return 0;
        }
    }

    /**
     * Detaches the pop over from the owning node. The pop over will no longer
     * display an arrow pointing at the owner node.
     */
    public final void detach() {
        if (isDetachable()) {
            setDetached(true);
        }
    }

    // always show header

    private final BooleanProperty headerAlwaysVisible = new SimpleBooleanProperty(this, "headerAlwaysVisible"); //$NON-NLS-1$

    /**
     * Determines whether or not the {@link PopOver} header should remain visible, even while attached.
     */
    public final BooleanProperty headerAlwaysVisibleProperty() {
        return headerAlwaysVisible;
    }

    /**
     * Sets the value of the headerAlwaysVisible property.
     *
     * @param visible
     *            if true, then the header is visible even while attached
     *
     * @see #headerAlwaysVisibleProperty()
     */
    public final void setHeaderAlwaysVisible(boolean visible) {
        headerAlwaysVisible.setValue(visible);
    }

    /**
     * Returns the value of the detachable property.
     *
     * @return true if the header is visible even while attached
     *
     * @see #headerAlwaysVisibleProperty()
     */
    public final boolean isHeaderAlwaysVisible() {
        return headerAlwaysVisible.getValue();
    }

    // enable close button

    private final BooleanProperty closeButtonEnabled = new SimpleBooleanProperty(this, "closeButtonEnabled", true); //$NON-NLS-1$

    /**
     * Determines whether or not the header's close button should be available.
     */
    public final BooleanProperty closeButtonEnabledProperty() {
        return closeButtonEnabled;
    }

    /**
     * Sets the value of the closeButtonEnabled property.
     *
     * @param enabled
     *            if false, the pop over will not be closeable by the header's close button
     *
     * @see #closeButtonEnabledProperty()
     */
    public final void setCloseButtonEnabled(boolean enabled) {
        closeButtonEnabled.setValue(enabled);
    }

    /**
     * Returns the value of the closeButtonEnabled property.
     *
     * @return true if the header's close button is enabled
     *
     * @see #closeButtonEnabledProperty()
     */
    public final boolean isCloseButtonEnabled() {
        return closeButtonEnabled.getValue();
    }

    // detach support

    private final BooleanProperty detachable = new SimpleBooleanProperty(this,
            "detachable", true); //$NON-NLS-1$

    /**
     * Determines if the pop over is detachable at all.
     */
    public final BooleanProperty detachableProperty() {
        return detachable;
    }

    /**
     * Sets the value of the detachable property.
     *
     * @param detachable
     *            if true then the user can detach / tear off the pop over
     *
     * @see #detachableProperty()
     */
    public final void setDetachable(boolean detachable) {
        detachableProperty().set(detachable);
    }

    /**
     * Returns the value of the detachable property.
     *
     * @return true if the user is allowed to detach / tear off the pop over
     *
     * @see #detachableProperty()
     */
    public final boolean isDetachable() {
        return detachableProperty().get();
    }

    private final BooleanProperty detached = new SimpleBooleanProperty(this,
            "detached", false); //$NON-NLS-1$

    /**
     * Determines whether the pop over is detached from the owning node or not.
     * A detached pop over no longer shows an arrow pointing at the owner and
     * features its own title bar.
     *
     * @return the detached property
     */
    public final BooleanProperty detachedProperty() {
        return detached;
    }

    /**
     * Sets the value of the detached property.
     *
     * @param detached
     *            if true the pop over will change its apperance to "detached"
     *            mode
     *
     * @see #detachedProperty()
     */
    public final void setDetached(boolean detached) {
        detachedProperty().set(detached);
    }

    /**
     * Returns the value of the detached property.
     *
     * @return true if the pop over is currently detached.
     *
     * @see #detachedProperty()
     */
    public final boolean isDetached() {
        return detachedProperty().get();
    }

    // arrow size support

    // TODO: make styleable

    private final DoubleProperty arrowSize = new SimpleDoubleProperty(this,
            "arrowSize", 12); //$NON-NLS-1$

    /**
     * Controls the size of the arrow. Default value is 12.
     *
     * @return the arrow size property
     */
    public final DoubleProperty arrowSizeProperty() {
        return arrowSize;
    }

    /**
     * Returns the value of the arrow size property.
     *
     * @return the arrow size property value
     *
     * @see #arrowSizeProperty()
     */
    public final double getArrowSize() {
        return arrowSizeProperty().get();
    }

    /**
     * Sets the value of the arrow size property.
     *
     * @param size
     *            the new value of the arrow size property
     *
     * @see #arrowSizeProperty()
     */
    public final void setArrowSize(double size) {
        arrowSizeProperty().set(size);
    }

    // arrow indent support

    // TODO: make styleable

    private final DoubleProperty arrowIndent = new SimpleDoubleProperty(this,
            "arrowIndent", 12); //$NON-NLS-1$

    /**
     * Controls the distance between the arrow and the corners of the pop over.
     * The default value is 12.
     *
     * @return the arrow indent property
     */
    public final DoubleProperty arrowIndentProperty() {
        return arrowIndent;
    }

    /**
     * Returns the value of the arrow indent property.
     *
     * @return the arrow indent value
     *
     * @see #arrowIndentProperty()
     */
    public final double getArrowIndent() {
        return arrowIndentProperty().get();
    }

    /**
     * Sets the value of the arrow indent property.
     *
     * @param size
     *            the arrow indent value
     *
     * @see #arrowIndentProperty()
     */
    public final void setArrowIndent(double size) {
        arrowIndentProperty().set(size);
    }

    // radius support

    // TODO: make styleable

    private final DoubleProperty cornerRadius = new SimpleDoubleProperty(this,
            "cornerRadius", 6); //$NON-NLS-1$

    /**
     * Returns the corner radius property for the pop over.
     *
     * @return the corner radius property (default is 6)
     */
    public final DoubleProperty cornerRadiusProperty() {
        return cornerRadius;
    }

    /**
     * Returns the value of the corner radius property.
     *
     * @return the corner radius
     *
     * @see #cornerRadiusProperty()
     */
    public final double getCornerRadius() {
        return cornerRadiusProperty().get();
    }

    /**
     * Sets the value of the corner radius property.
     *
     * @param radius
     *            the corner radius
     *
     * @see #cornerRadiusProperty()
     */
    public final void setCornerRadius(double radius) {
        cornerRadiusProperty().set(radius);
    }

    // Detached stage title

    private final StringProperty title = new SimpleStringProperty(this, "title", "No title set"); //$NON-NLS-1$ //$NON-NLS-2$

    /**
     * Stores the title to display in the PopOver's header.
     *
     * @return the title property
     */
    public final StringProperty titleProperty() {
        return title;
    }

    /**
     * Returns the value of the title property.
     *
     * @return the detached title
     * @see #titleProperty()
     */
    public final String getTitle() {
        return titleProperty().get();
    }

    /**
     * Sets the value of the title property.
     *
     * @param title the title to use when detached
     * @see #titleProperty()
     */
    public final void setTitle(String title) {
        if (title == null) {
            throw new IllegalArgumentException("title can not be null"); //$NON-NLS-1$
        }

        titleProperty().set(title);
    }

    private final ObjectProperty<ArrowLocation> arrowLocation = new SimpleObjectProperty<>(
            this, "arrowLocation", ArrowLocation.LEFT_TOP); //$NON-NLS-1$

    /**
     * Stores the preferred arrow location. This might not be the actual
     * location of the arrow if auto fix is enabled.
     *
     * @see #setAutoFix(boolean)
     *
     * @return the arrow location property
     */
    public final ObjectProperty<ArrowLocation> arrowLocationProperty() {
        return arrowLocation;
    }

    /**
     * Sets the value of the arrow location property.
     *
     * @see #arrowLocationProperty()
     *
     * @param location
     *            the requested location
     */
    public final void setArrowLocation(ArrowLocation location) {
        arrowLocationProperty().set(location);
    }

    /**
     * Returns the value of the arrow location property.
     *
     * @see #arrowLocationProperty()
     *
     * @return the preferred arrow location
     */
    public final ArrowLocation getArrowLocation() {
        return arrowLocationProperty().get();
    }

    /**
     * All possible arrow locations.
     */
    public enum ArrowLocation {
        LEFT_TOP, LEFT_CENTER, LEFT_BOTTOM, RIGHT_TOP, RIGHT_CENTER, RIGHT_BOTTOM, TOP_LEFT, TOP_CENTER, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
    }

    /**
     * Stores the fade-in duration. This should be set before calling PopOver.show(..).
     *
     * @return the fade-in duration property
     */
    public final ObjectProperty<Duration> fadeInDurationProperty() {
        return fadeInDuration;
    }

    /**
     * Stores the fade-out duration.
     *
     * @return the fade-out duration property
     */
    public final ObjectProperty<Duration> fadeOutDurationProperty() {
        return fadeOutDuration;
    }

    /**
     * Returns the value of the fade-in duration property.
     *
     * @return the fade-in duration
     * @see #fadeInDurationProperty()
     */
    public final Duration getFadeInDuration() {
        return fadeInDurationProperty().get();
    }

    /**
     * Sets the value of the fade-in duration property. This should be set before calling PopOver.show(..).
     *
     * @param duration the requested fade-in duration
     * @see #fadeInDurationProperty()
     */
    public final void setFadeInDuration(Duration duration) {
        fadeInDurationProperty().setValue(duration);
    }

    /**
     * Returns the value of the fade-out duration property.
     *
     * @return the fade-out duration
     * @see #fadeOutDurationProperty()
     */
    public final Duration getFadeOutDuration() {
        return fadeOutDurationProperty().get();
    }

    /**
     * Sets the value of the fade-out duration property.
     *
     * @param duration the requested fade-out duration
     * @see #fadeOutDurationProperty()
     */
    public final void setFadeOutDuration(Duration duration) {
        fadeOutDurationProperty().setValue(duration);
    }

    /**
     * Stores the "animated" flag. If true then the PopOver will be shown / hidden with a short fade in / out animation.
     *
     * @return the "animated" property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns the value of the "animated" property.
     *
     * @return true if the PopOver will be shown and hidden with a short fade animation
     * @see #animatedProperty()
     */
    public final boolean isAnimated() {
        return animatedProperty().get();
    }

    /**
     * Sets the value of the "animated" property.
     *
     * @param animated if true the PopOver will be shown and hidden with a short fade animation
     * @see #animatedProperty()
     */
    public final void setAnimated(boolean animated) {
        animatedProperty().set(animated);
    }
}
