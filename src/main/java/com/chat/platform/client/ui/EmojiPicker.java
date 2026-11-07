package com.chat.platform.client.ui;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.FlowPane;
import javafx.stage.Popup;

import java.util.function.Consumer;

public class EmojiPicker {

    private final Popup popup = new Popup();
    private Consumer<String> onSelect;

    public EmojiPicker() {
        FlowPane pane = new FlowPane();
        pane.setHgap(5);
        pane.setVgap(5);
        pane.setPrefWrapLength(250);

        pane.getStyleClass().add("emoji-picker");
        Styles.apply(pane);

        for (String emoji : EmojiCatalog.EMOJIS) {
            Button button = new Button(emoji);

            button.getStyleClass().add("emoji-option");

            button.setOnAction(event -> {
                if (onSelect != null) {
                    onSelect.accept(emoji);
                }
            });

            pane.getChildren().add(button);
        }

        popup.getContent().add(pane);
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
    }

    public void show(Node anchor, Consumer<String> callback) {
        this.onSelect = callback;

        if (anchor.getScene() == null ||
                anchor.getScene().getWindow() == null) {
            return;
        }

        Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());

        if (bounds == null) {
            return;
        }

        if (!popup.isShowing()) {
            popup.show(
                    anchor.getScene().getWindow(),
                    bounds.getMinX() - 400,
                    bounds.getMinY() - 100
            );
        }
    }
}
