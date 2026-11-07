package com.chat.platform.client.model;

import java.util.List;

public record Message(long messageId, String roomId, String sender, String content,
                      long timestamp, String fileId, String fileName, String contentType,
                      List<String> readBy, boolean recalled) {
    public Message {
        // Server có thể bỏ qua các field này (ví dụ tin nhắn chỉ có tệp).
        content = content == null ? "" : content;
        readBy = readBy == null ? List.of() : readBy;
    }
    public Message(
            long messageId,
            String roomId,
            String sender,
            String content,
            long timestamp,
            String fileId,
            String fileName,
            String contentType,
            List<String> readBy
    ) {
        this(messageId, roomId, sender, content, timestamp,
                fileId, fileName, contentType, readBy, false);
    }
}
