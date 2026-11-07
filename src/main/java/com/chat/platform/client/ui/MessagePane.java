package com.chat.platform.client.ui;

import static com.chat.platform.client.ui.UiUtils.spacer;

import javafx.scene.input.MouseButton;
import com.chat.platform.client.model.Message;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.scene.layout.StackPane;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;

import java.nio.file.Path;
import java.util.ArrayList;

/** Khung bên phải: thanh tiêu đề, danh sách tin nhắn và ô soạn tin. */
public final class MessagePane {
    public interface Listener {
        void onProfile();

        void onInfo();

        void onAttach();

        /** @return true nếu tin đã được gửi đi (ô nhập sẽ được xóa). */
        boolean onSend(String content);

        void onDownload(Message message);

        void onPreview(Message message);

        void onRecall(Message message);

        /** Tải ảnh của tệp đính kèm; callback phải được gọi trên luồng JavaFX. */
        void loadImage(Message message, Consumer<Image> callback);


        void loadMedia(Message message, Consumer<Path> callback);
    }

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final String username;
    private final AvatarService avatars;
    private final Listener listener;
    private final StackPane conversationAvatar = new StackPane();

    private final VBox messages = new VBox(10);
    private final ScrollPane messageScroll = new ScrollPane(messages);
    private final Label title = new Label("Chọn hoặc tham gia nhóm");
    private final Label membersLabel = new Label();
    private final TextField messageInput = new TextField();
    private final Button avatarButton = new Button();
    private final Map<String, Message> displayedMessages = new HashMap<>();
    private final BorderPane root = new BorderPane();

    private final List<MediaPlayer> mediaPlayers = new ArrayList<>();

    private Button activeMoreButton;
    private ContextMenu activeContextMenu;

    // Chỉ cho phép một media phát tại một thời điểm
    private void stopOtherPlayers(MediaPlayer currentPlayer) {
        for (MediaPlayer player : mediaPlayers) {
            if (player != currentPlayer
                    && player.getStatus() == MediaPlayer.Status.PLAYING) {
                player.pause();
            }
        }
    }

