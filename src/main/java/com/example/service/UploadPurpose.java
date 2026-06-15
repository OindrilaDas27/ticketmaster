package com.example.service;

public enum UploadPurpose {
    EVENT("events"),
    PROFILE("users");

    private final String keyPrefix;

    UploadPurpose(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String keyPrefix() {
        return keyPrefix;
    }
}
