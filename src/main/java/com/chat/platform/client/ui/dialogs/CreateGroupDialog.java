package com.chat.platform.client.ui.dialogs;

import com.chat.platform.client.ui.Styles;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.function.BiConsumer;

public final class CreateGroupDialog {
    private CreateGroupDialog() {
    }

    /** Hiện hộp thoại; nếu người dùng bấm "Tạo" thì gọi onCreate(tênNhóm, danhSáchThànhViên). */
    public static void show(BiConsumer<String, String> onCreate) {
        TextField name = new TextField();
        name.setPromptText("Ví dụ: Nhóm đồ án Java");
        TextField members = new TextField();
        members.setPromptText("thanhvien1, thanhvien2, ...");
        Label sectionTitle = new Label("Thông tin nhóm");
        sectionTitle.getStyleClass().add("section-title");
        VBox fields = new VBox(8, sectionTitle, new Label("Tên nhóm"), name,
                new Label("Thêm thành viên (có thể để trống)"), members);
        fields.getStyleClass().addAll("dialog-form", "dialog-card");
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Tạo nhóm mới");
        dialog.setHeaderText("Tạo cuộc trò chuyện nhóm");
        dialog.getDialogPane().setContent(fields);
        ButtonType createType = new ButtonType("Tạo", ButtonBar.ButtonData.OK_DONE);
        Styles.apply(dialog.getDialogPane());
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, createType);
        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        Button createButton = (Button) dialog.getDialogPane().lookupButton(createType);
        cancelButton.getStyleClass().add("secondary-button");
        createButton.getStyleClass().add("primary-button");
        dialog.showAndWait().filter(button -> button.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                .ifPresent(button -> onCreate.accept(name.getText(), members.getText()));
    }
}
