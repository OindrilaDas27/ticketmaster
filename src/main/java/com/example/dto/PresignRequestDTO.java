package com.example.dto;

import com.example.service.UploadPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PresignRequestDTO(
        @NotBlank String contentType,
        @NotNull UploadPurpose purpose
) {}
