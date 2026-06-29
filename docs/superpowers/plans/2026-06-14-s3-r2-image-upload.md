# R2 Image Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add browser-to-Cloudflare-R2 image upload for event posters and user profile pictures, persisting the public URL in `events.display_picture` and a new `users.display_picture` column.

**Architecture:** Frontend asks backend for a presigned PUT URL; browser PUTs the file body directly to R2; frontend then saves the public URL via the existing event-create / user-update endpoints. Backend validates that saved URLs originate from our R2 bucket.

**Tech Stack:** Spring Boot 3.2 (Java 17, JPA, Lombok), AWS SDK v2 (`software.amazon.awssdk:s3` — provider-agnostic; pointed at R2 via endpoint override), React 18 + Vite + Mantine, Postgres, JUnit 5 + Mockito + AssertJ.

**Spec reference:** `docs/superpowers/specs/2026-06-14-s3-r2-image-upload-design.md`

**Repo layout note:** `ticketmaster/` (backend) and `ticketmaster_fe/` (frontend) are **separate git repos**. Commits go in the repo where the file lives — run `git` commands from inside the corresponding folder.

---

## File map

### Backend (`ticketmaster/`) — create
- `src/main/java/com/example/configuration/S3Properties.java`
- `src/main/java/com/example/configuration/S3ClientConfig.java`
- `src/main/java/com/example/service/UploadPurpose.java`
- `src/main/java/com/example/dto/PresignRequestDTO.java`
- `src/main/java/com/example/dto/PresignedUploadResponse.java`
- `src/main/java/com/example/service/S3Service.java`
- `src/main/java/com/example/service/impl/S3ServiceImpl.java`
- `src/main/java/com/example/controller/UploadController.java`
- `src/test/java/com/example/service/impl/S3ServiceImplTest.java`
- `src/test/java/com/example/controller/UploadControllerTest.java`
- `src/test/java/com/example/service/impl/UserServiceImplDisplayPictureTest.java`
- `src/test/java/com/example/service/impl/EventServiceImplDisplayPictureTest.java`

### Backend — modify
- `pom.xml` (add AWS SDK v2 dependency)
- `.env` and `.env.example` (R2 env vars)
- `src/main/resources/application.yml` (`s3:` block)
- `src/main/resources/data.sql` (ALTER TABLE)
- `src/main/java/com/example/ApplicationConstants.java` (UPLOAD_ENDPOINT)
- `src/main/java/com/example/entity/User.java` (add `displayPicture`)
- `src/main/java/com/example/dto/UserDTO.java` (add `displayPicture`)
- `src/main/java/com/example/service/impl/UserServiceImpl.java` (URL validation)
- `src/main/java/com/example/service/impl/EventServiceImpl.java` (URL validation)

### Frontend (`ticketmaster_fe/`) — create
- `src/types/upload.types.ts`
- `src/types/user.types.ts`
- `src/service/uploadService.ts`
- `src/service/userService.ts`

### Frontend — modify
- `src/types/events.types.ts` (ensure `displayPicture` field)
- `src/service/index.ts` (barrel export)
- `src/service/locationService.ts` (fix pre-existing bug: wrong endpoint)
- `src/service/eventService.ts` (add `createEvent`)
- `src/container/createEvent/EventForm.tsx` (image picker)
- `src/pages/ProfilePage.tsx` (avatar picker + persist)

---

## Task 0: Manual R2 setup (no code, do this once)

**Files:** None — Cloudflare dashboard

- [x] **Step 1:** Sign up at https://dash.cloudflare.com/sign-up (credit card required; no charge under free tier)
- [x] **Step 2:** Cloudflare dashboard → R2 → "Create bucket" → name: `prozect-media` → region: closest to you
- [x] **Step 3:** Open bucket → Settings → "Public access" → "Allow Access" → note the public URL hostname (it looks like `https://pub-<long-hash>.r2.dev`)
- [x] **Step 4:** Bucket → Settings → CORS Policy → click "Add CORS policy" → paste:

```json
[
  {
    "AllowedOrigins": ["http://localhost:5173"],
    "AllowedMethods": ["PUT", "GET"],
    "AllowedHeaders": ["*"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3000
  }
]
```

- [x] **Step 5:** R2 → "Manage R2 API Tokens" → "Create API Token" → permissions: "Object Read & Write" → scope: only `prozect-media` → copy the Access Key ID and Secret Access Key (shown once)
- [x] **Step 6:** Note the S3 endpoint URL shown on the token page — format: `https://<account-id>.r2.cloudflarestorage.com`

Keep these 5 values handy; you'll paste them into `.env` in Task 2.

---

## Task 1: Add AWS SDK v2 dependency

**Files:**
- Modify: `ticketmaster/pom.xml`

- [x] **Step 1: Add the dependency**

Open `ticketmaster/pom.xml`. After the existing `spring-dotenv` dependency block (around line 80), add:

```xml
        <!-- AWS SDK v2 - S3 (used against Cloudflare R2 via endpoint override) -->
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>s3</artifactId>
            <version>2.25.0</version>
        </dependency>
```

- [x] **Step 2: Verify it downloads**

```bash
cd ticketmaster
mvn -q dependency:resolve
```

Expected: command exits 0 with no errors. (May take 30–60s the first time.)

- [x] **Step 3: Verify the project still compiles**

```bash
cd ticketmaster
mvn -q compile
```

Expected: BUILD SUCCESS.

- [x] **Step 4: Commit**

```bash
cd ticketmaster
git add pom.xml
git commit -m "feat: add AWS SDK v2 dependency for R2 image uploads"
```

---

## Task 2: Add R2 environment variables

**Files:**
- Modify: `ticketmaster/.env`
- Modify or create: `ticketmaster/.env.example`

- [x] **Step 1: Append R2 keys to `.env`**

