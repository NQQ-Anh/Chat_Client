package com.chat.platform.client.ui;

import static com.chat.platform.client.util.ErrorUtils.rootMessage;

import com.chat.platform.client.util.CheckedRunnable;
import javafx.application.Platform;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Chạy tác vụ mạng nền (daemon thread) và báo lỗi lên giao diện. */
public final class AsyncRunner {
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "chat-http");
        thread.setDaemon(true);
        return thread;
    });

    /** Chạy tác vụ; nếu ném exception thì hiện hộp thoại lỗi. */
    public void run(CheckedRunnable task) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                Platform.runLater(() -> Dialogs.showError(rootMessage(e)));
            }
        });
    }

    /** Chạy tác vụ thô, tự xử lý lỗi. */
    public void execute(Runnable task) {
        executor.execute(task);
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
