package com.example.service.impl;

import com.example.configuration.S3Properties;
import com.example.dto.PresignedUploadResponse;
import com.example.service.UploadPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void event_upload_uses_events_prefix_and_jpg_extension() throws Exception {
        S3Presigner mockPresigner = mock(S3Presigner.class);
        PresignedPutObjectRequest mockPresigned = mock(PresignedPutObjectRequest.class);
        when(mockPresigned.url()).thenReturn(new URL("https://example.r2.cloudflarestorage.com/prozect-media/x"));
        when(mockPresigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(mockPresigned);

        service = new S3ServiceImpl(props, mockPresigner);

        PresignedUploadResponse res = service.generatePresignedUpload("image/jpeg", UploadPurpose.EVENT);

        assertThat(res.key()).startsWith("events/");
        assertThat(res.key()).endsWith(".jpg");
        assertThat(res.contentType()).isEqualTo("image/jpeg");
        assertThat(res.publicUrl()).startsWith("https://pub-x.r2.dev/events/");
    }

    @Test
    void profile_upload_uses_users_prefix_and_correct_extension_for_webp() throws Exception {
        S3Presigner mockPresigner = mock(S3Presigner.class);
        PresignedPutObjectRequest mockPresigned = mock(PresignedPutObjectRequest.class);
        when(mockPresigned.url()).thenReturn(new URL("https://example.r2.cloudflarestorage.com/prozect-media/x"));
        when(mockPresigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(mockPresigned);

        service = new S3ServiceImpl(props, mockPresigner);

        PresignedUploadResponse res = service.generatePresignedUpload("image/webp", UploadPurpose.PROFILE);

        assertThat(res.key()).startsWith("users/");
        assertThat(res.key()).endsWith(".webp");
    }

    @Test
    void presign_request_uses_configured_bucket_and_expiry() {
        S3Presigner mockPresigner = mock(S3Presigner.class);
        PresignedPutObjectRequest mockPresigned = mock(PresignedPutObjectRequest.class);
        try {
            when(mockPresigned.url()).thenReturn(new URL("https://example.r2.cloudflarestorage.com/x"));
        } catch (Exception e) { throw new RuntimeException(e); }
        when(mockPresigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(mockPresigned);

        service = new S3ServiceImpl(props, mockPresigner);
        service.generatePresignedUpload("image/png", UploadPurpose.EVENT);

        ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(mockPresigner).presignPutObject(captor.capture());

        PutObjectPresignRequest req = captor.getValue();
        assertThat(req.signatureDuration()).isEqualTo(Duration.ofSeconds(300));
        PutObjectRequest inner = req.putObjectRequest();
        assertThat(inner.bucket()).isEqualTo("prozect-media");
        assertThat(inner.contentType()).isEqualTo("image/png");
        assertThat(inner.key()).endsWith(".png");
    }
}