Edit `ticketmaster/.env` and add (replace with your real Task 0 values):

```
S3_ENDPOINT=https://<your-account-id>.r2.cloudflarestorage.com
S3_REGION=auto
S3_ACCESS_KEY=<your-r2-access-key>
S3_SECRET_KEY=<your-r2-secret-key>
S3_BUCKET=prozect-media
S3_PUBLIC_URL_BASE=https://pub-<your-hash>.r2.dev
```

- [x] **Step 2: Append same keys (with placeholder values) to `.env.example`**

If `.env.example` exists, append the same six lines but with placeholder values:

```
S3_ENDPOINT=https://YOUR_ACCOUNT_ID.r2.cloudflarestorage.com
S3_REGION=auto
S3_ACCESS_KEY=YOUR_R2_ACCESS_KEY
S3_SECRET_KEY=YOUR_R2_SECRET_KEY
S3_BUCKET=prozect-media
S3_PUBLIC_URL_BASE=https://pub-YOUR_HASH.r2.dev
```

If `.env.example` does not exist, create it with these six lines + any existing `DB_*` keys mirrored from `.env`.

- [x] **Step 3: Confirm `.env` is gitignored**

```bash
cd ticketmaster
git check-ignore .env
```

Expected: prints `.env` (meaning it is ignored). If it prints nothing, add `.env` to `.gitignore` and commit that.

- [x] **Step 4: Commit only `.env.example`** (never commit `.env`)

```bash
cd ticketmaster
git add .env.example
# If you had to update .gitignore, add it too: git add .gitignore
git commit -m "docs: document R2 env vars in .env.example"
```

---

## Task 3: Add `s3:` config block to `application.yml`

**Files:**
- Modify: `ticketmaster/src/main/resources/application.yml`

- [x] **Step 1: Append the `s3:` block**

After the `logging:` block in `application.yml`, append:

```yaml
s3:
  endpoint: ${S3_ENDPOINT}
  region: ${S3_REGION:auto}
  access-key: ${S3_ACCESS_KEY}
  secret-key: ${S3_SECRET_KEY}
  bucket: ${S3_BUCKET}
  public-url-base: ${S3_PUBLIC_URL_BASE}
  presign-expiry-seconds: 300
  max-upload-bytes: 5242880
  allowed-content-types: image/jpeg,image/png,image/webp
```

- [x] **Step 2: Commit**

```bash
cd ticketmaster
git add src/main/resources/application.yml
git commit -m "feat: add s3 config block for R2 uploads"
```

---

## Task 4: Schema migration — add `users.display_picture`

**Files:**
- Modify: `ticketmaster/src/main/resources/data.sql`

- [x] **Step 1: Append the ALTER**

Add this block at the END of `data.sql` (after the existing `INSERT ... ON CONFLICT` statement):

```sql
-- Add display_picture column for user profile pictures (idempotent)
ALTER TABLE users ADD COLUMN IF NOT EXISTS display_picture VARCHAR(512);
```

(`continue-on-error: true` is already set in `application.yml`, but `IF NOT EXISTS` makes this explicit.)

- [x] **Step 2: Apply the migration by starting the app**

Make sure Postgres is running with `ticketmaster_db`, then:

```bash
cd ticketmaster
mvn spring-boot:run
```

Wait for "Started Application" in the logs, then Ctrl-C.

- [x] **Step 3: Verify the column exists**

```bash
psql -h localhost -p 5434 -U postgres -d ticketmaster_db -c "\d users"
```

Expected: a row showing `display_picture | character varying(512)`.

- [x] **Step 4: Commit**

```bash
cd ticketmaster
git add src/main/resources/data.sql
git commit -m "feat: add users.display_picture column for PFPs"
```

---

## Task 5: Create `S3Properties` config

**Files:**
- Create: `ticketmaster/src/main/java/com/example/configuration/S3Properties.java`

- [x] **Step 1: Write the class**

```java
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
        return List.of(allowedContentTypes.split(","));
    }
}
```

- [x] **Step 2: Register the properties record**

Open `ticketmaster/src/main/java/com/example/Application.java`. Confirm it has `@SpringBootApplication`. Add `@ConfigurationPropertiesScan("com.example.configuration")` directly below `@SpringBootApplication` if it isn't already there:

```java
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.example.configuration")
public class Application { ... }
```

- [x] **Step 3: Verify it compiles and binds**

```bash
cd ticketmaster
mvn -q compile
mvn spring-boot:run
```

Expected: app starts without "Failed to bind properties" errors. Ctrl-C after "Started Application".

- [x] **Step 4: Commit**

```bash
cd ticketmaster
git add src/main/java/com/example/configuration/S3Properties.java src/main/java/com/example/Application.java
git commit -m "feat: bind S3Properties from application.yml"
```

---

## Task 6: Create domain types — `UploadPurpose` and DTOs

**Files:**
- Create: `ticketmaster/src/main/java/com/example/service/UploadPurpose.java`
- Create: `ticketmaster/src/main/java/com/example/dto/PresignRequestDTO.java`
- Create: `ticketmaster/src/main/java/com/example/dto/PresignedUploadResponse.java`

- [x] **Step 1: Create `UploadPurpose` enum**

```java
package com.example.service;

public enum UploadPurpose {
    EVENT("events"),
    PROFILE("users");

    private final String keyPrefix;

    UploadPurpose(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String keyPrefix() {
        return keyPrefix;
    }
}
```

- [x] **Step 2: Create `PresignRequestDTO`**

```java
package com.example.dto;

import com.example.service.UploadPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PresignRequestDTO(
        @NotBlank String contentType,
        @NotNull UploadPurpose purpose
) {}
```

- [x] **Step 3: Create `PresignedUploadResponse`**

