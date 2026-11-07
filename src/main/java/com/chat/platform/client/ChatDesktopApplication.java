package com.chat.platform.client;

import com.chat.platform.client.controller.ChatController;
import com.chat.platform.client.network.ChatApiClient;
import com.chat.platform.client.network.ChatSession;
import com.chat.platform.client.ui.AsyncRunner;
import com.chat.platform.client.ui.LoginView;
import com.chat.platform.client.ui.Styles;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class ChatDesktopApplication extends Application {
    private final ChatApiClient http = new ChatApiClient();
    private final AsyncRunner runner = new AsyncRunner();

    private Stage stage;
    private ChatController chat;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        showLogin();
    }

    @Override
    public void stop() {
        runner.shutdown();
        if (chat != null) {
            chat.dispose("Client closed");
        }
    }

    private void showLogin() {
        chat = null;
        LoginView login = new LoginView(http, runner, this::showChat);
        stage.setTitle("Realtime Chat - Đăng nhập");
        show(login.root(), 600, 520);
    }

    private void showChat(ChatSession session) {
        chat = new ChatController(http, runner, session, stage, this::showLogin);
        stage.setTitle("Realtime Chat - " + session.username());
        show(chat.root(), 1100, 740);
    }

    private void show(Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);
        Styles.apply(scene);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
