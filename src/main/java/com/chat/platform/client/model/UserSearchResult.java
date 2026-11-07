package com.chat.platform.client.model;

public record UserSearchResult(
        String username,
        String displayName,
        boolean online
) {
}
