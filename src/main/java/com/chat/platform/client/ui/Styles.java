package com.chat.platform.client.ui;

import javafx.scene.Parent;
import javafx.scene.Scene;

public final class Styles {
    private Styles() {
    }

    public static void apply(Scene scene) {
        String stylesheet = stylesheet();
        if (!scene.getStylesheets().contains(stylesheet)) {
            scene.getStylesheets().add(stylesheet);
        }
    }

    /** Adds the app stylesheet to standalone popup or dialog content. */
    public static void apply(Parent root) {
        String stylesheet = stylesheet();
        if (!root.getStylesheets().contains(stylesheet)) {
            root.getStylesheets().add(stylesheet);
        }
    }

    private static String stylesheet() {
        var stylesheet = Styles.class.getResource("/chat.css");
        if (stylesheet == null) {
            throw new IllegalStateException("Missing JavaFX stylesheet: chat.css");
        }
        return stylesheet.toExternalForm();
    }
}
