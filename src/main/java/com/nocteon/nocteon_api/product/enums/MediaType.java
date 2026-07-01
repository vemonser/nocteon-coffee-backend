package com.nocteon.nocteon_api.product.enums;

public enum MediaType {
    IMAGE("image"),
    VIDEO("video");
    private final String cloudinaryType;

    MediaType(String cloudinaryType) {
        this.cloudinaryType = cloudinaryType;
    }

    public String getCloudinaryType() {
        return cloudinaryType;
    }
}