package com.chat.platform.client.util;

@FunctionalInterface
public interface CheckedRunnable {
    void run() throws Exception;
}
