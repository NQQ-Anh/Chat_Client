package com.chat.platform.client.ui;

import com.chat.platform.client.util.TextUtils;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.TextField;

import java.util.List;

/** Các hộp thoại dùng chung. */
public final class Dialogs {
    private Dialogs() {
    }

    public static void showError(String text) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Realtime Chat");
        alert.setHeaderText("Không thể hoàn tất thao tác");
        alert.setContentText(text);
        Styles.apply(alert.getDialogPane());
        alert.show();
    }

    public static void showInfo(String text) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Realtime Chat");
        alert.setHeaderText(null);
        alert.setContentText(text);
        Styles.apply(alert.getDialogPane());
        alert.show();
    }

    public static boolean confirm(String title, String header, String message) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, message,
                ButtonType.CANCEL, ButtonType.OK);
        confirm.setTitle(title);
        confirm.setHeaderText(header);
        Styles.apply(confirm.getDialogPane());
        return confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    /** @return danh sách username đã nhập; rỗng nếu người dùng hủy hoặc không nhập gì. */
    public static List<String> promptUsernames(String title, String header, String promptText) {
        TextField usernames = new TextField();
        usernames.setPromptText(promptText);
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.getDialogPane().setContent(usernames);
        Styles.apply(dialog.getDialogPane());
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return List.of();
        }
        return TextUtils.splitUsernames(usernames.getText());
    }

    /** @return người kế nhiệm được chọn, hoặc null nếu hủy. */
    public static String chooseSuccessor(List<String> candidates) {
        ChoiceDialog<String> choice = new ChoiceDialog<>(candidates.get(0), candidates);
        choice.setTitle("Chuyển quyền trưởng nhóm");
        choice.setHeaderText("Chọn người kế nhiệm trước khi rời nhóm");
        choice.setContentText("Trưởng nhóm mới:");
        Styles.apply(choice.getDialogPane());
        return choice.showAndWait().orElse(null);
    }
}
