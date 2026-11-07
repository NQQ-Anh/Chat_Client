package com.chat.platform.client.ui;

import static com.chat.platform.client.ui.UiUtils.spacer;

import com.chat.platform.client.model.ConversationEntry;
import com.chat.platform.client.model.DirectConversation;
import com.chat.platform.client.model.Member;
import com.chat.platform.client.model.Room;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Cột bên trái: danh sách nhóm / chat riêng, tìm kiếm, lọc, danh sách thành viên và nút tạo/tham gia nhóm. */
public final class ConversationSidebar {
    public interface Listener {
        void onRoomSelected(Room room);

        void onDirectSelected(DirectConversation conversation);

        void onFindFriend();

        void onCreateGroup();

        void onJoinRoom(String roomId);

        void onMemberChat(String username);

        void onProfile();
    }

    private final String username;
    private final AvatarService avatars;
    private final Listener listener;
    private Button profileButton;

    private final ObservableList<Room> rooms = FXCollections.observableArrayList();
    private final ObservableList<DirectConversation> directs = FXCollections.observableArrayList();

    private Map<String, Long> roomUnreadCounts = Map.of();
    private Map<String, Long> directUnreadCounts = Map.of();

    private final ListView<ConversationEntry> conversationList = new ListView<>();
    private final ListView<Member> memberList = new ListView<>();
    private final TextField conversationSearch = new TextField();
    private final ComboBox<String> conversationFilter = new ComboBox<>();
    private final VBox root;

    private String activeId;
    private boolean activeDirect;
    /** true khi đang thay đổi lựa chọn bằng code, để không kích hoạt listener. */
    private boolean updating;

    public ConversationSidebar(String username, AvatarService avatars, Listener listener) {
        this.username = username;
        this.avatars = avatars;
        this.listener = listener;

        conversationList.getStyleClass().add("conversation-list");
        conversationList.setAccessibleText("Danh sách cuộc trò chuyện");

        conversationList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(ConversationEntry entry, boolean empty) {
                super.updateItem(entry, empty);

                if (empty || entry == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                boolean group = entry.room() != null;

                String name = group
                        ? entry.room().name()
                        : (entry.direct().displayName() == null
                        || entry.direct().displayName().isBlank()
                        ? entry.direct().username()
                        : entry.direct().displayName());

                // Lấy số tin nhắn chưa đọc
                long unreadCount = group
                        ? roomUnreadCounts.getOrDefault(entry.room().roomId(), 0L)
                        : directUnreadCounts.getOrDefault(entry.direct().conversationId(), 0L);

                // Tên cuộc trò chuyện
                Label heading = new Label(name);
                heading.getStyleClass().add("room-name");

                // Trạng thái hoặc thông báo tin nhắn mới
                String subtitle;

                if (unreadCount > 0) {
                    subtitle = group
                            ? "Có tin nhắn mới"
                            : "Bạn có tin nhắn mới";
                } else {
                    subtitle = group
                            ? "Nhóm"
                            : (entry.direct().online()
                            ? "Đang hoạt động"
                            : "Ngoại tuyến");
                }

                Label kind = new Label(subtitle);
                kind.getStyleClass().add("member-status");

                if (unreadCount > 0) {
                    kind.getStyleClass().add("unread-preview");
                } else if (!group && entry.direct().online()) {
                    kind.getStyleClass().add("online");
                }

                // Thông tin tên và trạng thái
                VBox details = new VBox(3, heading, kind);

                // Số tin nhắn chưa đọc
                Label badge = new Label(String.valueOf(unreadCount));
                badge.getStyleClass().add("unread-count");
                badge.setVisible(unreadCount > 0);
                badge.setManaged(unreadCount > 0);

                // Avatar
                Node avatar = group
                        ? avatars.groupAvatar()
                        : avatars.avatarView(entry.direct().username());

                // Một dòng cuộc trò chuyện
                HBox row = new HBox(
                        9,
                        avatar,
                        details,
                        spacer(),
                        badge
                );

                row.setAlignment(Pos.CENTER_LEFT);

                HBox.setHgrow(details, Priority.ALWAYS);

                setText(null);
                setGraphic(row);
            }
        });

        conversationList.getSelectionModel().selectedItemProperty().addListener((observable, previous, selected) -> {
            if (updating || selected == null) return;
            if (selected.room() != null) listener.onRoomSelected(selected.room());
            else listener.onDirectSelected(selected.direct());
        });
        conversationList.setPlaceholder(new Label("Chưa có cuộc trò chuyện"));
        VBox.setVgrow(conversationList, Priority.ALWAYS);

