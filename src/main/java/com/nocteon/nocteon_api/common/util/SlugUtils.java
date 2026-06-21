package com.nocteon.nocteon_api.common.util;

public class SlugUtils {
    public static String toSlug(String input) {
        return input
            .toLowerCase()
            .trim()
            .replaceAll("[^a-z0-9\\s-]", "")
            .replaceAll("[\\s]+", "-");
    }
}
