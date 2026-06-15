package com.example.service.impl;

import com.example.configuration.S3Properties;
import com.example.service.UploadPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class S3ServiceImplTest {

    private S3ServiceImpl service;
    private S3Properties props;

    @BeforeEach
    void setUp() {
        props = new S3Properties(
                "https://example.r2.cloudflarestorage.com",
                "auto",
                "ak",
                "sk",
                "prozect-media",
                "https://pub-x.r2.dev",
                300,
                5_242_880L,
                "image/jpeg,image/png,image/webp"
        );
        service = new S3ServiceImpl(props, mock(S3Presigner.class));
    }

    @Test
    void rejects_disallowed_content_type() {
        assertThatThrownBy(() ->
                service.generatePresignedUpload("application/pdf", UploadPurpose.EVENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Content type not allowed");
    }
}
