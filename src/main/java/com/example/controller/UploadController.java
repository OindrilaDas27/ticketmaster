package com.example.controller;

import com.example.ApplicationConstants;
import com.example.dto.PresignRequestDTO;
import com.example.dto.PresignedUploadResponse;
import com.example.service.S3Service;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping(ApplicationConstants.UPLOAD_ENDPOINT)
public class UploadController {

    private final S3Service s3Service;

    public UploadController(S3Service s3Service) {
        this.s3Service = s3Service;
    }

    @PostMapping("/presign")
    public ResponseEntity<Map<String, Object>> presign(@Valid @RequestBody PresignRequestDTO req) {
        try {
            PresignedUploadResponse data = s3Service.generatePresignedUpload(req.contentType(), req.purpose());
            Map<String, Object> body = new HashMap<>();
            body.put("status", "success");
            body.put("message", ApplicationConstants.SUCCESS);
            body.put("data", data);
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            Map<String, Object> body = new HashMap<>();
            body.put("status", "error");
            body.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        } catch (Exception e) {
            Map<String, Object> body = new HashMap<>();
            body.put("status", "error");
            body.put("message", "Failed to presign upload: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }
}