    public MessagePane(String username, AvatarService avatars, Listener listener) {
        this.username = username;
        this.avatars = avatars;
        this.listener = listener;

        title.getStyleClass().add("chat-title");
        membersLabel.getStyleClass().add("member-count");

        avatarButton.setText("Tài khoản");
        avatarButton.setAccessibleText("Thông tin tài khoản và đăng xuất");
        avatarButton.setTooltip(new Tooltip("Tài khoản"));
        avatarButton.getStyleClass().add("profile-button");
        avatarButton.setOnAction(event -> listener.onProfile());

        conversationAvatar.setMinSize(40, 40);
        conversationAvatar.setPrefSize(40, 40);
        conversationAvatar.setMaxSize(40, 40);
        Button infoButton = new Button("i");
        infoButton.setAccessibleText("Thông tin cuộc trò chuyện");
        infoButton.setTooltip(new Tooltip("Thông tin cuộc trò chuyện"));
        infoButton.getStyleClass().addAll("utility-button", "chat-info-button");
        infoButton.setOnAction(event -> listener.onInfo());

        HBox header = new HBox(
                8,
                conversationAvatar,
                title,
                spacer(),
                membersLabel,
                avatarButton,
                infoButton
        );
        header.getStyleClass().add("chat-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14));

        messages.setPadding(new Insets(14));
        messageScroll.setFitToWidth(true);
        messageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        Button attach = new Button("Đính kèm");
        attach.getStyleClass().add("secondary-button");
        attach.setOnAction(event -> listener.onAttach());

        Button emojiButton = new Button("😊");
        emojiButton.setTooltip(new Tooltip("Chọn Emote"));
        emojiButton.getStyleClass().add("secondary-button");

        EmojiPicker emojiPicker = new EmojiPicker();

        emojiButton.setOnAction(event -> {
            emojiPicker.show(emojiButton, emoji -> {
                messageInput.requestFocus();
                messageInput.replaceSelection(emoji);
            });
        });

        Button send = new Button("Gửi");
        send.getStyleClass().add("primary-button");
        send.setDefaultButton(true);
        send.setOnAction(event -> send());
        messageInput.setPromptText("Nhập tin nhắn...");
        messageInput.getStyleClass().add("message-input");
        messageInput.setOnAction(event -> send());
        messageInput.setDisable(true);
        HBox composer = new HBox(8, attach, messageInput, emojiButton, send);
        composer.getStyleClass().add("composer");
        composer.setPadding(new Insets(12));
        HBox.setHgrow(messageInput, Priority.ALWAYS);

        root.getStyleClass().add("chat-content");
        root.setTop(header);
        root.setCenter(messageScroll);
        root.setBottom(composer);
    }

    public Node root() {
        return root;
    }


    public void setTitle(String text) {
        title.setText(text);
    }

    // Hiển thị avatar của người đang trò chuyện cùng.
    public void setUserAvatar(String otherUsername) {
        StackPane avatar = avatars.avatarView(otherUsername);

        // Avatar mặc định của người dùng có kích thước 24x24.
        // Phóng lớn để phù hợp với thanh tiêu đề.
        avatar.setScaleX(1.5);
        avatar.setScaleY(1.5);

        conversationAvatar.getChildren().setAll(avatar);
    }

    // Hiển thị avatar nhóm.
    public void setGroupAvatar() {
        conversationAvatar.getChildren().setAll(avatars.groupAvatar());
    }

    // Xóa avatar khi chưa chọn cuộc trò chuyện.
    public void clearConversationAvatar() {
        conversationAvatar.getChildren().clear();
    }

    public void setMembersText(String text) {
        setMembersText(text, false);
    }

    public void setMembersText(String text, boolean online) {
        membersLabel.setText(text);
        membersLabel.getStyleClass().remove("online");
        if (online) {
            membersLabel.getStyleClass().add("online");
        }
    }

    public String membersText() {
        return membersLabel.getText();
    }

    public void setInputEnabled(boolean enabled) {
        messageInput.setDisable(!enabled);
    }

    public void clearInput() {
        messageInput.clear();
    }

    private void send() {
        String content = messageInput.getText().trim();
        if (content.isBlank()) {
            return;
        }
        if (listener.onSend(content)) {
            messageInput.clear();
        }
    }


    public void clearMessages() {
        messages.getChildren().clear();
        displayedMessages.clear();
        activeMoreButton = null;
        activeContextMenu = null;
    }



    public void applyRecall(String roomId, long messageId) {
        if (roomId == null || messageId <= 0) {
            return;
        }

        // Tìm tin nhắn cần thu hồi trong danh sách đang hiển thị.
        String targetKey = Long.toString(messageId);
        Message oldMessage = displayedMessages.get(targetKey);

        // Kiểm tra tin nhắn có thuộc cuộc trò chuyện hiện tại không.
        if (oldMessage == null
                || !roomId.equals(oldMessage.roomId())) {
            return;
        }

        // Tạo dữ liệu tin nhắn mới với trạng thái đã thu hồi.
        Message recalledMessage = new Message(
                oldMessage.messageId(),
                oldMessage.roomId(),
                oldMessage.sender(),
                "",
                oldMessage.timestamp(),
                null,
                null,
                null,
                List.of(),
                true
        );

        // Cập nhật dữ liệu trong Map, không xóa tin nhắn cũ.
        displayedMessages.put(targetKey, recalledMessage);

        // Tìm bubble cũ và cập nhật nội dung ngay tại vị trí hiện tại.
        for (Node node : messages.getChildren()) {
            VBox bubble = findBubble(node);
            if (bubble == null) {
                continue;
            }

            Object key = bubble.getProperties().get("message-key");

            if (!targetKey.equals(key)) {
                continue;
            }

            // Giữ lại dòng tác giả và thời gian.
            // Xóa phần nội dung cũ và tệp đính kèm.
            if (bubble.getChildren().size() > 1) {
                bubble.getChildren().remove(
                        1,
                        bubble.getChildren().size()
                );
            }

            // Hiển thị thông báo thu hồi tại vị trí cũ.
            Label recalledLabel = new Label(
                    oldMessage.sender().equals(username)
                            ? "Tin nhắn đã thu hồi"
                            : "Tin nhắn đã bị xóa"
            );

            recalledLabel.setWrapText(true);
            recalledLabel.getStyleClass().add("message-recalled");
            bubble.getChildren().add(recalledLabel);

            // Ẩn trạng thái đã xem vì tin nhắn đã thu hồi.
            VBox receipt = (VBox) bubble.getUserData();
            receipt.setVisible(false);
            receipt.setManaged(false);

            // Xóa nút ba chấm và bỏ sự kiện bấm đúp vì tin nhắn đã thu hồi.
            if (node instanceof HBox row) {
                row.getChildren().removeIf(child -> child instanceof Button);
            }
            bubble.setOnMouseClicked(null);

            // Không tạo bubble mới, không thay đổi thứ tự tin nhắn.
            break;
        }
    }



    public void scrollToBottom() {
        Platform.runLater(() -> {
            messageScroll.applyCss();
            messageScroll.layout();
            messageScroll.setVvalue(1.0);

            Platform.runLater(() -> {
                messageScroll.applyCss();
                messageScroll.layout();
                messageScroll.setVvalue(1.0);
            });
        });
    }

    /** Vẽ lại avatar của tài khoản và các dấu "đã xem" (sau khi đổi avatar). */
    public void refreshAvatars() {
        displayedMessages.values().forEach(this::showMessage);
    }

    /** Cập nhật danh sách người đã xem của một tin nhắn đang hiển thị. */
    public void applyReadReceipt(String roomId, long messageId, List<String> readers) {
        if (roomId == null || messageId <= 0) {
            return;
        }
        Message updated = null;
        for (Map.Entry<String, Message> entry : displayedMessages.entrySet()) {
            Message message = entry.getValue();
            if (message.messageId() == messageId && message.roomId().equals(roomId)) {
                updated = new Message(message.messageId(), message.roomId(), message.sender(),
                        message.content(), message.timestamp(), message.fileId(),
                        message.fileName(), message.contentType(), readers, message.recalled());
                entry.setValue(updated);
                break;
            }
        }
        if (updated != null) {
            showMessage(updated);
        }
    }

    public void showMessage(Message message) {
        String key = message.messageId() > 0 ? Long.toString(message.messageId())
                : message.fileId() == null ? message.sender() + message.timestamp() + message.content()
                : message.fileId();
        if (!displayedMessages.containsKey(key)) {
            displayedMessages.put(key, message);
            VBox bubble = new VBox(5);
            bubble.setPadding(new Insets(9));
            bubble.setMaxWidth(620);
            bubble.getStyleClass().addAll("chat-bubble",
                    message.sender().equals(username) ? "own-message" : "other-message");
            Label author = new Label(message.sender() + "  ·  "
                    + TIME_FORMAT.format(Instant.ofEpochMilli(message.timestamp())));
            author.getStyleClass().add("message-author");
            bubble.getChildren().add(author);

            if (message.recalled()) {
                Label recalledLabel = new Label(
                        message.sender().equals(username)
                                ? "Tin nhắn đã thu hồi"
                                : "Tin nhắn đã bị xóa"
                );

                recalledLabel.setWrapText(true);
                recalledLabel.getStyleClass().add("message-recalled");
                bubble.getChildren().add(recalledLabel);

            } else {
                if (!message.content().isBlank()) {
                    Label content = new Label(message.content());
                    content.setWrapText(true);
                    content.setMaxWidth(580);
                    content.getStyleClass().add("message-content");
                    bubble.getChildren().add(content);
                }

                if (message.fileId() != null) {
                    bubble.getChildren().add(attachmentControl(message));
                }
            }

            VBox receipt = new VBox(3);
            Label receiptText = new Label();
            receiptText.getStyleClass().add("message-receipt");
            HBox readers = new HBox(3);
            receipt.getChildren().addAll(receiptText, readers);
            bubble.getChildren().add(receipt);
            bubble.setUserData(receipt);
            bubble.getProperties().put("message-key", key);

            HBox row = createMessageRow(bubble, message);

            messages.getChildren().add(row);
            updateReadReceipt(bubble, message);
        } else {
            displayedMessages.put(key, message);
            for (Node child : messages.getChildren()) {
                VBox bubble = findBubble(child);
                if (bubble != null && key.equals(bubble.getProperties().get("message-key"))) {
                    updateReadReceipt(bubble, message);
                    break;
                }
            }
        }
    }

    /** Tìm bubble trong một hàng tin nhắn, bất kể nút ba chấm nằm bên trái hay phải. */
    private VBox findBubble(Node node) {
        if (!(node instanceof HBox row)) {
            return null;
        }
        for (Node child : row.getChildren()) {
            if (child instanceof VBox bubble
                    && bubble.getProperties().containsKey("message-key")) {
                return bubble;
            }
        }
        return null;
    }

    private void updateReadReceipt(VBox bubble, Message message) {
        VBox receipt = (VBox) bubble.getUserData();

        if (message.recalled()) {
            receipt.setVisible(false);
            receipt.setManaged(false);
            return;
        }

        Label receiptText = (Label) receipt.getChildren().get(0);
        HBox readers = (HBox) receipt.getChildren().get(1);

        receiptText.setText(readText(message));
        readers.getChildren().clear();

        if (message.sender().equals(username)) {
            message.readBy().stream()
                    .filter(reader -> !reader.equals(username))
                    .distinct()
                    .forEach(reader ->
                            readers.getChildren().add(avatars.avatarView(reader)));
        }

        readers.setVisible(!readers.getChildren().isEmpty());
        readers.setManaged(!readers.getChildren().isEmpty());

        receipt.setVisible(
                !receiptText.getText().isBlank()
                        || !readers.getChildren().isEmpty()
        );

        receipt.setManaged(receipt.isVisible());
    }


    private String readText(Message message) {
        if (!message.sender().equals(username)) {
            return "";
        }
        List<String> others = message.readBy().stream().filter(reader -> !reader.equals(username)).toList();
        return others.isEmpty() ? "Đã gửi" : "Đã xem bởi: " + String.join(", ", others);
    }

    private boolean isAudio(Message message) {
        return message.contentType() != null
                && message.contentType().toLowerCase().startsWith("audio/");
    }

    private boolean isVideo(Message message) {
        return message.contentType() != null
                && message.contentType().toLowerCase().startsWith("video/");
    }


    private Node mediaControl(Message message) {
        VBox container = new VBox(6);
        container.setPadding(new Insets(6));

        Label status = new Label("Đang tải media...");
        container.getChildren().add(status);

        listener.loadMedia(message, path -> {
            if (path == null) {
                status.setText("Không thể tải tệp media.");
                return;
            }

            try {
                Media media = new Media(path.toUri().toString());

                media.setOnError(() -> {
                    Throwable error = media.getError();

                    System.err.println("Lỗi đọc media:");
                    if (error != null) {
                        error.printStackTrace();
                    }

                    Platform.runLater(() ->
                            status.setText("Không thể đọc tệp video.")
                    );
                });

                MediaPlayer player = new MediaPlayer(media);
                mediaPlayers.add(player);

                player.setOnError(() -> {
                    Throwable error = player.getError();

                    System.err.println("Lỗi phát media:");
                    if (error != null) {
                        error.printStackTrace();
                    }

                    Platform.runLater(() ->
                            status.setText("Không thể phát video.")
                    );
                });

                player.setOnReady(() -> {
                    container.getChildren().clear();

                    Button playPause = new Button("▶");
                    playPause.setFocusTraversable(false);
                    playPause.setMinSize(32, 32);
                    playPause.setPrefSize(32, 32);
                    playPause.setMaxSize(32, 32);
                    playPause.getStyleClass().add("media-play-button");

                    player.statusProperty().addListener((obs, oldStatus, newStatus) -> {
                        if (newStatus == MediaPlayer.Status.PLAYING) {
                            playPause.setText("❚❚");
                        } else {
                            playPause.setText("▶");
                        }
                    });
                    Slider progress = new Slider(0, 100, 0);

                    Label currentTime = new Label("00:00");
                    Label totalTime = new Label(
                            formatDuration(player.getTotalDuration())
                    );

                    HBox controls = new HBox(
                            8, playPause, currentTime, progress, totalTime
                    );

                    controls.setAlignment(Pos.CENTER_LEFT);
                    HBox.setHgrow(progress, Priority.ALWAYS);

                    if (isVideo(message)) {
                        MediaView mediaView = new MediaView(player);
                        mediaView.setPreserveRatio(true);
                        mediaView.setFitWidth(360);
                        mediaView.setFitHeight(220);

                        container.getChildren().addAll(
                                mediaView, controls
                        );
                    } else {
                        container.getChildren().add(controls);
                    }

                    playPause.setOnAction(event -> {
                        if (player.getStatus() == MediaPlayer.Status.PLAYING) {
                            player.pause();
                        } else {
                            stopOtherPlayers(player);
                            player.play();
                        }
                    });

                    player.currentTimeProperty().addListener(
                            (obs, oldTime, newTime) -> {
                                Duration total = player.getTotalDuration();

                                if (total != null
                                        && total.toMillis() > 0
                                        && !progress.isValueChanging()) {
                                    progress.setValue(
                                            newTime.toMillis()
                                                    / total.toMillis() * 100
                                    );
                                }

                                currentTime.setText(formatDuration(newTime));
                            }
                    );

                    progress.valueChangingProperty().addListener(
                            (obs, wasChanging, changing) -> {
                                if (!changing) {
                                    seekMedia(player, progress);
                                }
                            }
                    );

                    progress.setOnMouseReleased(
                            event -> seekMedia(player, progress)
                    );

                    player.setOnEndOfMedia(() -> progress.setValue(0));
                });

            } catch (Exception exception) {
                exception.printStackTrace();
                status.setText("Không thể mở tệp media.");
            }
        });

        return container;
    }


    private void seekMedia(MediaPlayer player, Slider progress) {
        Duration total = player.getTotalDuration();

        if (total != null && total.toMillis() > 0) {
            double millis = total.toMillis() * progress.getValue() / 100.0;
            player.seek(Duration.millis(millis));
        }
    }

    private String formatDuration(Duration duration) {
        if (duration == null || duration.isUnknown()
                || duration.isIndefinite()) {
            return "00:00";
        }

        int seconds = (int) duration.toSeconds();
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private VBox attachmentControl(Message message) {
        Label fileName = new Label(
                message.fileName() == null
                        ? "Tệp đính kèm"
                        : message.fileName()
        );

        // Đã bỏ nút "Tải xuống": dùng menu ⋮ để tải.
        HBox row = new HBox(10, fileName);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox attachment = new VBox(6, row);

        if (message.contentType() != null) {
            if (message.contentType().startsWith("image/")) {
                listener.loadImage(message, image -> {
                    ImageView preview = new ImageView(image);
                    preview.setPreserveRatio(true);
                    preview.setFitWidth(280);
                    preview.setFitHeight(200);
                    preview.setOnMouseClicked(event -> listener.onPreview(message));

                    attachment.getChildren().add(preview);
                });
            } else if (isAudio(message) || isVideo(message)) {
                attachment.getChildren().add(mediaControl(message));
            }
        }

        return attachment;
    }


    private HBox createMessageRow(VBox bubble, Message message) {

        HBox row = new HBox(6);

        row.setAlignment(
                message.sender().equals(username)
                        ? Pos.CENTER_RIGHT
                        : Pos.CENTER_LEFT
        );

        // Tạo nút ba chấm.
        Button moreButton = new Button("⋮");
        moreButton.getStyleClass().add("message-more-button");
        moreButton.setTooltip(new Tooltip("Thao tác tin nhắn"));
        moreButton.setFocusTraversable(false);

        // Tạo menu thao tác.
        ContextMenu contextMenu = new ContextMenu();

        // Sao chép nội dung.
        // Khung menu: bo góc, viền mảnh, đổ bóng nhẹ.
        contextMenu.setStyle(
                "-fx-background-color: white;"
                        + "-fx-background-radius: 10;"
                        + "-fx-border-color: #d0d3d9;"
                        + "-fx-border-radius: 10;"
                        + "-fx-border-width: 1;"
                        + "-fx-padding: 4;"
                        + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 8, 0.1, 0, 2);"
        );

// Sao chép nội dung.
        if (!message.recalled() && !message.content().isBlank()) {
            MenuItem copyItem = new MenuItem("Sao chép");
            styleMenuItem(copyItem);
            copyItem.setOnAction(event -> {
                ClipboardContent clipboardContent = new ClipboardContent();
                clipboardContent.putString(message.content());
                Clipboard.getSystemClipboard().setContent(clipboardContent);
            });
            contextMenu.getItems().add(copyItem);
        }

// Chỉ người gửi mới được thu hồi tin nhắn của mình.
        if (username.equals(message.sender())
                && !message.recalled()
                && message.messageId() > 0) {

            MenuItem recallItem = new MenuItem("Thu hồi tin nhắn");
            styleMenuItem(recallItem);
            recallItem.setOnAction(event -> listener.onRecall(message));
            contextMenu.getItems().add(recallItem);
        }

// Nếu tin nhắn có tệp đính kèm thì thêm chức năng tải xuống.
        if (message.fileId() != null && !message.recalled()) {

            MenuItem downloadItem = new MenuItem("Tải xuống");
            styleMenuItem(downloadItem);
            downloadItem.setOnAction(event -> listener.onDownload(message));
            contextMenu.getItems().add(downloadItem);
        }

        // Chỉ hiển thị nút nếu có thao tác.
        if (!contextMenu.getItems().isEmpty()) {

            // Ẩn bằng visible (nút vẫn giữ chỗ vì managed = true).
            moreButton.setVisible(false);

            moreButton.setOnAction(event ->
                    contextMenu.show(
                            moreButton,
                            javafx.geometry.Side.BOTTOM,
                            0,
                            0
                    )
            );

            // Bấm đúp chuột trái vào tin nhắn: hiện / ẩn nút ba chấm.
            // Dùng filter để bắt sự kiện trước cả các node con (ảnh, label...).
            bubble.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                if (event.getButton() == MouseButton.PRIMARY
                        && event.getClickCount() == 2) {

                    boolean show = !moreButton.isVisible();

                    // Ẩn nút ⋮ của tin nhắn khác đang hiện.
                    if (show && activeMoreButton != null
                            && activeMoreButton != moreButton) {
                        if (activeContextMenu != null) {
                            activeContextMenu.hide();
                        }
                        activeMoreButton.setVisible(false);
                    }

                    moreButton.setVisible(show);

                    if (show) {
                        activeMoreButton = moreButton;
                        activeContextMenu = contextMenu;
                    } else {
                        contextMenu.hide();
                        if (activeMoreButton == moreButton) {
                            activeMoreButton = null;
                            activeContextMenu = null;
                        }
                    }
                }
            });

            // Khi menu đóng lại (chọn xong hoặc bấm ra ngoài) thì ẩn nút.
            contextMenu.showingProperty().addListener((obs, wasShowing, showing) -> {
                if (!showing) {
                    moreButton.setVisible(false);
                    if (activeMoreButton == moreButton) {
                        activeMoreButton = null;
                        activeContextMenu = null;
                    }
                }
            });

            // Tin nhắn của mình: nút nằm bên phải.
            // Tin nhắn người khác: nút nằm bên trái.
            if (message.sender().equals(username)) {
                row.getChildren().addAll(moreButton, bubble);
            } else {
                row.getChildren().addAll(bubble, moreButton);
            }

        } else {
            row.getChildren().add(bubble);
        }

        return row;
    }

    /** Chữ nhỏ, padding gọn, bo góc cho một mục trong menu tin nhắn. */
    private void styleMenuItem(MenuItem item) {
        item.setStyle(
                "-fx-font-size: 12px;"
                        + "-fx-padding: 5 14 5 14;"
                        + "-fx-background-radius: 6;"
        );
    }

}
