package com.chat.platform.client.controller;

import static com.chat.platform.client.network.ChatApiClient.ensureSuccess;
import static com.chat.platform.client.util.ErrorUtils.rootMessage;
import static com.chat.platform.client.util.JsonUtils.JSON;
import static com.chat.platform.client.util.NetworkUtils.encodePathSegment;
import static com.chat.platform.client.util.NetworkUtils.multipartBody;

import com.chat.platform.client.model.DirectConversation;
import com.chat.platform.client.model.Member;
import com.chat.platform.client.model.Message;
import com.chat.platform.client.model.Room;
import com.chat.platform.client.network.ChatApi;
import com.chat.platform.client.network.ChatApiClient;
import com.chat.platform.client.network.ChatSession;
import com.chat.platform.client.network.ChatSocket;
import com.chat.platform.client.ui.AsyncRunner;
import com.chat.platform.client.ui.AvatarService;
import com.chat.platform.client.ui.ConversationSidebar;
import com.chat.platform.client.ui.Dialogs;
import com.chat.platform.client.ui.MessagePane;
import com.chat.platform.client.ui.Styles;
import com.chat.platform.client.ui.dialogs.CreateGroupDialog;
import com.chat.platform.client.ui.dialogs.FindFriendDialog;
import com.chat.platform.client.ui.dialogs.GroupInfoDialog;
import com.chat.platform.client.ui.dialogs.ProfileDialog;
import com.chat.platform.client.util.TextUtils;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.Alert;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

/**
 * Điều phối màn hình chat: giữ trạng thái cuộc trò chuyện đang mở, gọi REST API,
 * xử lý sự kiện WebSocket và nối các view (sidebar, khung tin nhắn, dialog) với nhau.
 * Mọi phương thức public/private đều chạy trên luồng JavaFX, trừ phần bên trong runner.run(...).
 */
public final class ChatController implements ConversationSidebar.Listener, MessagePane.Listener {
    private final AsyncRunner runner;
    private final ChatSession session;
    private final String username;
    private String displayName;

    private final ChatSocket socket;
    private final ChatApi api;
    private final Stage stage;
    private final Runnable onLogout;
    private final AvatarService avatars;
    private final ConversationSidebar sidebar;
    private final MessagePane messagePane;
    private final BorderPane root = new BorderPane();
    private final Set<String> subscriptions = new HashSet<>();

    private final Map<String, Long> roomUnreadCounts = new java.util.HashMap<>();

    private final Map<String, Long> directUnreadCounts = new java.util.HashMap<>();

    private String activeRoomId;
    private boolean activeDirectConversation;
    private String activeDirectUsername;
    private boolean disposed;

    public ChatController(ChatApiClient http, AsyncRunner runner, ChatSession session,
                          Stage stage, Runnable onLogout) {
        this.runner = runner;
        this.session = session;
        this.username = session.username();
        this.displayName = username;
        this.socket = session.socket();
        this.stage = stage;
        this.onLogout = onLogout;
        this.api = new ChatApi(http, session.baseUrl(), session.token());
        this.avatars = new AvatarService(api, runner);
        this.sidebar = new ConversationSidebar(username, avatars, this);
        this.messagePane = new MessagePane(username, avatars, this);

        root.getStyleClass().add("chat-root");
        root.setCenter(messagePane.root());
        root.setLeft(sidebar.root());

        socket.setHandler(new ChatSocket.Handler() {
            @Override
            public void onMessage(String json) {
                Platform.runLater(() -> {
                    if (!disposed) {
                        handleServerMessage(json);
                    }
                });
            }

            @Override
            public void onError(Throwable error) {
                Platform.runLater(() -> {
                    if (!disposed) {
                        Dialogs.showError("WebSocket: " + rootMessage(error));
                    }
                });
            }

            @Override
            public void onClosed(int statusCode, String reason) {
                Platform.runLater(() -> {
                    if (!disposed) {
                        Dialogs.showError("Kết nối realtime với server đã đóng. "
                                + "Hãy đăng xuất và đăng nhập lại để nhận tin nhắn mới.");
                    }
                });
            }
        });

        runner.run(() -> {
            refreshRooms(null);
            refreshDirectConversations(null);
            refreshUnreadCounts();
        });
    }

    public Parent root() {
        return root;
    }

