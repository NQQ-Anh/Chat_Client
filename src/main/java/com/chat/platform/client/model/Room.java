package com.chat.platform.client.model;

public record Room(String roomId, String name, String createdBy, long createdAt) {
}
