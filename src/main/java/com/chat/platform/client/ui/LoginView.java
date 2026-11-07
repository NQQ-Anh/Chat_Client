package com.chat.platform.client.ui;

import static com.chat.platform.client.network.ChatApiClient.ensureSuccess;
import static com.chat.platform.client.util.ErrorUtils.rootMessage;
import static com.chat.platform.client.util.JsonUtils.JSON;
import static com.chat.platform.client.util.NetworkUtils.isLocalHost;
import static com.chat.platform.client.util.NetworkUtils.isPrivateNetwork;

import com.chat.platform.client.network.ChatApiClient;
import com.chat.platform.client.network.ChatSession;
import com.chat.platform.client.network.ChatSocket;
import com.chat.platform.client.network.LanServerDiscovery;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.net.URI;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.function.Consumer;

/** Màn hình đăng nhập / đăng ký. */
public final class LoginView {
    private static final String DEFAULT_SERVER = "https://p-chat-ql5o.onrender.com";

    private final ChatApiClient http;
    private final AsyncRunner runner;
    private final Consumer<ChatSession> onLoggedIn;

    private final TextField serverField = new TextField(DEFAULT_SERVER);
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final Button loginButton = new Button("Đăng nhập");
    private final Button registerButton = new Button("Đăng ký");
    private final Label status = new Label("Kết nối tới server trung tâm để bắt đầu chat.");
    private final BorderPane root;

    public LoginView(ChatApiClient http, AsyncRunner runner, Consumer<ChatSession> onLoggedIn) {
        this.http = http;
        this.runner = runner;
        this.onLoggedIn = onLoggedIn;

        serverField.setPromptText("Server URL, ví dụ https://chat.example.com");
        usernameField.setPromptText("Tên đăng nhập");
        passwordField.setPromptText("Mật khẩu (ít nhất 8 ký tự)");
        Label serverLabel = new Label("Máy chủ");
        Label usernameLabel = new Label("Tên đăng nhập");
        Label passwordLabel = new Label("Mật khẩu");
        serverLabel.getStyleClass().add("field-label");
        usernameLabel.getStyleClass().add("field-label");
        passwordLabel.getStyleClass().add("field-label");
        serverLabel.setLabelFor(serverField);
        usernameLabel.setLabelFor(usernameField);
        passwordLabel.setLabelFor(passwordField);
        serverField.getStyleClass().add("form-field");
        usernameField.getStyleClass().add("form-field");
        passwordField.getStyleClass().add("form-field");
        loginButton.getStyleClass().add("primary-button");
        registerButton.getStyleClass().add("secondary-button");
        status.getStyleClass().add("login-status");
        HBox buttons = new HBox(10, loginButton, registerButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        Label brandMark = new Label("R");
        brandMark.getStyleClass().add("brand-mark");
        Label brandName = new Label("REALTIME CHAT");
        brandName.getStyleClass().add("brand-name");
        HBox brand = new HBox(11, brandMark, brandName);
        brand.setAlignment(Pos.CENTER_LEFT);
        Label heading = new Label("Chào mừng trở lại");
        heading.getStyleClass().add("login-heading");
        Label subtitle = new Label("Đăng nhập hoặc tạo tài khoản để tiếp tục trò chuyện.");
        subtitle.getStyleClass().add("login-subtitle");
        VBox form = new VBox(9, brand, heading, subtitle,
                serverLabel, serverField, usernameLabel, usernameField,
                passwordLabel, passwordField, buttons, status);
        form.getStyleClass().add("login-card");
        root = new BorderPane(form);
        root.getStyleClass().add("login-root");
        BorderPane.setAlignment(form, Pos.CENTER);

        loginButton.setOnAction(event -> authenticate(false));
        registerButton.setOnAction(event -> authenticate(true));
        discoverLanServer();
    }

    public Parent root() {
        return root;
    }

    private void setBusy(boolean busy) {
        loginButton.setDisable(busy);
        registerButton.setDisable(busy);
    }

    private void discoverLanServer() {
        serverField.setText("Đang tìm server");
        setBusy(true);
        runner.execute(() -> {
            String result = LanServerDiscovery.discover();
            Platform.runLater(() -> {
                if (result != null) {
                    serverField.setText(result);
                    status.setText("Đã tìm thấy server");
                } else {
                    serverField.setText(DEFAULT_SERVER);
                    status.setText("Không tự tìm thấy server; hãy nhập URL server nếu cần.");
                }
                setBusy(false);
            });
        });
    }

    private void authenticate(boolean register) {
        String server = serverField.getText().trim().replaceAll("/+$", "");
        String enteredUsername = usernameField.getText().trim();
        String password = passwordField.getText();
        if (!(server.startsWith("http://") || server.startsWith("https://"))
                || enteredUsername.isBlank() || password.isBlank()) {
            status.setText("Nhập URL http(s), tên đăng nhập và mật khẩu hợp lệ.");
            return;
        }
        URI serverUri;
        try {
            serverUri = URI.create(server);
        } catch (IllegalArgumentException e) {
            status.setText("URL server không hợp lệ.");
            return;
        }
        String host = serverUri.getHost();
        if (host == null) {
            status.setText("URL server không hợp lệ.");
            return;
        }
        boolean plainHttp = "http".equalsIgnoreCase(serverUri.getScheme());
        status.setText("Đang kết nối...");
        setBusy(true);
        runner.execute(() -> {
            // isPrivateNetwork có thể phải phân giải DNS nên chạy ở luồng nền.
            if (plainHttp && !isLocalHost(host) && !isPrivateNetwork(host)) {
                Platform.runLater(() -> {
                    status.setText("Server ngoài máy này phải dùng HTTPS để bảo vệ mật khẩu và token.");
                    setBusy(false);
                });
                return;
            }
            String issuedToken = null;
            try {
                HttpResponse<String> response = http.sendJson(server + "/api/auth/"
                        + (register ? "register" : "login"), Map.of(
                        "username", enteredUsername, "password", password), null);
                ensureSuccess(response);
                JsonNode auth = JSON.readTree(response.body());
                String username = auth.path("username").asText();
                issuedToken = auth.path("token").asText();
                ChatSocket socket = ChatSocket.connect(http.httpClient(), server, issuedToken);
                ChatSession session = new ChatSession(server, username, issuedToken, socket);
                Platform.runLater(() -> onLoggedIn.accept(session));
            } catch (Exception e) {
                if (issuedToken != null && !issuedToken.isBlank()) {
                    try {
                        // Đã có token nhưng không mở được WebSocket: thu hồi phiên vừa tạo.
                        http.sendJson(server + "/api/auth/logout", Map.of(), issuedToken);
                    } catch (Exception ignored) {
                        // Không còn gì để làm; người dùng sẽ thấy lỗi đăng nhập bên dưới.
                    }
                }
                Platform.runLater(() -> {
                    status.setText("Không thể đăng nhập: " + rootMessage(e));
                    setBusy(false);
                });
            }
        });
    }
}
