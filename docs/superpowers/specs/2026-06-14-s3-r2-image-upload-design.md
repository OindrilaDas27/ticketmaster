# Image Upload via Cloudflare R2 (S3-Compatible)

**Date:** 2026-06-14
**Status:** Design approved; ready for implementation planning
**Scope:** Event posters (`events.display_picture`) and user profile pictures (`users.display_picture`)
**Provider:** Cloudflare R2

## Summary

Add image upload to the Prozect ticketing platform. Files are uploaded directly from the React frontend to Cloudflare R2 via short-lived presigned POSTs minted by the Spring Boot backend. The backend never touches file bytes. The final public URL is stored in the existing `events.display_picture` column and a new `users.display_picture` column. R2 is used in both development and production with the same code path; only the backend's environment variables differ between environments.

## Decisions

| Topic | Choice | Rationale |
|---|---|---|
| Upload pattern | Presigned PUT (browser → R2 direct) | Backend stays cheap. AWS SDK v2 has first-class PUT support (POST-policy needs hand-rolled HMAC — overkill for personal use). Content-type is bound to the signature. Size enforcement is client-side; trade-off accepted as YAGNI. |
| Provider | Cloudflare R2 | Free tier (10 GB / 1M Class A ops / 10M Class B ops per month), zero egress fees, S3-compatible. Same service in dev and prod. |
| Bucket layout | Single bucket, no dev/prod split | Simplest for a personal project; URLs identical across environments. |
| Read access | Public bucket | Event posters and PFPs are not sensitive; lets `<img src=...>` work with no token refresh. |
| Value stored in `display_picture` | Full public URL | Matches existing column type; no template/rendering changes needed. Acceptable because we use one bucket forever. |
| Scope | Event images and PFPs in one round | Same upload mechanism, no duplication. Adds one `users` column. |
| Upload limits | 5 MB max; `image/jpeg`, `image/png`, `image/webp` | Sensible defaults; enforced server-side via the presigned-POST policy. |
| Image processing | None | YAGNI. R2 serves whatever was uploaded. Resizing/thumbnails can be added later if needed. |
| Code portability | Provider-agnostic application code | Only three lines in `S3ClientConfig` know about R2 (endpoint, region=`auto`, path-style addressing). |

## Architecture

### Components

- **Cloudflare R2 bucket** `prozect-media` — public read, CORS allowing POST/GET from frontend origins
- **Cloudflare R2 API token** — scoped to Object Read & Write on this bucket only; stored in backend `.env`
- **Backend** (Spring Boot)
  - `S3Properties` — typed config bound from `application.yml`
  - `S3ClientConfig` — builds `S3Client` and `S3Presigner` beans, points the SDK at R2
  - `S3Service` — generates presigned POSTs and derives object keys
  - `UploadController` — single endpoint `POST /api/uploads/presign`
  - `EventServiceImpl.createEvent` — already accepts `displayPicture`; gains optional URL validation
  - `User` entity / `UserDTO` / `UserDaoImpl` / `UserServiceImpl` — gain `displayPicture` field; existing `PUT /api/users/{id}` carries it