    /** Đóng kết nối và hủy mọi kết quả bất đồng bộ còn chờ (khi đăng xuất hoặc tắt ứng dụng). */
    public void dispose(String reason) {
        if (disposed) {
            return;
        }
        disposed = true;
        socket.close(reason);
        avatars.close();
    }

    private void logout() {
        dispose("Logged out");
        onLogout.run();
        runner.execute(() -> {
            try {
                ensureSuccess(api.post("/api/auth/logout", Map.of()));
            } catch (Exception e) {
                Platform.runLater(() -> Dialogs.showError(
                        "Đã đăng xuất khỏi ứng dụng nhưng không thể thu hồi phiên trên server: "
                                + rootMessage(e)));
            }
        });
    }


    @Override
    public void onRoomSelected(Room room) {
        openRoom(room);
    }

    @Override
    public void onDirectSelected(DirectConversation conversation) {
        openDirectConversation(conversation);
    }

    @Override
    public void onFindFriend() {
        new FindFriendDialog(api, runner, avatars, this::startDirectConversation).show();
    }

    @Override
    public void onCreateGroup() {
        CreateGroupDialog.show(this::createRoom);
    }

    @Override
    public void onJoinRoom(String roomId) {
        joinRoom(roomId);
    }

    @Override
    public void onMemberChat(String otherUsername) {
        startDirectConversation(otherUsername);
    }

    // ----------------------------------------------------------- message pane
    @Override
    public void onRecall(Message message) {

        if (message == null || message.messageId() <= 0) {
            return;
        }

        if (!username.equals(message.sender())) {
            return;
        }

        if (message.recalled()) {
            return;
        }

        String conversationId = activeRoomId;

        if (conversationId == null || conversationId.isBlank()) {
            return;
        }

        if (!conversationId.equals(message.roomId())) {
            return;
        }

        boolean isDirect = activeDirectConversation;

        if (!Dialogs.confirm(
                "Thu hồi tin nhắn",
                null,
                "Bạn có chắc chắn muốn thu hồi tin nhắn này không?")) {
            return;
        }

        runner.run(() -> {
            try {

                String endpoint;

                if (isDirect) {
                    endpoint = directPath(conversationId)
                            + "/messages/"
                            + message.messageId()
                            + "/recall";
                } else {
                    endpoint = roomPath(conversationId)
                            + "/messages/"
                            + message.messageId()
                            + "/recall";
                }

                HttpResponse<String> response =
                        api.post(endpoint, Map.of());

                ensureSuccess(response);

                Platform.runLater(() -> {
                    if (!disposed) {
                        Dialogs.showInfo("Đã thu hồi tin nhắn.");
                    }
                });

            } catch (Exception e) {

                Platform.runLater(() -> {
                    if (!disposed) {
                        Dialogs.showError(
                                "Không thể thu hồi tin nhắn: "
                                        + rootMessage(e)
                        );
                    }
                });
            }
        });
    }
    @Override
    public void onProfile() {
        ProfileDialog.show(
                stage,
                username,
                displayName,
                avatars,
                this::changeAvatar,
                this::changeDisplayName,
                this::logout
        );
    }

    @Override
    public void onInfo() {
        showConversationInfo();
    }

    @Override
    public void onAttach() {
        uploadFile();
    }

    @Override
    public boolean onSend(String content) {
        String roomId = activeRoomId;
        if (roomId == null) {
            return false;
        }
        if (activeDirectConversation) {
            socket.send(Map.of("type", "direct_message",
                    "conversationId", roomId, "content", content));
        } else {
            socket.send(Map.of("type", "message", "roomId", roomId, "content", content));
        }
        return true;
    }

    @Override
    public void onDownload(Message message) {
        downloadFile(message);
    }

    @Override
    public void onPreview(Message message) {
        previewImage(message);
    }

    @Override
    public void loadImage(Message message, Consumer<Image> callback) {
        runner.run(() -> {
            byte[] bytes = downloadBytes(message.fileId());
            Image image = new Image(new ByteArrayInputStream(bytes));
            Platform.runLater(() -> {
                if (!disposed) {
                    callback.accept(image);
                }
            });
        });
    }

    @Override
    public void loadMedia(Message message, Consumer<Path> callback) {
        loadMediaAsync(message, callback);
    }