```java
package com.example.dto;

public record PresignedUploadResponse(
        String uploadUrl,
        String publicUrl,
        String key,
        String contentType
) {}
```

- [x] **Step 4: Verify compile**

```bash
cd ticketmaster
mvn -q compile
```

Expected: BUILD SUCCESS.

- [x] **Step 5: Commit**

```bash
cd ticketmaster
git add src/main/java/com/example/service/UploadPurpose.java src/main/java/com/example/dto/PresignRequestDTO.java src/main/java/com/example/dto/PresignedUploadResponse.java
git commit -m "feat: add UploadPurpose enum and presign DTOs"
```

---

## Task 7: Create `S3ClientConfig` — the bean factory

**Files:**
- Create: `ticketmaster/src/main/java/com/example/configuration/S3ClientConfig.java`

- [x] **Step 1: Write the configuration class**

```java
package com.example.configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

@Configuration
public class S3ClientConfig {

    private final S3Properties props;

    public S3ClientConfig(S3Properties props) {
        this.props = props;
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .region(Region.of(props.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .region(Region.of(props.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}
```

- [x] **Step 2: Start the app and verify the beans wire up**

```bash
cd ticketmaster
mvn spring-boot:run
```

Expected: "Started Application" with no "Error creating bean with name 's3Client'" or similar. Ctrl-C after success.

- [x] **Step 3: Commit**

```bash
cd ticketmaster
git add src/main/java/com/example/configuration/S3ClientConfig.java
git commit -m "feat: build S3Client and S3Presigner beans for R2"
```

---

## Task 8: Create `S3Service` interface

**Files:**
- Create: `ticketmaster/src/main/java/com/example/service/S3Service.java`

- [x] **Step 1: Write the interface**

```java
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
```

- [x] **Step 2: Verify compile**

```bash
cd ticketmaster
mvn -q compile
```

Expected: BUILD SUCCESS.

- [x] **Step 3: Commit**

```bash
cd ticketmaster
git add src/main/java/com/example/service/S3Service.java
git commit -m "feat: declare S3Service interface"
```

---

## Task 9: TDD — `S3ServiceImpl` content-type validation

**Files:**
- Create: `ticketmaster/src/test/java/com/example/service/impl/S3ServiceImplTest.java`
- Create: `ticketmaster/src/main/java/com/example/service/impl/S3ServiceImpl.java`

- [x] **Step 1: Write the failing test**

Create `src/test/java/com/example/service/impl/S3ServiceImplTest.java`:

```java
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
```