        conversationSearch.setPromptText("Tìm kiếm cuộc trò chuyện");
        conversationSearch.getStyleClass().add("sidebar-field");
        conversationSearch.textProperty().addListener((observable, oldValue, newValue) -> rebuild());
        conversationFilter.getItems().setAll("Tất cả", "Nhóm", "Cá nhân");
        conversationFilter.setValue("Tất cả");
        conversationFilter.setAccessibleText("Lọc cuộc trò chuyện");
        conversationFilter.getStyleClass().add("conversation-filter");
        conversationFilter.setOnAction(event -> rebuild());

        memberList.getStyleClass().add("member-list");
        memberList.setPrefHeight(190);
        memberList.setPlaceholder(new Label("Chọn phòng để xem thành viên"));
        memberList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Member member, boolean empty) {
                super.updateItem(member, empty);
                if (empty || member == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label name = new Label(member.username());
                name.getStyleClass().add("member-name");
                Label status = new Label(member.online() ? "Đang hoạt động" : "Ngoại tuyến");
                status.getStyleClass().addAll("member-status",
                        member.online() ? "online" : "offline");
                Label role = new Label("LEADER".equals(member.role()) ? "Trưởng nhóm" : "Thành viên");
                role.getStyleClass().add("member-role");
                VBox details = new VBox(2, name, status, role);
                StackPane avatar = avatars.avatarView(member.username());
                if (!member.username().equals(username)) {
                    installDirectChatMenu(avatar, member.username());
                }
                HBox row = new HBox(8, avatar, details);
                row.setAlignment(Pos.CENTER_LEFT);
                setText(null);
                setGraphic(row);
            }
        });

        Button createRoom = new Button("+");
        createRoom.setAccessibleText("Tạo nhóm mới");
        createRoom.setTooltip(new Tooltip("Tạo nhóm mới"));
        createRoom.getStyleClass().addAll("primary-button", "create-group-button");
        createRoom.setOnAction(event -> listener.onCreateGroup());
        TextField roomId = new TextField();
        roomId.setPromptText("ID phòng được chia sẻ");
        roomId.getStyleClass().add("sidebar-field");
        Button joinRoom = new Button("Tham gia");
        joinRoom.getStyleClass().add("secondary-button");
        joinRoom.setOnAction(event -> listener.onJoinRoom(roomId.getText()));

        Label conversationsHeading = new Label("Tin nhắn");
        conversationsHeading.getStyleClass().add("section-title");

        profileButton = new Button();
        profileButton.setGraphic(avatars.avatarView(username));
        profileButton.setAccessibleText("Thông tin tài khoản và đăng xuất");
        profileButton.setTooltip(new Tooltip("Tài khoản"));
        profileButton.getStyleClass().add("profile-button");
        profileButton.setOnAction(event -> listener.onProfile());

        Button findFriendButton = new Button("Tìm bạn");
        findFriendButton.getStyleClass().add("secondary-button");
        findFriendButton.setOnAction(event -> listener.onFindFriend());
        HBox conversationsHeader = new HBox(8, profileButton, conversationsHeading ,spacer(), findFriendButton);
        conversationsHeader.setAlignment(Pos.CENTER_LEFT);

        HBox searchAndFilter = new HBox(6, conversationSearch, conversationFilter);
        searchAndFilter.getStyleClass().add("conversation-toolbar");
        HBox.setHgrow(conversationSearch, Priority.ALWAYS);
        HBox createAndJoin = new HBox(6, createRoom, roomId, joinRoom);
        createAndJoin.getStyleClass().add("join-room-row");
        HBox.setHgrow(roomId, Priority.ALWAYS);
        Label membersHeading = new Label("Người trò chuyện");
        membersHeading.getStyleClass().addAll("section-title", "member-section-title");

        root = new VBox(10, conversationsHeader, searchAndFilter,
                conversationList, new Separator(),
                membersHeading, memberList, createAndJoin);
        root.getStyleClass().add("sidebar");
        root.setPadding(new Insets(14));
        root.setPrefWidth(285);
    }

    public void refreshProfileAvatar() {
        profileButton.setGraphic(avatars.avatarView(username));
    }

    public Node root() {
        return root;
    }

    public void setRooms(List<Room> newRooms) {
        rooms.setAll(newRooms);
        rebuild();
    }

    public List<Room> rooms() {
        return rooms;
    }

    public void setDirects(List<DirectConversation> conversations) {
        directs.setAll(conversations);
        rebuild();
    }

    public void setRoomUnreadCounts(Map<String, Long> counts) {
        roomUnreadCounts = counts == null
                ? Map.of()
                : Map.copyOf(counts);

        rebuild();
    }

    public void setDirectUnreadCounts(Map<String, Long> counts) {
        directUnreadCounts = counts == null
                ? Map.of()
                : Map.copyOf(counts);
        rebuild();
    }

    public List<DirectConversation> directs() {
        return directs;
    }

    public void updateDirectPresence(String conversationId, String otherUsername, boolean online) {
        for (int index = 0; index < directs.size(); index++) {
            DirectConversation conversation = directs.get(index);
            if (conversation.conversationId().equals(conversationId)
                    && conversation.username().equals(otherUsername)) {
                directs.set(index, new DirectConversation(conversationId, otherUsername,conversation.displayName(), online));
                break;
            }
        }
        rebuild();
    }

    public void setMembers(List<Member> members) {
        memberList.getItems().setAll(members);
    }

    public List<Member> members() {
        return memberList.getItems();
    }

    public Member selectedMember() {
        return memberList.getSelectionModel().getSelectedItem();
    }

    /** Đánh dấu cuộc trò chuyện đang mở (id = null nếu không có). */
    public void setActive(String id, boolean direct) {
        activeId = id;
        activeDirect = direct;
        updating = true;
        try {
            selectActive();
        } finally {
            updating = false;
        }
    }

    /** Vẽ lại các ô (ví dụ sau khi avatar thay đổi). */
    public void refreshCells() {
        conversationList.refresh();
        memberList.refresh();
    }

    // ---------- nội bộ ----------

    private void selectActive() {
        if (activeId != null) {
            for (ConversationEntry entry : conversationList.getItems()) {
                boolean matches = activeDirect
                        ? entry.direct() != null && entry.direct().conversationId().equals(activeId)
                        : entry.room() != null && entry.room().roomId().equals(activeId);
                if (matches) {
                    conversationList.getSelectionModel().select(entry);
                    return;
                }
            }
        }
        conversationList.getSelectionModel().clearSelection();
    }

    private void rebuild() {
        List<ConversationEntry> all = new ArrayList<>();

        rooms.forEach(room ->
                all.add(new ConversationEntry(room, null)));

        directs.forEach(direct ->
                all.add(new ConversationEntry(null, direct)));

        String query = conversationSearch.getText() == null
                ? ""
                : conversationSearch.getText().trim().toLowerCase();

        String filter = conversationFilter.getValue() == null
                ? "Tất cả"
                : conversationFilter.getValue();

        List<ConversationEntry> filtered = all.stream()
                .filter(entry -> {
                    boolean group = entry.room() != null;

                    boolean typeMatches = "Tất cả".equals(filter)
                            || ("Nhóm".equals(filter) && group)
                            || ("Cá nhân".equals(filter) && !group);

                    String name = group
                            ? entry.room().name()
                            : ((entry.direct().displayName() == null
                            || entry.direct().displayName().isBlank())
                            ? entry.direct().username()
                            : entry.direct().displayName());

                    return typeMatches
                            && name.toLowerCase().contains(query);
                })
                .sorted((a, b) ->
                        Long.compare(getUnreadCount(b), getUnreadCount(a)))
                .toList();

        updating = true;
        try {
            conversationList.getItems().setAll(filtered);
            selectActive();
        } finally {
            updating = false;
        }
    }

    private void installDirectChatMenu(StackPane avatar, String otherUsername) {
        avatar.setCursor(Cursor.HAND);
        avatar.setAccessibleText("Mở trò chuyện riêng với " + otherUsername);
        avatar.setFocusTraversable(true);
        avatar.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                directChatMenu(otherUsername).show(avatar, Side.BOTTOM, 0, 0);
                event.consume();
            }
        });
        avatar.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                directChatMenu(otherUsername).show(avatar, Side.BOTTOM, 0, 0);
                event.consume();
            }
        });
    }

    private ContextMenu directChatMenu(String otherUsername) {
        MenuItem chatPrivately = new MenuItem("Trò chuyện riêng");
        chatPrivately.setOnAction(event -> listener.onMemberChat(otherUsername));
        return new ContextMenu(chatPrivately);
    }

    private long getUnreadCount(ConversationEntry entry) {
        if (entry.room() != null) {
            return roomUnreadCounts.getOrDefault(
                    entry.room().roomId(), 0L);
        }

        return directUnreadCounts.getOrDefault(
                entry.direct().conversationId(), 0L);
    }
}
