package com.chat.platform.client.ui.dialogs;

import static com.chat.platform.client.network.ChatApiClient.ensureSuccess;
import static com.chat.platform.client.ui.UiUtils.spacer;
import static com.chat.platform.client.util.JsonUtils.JSON;

import com.chat.platform.client.model.UserSearchResult;
import com.chat.platform.client.network.ChatApi;
import com.chat.platform.client.ui.AsyncRunner;
import com.chat.platform.client.ui.AvatarService;
import com.chat.platform.client.ui.Styles;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/** Hộp thoại tìm người dùng và bắt đầu chat riêng. */
public final class FindFriendDialog {
    private final ChatApi api;
    private final AsyncRunner runner;
    private final AvatarService avatars;
    private final Consumer<String> onChat;

    public FindFriendDialog(ChatApi api, AsyncRunner runner, AvatarService avatars,
                            Consumer<String> onChat) {
        this.api = api;
        this.runner = runner;
        this.avatars = avatars;
        this.onChat = onChat;
    }

    public void show() {
        Dialog<Void> dialog = new Dialog<>();

        dialog.setTitle("Tìm bạn");
        dialog.setHeaderText("Tìm kiếm người dùng");

        TextField keywordField = new TextField();
        keywordField.setPromptText("Nhập tên hoặc tên đăng nhập...");

        Button searchButton = new Button("Tìm kiếm");
        searchButton.getStyleClass().add("primary-button");

        ListView<UserSearchResult> resultList = new ListView<>();
        resultList.getStyleClass().add("search-results");
        resultList.setPrefSize(400, 300);
        resultList.setPlaceholder(new Label("Chưa có kết quả tìm kiếm"));

        Label statusLabel = new Label("Nhập tên người dùng để tìm kiếm.");
        statusLabel.getStyleClass().add("dialog-status");

        Label searchHeading = new Label("Tìm kiếm");
        searchHeading.getStyleClass().add("section-title");
        HBox searchRow = new HBox(8, keywordField, searchButton);
        searchRow.getStyleClass().add("search-controls-row");
        HBox.setHgrow(keywordField, Priority.ALWAYS);
        VBox searchSection = new VBox(8, searchHeading, searchRow);
        searchSection.getStyleClass().addAll("dialog-section", "friend-search-section");

        Label resultsHeading = new Label("Người dùng");
        resultsHeading.getStyleClass().add("section-title");
        VBox resultsSection = new VBox(8, resultsHeading, statusLabel, resultList);
        resultsSection.getStyleClass().addAll("dialog-section", "friend-results-section");

        resultList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(UserSearchResult user, boolean empty) {
                super.updateItem(user, empty);

                if (empty || user == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                String shownName = user.displayName() == null || user.displayName().isBlank()
                        ? user.username() : user.displayName();
                Label displayName = new Label(shownName);
                displayName.getStyleClass().add("user-result-name");

                Label usernameLabel = new Label("@" + user.username());
                usernameLabel.getStyleClass().add("result-username");

                Label onlineLabel = new Label(
                        user.online() ? "Đang hoạt động" : "Ngoại tuyến"
                );
                onlineLabel.getStyleClass().addAll("online-status",
                        user.online() ? "online" : "offline");

                Button chatButton = new Button("Nhắn tin");
                chatButton.getStyleClass().add("primary-button");

                chatButton.setOnAction(event -> {
                    dialog.close();

                    // Sử dụng hàm chat cá nhân đã có trong ứng dụng.
                    onChat.accept(user.username());
                });

                VBox userInfo = new VBox(
                        4,
                        displayName,
                        usernameLabel,
                        onlineLabel
                );

                HBox row = new HBox(
                        10,
                        avatars.avatarView(user.username()),
                        userInfo,
                        spacer(),
                        chatButton
                );

                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("search-result-row");

                setGraphic(row);
                setText(null);
            }
        });

        searchButton.setOnAction(event ->
                searchUsers(keywordField.getText(), resultList, statusLabel)
        );

        keywordField.setOnAction(event ->
                searchUsers(keywordField.getText(), resultList, statusLabel)
        );

        VBox content = new VBox(10, searchSection, resultsSection);
        content.getStyleClass().add("friend-search-content");

        content.setPadding(new Insets(10));

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(480);
        Styles.apply(dialog.getDialogPane());
        Button closeButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE);
        closeButton.getStyleClass().add("secondary-button");

        dialog.show();
    }

    private void searchUsers(String keyword, ListView<UserSearchResult> resultList, Label statusLabel) {
        String query = keyword == null ? "" : keyword.trim();

        if (query.isBlank()) {
            statusLabel.setText("Vui lòng nhập tên người dùng.");
            resultList.getItems().clear();
            return;
        }

        if (query.length() > 64) {
            statusLabel.setText("Từ khóa tìm kiếm không được vượt quá 64 ký tự.");
            return;
        }

        statusLabel.setText("Đang tìm kiếm...");

        resultList.getItems().clear();

        runner.run(() -> {
            String encodedKeyword = java.net.URLEncoder.encode(query, StandardCharsets.UTF_8);

            // Đúng với @GetMapping("/users") trong UserController.
            HttpResponse<String> response = api.get("/api/users?q=" + encodedKeyword);

            ensureSuccess(response, "Tìm kiếm người dùng");

            List<UserSearchResult> users =
                    JSON.readerForListOf(UserSearchResult.class).readValue(response.body());

            Platform.runLater(() -> {
                resultList.getItems().setAll(users);

                if (users.isEmpty()) {
                    statusLabel.setText("Không tìm thấy người dùng.");
                } else {
                    statusLabel.setText("Tìm thấy " + users.size() + " người dùng.");
                }
            });
        });
    }
}