- [x] **Step 2: Run test, expect failure (class doesn't exist yet)**

```bash
cd ticketmaster
mvn -q test -Dtest=S3ServiceImplTest
```

Expected: FAIL with compile error "cannot find symbol S3ServiceImpl".

- [x] **Step 3: Write minimal `S3ServiceImpl` to satisfy the test**

Create `src/main/java/com/example/service/impl/S3ServiceImpl.java`:

```java
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
```

- [x] **Step 4: Run test, expect pass**

```bash
cd ticketmaster
mvn -q test -Dtest=S3ServiceImplTest
```

Expected: PASS (1 test).

- [x] **Step 5: Commit**

```bash
cd ticketmaster
git add src/main/java/com/example/service/impl/S3ServiceImpl.java src/test/java/com/example/service/impl/S3ServiceImplTest.java
git commit -m "feat: validate content-type allowlist in S3ServiceImpl"
```

---

## Task 10: TDD — `S3ServiceImpl` key derivation and happy path

**Files:**
- Modify: `ticketmaster/src/test/java/com/example/service/impl/S3ServiceImplTest.java`
- Modify: `ticketmaster/src/main/java/com/example/service/impl/S3ServiceImpl.java`

- [x] **Step 1: Add the failing tests**

Add these test methods to `S3ServiceImplTest.java` inside the existing class (and import `org.mockito.ArgumentCaptor`, `org.mockito.Mockito.*`, `software.amazon.awssdk.services.s3.model.PutObjectRequest`, `software.amazon.awssdk.services.s3.presigner.model.*`, `java.net.URL`, `java.time.Duration`, and `static org.assertj.core.api.Assertions.assertThat`):

```java
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
```

- [x] **Step 2: Run tests, expect failure**

```bash
cd ticketmaster
mvn -q test -Dtest=S3ServiceImplTest
```

Expected: 3 new tests FAIL with `UnsupportedOperationException("not yet implemented")`.

- [x] **Step 3: Implement the full method**

Replace the body of `generatePresignedUpload` in `S3ServiceImpl.java`:

```java
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
```

- [x] **Step 4: Run tests, expect pass**

```bash
cd ticketmaster
mvn -q test -Dtest=S3ServiceImplTest
```

Expected: 4 tests PASS.

- [x] **Step 5: Commit**

```bash
cd ticketmaster
git add src/main/java/com/example/service/impl/S3ServiceImpl.java src/test/java/com/example/service/impl/S3ServiceImplTest.java
git commit -m "feat: implement R2 presigned PUT generation in S3ServiceImpl"
```

---

## Task 11: Add `UPLOAD_ENDPOINT` constant

**Files:**
- Modify: `ticketmaster/src/main/java/com/example/ApplicationConstants.java`

- [x] **Step 1: Add the constant**

In `ApplicationConstants.java`, add to the API Endpoints section (after `LOCATION_ENDPOINT`):

```java
    public static final String UPLOAD_ENDPOINT = "/uploads";
```

- [x] **Step 2: Verify compile**

```bash
cd ticketmaster
mvn -q compile
```

Expected: BUILD SUCCESS.

- [x] **Step 3: Commit**

```bash
cd ticketmaster
git add src/main/java/com/example/ApplicationConstants.java
git commit -m "chore: add UPLOAD_ENDPOINT constant"
```

---

## Task 12: TDD — `UploadController`

**Files:**
- Create: `ticketmaster/src/test/java/com/example/controller/UploadControllerTest.java`
- Create: `ticketmaster/src/main/java/com/example/controller/UploadController.java`

- [x] **Step 1: Write the failing test**

```java
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

        mvc.perform(post("/api/uploads/presign")
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

        mvc.perform(post("/api/uploads/presign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"purpose\":\"EVENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.publicUrl").value("https://pub-x.r2.dev/events/2026/06/a.jpg"))
                .andExpect(jsonPath("$.data.contentType").value("image/jpeg"));
    }
}
```

You'll also need H2 for the in-memory test DB. Add this to `pom.xml` (test scope):

```xml
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
```

- [x] **Step 2: Run test, expect failure**

```bash
cd ticketmaster
mvn -q test -Dtest=UploadControllerTest
```

Expected: 404 or compilation error — `UploadController` not yet built.

- [x] **Step 3: Implement the controller**

```java
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
```

- [x] **Step 4: Run tests, expect pass**

```bash
cd ticketmaster
mvn -q test -Dtest=UploadControllerTest
```

Expected: 2 tests PASS.

- [x] **Step 5: Commit**

```bash
cd ticketmaster
git add pom.xml src/main/java/com/example/controller/UploadController.java src/test/java/com/example/controller/UploadControllerTest.java
git commit -m "feat: add POST /api/uploads/presign endpoint"
```

---

## Task 13: Add `displayPicture` to `User` entity and DTO

**Files:**
- Modify: `ticketmaster/src/main/java/com/example/entity/User.java`
- Modify: `ticketmaster/src/main/java/com/example/dto/UserDTO.java`

- [x] **Step 1: Add field to `User.java`**

After the `phoneNumber` field block, add:

```java
    @Column(name = "display_picture", length = 512)
    private String displayPicture;
```

- [x] **Step 2: Add field to `UserDTO.java`**

After the `phoneNumber` field, add:

```java
    @Size(max = 512, message = "Display picture URL must not exceed 512 characters")
    private String displayPicture;
```

- [x] **Step 3: Propagate the field through the User mappers in `ApplicationUtils`**

Open `src/main/java/com/example/util/ApplicationUtils.java`. The User mapping is centralized here. Add `displayPicture` to all three User methods:

In `convertToDto(User user)` — after `userDto.setPhoneNumber(...)`:

```java
        userDto.setDisplayPicture(user.getDisplayPicture());
```

In `convertToEntity(UserDTO userDto)` — after `user.setPhoneNumber(...)`:

```java
        user.setDisplayPicture(userDto.getDisplayPicture());
```

In `updateEntityFromDto(User user, UserDTO userDto)` — after `user.setPhoneNumber(...)`:

```java
        user.setDisplayPicture(userDto.getDisplayPicture());
```

- [x] **Step 4: Boot and verify schema validates**

```bash
cd ticketmaster
mvn spring-boot:run
```

Expected: "Started Application" — no Hibernate validation error about the missing `display_picture` column (proves Task 4's migration worked and the entity matches).

Ctrl-C after success.

- [x] **Step 5: Commit**

```bash
cd ticketmaster
git add src/main/java/com/example/entity/User.java src/main/java/com/example/dto/UserDTO.java src/main/java/com/example/util/ApplicationUtils.java
git commit -m "feat: add displayPicture field to User entity, DTO, and mappers"
```

---

## Task 14: TDD — URL validation in `UserServiceImpl`

**Files:**
- Create: `ticketmaster/src/test/java/com/example/service/impl/UserServiceImplDisplayPictureTest.java`
- Modify: `ticketmaster/src/main/java/com/example/service/impl/UserServiceImpl.java`

- [x] **Step 1: Write the failing test**

```java
package com.example.service.impl;

import com.example.configuration.S3Properties;
import com.example.dao.impl.UserDaoImpl;
import com.example.dto.UserDTO;
import com.example.entity.User;
import com.example.validation.UserValidation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceImplDisplayPictureTest {

    private UserServiceImpl service;
    private UserDaoImpl dao;

    @BeforeEach
    void setUp() {
        dao = mock(UserDaoImpl.class);
        UserValidation validation = mock(UserValidation.class);
        S3Properties props = new S3Properties(
                "https://e.r2.cloudflarestorage.com", "auto", "ak", "sk",
                "prozect-media", "https://pub-x.r2.dev", 300, 5_242_880L,
                "image/jpeg,image/png,image/webp"
        );
        service = new UserServiceImpl(dao, validation, props);

        User existing = new User();
        existing.setId(1L);
        existing.setUsername("u");
        existing.setEmail("e@e.com");
        existing.setFirstName("f");
        existing.setLastName("l");
        when(dao.findById(1L)).thenReturn(Optional.of(existing));
        when(dao.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void rejects_displayPicture_url_not_from_r2_base() {
        UserDTO dto = new UserDTO();
        dto.setUsername("u"); dto.setEmail("e@e.com");
        dto.setFirstName("f"); dto.setLastName("l");
        dto.setPhoneNumber("1");
        dto.setDisplayPicture("https://evil.example.com/x.jpg");

        assertThatThrownBy(() -> service.updateUser(1L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("displayPicture");
    }

    @Test
    void accepts_displayPicture_url_from_r2_base() {
        UserDTO dto = new UserDTO();
        dto.setUsername("u"); dto.setEmail("e@e.com");
        dto.setFirstName("f"); dto.setLastName("l");
        dto.setPhoneNumber("1");
        dto.setDisplayPicture("https://pub-x.r2.dev/users/2026/06/a.jpg");

        UserDTO result = service.updateUser(1L, dto);
        // No exception = pass; sanity-check the field flowed through:
        org.assertj.core.api.Assertions.assertThat(result.getDisplayPicture())
                .isEqualTo("https://pub-x.r2.dev/users/2026/06/a.jpg");
    }
}
```

- [x] **Step 2: Run test, expect compile failure**

```bash
cd ticketmaster
mvn -q test -Dtest=UserServiceImplDisplayPictureTest
```

Expected: FAIL — `UserServiceImpl` constructor doesn't take `S3Properties` yet.

- [x] **Step 3: Update `UserServiceImpl` constructor and add validation**

In `UserServiceImpl.java`:

1. Add field and replace the existing constructor.

Existing constructor:
```java
    private final UserDaoImpl userDao;
    private final UserValidation userValidation;

    @Autowired
    public UserServiceImpl(UserDaoImpl userDao, UserValidation userValidation) {
        this.userDao = userDao;
        this.userValidation = userValidation;
    }
```

Replace with:
```java
    private final UserDaoImpl userDao;
    private final UserValidation userValidation;
    private final S3Properties s3Properties;

    @Autowired
    public UserServiceImpl(UserDaoImpl userDao, UserValidation userValidation, S3Properties s3Properties) {
        this.userDao = userDao;
        this.userValidation = userValidation;
        this.s3Properties = s3Properties;
    }
```

2. At the top of `updateUser` (immediately after the existing `userValidation.validateId(id);` line), add:

```java
        if (userDto.getDisplayPicture() != null
                && !userDto.getDisplayPicture().startsWith(s3Properties.publicUrlBase())) {
            throw new IllegalArgumentException(
                    "displayPicture URL must originate from configured storage base");
        }
```

Add the same check at the top of `createUser` (after `userValidation.validateForCreate(userDto);`).

3. Add the import at the top of the file: `import com.example.configuration.S3Properties;`

- [x] **Step 4: Run test, expect pass**

```bash
cd ticketmaster
mvn -q test -Dtest=UserServiceImplDisplayPictureTest
```

Expected: 2 tests PASS.

- [x] **Step 5: Commit**

```bash
cd ticketmaster
git add src/main/java/com/example/service/impl/UserServiceImpl.java src/test/java/com/example/service/impl/UserServiceImplDisplayPictureTest.java
git commit -m "feat: validate user displayPicture URL originates from configured base"
```

---

## Task 15: TDD — URL validation in `EventServiceImpl`

**Files:**
- Create: `ticketmaster/src/test/java/com/example/service/impl/EventServiceImplDisplayPictureTest.java`
- Modify: `ticketmaster/src/main/java/com/example/service/impl/EventServiceImpl.java`

- [x] **Step 1: Write the failing test**

```java
package com.example.service.impl;

import com.example.configuration.S3Properties;
import com.example.dao.impl.EventsDaoImpl;
import com.example.dto.EventsDTO;
import com.example.service.LocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class EventServiceImplDisplayPictureTest {

    private EventServiceImpl service;

    @BeforeEach
    void setUp() {
        S3Properties props = new S3Properties(
                "https://e.r2.cloudflarestorage.com", "auto", "ak", "sk",
                "prozect-media", "https://pub-x.r2.dev", 300, 5_242_880L,
                "image/jpeg,image/png,image/webp"
        );
        service = new EventServiceImpl(mock(EventsDaoImpl.class), mock(LocationService.class), props);
    }

    @Test
    void rejects_event_displayPicture_url_not_from_r2_base() {
        EventsDTO dto = new EventsDTO();
        dto.setName("Test");
        dto.setDisplayPicture("https://evil.example.com/x.jpg");

        assertThatThrownBy(() -> service.createEvent(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("displayPicture");
    }
}
```

- [x] **Step 2: Run test, expect compile failure**

```bash
cd ticketmaster
mvn -q test -Dtest=EventServiceImplDisplayPictureTest
```

Expected: FAIL — constructor doesn't accept `S3Properties` yet.

- [x] **Step 3: Update `EventServiceImpl` constructor and add validation**

In `EventServiceImpl.java`:

1. Add the field and constructor parameter:

```java
    private final S3Properties s3Properties;

    @Autowired
    public EventServiceImpl(EventsDaoImpl eventsDao, LocationService locationService, S3Properties s3Properties) {
        this.eventsDao = eventsDao;
        this.locationService = locationService;
        this.s3Properties = s3Properties;
    }
```

2. At the top of `createEvent` (after the null check, before the entity mapping), add:

```java
        if (eventRequestBody.getDisplayPicture() != null
                && !eventRequestBody.getDisplayPicture().startsWith(s3Properties.publicUrlBase())) {
            throw new IllegalArgumentException(
                    "displayPicture URL must originate from configured storage base");
        }
```

3. Add import: `import com.example.configuration.S3Properties;`

4. The `EventController` wraps `IllegalArgumentException` as a 500 today (it catches `Exception` generically). Update its `createEvent` handler to catch `IllegalArgumentException` first and return `400`:

```java
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            // existing 500 handler ...
        }
```

- [x] **Step 4: Run test, expect pass**

```bash
cd ticketmaster
mvn -q test -Dtest=EventServiceImplDisplayPictureTest
```

Expected: 1 test PASS.

- [x] **Step 5: Run the full test suite**

```bash
cd ticketmaster
mvn -q test
```

Expected: all tests pass.

- [x] **Step 6: Commit**

```bash
cd ticketmaster
git add src/main/java/com/example/service/impl/EventServiceImpl.java src/main/java/com/example/controller/EventController.java src/test/java/com/example/service/impl/EventServiceImplDisplayPictureTest.java
git commit -m "feat: validate event displayPicture URL originates from configured base"
```

---

## Task 16: Backend manual smoke test

**Files:** None (manual verification with curl)

- [x] **Step 1: Start Postgres and the backend**

```bash
cd ticketmaster
mvn spring-boot:run
```

Wait for "Started Application".

- [x] **Step 2: Ask for a presigned URL for a JPEG event poster**

In another terminal:

```bash
curl -s -X POST http://localhost:8080/api/uploads/presign \
  -H "Content-Type: application/json" \
  -d '{"contentType":"image/jpeg","purpose":"EVENT"}' | jq
```

Expected: JSON response with `status=success` and a `data` object containing `uploadUrl`, `publicUrl`, `key`, `contentType`. The `uploadUrl` should be a long signed URL pointing to your R2 endpoint.

- [x] **Step 3: Upload a test JPEG using the returned URL**

Save the `uploadUrl` from step 2 as a shell variable, then:

```bash
# Use any small JPEG; if you don't have one handy:
curl -L -o /tmp/test.jpg https://picsum.photos/200/300.jpg

UPLOAD_URL='<paste the uploadUrl from step 2>'

curl -v -X PUT "$UPLOAD_URL" \
  -H "Content-Type: image/jpeg" \
  --data-binary @/tmp/test.jpg
```

Expected: HTTP 200.

- [x] **Step 4: Open the public URL in a browser**

Paste the `publicUrl` from step 2 into a browser. Expected: the test image renders.

- [x] **Step 5: Verify content-type rejection**

```bash
curl -s -X POST http://localhost:8080/api/uploads/presign \
  -H "Content-Type: application/json" \
  -d '{"contentType":"application/pdf","purpose":"EVENT"}' | jq
```

Expected: `status=error`, message contains "Content type not allowed".

- [x] **Step 6: Verify URL validation rejection**

```bash
curl -s -X POST http://localhost:8080/api/events \
  -H "Content-Type: application/json" \
  -d '{"name":"X","hostedFrom":"2030-01-01T00:00:00","hostedTo":"2030-01-02T00:00:00","ticketAmount":100,"category":"Music","location":"Mumbai","venue":"V","capacity":1,"displayPicture":"https://evil.example.com/x.jpg"}' | jq
```

Expected: `status=error`, message about URL origin.

If any step fails, debug and fix before continuing. Stop the backend (Ctrl-C) once verified.

---

## Task 17: Frontend types

**Files:**
- Create: `ticketmaster_fe/src/types/upload.types.ts`
- Create: `ticketmaster_fe/src/types/user.types.ts`
- Modify: `ticketmaster_fe/src/types/events.types.ts` (only if `displayPicture` is missing)

- [x] **Step 1: Create upload types**

```ts
export type UploadPurpose = 'EVENT' | 'PROFILE';

export interface PresignResponse {
  uploadUrl: string;
  publicUrl: string;
  key: string;
  contentType: string;
}

export interface ApiEnvelope<T> {
  status: 'success' | 'error';
  message: string;
  data?: T;
}
```

- [x] **Step 2: Create user types**

```ts
export interface User {
  id: number;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  phoneNumber: string;
  displayPicture?: string | null;
  createdAt?: string;
  updatedAt?: string;
}
```

- [x] **Step 3: Confirm `events.types.ts` includes `displayPicture`**

Open `ticketmaster_fe/src/types/events.types.ts`. If the `Events` interface does not include a `displayPicture` field, add it:

```ts
  displayPicture?: string | null;
```

- [x] **Step 4: Verify build**

```bash
cd ticketmaster_fe
yarn build
```

Expected: BUILD SUCCESS, no TypeScript errors.

- [x] **Step 5: Commit**

```bash
cd ticketmaster_fe
git add src/types/upload.types.ts src/types/user.types.ts src/types/events.types.ts
git commit -m "feat: add upload and user types; include displayPicture on Events"
```

---

## Task 18: Create `uploadService.ts`

**Files:**
- Create: `ticketmaster_fe/src/service/uploadService.ts`

- [x] **Step 1: Write the service**

```ts
import type { ApiEnvelope, PresignResponse, UploadPurpose } from '../types/upload.types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9000';

const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const MAX_BYTES = 5 * 1024 * 1024;

export async function uploadImage(file: File, purpose: UploadPurpose): Promise<string> {
  if (!ALLOWED_TYPES.includes(file.type)) {
    throw new Error('Only JPEG, PNG, or WebP images are allowed.');
  }
  if (file.size > MAX_BYTES) {
    throw new Error('Image must be 5 MB or smaller.');
  }

  const presignRes = await fetch(`${API_BASE_URL}/api/uploads/presign`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ contentType: file.type, purpose }),
  });
  if (!presignRes.ok) {
    throw new Error('Failed to get upload URL.');
  }
  const envelope = (await presignRes.json()) as ApiEnvelope<PresignResponse>;
  if (envelope.status !== 'success' || !envelope.data) {
    throw new Error(envelope.message || 'Failed to get upload URL.');
  }
  const { uploadUrl, publicUrl, contentType } = envelope.data;

  const uploadRes = await fetch(uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': contentType },
    body: file,
  });
  if (!uploadRes.ok) {
    throw new Error('Upload to storage failed.');
  }

  return publicUrl;
}
```

- [x] **Step 2: Verify build**

```bash
cd ticketmaster_fe
yarn build
```

Expected: BUILD SUCCESS.

- [x] **Step 3: Commit**

```bash
cd ticketmaster_fe
git add src/service/uploadService.ts
git commit -m "feat: add uploadService for presigned R2 uploads"
```

---

## Task 19: Create `userService.ts` and add `createEvent` to `eventService.ts`

**Files:**
- Create: `ticketmaster_fe/src/service/userService.ts`
- Modify: `ticketmaster_fe/src/service/eventService.ts`

- [x] **Step 1: Create `userService.ts`**

```ts
import type { User } from '../types/user.types';
import type { ApiEnvelope } from '../types/upload.types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9000';

export async function getUser(id: number): Promise<User> {
  const res = await fetch(`${API_BASE_URL}/api/users/${id}`);
  if (!res.ok) throw new Error('Failed to fetch user');
  const env = (await res.json()) as ApiEnvelope<User>;
  if (env.status !== 'success' || !env.data) throw new Error(env.message);
  return env.data;
}

export async function updateUser(id: number, dto: Partial<User>): Promise<User> {
  const res = await fetch(`${API_BASE_URL}/api/users/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(dto),
  });
  if (!res.ok) {
    const env = (await res.json().catch(() => ({}))) as ApiEnvelope<unknown>;
    throw new Error(env.message || 'Failed to update user');
  }
  const env = (await res.json()) as ApiEnvelope<User>;
  if (env.status !== 'success' || !env.data) throw new Error(env.message);
  return env.data;
}
```

- [x] **Step 2: Add `createEvent` to `eventService.ts`**

Replace the contents of `ticketmaster_fe/src/service/eventService.ts` with:

```ts
import type { Events } from '../types/events.types';
import type { ApiEnvelope } from '../types/upload.types';

