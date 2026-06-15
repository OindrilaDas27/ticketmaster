package com.example.service.impl;

import com.example.configuration.S3Properties;
import com.example.dto.PresignedUploadResponse;
import com.example.service.S3Service;
import com.example.service.UploadPurpose;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Service
public class S3ServiceImpl implements S3Service {

    private final S3Properties props;
    private final S3Presigner presigner;

    public S3ServiceImpl(S3Properties props, S3Presigner presigner) {
        this.props = props;
        this.presigner = presigner;
    }

    @Override
    public PresignedUploadResponse generatePresignedUpload(String contentType, UploadPurpose purpose) {
        if (!props.allowedContentTypesList().contains(contentType)) {
            throw new IllegalArgumentException(
                    "Content type not allowed: " + contentType);
        }

        String ext = extensionFor(contentType);
        String key = "%s/%s/%s.%s".formatted(
                purpose.keyPrefix(),
                java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM")),
                java.util.UUID.randomUUID(),
                ext);

        software.amazon.awssdk.services.s3.model.PutObjectRequest putRequest =
                software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                        .bucket(props.bucket())
                        .key(key)
                        .contentType(contentType)
                        .build();

        software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest presignRequest =
                software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.builder()
                        .signatureDuration(java.time.Duration.ofSeconds(props.presignExpirySeconds()))
                        .putObjectRequest(putRequest)
                        .build();

        software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest presigned =
                presigner.presignPutObject(presignRequest);

        String publicUrl = props.publicUrlBase() + "/" + key;
        return new PresignedUploadResponse(presigned.url().toString(), publicUrl, key, contentType);
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("Unexpected content type: " + contentType);
        };
    }
}
