package com.chat.platform.client.network;

import static com.chat.platform.client.util.JsonUtils.errorText;
import static com.chat.platform.client.util.JsonUtils.toJson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Gói các lời gọi HTTP tới server chat (không giữ trạng thái đăng nhập). */
public final class ChatApiClient {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public HttpClient httpClient() {
        return http;
    }

    public HttpResponse<String> get(String url, String bearer) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + bearer).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<byte[]> getBytes(String url, String bearer, Duration timeout) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Authorization", "Bearer " + bearer).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    public HttpResponse<String> sendJson(String url, Object body, String bearer) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body)));
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> delete(String url, String bearer) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + bearer).DELETE().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public HttpResponse<String> postMultipart(String url, String bearer, String boundary, byte[] body)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + bearer)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public static void ensureSuccess(HttpResponse<String> response) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(errorText(response.body()));
        }
    }

    public static void ensureSuccess(HttpResponse<String> response, String operation) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(operation + " (HTTP " + response.statusCode() + "): "
                    + errorText(response.body()));
        }
    }

    public HttpResponse<String> patchJson(String url, Object body, String bearer) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(toJson(body)));

        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }

        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