const envConfig = import.meta.env;
const API_BASE_URL = envConfig.VITE_API_BASE_URL || 'http://localhost:9000';
const eventsUrl = `${API_BASE_URL}/api/events`;

export async function getEvents() {
  const response = await fetch(eventsUrl);
  if (!response.ok) {
    throw new Error('Failed to fetch events');
  }
  return response.json();
}

export async function createEvent(dto: Partial<Events>): Promise<Events> {
  const res = await fetch(eventsUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(dto),
  });
  if (!res.ok) {
    const env = (await res.json().catch(() => ({}))) as ApiEnvelope<unknown>;
    throw new Error(env.message || 'Failed to create event');
  }
  const env = (await res.json()) as ApiEnvelope<Events>;
  if (env.status !== 'success' || !env.data) throw new Error(env.message);
  return env.data;
}
```

- [x] **Step 3: Verify build**

```bash
cd ticketmaster_fe
yarn build
```

Expected: BUILD SUCCESS.

- [x] **Step 4: Commit**

```bash
cd ticketmaster_fe
git add src/service/userService.ts src/service/eventService.ts
git commit -m "feat: add userService and createEvent service helper"
```

---

## Task 20: Fix `locationService.ts` bug and update barrel

**Files:**
- Modify: `ticketmaster_fe/src/service/locationService.ts`
- Modify: `ticketmaster_fe/src/service/index.ts`

- [x] **Step 1: Fix the wrong endpoint in `locationService.ts`**

Replace the contents of `ticketmaster_fe/src/service/locationService.ts`:

```ts
import type { LocationResponse } from '../types/location.types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9000';

