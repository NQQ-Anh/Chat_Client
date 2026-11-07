package com.chat.platform.client.network;

import java.net.http.HttpResponse;
import java.time.Duration;

public final class ChatApi {
    private final ChatApiClient client;
    private final String baseUrl;
    private final String token;

    public ChatApi(ChatApiClient client, String baseUrl, String token) {
        this.client = client;
        this.baseUrl = baseUrl;
        this.token = token;
    }

    public HttpResponse<String> get(String path) throws Exception {
        return client.get(baseUrl + path, token);
    }

    public HttpResponse<String> post(String path, Object body) throws Exception {
        return client.sendJson(baseUrl + path, body, token);
    }

    public HttpResponse<String> delete(String path) throws Exception {
        return client.delete(baseUrl + path, token);
    }

    public HttpResponse<String> upload(String path, String boundary, byte[] body) throws Exception {
        return client.postMultipart(baseUrl + path, token, boundary, body);
    }

    public HttpResponse<byte[]> getBytes(String path, Duration timeout) throws Exception {
        return client.getBytes(baseUrl + path, token, timeout);
    }

    public HttpResponse<String> patch(String path, Object body) throws Exception {
        return client.patchJson(baseUrl + path, body, token);
    }
}
