package com.chat.platform.client.ui.dialogs;

import com.chat.platform.client.ui.AvatarService;
import com.chat.platform.client.ui.Styles;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.function.Consumer;

public final class ProfileDialog {

    private ProfileDialog() {
    }

    public static void show(Stage owner, String username, String displayName,
                            AvatarService avatars, Runnable onChangeAvatar,
                            Consumer<String> onUpdateDisplayName, Runnable onLogout) {

        String currentDisplayName = displayName == null || displayName.isBlank()
                ? username : displayName;

        Label user = new Label("Tài khoản: " + username);
        Label displayNameLabel = new Label("Tên hiển thị: " + currentDisplayName);

        Button changeName = new Button("Đổi tên hiển thị");
        changeName.getStyleClass().add("secondary-button");

        Button changeAvatar = new Button("Đổi avatar");
        changeAvatar.getStyleClass().add("secondary-button");

        Button logout = new Button("Đăng xuất");
        logout.getStyleClass().add("danger-button");


        HBox actions = new HBox(8, changeName, changeAvatar, logout);
        actions.getStyleClass().add("dialog-actions");

        VBox content = new VBox(
                12,
                avatars.avatarView(username),
                user,
                displayNameLabel,
                actions
        );
        content.setPadding(new Insets(8));
        content.getStyleClass().add("profile-content");

        Dialog<ButtonType> profile = new Dialog<>();
        profile.setTitle("Thông tin người dùng");
        profile.setHeaderText("Tài khoản của bạn");
        profile.initOwner(owner);
        profile.getDialogPane().setContent(content);
        profile.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        Styles.apply(profile.getDialogPane());

        changeName.setOnAction(event -> {
            TextInputDialog input = new TextInputDialog(currentDisplayName);
            input.setTitle("Đổi tên hiển thị");
            input.setHeaderText("Cập nhật tên hiển thị");
            input.setContentText("Tên hiển thị:");
            Styles.apply(input.getDialogPane());

            input.initOwner(owner);

            input.showAndWait().ifPresent(value -> {
                String newName = value.trim();

                if (newName.isBlank()) {
                    showWarning("Tên hiển thị không được để trống.");
                    return;
                }

                if (newName.length() > 100) {
                    showWarning("Tên hiển thị không được vượt quá 100 ký tự.");
                    return;
                }

                if (newName.equals(currentDisplayName)) {
                    return;
                }

                onUpdateDisplayName.accept(newName);
                profile.close();
            });
        });

        changeAvatar.setOnAction(event -> {
            profile.close();
            onChangeAvatar.run();
        });

        logout.setOnAction(event -> {
            profile.close();
            onLogout.run();
        });

        profile.show();
    }

    private static void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Thông báo");
        alert.setHeaderText(null);
        alert.setContentText(message);
        Styles.apply(alert.getDialogPane());
        alert.showAndWait();
    }
}