export const getCities = async (): Promise<LocationResponse> => {
  const response = await fetch(`${API_BASE_URL}/api/locations`);
  if (!response.ok) {
    throw new Error(`Failed to fetch locations: ${response.statusText}`);
  }
  return response.json();
};
```

(The bug: it previously called `/api/events/category`.)

- [x] **Step 2: Update the barrel `src/service/index.ts`**

Replace its contents with:

```ts
export * from './categoryService';
export * from './eventService';
export * from './locationService';
export * from './uploadService';
export * from './userService';
```

- [x] **Step 3: Verify build**

```bash
cd ticketmaster_fe
yarn build
```

Expected: BUILD SUCCESS.

- [x] **Step 4: Commit**

```bash
cd ticketmaster_fe
git add src/service/locationService.ts src/service/index.ts
git commit -m "fix: locationService points at /api/locations; expand service barrel"
```

---

## Task 21: Wire image picker into `EventForm.tsx`

**Files:**
- Modify: `ticketmaster_fe/src/container/createEvent/EventForm.tsx`

- [x] **Step 1: Replace the file**

```tsx
import { FileInput, Image, Loader, Stack, Text } from '@mantine/core';
import { useState } from 'react';
import { notifications } from '@mantine/notifications';
import SecondaryNavBar from '../../components/common/SecondaryNavBar';
import { uploadImage } from '../../service/uploadService';

