package com.chat.platform.client.network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/** Tìm server chat trong mạng LAN bằng UDP broadcast. Hàm discover() là blocking. */
public final class LanServerDiscovery {
    private static final int DISCOVERY_PORT = 39555;
    private static final String DISCOVERY_REQUEST = "CHAT_PLATFORM_DISCOVER";
    private static final String DISCOVERY_RESPONSE_PREFIX = "CHAT_PLATFORM_SERVER|";

    private LanServerDiscovery() {
    }

    /** @return URL server tìm được, hoặc null nếu không tìm thấy. */
    public static String discover() {
        String discoveredUrl = null;
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(1200);
            byte[] requestBytes = DISCOVERY_REQUEST.getBytes(StandardCharsets.UTF_8);
            InetAddress broadcast = InetAddress.getByName("255.255.255.255");
            for (int attempt = 0; attempt < 3 && discoveredUrl == null; attempt++) {
                socket.send(new DatagramPacket(requestBytes, requestBytes.length,
                        broadcast, DISCOVERY_PORT));
                try {
                    byte[] responseBytes = new byte[128];
                    DatagramPacket response = new DatagramPacket(responseBytes, responseBytes.length);
                    socket.receive(response);
                    String payload = new String(response.getData(), response.getOffset(),
                            response.getLength(), StandardCharsets.UTF_8);
                    if (payload.startsWith(DISCOVERY_RESPONSE_PREFIX)) {
                        String port = payload.substring(DISCOVERY_RESPONSE_PREFIX.length()).trim();
                        discoveredUrl = "http://" + response.getAddress().getHostAddress() + ":" + port;
                    }
                } catch (java.net.SocketTimeoutException ignored) {
                    // Try the next broadcast. The server may be on another network adapter.
                }
            }
        } catch (IOException ignored) {
            // Manual server URL entry remains available if LAN discovery is unavailable.
        }
        return discoveredUrl;
    }
}
