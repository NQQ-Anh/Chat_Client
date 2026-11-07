package com.chat.platform.client.ui;

import static com.chat.platform.client.util.NetworkUtils.encodePathSegment;

import com.chat.platform.client.network.ChatApi;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;

import java.io.ByteArrayInputStream;
import java.lang.ref.WeakReference;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Tạo avatar tròn, tải ảnh từ server, cache và tự cập nhật các avatar đang hiển thị. Dùng trên luồng JavaFX. */
public final class AvatarService {
    private final Map<String, Image> cache = new HashMap<>();
    private final Set<String> withoutImage = new HashSet<>();
    private final Set<String> loading = new HashSet<>();
    private final Map<String, List<WeakReference<StackPane>>> views = new HashMap<>();
    private final Map<String, Long> requestVersions = new HashMap<>();

    private final ChatApi api;
    private final AsyncRunner runner;
    private boolean closed;

    public AvatarService(ChatApi api, AsyncRunner runner) {
        this.api = api;
        this.runner = runner;
    }

    /** Hủy phiên (khi đăng xuất): các kết quả tải ảnh đến muộn sẽ bị bỏ qua. */
    public void close() {
        closed = true;
        cache.clear();
        withoutImage.clear();
        loading.clear();
        views.clear();
        requestVersions.clear();
    }

    /** Xóa cache và tải lại ảnh trên mọi vị trí đang hiển thị người dùng này. */
    public void invalidate(String user) {
        if (user == null || user.isBlank() || closed) {
            return;
        }
        cache.remove(user);
        withoutImage.remove(user);
        requestVersions.merge(user, 1L, Long::sum);
        loading.remove(user);
        for (StackPane view : activeViews(user)) {
            showInitials(view);
        }
        request(user);
    }

    public StackPane groupAvatar() {
        Label mark = new Label("#");
        mark.getStyleClass().add("group-avatar-mark");
        StackPane badge = new StackPane(mark);
        badge.getStyleClass().add("group-avatar");
        badge.setMinSize(40, 40);
        badge.setPrefSize(40, 40);
        return badge;
    }

    public StackPane avatarView(String name) {
        Circle background = new Circle(12);
        background.setStyle("-fx-fill: #d9e2ef;");
        String letter = name == null || name.isBlank() ? "?"
                : name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
        Label initials = new Label(letter);
        initials.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #43536a;");
        ImageView imageView = new ImageView();
        imageView.setFitWidth(24);
        imageView.setFitHeight(24);
        imageView.setPreserveRatio(false);
        imageView.setClip(new Circle(12, 12, 12));
        imageView.setVisible(false);
        StackPane avatar = new StackPane(background, initials, imageView);
        avatar.setPrefSize(24, 24);
        avatar.setMinSize(24, 24);
        avatar.setMaxSize(24, 24);
        avatar.setUserData(name);
        Tooltip.install(avatar, new Tooltip(name));
        List<WeakReference<StackPane>> userViews = views.computeIfAbsent(name, key -> new ArrayList<>());
        userViews.removeIf(reference -> reference.get() == null);
        userViews.add(new WeakReference<>(avatar));
        Image cached = cache.get(name);
        if (cached != null) {
            applyImage(avatar, cached);
        } else if (!withoutImage.contains(name) && !closed) {
            request(name);
        }
        return avatar;
    }

    private List<StackPane> activeViews(String name) {
        List<WeakReference<StackPane>> references = views.get(name);
        if (references == null) {
            return List.of();
        }
        references.removeIf(reference -> reference.get() == null);
        return references.stream()
                .map(WeakReference::get)
                .filter(view -> view != null)
                .toList();
    }

    private void applyImage(StackPane avatar, Image image) {
        ImageView imageView = (ImageView) avatar.getChildren().get(2);
        imageView.setImage(image);
        imageView.setVisible(true);
        avatar.getChildren().get(1).setVisible(false);
    }

    private void showInitials(StackPane avatar) {
        ImageView imageView = (ImageView) avatar.getChildren().get(2);
        imageView.setImage(null);
        imageView.setVisible(false);
        avatar.getChildren().get(1).setVisible(true);
    }

    private void request(String name) {
        if (!loading.add(name)) {
            return;
        }
        long requestVersion = requestVersions.getOrDefault(name, 0L);
        runner.execute(() -> {
            Image image = null;
            try {
                HttpResponse<byte[]> response = api.getBytes(
                        "/api/users/" + encodePathSegment(name) + "/avatar", Duration.ofSeconds(15));
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    Image decoded = new Image(new ByteArrayInputStream(response.body()));
                    if (!decoded.isError()) {
                        image = decoded;
                    }
                }
            } catch (Exception ignored) {
                // Avatar chỉ là phần trang trí: hiện chữ cái đầu thay vì báo lỗi cho từng ô.
            }
            Image loaded = image;
            Platform.runLater(() -> {
                if (closed || requestVersions.getOrDefault(name, 0L) != requestVersion) {
                    return;
                }
                loading.remove(name);
                if (loaded == null) {
                    withoutImage.add(name);
                    return;
                }
                cache.put(name, loaded);
                for (StackPane view : activeViews(name)) {
                    applyImage(view, loaded);
                }
            });
        });
    }
}
