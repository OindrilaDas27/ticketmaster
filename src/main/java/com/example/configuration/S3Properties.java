package com.example.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "s3")
public record S3Properties(
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        String publicUrlBase,
        int presignExpirySeconds,
        long maxUploadBytes,
        String allowedContentTypes
) {
    public List<String> allowedContentTypesList() {
        return List.of(allowedContentTypes.split("\\s*,\\s*"));
    }
}