    private void loadMediaAsync(Message message, Consumer<Path> callback) {
        CompletableFuture.runAsync(() -> {
            try {
                byte[] bytes = downloadBytes(message.fileId());

                String fileName = message.fileName();
                String suffix = ".tmp";

                if (fileName != null && fileName.lastIndexOf('.') >= 0) {
                    String extension = fileName.substring(fileName.lastIndexOf('.'));
                    if (extension.matches("\\.[a-zA-Z0-9]{1,10}")) {
                        suffix = extension;
                    }
                }

                Path tempFile = Files.createTempFile("chat-media-", suffix);
                Files.write(tempFile, bytes);

                Platform.runLater(() -> callback.accept(tempFile));

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> callback.accept(null));
            }
        });
    }

    private static String roomPath(String roomId) {
        return "/api/rooms/" + encodePathSegment(roomId);
    }

    private static String directPath(String conversationId) {
        return "/api/direct-conversations/" + encodePathSegment(conversationId);
    }

    /** Tải lại danh sách nhóm; nếu openRoomId != null thì mở nhóm đó sau khi tải. */
    private void refreshRooms(String openRoomId) throws Exception {
        HttpResponse<String> response = api.get("/api/rooms");
        ensureSuccess(response);
        List<Room> rooms = JSON.readerForListOf(Room.class).readValue(response.body());
        Platform.runLater(() -> {
            if (disposed) {
                return;
            }
            sidebar.setRooms(rooms);
            if (openRoomId != null) {
                rooms.stream().filter(room -> room.roomId().equals(openRoomId)).findFirst()
                        .ifPresent(this::openRoom);
            }
        });
    }

    /** Tải lại danh sách chat riêng; nếu openConversationId != null thì mở cuộc trò chuyện đó. */
    private void refreshDirectConversations(String openConversationId) throws Exception {
        HttpResponse<String> response = api.get("/api/direct-conversations");
        ensureSuccess(response);
        List<DirectConversation> conversations =
                JSON.readerForListOf(DirectConversation.class).readValue(response.body());
        Platform.runLater(() -> {
            if (disposed) {
                return;
            }
            sidebar.setDirects(conversations);
            if (openConversationId != null) {
                conversations.stream()
                        .filter(conversation -> conversation.conversationId().equals(openConversationId))
                        .findFirst().ifPresent(this::openDirectConversation);
            }
        });
    }

    private void refreshUnreadCounts() throws Exception {

        // Lấy số tin nhắn chưa đọc trong nhóm
        HttpResponse<String> roomResponse =
                api.get("/api/rooms/unread-counts");

        ensureSuccess(roomResponse);

        JsonNode roomArray = JSON.readTree(roomResponse.body());

        Map<String, Long> roomCounts = new java.util.HashMap<>();

        if (roomArray.isArray()) {
            for (JsonNode item : roomArray) {
                String roomId = item.path("roomId").asText();
                long count = item.path("unreadCount").asLong();

                if (!roomId.isBlank() && count > 0) {
                    roomCounts.put(roomId, count);
                }
            }
        }

        // Lấy số tin nhắn chưa đọc trong chat riêng
        HttpResponse<String> directResponse =
                api.get("/api/direct-conversations/unread-counts");

        ensureSuccess(directResponse);

        JsonNode directArray = JSON.readTree(directResponse.body());

        Map<String, Long> directCounts = new java.util.HashMap<>();

        if (directArray.isArray()) {
            for (JsonNode item : directArray) {
                String conversationId =
                        item.path("conversationId").asText();

                long count = item.path("unreadCount").asLong();

                if (!conversationId.isBlank() && count > 0) {
                    directCounts.put(conversationId, count);
                }
            }
        }

        Platform.runLater(() -> {
            if (disposed) {
                return;
            }

            roomUnreadCounts.clear();
            roomUnreadCounts.putAll(roomCounts);

            directUnreadCounts.clear();
            directUnreadCounts.putAll(directCounts);

            sidebar.setRoomUnreadCounts(roomUnreadCounts);
            sidebar.setDirectUnreadCounts(directUnreadCounts);
        });
    }

    private void refreshMembers(String roomId) {
        runner.run(() -> {
            HttpResponse<String> response = api.get(roomPath(roomId) + "/members");
            ensureSuccess(response);
            List<Member> members = JSON.readerForListOf(Member.class).readValue(response.body());
            Platform.runLater(() -> {
                if (!disposed && roomId.equals(activeRoomId)) {
                    setMembers(members);
                }
            });
        });
    }

    private void setMembers(List<Member> members) {
        sidebar.setMembers(members);
        long onlineCount = members.stream().filter(Member::online).count();
        messagePane.setMembersText(members.size() + " thành viên · " + onlineCount + " online",
                onlineCount > 0);
    }


    private void openRoom(Room room) {
        activeRoomId = room.roomId();
        roomUnreadCounts.remove(room.roomId());
        sidebar.setRoomUnreadCounts(roomUnreadCounts);

        activeDirectConversation = false;
        activeDirectUsername = null;

        sidebar.setActive(room.roomId(), false);
        sidebar.setMembers(List.of());

        messagePane.setTitle(room.name());
        messagePane.setGroupAvatar();
        messagePane.setMembersText("");
        messagePane.setInputEnabled(true);
        messagePane.clearMessages();

        if (subscriptions.add("room:" + room.roomId())) {
            socket.send(Map.of("type", "subscribe", "roomId", room.roomId()));
        }
        runner.run(() -> {
            HttpResponse<String> response = api.get(roomPath(room.roomId()) + "/messages");
            ensureSuccess(response);
            List<Message> history = JSON.readerForListOf(Message.class).readValue(response.body());
            HttpResponse<String> membersResponse = api.get(roomPath(room.roomId()) + "/members");
            ensureSuccess(membersResponse);
            List<Member> members = JSON.readerForListOf(Member.class).readValue(membersResponse.body());
            Platform.runLater(() -> {
                if (disposed || activeDirectConversation || !room.roomId().equals(activeRoomId)) {
                    return;
                }
                setMembers(members);
                history.forEach(messagePane::showMessage);
                history.stream().filter(message -> !message.sender().equals(username))
                        .forEach(this::markRead);
                messagePane.scrollToBottom();
            });
        });
    }

    private static String directStatus(boolean online) {
        return online ? "Trò chuyện riêng · Đang hoạt động" : "Trò chuyện riêng · Ngoại tuyến";
    }

    private void openDirectConversation(DirectConversation conversation) {
        activeRoomId = conversation.conversationId();
        directUnreadCounts.remove(conversation.conversationId());
        sidebar.setDirectUnreadCounts(directUnreadCounts);

        activeDirectConversation = true;
        activeDirectUsername = conversation.username();
        sidebar.setActive(conversation.conversationId(), true);
        sidebar.setMembers(List.of());

        messagePane.setTitle(
                        (conversation.displayName() == null
                        || conversation.displayName().isBlank()
                        ? conversation.username()
                        : conversation.displayName())
        );

        messagePane.setUserAvatar(conversation.username());

        messagePane.setMembersText(directStatus(conversation.online()), conversation.online());
        messagePane.setInputEnabled(true);
        messagePane.clearMessages();
        String subscription = "direct:" + conversation.conversationId();
        if (subscriptions.add(subscription)) {
            socket.send(Map.of("type", "direct_subscribe",
                    "conversationId", conversation.conversationId()));
        }
        runner.run(() -> {
            HttpResponse<String> response = api.get(directPath(conversation.conversationId()) + "/messages");
            ensureSuccess(response, "GET /api/direct-conversations/" + conversation.conversationId()
                    + "/messages");
            List<Message> history = JSON.readerForListOf(Message.class).readValue(response.body());
            Platform.runLater(() -> {
                if (disposed || !conversation.conversationId().equals(activeRoomId)
                        || !activeDirectConversation) {
                    return;
                }
                history.forEach(messagePane::showMessage);
                history.stream().filter(message -> !message.sender().equals(username))
                        .forEach(this::markRead);
                messagePane.scrollToBottom();
            });
        });
    }

    private void startDirectConversation(String otherUsername) {
        if (otherUsername.equals(username)) {
            return;
        }
        runner.run(() -> {
            HttpResponse<String> response = api.post(
                    "/api/direct-conversations/" + encodePathSegment(otherUsername), Map.of());
            ensureSuccess(response, "POST /api/direct-conversations/" + otherUsername);
            DirectConversation conversation = JSON.readValue(response.body(), DirectConversation.class);
            refreshDirectConversations(conversation.conversationId());
        });
    }

    private void markRead(Message message) {
        if (message.messageId() > 0) {
            if (activeDirectConversation) {
                socket.send(Map.of("type", "direct_read",
                        "conversationId", message.roomId(), "messageId", message.messageId()));
            } else {
                socket.send(Map.of("type", "read", "roomId", message.roomId(),
                        "messageId", message.messageId()));
            }
        }
    }

    // --------------------------------------------------------------- nhóm

    private void createRoom(String name, String memberNames) {
        if (name == null || name.isBlank()) {
            Dialogs.showError("Nhập tên nhóm trước khi tạo.");
            return;
        }
        List<String> invitees = TextUtils.splitUsernames(memberNames);
        runner.run(() -> {
            HttpResponse<String> response = api.post("/api/rooms",
                    Map.of("name", name.trim(), "members", invitees));
            ensureSuccess(response);
            Room room = JSON.readValue(response.body(), Room.class);
            refreshRooms(room.roomId());
        });
    }

    private void joinRoom(String roomId) {
        String id = roomId == null ? "" : roomId.trim();
        if (id.isBlank()) {
            Dialogs.showError("Nhập ID phòng cần tham gia.");
            return;
        }
        runner.run(() -> {
            HttpResponse<String> response = api.post(roomPath(id) + "/join", Map.of());
            ensureSuccess(response);
            Room room = JSON.readValue(response.body(), Room.class);
            refreshRooms(room.roomId());
        });
    }

    private Member currentMember() {
        return sidebar.members().stream()
                .filter(member -> member.username().equals(username)).findFirst().orElse(null);
    }

    private void showConversationInfo() {
        if (activeDirectConversation) {
            Dialogs.showInfo("Trò chuyện với " + activeDirectUsername
                    + "\nTrạng thái: " + messagePane.membersText());
            return;
        }
        if (activeRoomId == null) {
            return;
        }
        Room room = sidebar.rooms().stream().filter(item -> item.roomId().equals(activeRoomId))
                .findFirst().orElse(null);
        if (room == null) {
            return;
        }
        GroupInfoDialog.show(room, List.copyOf(sidebar.members()), username,
                new GroupInfoDialog.Actions(this::addMembersToCurrentGroup, this::leaveCurrentGroup,
                        this::transferGroupLeadership, this::deleteCurrentGroup));
    }

    private void addMembersToCurrentGroup() {
        String roomId = activeRoomId;
        if (roomId == null || activeDirectConversation) {
            return;
        }
        List<String> invitees = Dialogs.promptUsernames("Thêm thành viên",
                "Nhập username cần thêm (phân cách bằng dấu phẩy)", "alice, bob");
        if (invitees.isEmpty()) {
            return;
        }
        runner.run(() -> {
            HttpResponse<String> response = api.post(roomPath(roomId) + "/members",
                    Map.of("members", invitees));
            ensureSuccess(response);
            refreshMembers(roomId);
            refreshRooms(null);
        });
    }

    private void leaveCurrentGroup() {
        String roomId = activeRoomId;
        if (roomId == null || activeDirectConversation) {
            return;
        }
        Member current = currentMember();
        boolean isLeader = current != null && "LEADER".equals(current.role());
        Map<String, String> body = Map.of();
        if (isLeader) {
            List<String> candidates = sidebar.members().stream()
                    .map(Member::username).filter(member -> !member.equals(username)).toList();
            if (candidates.isEmpty()) {
                Dialogs.showError("Bạn là thành viên cuối cùng. Hãy dùng Xóa nhóm để kết thúc nhóm.");
                return;
            }
            String successor = Dialogs.chooseSuccessor(candidates);
            if (successor == null) {
                return;
            }
            body = Map.of("successorUsername", successor);
        }
        if (!Dialogs.confirm("Rời nhóm", null, "Bạn có chắc muốn rời nhóm này?")) {
            return;
        }
        Map<String, String> requestBody = body;
        runner.run(() -> {
            HttpResponse<String> response = api.post(roomPath(roomId) + "/leave", requestBody);
            ensureSuccess(response);
            Platform.runLater(() -> closeActiveGroup(roomId));
            refreshRooms(null);
        });
    }

    private void transferGroupLeadership() {
        String roomId = activeRoomId;
        Member selected = sidebar.selectedMember();
        if (roomId == null || activeDirectConversation || selected == null
                || selected.username().equals(username)) {
            Dialogs.showError("Chọn một thành viên khác trong danh sách để chuyển quyền.");
            return;
        }
        if (!Dialogs.confirm("Chuyển trưởng nhóm", null,
                "Chuyển quyền trưởng nhóm cho " + selected.username() + "?")) {
            return;
        }
        runner.run(() -> {
            HttpResponse<String> response = api.post(roomPath(roomId) + "/transfer-leadership",
                    Map.of("successorUsername", selected.username()));
            ensureSuccess(response);
            refreshMembers(roomId);
        });
    }

    private void deleteCurrentGroup() {
        String roomId = activeRoomId;
        if (roomId == null || activeDirectConversation) {
            return;
        }
        if (!Dialogs.confirm("Xóa nhóm", "Chỉ trưởng nhóm mới có thể thực hiện thao tác này.",
                "Nhóm sẽ bị ẩn với các thành viên. Lịch sử được giữ lại. Tiếp tục?")) {
            return;
        }
        runner.run(() -> {
            HttpResponse<String> response = api.delete(roomPath(roomId));
            ensureSuccess(response);
            Platform.runLater(() -> closeActiveGroup(roomId));
            refreshRooms(null);
        });
    }

    private void closeActiveGroup(String roomId) {
        if (!roomId.equals(activeRoomId)) {
            return;
        }
        if (subscriptions.remove("room:" + roomId)) {
            socket.send(Map.of("type", "unsubscribe", "roomId", roomId));
        }
        activeRoomId = null;
        activeDirectConversation = false;
        activeDirectUsername = null;
        sidebar.setActive(null, false);
        sidebar.setMembers(List.of());
        messagePane.setTitle("Chọn hoặc tham gia nhóm");
        messagePane.setMembersText("");
        messagePane.clearInput();
        messagePane.setInputEnabled(false);
        messagePane.clearMessages();
        messagePane.clearConversationAvatar();
    }

    // ----------------------------------------------------- tệp & avatar

    private void changeAvatar() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn ảnh đại diện");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Ảnh PNG, JPEG, GIF, BMP", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        java.io.File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        try {
            if (Files.size(file.toPath()) > 5L * 1024 * 1024) {
                Dialogs.showError("Ảnh đại diện vượt quá giới hạn 5 MB.");
                return;
            }
        } catch (IOException e) {
            Dialogs.showError("Không thể đọc ảnh: " + e.getMessage());
            return;
        }
        runner.run(() -> {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String boundary = "----AvatarBoundary" + System.nanoTime();
            String bodyType = Files.probeContentType(file.toPath());
            if (bodyType == null) {
                bodyType = "application/octet-stream";
            }
            byte[] body = multipartBody(boundary, file.getName(), bodyType, bytes);
            HttpResponse<String> response = api.upload("/api/profile/avatar", boundary, body);
            ensureSuccess(response);
            Platform.runLater(() -> {
                if (disposed) {
                    return;
                }
                avatars.invalidate(username);
                sidebar.refreshProfileAvatar();
                messagePane.refreshAvatars();
                sidebar.refreshCells();
                Dialogs.showInfo("Đã cập nhật ảnh đại diện.");
            });
        });
    }

    private void changeDisplayName(String newName) {
        if (newName == null || newName.isBlank()) {
            Dialogs.showError("Tên hiển thị không được để trống.");
            return;
        }

        String name = newName.trim();

        if (name.length() > 100) {
            Dialogs.showError("Tên hiển thị không được vượt quá 100 ký tự.");
            return;
        }

        runner.run(() -> {
            try {
                HttpResponse<String> response = api.patch(
                        "/api/profile",
                        Map.of("displayName", name)
                );

                ensureSuccess(response);

                // Lấy tên hiển thị server trả về.
                JsonNode result = JSON.readTree(response.body());
                String savedName = result.path("displayName").asText(name);

                Platform.runLater(() -> {
                    if (disposed) {
                        return;
                    }

                    displayName = savedName;
                    Dialogs.showInfo("Đã cập nhật tên hiển thị thành công.");
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!disposed) {
                        Dialogs.showError(
                                "Không thể cập nhật tên hiển thị: " + rootMessage(e)
                        );
                    }
                });
            }
        });
    }

    private void uploadFile() {
        if (activeRoomId == null) {
            Dialogs.showError("Chọn cuộc trò chuyện trước khi gửi file.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn ảnh hoặc tệp đính kèm");

        java.io.File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }

        try {
            if (Files.size(file.toPath()) > 10L * 1024 * 1024) {
                Dialogs.showError("File vượt quá giới hạn 10 MB.");
                return;
            }
        } catch (IOException e) {
            Dialogs.showError("Không thể đọc file: " + e.getMessage());
            return;
        }

        String conversationId = activeRoomId;
        boolean isDirect = activeDirectConversation;

        runner.run(() -> {
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());

                String boundary = "----ChatBoundary" + System.nanoTime();

                String contentType = Files.probeContentType(file.toPath());
                if (contentType == null) {
                    contentType = "application/octet-stream";
                }

                byte[] body = multipartBody(
                        boundary,
                        file.getName(),
                        contentType,
                        bytes
                );

                String endpoint = isDirect
                        ? directPath(conversationId) + "/files"
                        : roomPath(conversationId) + "/files";

                HttpResponse<String> response = api.upload(
                        endpoint, boundary, body
                );
                ensureSuccess(response);

            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(
                        "Upload thất bại: " + e.getMessage(), e
                );
            }
        });
    }

    private void previewImage(Message message) {
        runner.run(() -> {
            byte[] data = downloadBytes(message.fileId());
            Image image = new Image(new ByteArrayInputStream(data));
            Platform.runLater(() -> {
                ImageView imageView = new ImageView(image);
                imageView.setPreserveRatio(true);
                imageView.setFitWidth(800);
                imageView.setFitHeight(600);
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle(message.fileName());
                alert.setHeaderText(null);
                alert.getDialogPane().setContent(imageView);
                alert.getDialogPane().setPrefSize(850, 650);
                Styles.apply(alert.getDialogPane());
                alert.show();
            });
        });
    }

    private void downloadFile(Message message) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Lưu tệp");
        chooser.setInitialFileName(message.fileName() == null ? "attachment" : message.fileName());
        java.io.File target = chooser.showSaveDialog(stage);
        if (target != null) {
            runner.run(() -> Files.write(target.toPath(), downloadBytes(message.fileId())));
        }
    }

    private byte[] downloadBytes(String fileId) throws Exception {
        HttpResponse<byte[]> response = api.getBytes("/api/files/" + encodePathSegment(fileId),
                Duration.ofSeconds(20));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Tải file thất bại (HTTP " + response.statusCode() + ").");
        }
        return response.body();
    }

    // ------------------------------------------------ sự kiện từ server

    private void handleServerMessage(String payload) {
        try {
            JsonNode event = JSON.readTree(payload);
            String type = event.path("type").asText();
            switch (type) {
                case "message" -> onRoomMessage(event);
                case "direct_message" -> onDirectMessage(event);
                case "read" -> onReadReceipt(event, false);
                case "direct_read" -> onReadReceipt(event, true);
                case "message_recalled" -> onMessageRecalled(event, false);
                case "direct_message_recalled" -> onMessageRecalled(event, true);
                case "direct_presence" -> updateDirectPresence(event.path("conversationId").asText(),
                        event.path("username").asText(), event.path("online").asBoolean());
                case "presence" -> {
                    if (!activeDirectConversation && event.path("roomId").asText().equals(activeRoomId)) {
                        updateMemberPresence(event.path("username").asText(),
                                event.path("online").asBoolean());
                    }
                }
                case "group_created" -> runner.run(() -> refreshRooms(null));
                case "group_members_changed" -> {
                    String roomId = event.path("roomId").asText();
                    if (roomId.equals(activeRoomId)) refreshMembers(roomId);
                    runner.run(() -> refreshRooms(null));
                }
                case "group_member_left" -> {
                    String roomId = event.path("roomId").asText();
                    String member = event.path("username").asText();
                    if (roomId.equals(activeRoomId) && member.equals(username)) {
                        closeActiveGroup(roomId);
                    } else if (roomId.equals(activeRoomId)) {
                        refreshMembers(roomId);
                    }
                    runner.run(() -> refreshRooms(null));
                }
                case "group_leader_changed" -> {
                    String roomId = event.path("roomId").asText();
                    if (roomId.equals(activeRoomId)) {
                        refreshMembers(roomId);
                    }
                }
                case "group_deleted" -> {
                    String roomId = event.path("roomId").asText();
                    if (roomId.equals(activeRoomId)) {
                        closeActiveGroup(roomId);
                    }
                    runner.run(() -> refreshRooms(null));
                }
                case "error" -> Dialogs.showError(event.path("message").asText("Server request failed."));
                default -> {
                    // Sự kiện không xác định: bỏ qua.
                }
            }
        } catch (Exception e) {
            Dialogs.showError("Không đọc được dữ liệu server: " + rootMessage(e));
        }
    }

    private void onRoomMessage(JsonNode event) throws Exception {
        Message message = JSON.treeToValue(
                event.path("message"),
                Message.class
        );

        // Nếu đang mở đúng nhóm có tin nhắn mới
        if (!activeDirectConversation
                && message.roomId().equals(activeRoomId)) {

            messagePane.showMessage(message);

            if (!message.sender().equals(username)) {
                markRead(message);
            }

            messagePane.scrollToBottom();
            return;
        }

        // Tin nhắn thuộc nhóm chưa mở
        if (!message.sender().equals(username)) {
            String roomId = message.roomId();

            long unread = roomUnreadCounts.getOrDefault(roomId, 0L);
            roomUnreadCounts.put(roomId, unread + 1);

            sidebar.setRoomUnreadCounts(roomUnreadCounts);
        }
        Platform.runLater(messagePane::scrollToBottom);
        runner.run(() -> refreshRooms(null));
    }

    private void onDirectMessage(JsonNode event) throws Exception {
        Message message = JSON.treeToValue(
                event.path("message"),
                Message.class
        );

        // Nếu đang mở đúng cuộc trò chuyện riêng
        if (activeDirectConversation
                && message.roomId().equals(activeRoomId)) {

            messagePane.showMessage(message);

            if (!message.sender().equals(username)) {
                markRead(message);
            }

            messagePane.scrollToBottom();
            return;
        }

        // Tin nhắn thuộc cuộc trò chuyện riêng chưa mở
        if (!message.sender().equals(username)) {
            String conversationId = message.roomId();

            long unread = directUnreadCounts.getOrDefault(
                    conversationId,
                    0L
            );

            directUnreadCounts.put(conversationId, unread + 1);

            sidebar.setDirectUnreadCounts(directUnreadCounts);
        }
        Platform.runLater(messagePane::scrollToBottom);

        runner.run(() -> refreshDirectConversations(null));
    }

    private void showIncoming(Message message) {
        messagePane.showMessage(message);

        if (!message.sender().equals(username)) {
            markRead(message);
        }

        Platform.runLater(messagePane::scrollToBottom);
    }

    private void onReadReceipt(JsonNode event, boolean direct) {
        if (activeDirectConversation != direct) {
            return;
        }
        long messageId = event.path("messageId").asLong();
        List<String> readers = new ArrayList<>();
        event.path("readBy").forEach(node -> readers.add(node.asText()));
        messagePane.applyReadReceipt(activeRoomId, messageId, readers);
    }


    private void onMessageRecalled(JsonNode event, boolean direct) {
        String conversationId = direct
                ? event.path("conversationId").asText()
                : event.path("roomId").asText();

        long messageId = event.path("messageId").asLong(-1);

        if (conversationId.isBlank() || messageId < 1) {
            return;
        }

        // Chỉ cập nhật nếu đang mở đúng cuộc trò chuyện.
        if (activeDirectConversation != direct
                || !conversationId.equals(activeRoomId)) {
            return;
        }

        messagePane.applyRecall(conversationId, messageId);
    }


    private void updateMemberPresence(String memberUsername, boolean online) {
        if (memberUsername == null || memberUsername.isBlank()) {
            return;
        }
        List<Member> updatedMembers = sidebar.members().stream()
                .map(member -> member.username().equals(memberUsername)
                        ? new Member(member.username(), online, member.role()) : member)
                .toList();
        if (updatedMembers.stream().anyMatch(member -> member.username().equals(memberUsername))) {
            setMembers(updatedMembers);
        } else {
            refreshMembers(activeRoomId);
        }
    }

    private void updateDirectPresence(String conversationId, String otherUsername, boolean online) {
        sidebar.updateDirectPresence(conversationId, otherUsername, online);
        if (activeDirectConversation && conversationId.equals(activeRoomId)
                && otherUsername.equals(activeDirectUsername)) {
            messagePane.setMembersText(directStatus(online), online);
        }
    }
}