- **Frontend** (React + Vite)
  - `uploadService.ts` — one helper `uploadImage(file, purpose): Promise<publicUrl>`
  - `EventForm.tsx` — file picker that calls `uploadImage(file, 'EVENT')`, submits URL with the rest of the form
  - `ProfilePage.tsx` — avatar picker that calls `uploadImage(file, 'PROFILE')`, then `PUT /api/users/{id}` (the existing endpoint, with the user's full DTO including the new `displayPicture` URL)
  - `service/index.ts` — barrel updated to export new and existing services
- **Postgres** — one migration adding `users.display_picture varchar(512)` (nullable)

### Data flow (event poster; PFP is identical with `purpose: 'PROFILE'`)

```
React EventForm
   │
   │ 1. User picks file (Mantine FileInput)
   │ 2. Client validates type + size (UX guardrail)
   │ 3. POST /api/uploads/presign { contentType, purpose: "EVENT" }
   ▼
Spring Boot UploadController → S3Service
   │ 4. Generate key:  events/2026/06/{uuid}.{ext}
   │ 5. Sign PUT with content-type bound to <exact value from request>
   │    (5-minute expiry from s3.presign-expiry-seconds)
   │ 6. Return { uploadUrl, publicUrl, key, contentType }
   ▼
React frontend
   │ 7. PUT file body to uploadUrl with matching Content-Type header
   ▼
Cloudflare R2
   │ 8. Validates signature + content-type; stores object; returns 200
   ▼
React frontend
   │ 9. Submit event form: POST /api/events { ..., displayPicture: publicUrl }
   ▼
Spring Boot EventService
   │ 10. (Optional) Validate publicUrl starts with s3.publicUrlBase
   │ 11. Persist row with display_picture = publicUrl
```

### Trust model

- The R2 API token lives only in the backend; the browser never sees it.
- The presigned POST is bound to a single key, content-type, and size range chosen by the backend. An intercepted URL cannot be reused for a different file.
- Presign expiry: 5 minutes (configurable via `s3.presign-expiry-seconds`).
- Backend validates that any non-null `displayPicture` URL submitted with an event-create or user-update request starts with `${s3.publicUrlBase}`. URLs that fail this check are rejected with `400 Bad Request`. This blocks arbitrary external URLs without coupling business code to R2 internals.

## Cloudflare R2 setup (one-time, manual)

1. Sign up at cloudflare.com (credit card required; no charge under free tier).
2. R2 → "Create bucket" → name `prozect-media` → choose a region close to users.
3. Bucket → Settings → "Public access" → enable. Note the public URL hostname (`https://pub-<hash>.r2.dev/...`).
4. Bucket → Settings → CORS Policy → add:
   ```json
   [
     {
       "AllowedOrigins": ["http://localhost:5173", "https://<prod-domain>"],
       "AllowedMethods": ["POST", "GET"],
       "AllowedHeaders": ["*"],
       "ExposeHeaders": ["ETag"],
       "MaxAgeSeconds": 3000
     }
   ]
   ```
5. R2 → "Manage API Tokens" → Create API token → scope: Object Read & Write on this bucket → copy access key and secret.
6. Populate `ticketmaster/.env` (do not commit):
   ```
   S3_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
   S3_REGION=auto
   S3_ACCESS_KEY=<token-access-key>
   S3_SECRET_KEY=<token-secret>
   S3_BUCKET=prozect-media
   S3_PUBLIC_URL_BASE=https://pub-<hash>.r2.dev
   ```

## Backend changes

### Dependency (`pom.xml`)

```xml
<dependency>
  <groupId>software.amazon.awssdk</groupId>
  <artifactId>s3</artifactId>
  <version>2.25.0</version>
</dependency>
```

### `application.yml` (additions)

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

### New files

- `com.example.configuration.S3Properties` — `@ConfigurationProperties("s3")` record/class
- `com.example.configuration.S3ClientConfig` — `@Configuration` building `S3Client` and `S3Presigner` beans with R2 endpoint override, `Region.of("auto")`, and `pathStyleAccessEnabled(true)`
- `com.example.service.S3Service` — interface with `PresignedUploadResponse generatePresignedUpload(String contentType, UploadPurpose purpose)`
- `com.example.service.impl.S3ServiceImpl` — implementation; validates content-type, derives key, signs POST
- `com.example.dto.PresignRequestDTO` — `{ contentType, purpose }`
- `com.example.dto.PresignedUploadResponse` — record `{ uploadUrl, publicUrl, key, contentType }`
- `com.example.service.UploadPurpose` — enum `EVENT` (prefix `events`) and `PROFILE` (prefix `users`)
- `com.example.controller.UploadController` — `POST /api/uploads/presign`

### Existing files to update

- `com.example.ApplicationConstants` — add `UPLOAD_ENDPOINT = "/uploads"`
- `com.example.entity.User` — add `display_picture` field
- `com.example.dto.UserDTO` — add `displayPicture` field
- `com.example.dao.impl.UserDaoImpl` — include `display_picture` in insert/update/select
- `com.example.service.impl.UserServiceImpl` — include `displayPicture` in update merge logic
- `com.example.service.impl.EventServiceImpl.createEvent` — validates that any non-null `displayPicture` starts with `${s3.publicUrlBase}` and rejects with `400` otherwise
- `com.example.service.impl.UserServiceImpl.updateUser` — same validation for the user's `displayPicture` field

### `UploadController` contract

```
POST /api/uploads/presign
Request:  { "contentType": "image/jpeg", "purpose": "EVENT" }
200 OK:   {
            "status": "success",
            "message": "Success",
            "data": {
              "uploadUrl": "https://<account>.r2.cloudflarestorage.com/prozect-media/events/2026/06/{uuid}.jpg?X-Amz-...",
              "publicUrl": "https://pub-<hash>.r2.dev/events/2026/06/{uuid}.jpg",
              "key": "events/2026/06/{uuid}.jpg",
              "contentType": "image/jpeg"
            }
          }
400 BAD REQUEST when contentType not in allowlist or purpose unknown
500 INTERNAL SERVER ERROR on signing failure
```

## Frontend changes

### Environment

`ticketmaster_fe/.env` (or `.env.example`) gains nothing — the frontend only talks to the backend; the backend talks to R2.

### New file: `src/service/uploadService.ts`

Single exported function:

```ts
type UploadPurpose = 'EVENT' | 'PROFILE';

export async function uploadImage(file: File, purpose: UploadPurpose): Promise<string>;
```

Behavior:
1. Client-side validation (`image/jpeg|png|webp`, ≤ 5 MB) — purely UX; backend re-enforces via R2 policy.
2. `POST {API_BASE_URL}/api/uploads/presign` with `{ contentType, purpose }` → returns `{ uploadUrl, publicUrl, key, contentType }`.
3. `PUT` the file body to `uploadUrl` with `Content-Type: <returned contentType>` header.
4. Return `publicUrl` on success; throw with a friendly message on failure.

### Updated files

- `src/service/index.ts` — re-export `categoryService`, `eventService`, `locationService`, `uploadService` (current barrel only re-exports `categoryService` — fix this).
- `src/service/locationService.ts` — pre-existing bug to fix as part of this work: `getCities()` calls `/api/events/category` and should call `/api/locations` (cross-checked while wiring services). Same URL pattern as other services. Small fix, related to the broader services cleanup.
- `src/container/createEvent/EventForm.tsx` — currently empty. This spec adds only the image picker piece (not the whole form):
  - Mantine `<FileInput accept="image/jpeg,image/png,image/webp">`
  - On change: call `uploadImage(file, 'EVENT')`, store returned URL in form state
  - Preview `<Image src={posterUrl}>` once set
  - On form submit, pass `displayPicture: posterUrl` to the event-create request
  - Error path: Mantine `notifications` for upload failures
- `src/pages/ProfilePage.tsx` — currently placeholder text. This spec adds only the PFP control:
  - Mantine `<Avatar src={user.displayPicture}>` with an "Edit" overlay/button
  - On file pick: `uploadImage(file, 'PROFILE')` → `PUT /api/users/{id}` with the existing `UserDTO` shape plus the new `displayPicture` field set to the returned URL
  - A new `src/service/userService.ts` with `getUser(id)` and `updateUser(id, dto)` is added for this call

The full event-form layout and profile-page rebuild are out of scope; only the upload + persist path is in scope here.

## Schema migration

```sql
ALTER TABLE users
  ADD COLUMN display_picture VARCHAR(512);
```

Nullable; default of `NULL` represents "no PFP" and the UI falls back to a generated avatar. 512 chars accommodates any R2 public URL with headroom.

No migration is needed for `events.display_picture` — the column already exists as `character varying`.

The migration is applied via the project's existing schema bootstrap (`data.sql` / `schema.sql` pattern under `src/main/resources/`).

## Out of scope (YAGNI)

- Thumbnail generation / multiple sizes
- Server-side image processing (resize, EXIF strip, format conversion)
- Delete old image on replace (orphans are acceptable at this scale)
- Background virus/malware scanning
- CDN configuration in front of R2 (R2 already caches public objects)
- Upload progress UI
- Drag-and-drop upload UX
- File-listing endpoints
- Bucket separation between dev and prod
- Storing object key (instead of URL) — accepted simplification given the single-bucket choice
- Auth on `/api/uploads/presign` — this project does not yet have an auth story, so this endpoint is open like every other endpoint. When auth lands, this endpoint must be protected.

## Testing approach

- **Backend unit tests**
  - `S3ServiceImpl`: content-type allowlist (accept and reject paths), key derivation includes correct prefix and extension, `PresignedUploadResponse` carries the expected `publicUrl`.
  - `UploadController`: 400 on bad content-type; 400 on unknown purpose; 200 happy path with mocked `S3Service`.
  - `EventServiceImpl.createEvent` and `UserServiceImpl.updateUser`: reject `displayPicture` URLs that don't start with `s3.publicUrlBase`.
- **Backend integration test** (optional, manual): point at a real R2 bucket, exercise full presign → upload → verify public URL returns 200.
- **Frontend smoke**
  - Manual: open EventForm, pick a JPG, confirm preview, submit form, verify `events.display_picture` row and the URL renders on the landing page.
  - Manual: PFP flow on ProfilePage.
- **Negative paths** (manual)
  - Try uploading a 10 MB file → R2 rejects with 403; UX shows error.
  - Try uploading a `.txt` file → client blocks; if forged, R2 rejects.

## Future work / open questions

- **Storing key vs URL.** If the project ever needs to swap providers or move buckets, the full URL stored in `display_picture` becomes brittle. The cleaner pattern (store the object key, prepend a public base at render time) is deferred. Note this as a known migration if R2 is ever replaced.
- **Authenticated uploads.** When the project gains login (currently unbuilt — `users` has no `password` column), the presign endpoint must require an authenticated user and the key prefix for `PROFILE` should be derived from the authenticated user's ID, not accepted from the client.
- **Orphan cleanup.** A scheduled job (or a write-time hook on event/user update that deletes the prior key) could reclaim space if usage grows.
