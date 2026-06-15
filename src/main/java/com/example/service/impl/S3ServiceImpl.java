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
        throw new UnsupportedOperationException("not yet implemented");
    }
}
