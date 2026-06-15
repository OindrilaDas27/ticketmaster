package com.example.dto;

public record PresignedUploadResponse(
        String uploadUrl,
        String publicUrl,
        String key,
        String contentType
) {}
