package com.example.controller;

import com.example.dto.PresignedUploadResponse;
import com.example.service.S3Service;
import com.example.service.UploadPurpose;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import jakarta.annotation.PostConstruct;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "PORT=0",
        "S3_ENDPOINT=https://example.r2.cloudflarestorage.com",
        "S3_REGION=auto",
        "S3_ACCESS_KEY=ak",
        "S3_SECRET_KEY=sk",
        "S3_BUCKET=prozect-media",
        "S3_PUBLIC_URL_BASE=https://pub-x.r2.dev"
})
class UploadControllerTest {

    @Autowired WebApplicationContext ctx;
    @MockBean S3Service s3Service;

    private MockMvc mvc;

    @PostConstruct
    void init() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).build();
    }

    @Test
    void returns_400_when_content_type_disallowed() throws Exception {
        when(s3Service.generatePresignedUpload(eq("application/pdf"), any()))
                .thenThrow(new IllegalArgumentException("Content type not allowed: application/pdf"));

        mvc.perform(post("/uploads/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"application/pdf\",\"purpose\":\"EVENT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void returns_200_with_presign_payload_on_happy_path() throws Exception {
        PresignedUploadResponse fake = new PresignedUploadResponse(
                "https://r2/upload?sig=...",
                "https://pub-x.r2.dev/events/2026/06/a.jpg",
                "events/2026/06/a.jpg",
                "image/jpeg"
        );
        when(s3Service.generatePresignedUpload(eq("image/jpeg"), eq(UploadPurpose.EVENT))).thenReturn(fake);

        mvc.perform(post("/uploads/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"purpose\":\"EVENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.publicUrl").value("https://pub-x.r2.dev/events/2026/06/a.jpg"))
                .andExpect(jsonPath("$.data.contentType").value("image/jpeg"));
    }
}
