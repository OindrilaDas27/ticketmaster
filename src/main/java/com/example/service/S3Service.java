package com.example.service;

import com.example.dto.PresignedUploadResponse;

public interface S3Service {

    /**
     * Generates a presigned PUT URL for uploading an image directly to R2.
     *
     * @param contentType MIME type from the client (must be in the configured allowlist)
     * @param purpose     what the upload is for (selects the key prefix)
     * @return URL the browser uses to upload, plus the resulting public URL
     * @throws IllegalArgumentException if contentType is not allowed
     */
    PresignedUploadResponse generatePresignedUpload(String contentType, UploadPurpose purpose);
}