const EventForm = () => {
  const [uploading, setUploading] = useState(false);
  const [posterUrl, setPosterUrl] = useState<string | null>(null);

  async function handleFileChange(file: File | null) {
    if (!file) return;
    setUploading(true);
    try {
      const url = await uploadImage(file, 'EVENT');
      setPosterUrl(url);
      notifications.show({ message: 'Image uploaded.', color: 'green' });
    } catch (e) {
      notifications.show({ message: (e as Error).message, color: 'red' });
    } finally {
      setUploading(false);
    }
  }

  return (
    <Stack gap="xl">
      <SecondaryNavBar />
      <Stack gap="md" px="md">
        <Text fw={600}>Event poster</Text>
        <FileInput
          accept="image/jpeg,image/png,image/webp"
          placeholder="Pick an image (JPEG, PNG, WebP — up to 5 MB)"
          onChange={handleFileChange}
          disabled={uploading}
        />
        {uploading && <Loader size="sm" />}
        {posterUrl && (
          <Stack gap="xs">
            <Image src={posterUrl} radius="md" h={220} fit="contain" />
            <Text size="xs" c="dimmed" style={{ wordBreak: 'break-all' }}>{posterUrl}</Text>
          </Stack>
        )}
      </Stack>
    </Stack>
  );
};

export default EventForm;
```

- [x] **Step 2: Confirm `@mantine/notifications` is wired in `main.tsx` or `App.tsx`**

Open `ticketmaster_fe/src/main.tsx`. If you do not see `<Notifications />` rendered (typically just inside `<MantineProvider>`), add it:

```tsx
import { Notifications } from '@mantine/notifications';
// ...
<MantineProvider>
  <Notifications />
  <App />
