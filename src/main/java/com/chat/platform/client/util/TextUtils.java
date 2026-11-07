package com.chat.platform.client.util;

import java.util.Arrays;
import java.util.List;

public final class TextUtils {
    private TextUtils() {
    }

    /** Tách danh sách username phân cách bằng dấu phẩy, bỏ phần trống và trùng. */
    public static List<String> splitUsernames(String raw) {
        return Arrays.stream(raw == null ? new String[0] : raw.split(","))
                .map(String::trim).filter(value -> !value.isBlank()).distinct().toList();
    }
}
