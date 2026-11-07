package com.chat.platform.client.network;

import static com.chat.platform.client.util.JsonUtils.toJson;
import static com.chat.platform.client.util.NetworkUtils.webSocketUrl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Kết nối WebSocket tới server. Các lệnh gửi được xếp hàng tuần tự vì
 * java.net.http.WebSocket không cho gọi sendText khi lần gửi trước chưa hoàn tất.
 * Handler được gọi trên luồng của WebSocket, không phải luồng JavaFX.
 */
public final class ChatSocket implements WebSocket.Listener {
    public interface Handler {
        void onMessage(String json);

        void onError(Throwable error);

        void onClosed(int statusCode, String reason);
    }

    private static final Handler NO_OP = new Handler() {
        @Override
        public void onMessage(String json) {
        }

        @Override
        public void onError(Throwable error) {
        }

        @Override
        public void onClosed(int statusCode, String reason) {
        }
    };

    private final StringBuilder incoming = new StringBuilder();
    private volatile Handler handler = NO_OP;
    private volatile boolean closed;
    private volatile WebSocket webSocket;
    private CompletableFuture<Void> lastSend = CompletableFuture.completedFuture(null);

    private ChatSocket() {
    }

    /** Kết nối (blocking). Ném exception nếu không kết nối được. */
    public static ChatSocket connect(HttpClient http, String baseUrl, String token) {
        ChatSocket socket = new ChatSocket();
        socket.webSocket = http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer " + token)
                .buildAsync(URI.create(webSocketUrl(baseUrl)), socket).join();
        return socket;
    }

    public void setHandler(Handler handler) {
        this.handler = handler == null ? NO_OP : handler;
    }

    public synchronized void send(Object payload) {
        WebSocket target = webSocket;
        if (closed || target == null) {
            return;
        }
        String text = toJson(payload);
        lastSend = lastSend
                .thenCompose(ignored -> target.sendText(text, true))
                .<Void>thenApply(ignored -> null)
                .exceptionally(error -> {
                    if (!closed) {
                        handler.onError(error);
                    }
                    return null;
                });
    }

    public synchronized void close(String reason) {
        if (closed) {
            return;
        }
        closed = true;
        WebSocket target = webSocket;
        if (target == null) {
            return;
        }
        lastSend = lastSend
                .thenCompose(ignored -> target.isOutputClosed()
                        ? CompletableFuture.<WebSocket>completedFuture(target)
                        : target.sendClose(WebSocket.NORMAL_CLOSURE, reason))
                .<Void>thenApply(ignored -> null)
                .exceptionally(error -> null);
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        String complete = null;
        synchronized (incoming) {
            incoming.append(data);
            if (last) {
                complete = incoming.toString();
                incoming.setLength(0);
            }
        }
        if (complete != null && !closed) {
            handler.onMessage(complete);
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        if (!closed) {
            handler.onError(error);
        }
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        if (!closed) {
            handler.onClosed(statusCode, reason);
        }
        return null;
    }
}