</MantineProvider>
```

And add the CSS import near other Mantine imports:
```tsx
import '@mantine/notifications/styles.css';
```

- [x] **Step 3: Verify build and lint**

```bash
cd ticketmaster_fe
yarn build
yarn lint
```

Expected: both succeed.

- [x] **Step 4: Commit**

```bash
cd ticketmaster_fe
git add src/container/createEvent/EventForm.tsx src/main.tsx
git commit -m "feat: image picker in EventForm with R2 upload preview"
```

---

## Task 22: Wire PFP picker into `ProfilePage.tsx`

**Files:**
- Modify: `ticketmaster_fe/src/pages/ProfilePage.tsx`

- [x] **Step 1: Replace the file**

```tsx
import { Avatar, Box, Center, FileButton, Group, Loader, Stack, Text } from '@mantine/core';
import { useEffect, useState } from 'react';
import { notifications } from '@mantine/notifications';
import SecondaryNavBar from '../components/common/SecondaryNavBar';
import { getUser, updateUser } from '../service/userService';
import { uploadImage } from '../service/uploadService';
import type { User } from '../types/user.types';

const TEMP_USER_ID = 1; // No auth yet — use user 1 for the smoke test.

export default function ProfilePage() {
  const [user, setUser] = useState<User | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    getUser(TEMP_USER_ID).then(setUser).catch((e: Error) =>
      notifications.show({ message: e.message, color: 'red' })
    );
  }, []);

  async function handleFile(file: File | null) {
    if (!file || !user) return;
    setBusy(true);
    try {
      const url = await uploadImage(file, 'PROFILE');
      const updated = await updateUser(user.id, { ...user, displayPicture: url });
      setUser(updated);
      notifications.show({ message: 'Profile picture updated.', color: 'green' });
    } catch (e) {
      notifications.show({ message: (e as Error).message, color: 'red' });
    } finally {
      setBusy(false);
    }
  }

  return (
    <Center py={40}>
      <Box w="90%">
        <Stack gap="xl">
          <SecondaryNavBar />
          {!user ? (
            <Loader />
          ) : (
            <Group gap="md">
              <Avatar src={user.displayPicture ?? undefined} size={120} radius="xl" />
              <Stack gap="xs">
                <Text fw={600}>{user.firstName} {user.lastName}</Text>
                <Text size="sm" c="dimmed">@{user.username}</Text>
                <FileButton onChange={handleFile} accept="image/jpeg,image/png,image/webp">
                  {(props) => (
                    <button {...props} disabled={busy}>
                      {busy ? 'Uploading…' : 'Change photo'}
                    </button>
                  )}
                </FileButton>
              </Stack>
            </Group>
          )}
        </Stack>
      </Box>
    </Center>
  );
}
```

- [x] **Step 2: Verify build and lint**

```bash
cd ticketmaster_fe
yarn build
yarn lint
```

Expected: both succeed.

- [x] **Step 3: Commit**

```bash
cd ticketmaster_fe
git add src/pages/ProfilePage.tsx
git commit -m "feat: PFP picker on ProfilePage with R2 upload and PUT user"
```

---

## Task 23: End-to-end manual smoke test

**Files:** None (manual verification)

- [x] **Step 1: Start the backend** (in one terminal)

```bash
cd ticketmaster
mvn spring-boot:run
```

Wait for "Started Application".

- [x] **Step 2: Start the frontend** (in another terminal)

```bash
cd ticketmaster_fe
yarn dev
```

Open the URL printed (usually `http://localhost:5173`).

- [x] **Step 3: Verify event-poster upload**

1. Navigate to `http://localhost:5173/create-event`
2. Open DevTools → Network tab
3. Pick a small JPEG with the FileInput
4. Confirm in Network:
   - `POST /api/uploads/presign` → 200, response contains `uploadUrl`, `publicUrl`
   - `PUT <r2 url>` → 200
5. Confirm the preview image appears below the picker
6. Click on the displayed `publicUrl` (or paste it into a new tab) — image loads from R2

- [x] **Step 4: Verify PFP upload + persistence**

1. Navigate to `http://localhost:5173/profile`
2. Wait for the user to load (Avatar appears)
3. Click "Change photo", pick a JPEG
4. Confirm in Network: `POST /api/uploads/presign` → 200, `PUT <r2 url>` → 200, `PUT /api/users/1` → 200
5. Avatar updates to the new image
6. Reload the page → Avatar still shows the new image (proves it was persisted)
7. Verify in Postgres:

```bash
psql -h localhost -p 5434 -U postgres -d ticketmaster_db \
  -c "SELECT id, username, display_picture FROM users WHERE id = 1;"
```

The `display_picture` column should hold a `https://pub-...r2.dev/users/...` URL.

- [x] **Step 5: Verify negative paths**

1. On `/create-event`, pick a `.pdf` file → notification reads "Only JPEG, PNG, or WebP images are allowed." Network shows zero requests.
2. Pick a JPEG larger than 5 MB → notification reads "Image must be 5 MB or smaller." Network shows zero requests.

- [x] **Step 6: Stop both servers** (Ctrl-C in each terminal)

- [x] **Step 7: Final summary commit (optional, frontend repo)**

If you made any small fixups in step 3 or 4, commit them now. Otherwise nothing to commit — the prior tasks already covered all changes.

---

## Out of scope (do not implement)

These are deferred per the spec — do not add them:

- Thumbnail generation, server-side image processing
- Delete old object on replace (orphaning is acceptable at this scale)
- Auth on `/api/uploads/presign` (no auth story yet in the project)
- Switching from full URL to object-key storage
- Upload progress bars or drag-and-drop UX
- Full `EventForm` build-out (only the image picker is in scope)
- Full `ProfilePage` rebuild (only the PFP picker is in scope)
