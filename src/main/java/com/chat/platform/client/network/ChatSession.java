package com.chat.platform.client.network;

/** Thông tin một phiên đăng nhập đang hoạt động. */
public record ChatSession(String baseUrl, String username, String token, ChatSocket socket) {
}
