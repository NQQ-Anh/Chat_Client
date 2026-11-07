package com.chat.platform.client.util;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public final class NetworkUtils {
    private NetworkUtils() {
    }

    public static String webSocketUrl(String httpUrl) {
        return (httpUrl.startsWith("https://") ? "wss://" + httpUrl.substring(8)
                : "ws://" + httpUrl.substring(7)) + "/ws";
    }

    /** Mã hóa một đoạn path (username, id...) để ghép vào URL. */
    public static String encodePathSegment(String segment) {
        return java.net.URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static boolean isLocalHost(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "[::1]".equals(host);
    }

    public static boolean isPrivateNetwork(String host) {
        try {
            return InetAddress.getByName(host).isSiteLocalAddress();
        } catch (Exception e) {
            return false;
        }
    }

    public static byte[] multipartBody(String boundary, String fileName, String contentType, byte[] bytes)
            throws IOException {
        var output = new java.io.ByteArrayOutputStream();
        output.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\""
                + fileName.replace("\"", "") + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(bytes);
        output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return output.toByteArray();
    }
}
