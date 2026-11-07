package com.chat.platform.client.ui.dialogs;

import com.chat.platform.client.model.Member;
import com.chat.platform.client.model.Room;
import com.chat.platform.client.ui.Styles;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/** Thông tin nhóm: mã phòng, thành viên và các thao tác quản lý. */
public final class GroupInfoDialog {
    public record Actions(Runnable addMembers, Runnable leave,
                          Runnable transferLeadership, Runnable delete) {
    }

    private GroupInfoDialog() {
    }

    public static void show(Room room, List<Member> memberList, String username, Actions actions) {
        boolean leader = memberList.stream().anyMatch(member -> member.username().equals(username)
                && "LEADER".equals(member.role()));
        Alert info = new Alert(Alert.AlertType.NONE);
        Label id = new Label("Mã phòng: " + room.roomId());
        id.setWrapText(true);
        id.getStyleClass().add("group-id");
        Button copy = new Button("Copy mã phòng");
        copy.getStyleClass().add("secondary-button");
        copy.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(room.roomId());
            Clipboard.getSystemClipboard().setContent(content);
            copy.setText("Đã sao chép");
        });
        Label groupName = new Label(room.name());
        groupName.getStyleClass().add("group-name");
        Label summaryHeading = new Label("Thông tin nhóm");
        summaryHeading.getStyleClass().add("section-title");
        VBox summary = new VBox(8, summaryHeading, groupName, id, copy);
        summary.getStyleClass().addAll("dialog-card", "group-summary");

        VBox members = new VBox(4);
        members.getStyleClass().add("group-member-list");
        memberList.forEach(member -> {
            Label memberRow = new Label(member.username()
                    + ("LEADER".equals(member.role()) ? " · Trưởng nhóm" : " · Thành viên"));
            memberRow.getStyleClass().add("group-member-row");
            members.getChildren().add(memberRow);
        });
        Label memberHeading = new Label("Thành viên · " + memberList.size());
        memberHeading.getStyleClass().add("section-title");
        VBox memberSection = new VBox(8, memberHeading, members);
        memberSection.getStyleClass().addAll("dialog-card", "group-members-section");

        Button add = new Button("Thêm thành viên");
        add.getStyleClass().add("secondary-button");
        add.setDisable(!leader);
        add.setOnAction(event -> closeThen(info, actions.addMembers()));
        Button leave = new Button("Rời nhóm");
        leave.getStyleClass().add("utility-button");
        leave.setOnAction(event -> closeThen(info, actions.leave()));
        HBox leadershipButtons = new HBox(8);
        if (leader) {
            Button transfer = new Button("Chuyển trưởng nhóm");
            transfer.getStyleClass().add("secondary-button");
            transfer.setOnAction(event -> closeThen(info, actions.transferLeadership()));
            Button delete = new Button("Xóa nhóm");
            delete.getStyleClass().add("danger-button");
            delete.setOnAction(event -> closeThen(info, actions.delete()));
            leadershipButtons.getChildren().addAll(transfer, delete);
        }
        HBox memberButtons = new HBox(8, add, leave);
        VBox buttons = new VBox(8);
        if (leader) {
            buttons.getChildren().add(leadershipButtons);
        }
        buttons.getChildren().add(memberButtons);
        Label actionsHeading = new Label("Quản lý nhóm");
        actionsHeading.getStyleClass().add("section-title");
        VBox actionsSection = new VBox(8, actionsHeading, buttons);
        actionsSection.getStyleClass().addAll("dialog-card", "group-actions");

        VBox details = new VBox(10, summary, memberSection, actionsSection);
        details.setPadding(new Insets(8));
        details.getStyleClass().add("group-info-content");
        info.setTitle("Thông tin nhóm");
        info.setHeaderText("Thông tin cuộc trò chuyện");
        info.getDialogPane().setContent(details);
        info.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        Styles.apply(info.getDialogPane());
        Button close = (Button) info.getDialogPane().lookupButton(ButtonType.CLOSE);
        close.getStyleClass().add("secondary-button");
        info.show();
    }

    /** Đóng hộp thoại trước khi chạy thao tác, tránh để thông tin cũ hiển thị sau khi nhóm đã thay đổi. */
    private static void closeThen(Alert info, Runnable action) {
        info.close();
        action.run();
    }
}
