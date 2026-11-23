# Media HTTP Smoke Test Guide

This guide verifies the Media module through HTTP before business features integrate the in-process `MediaManagement` contract.

## Important

The `MediaSmokeTestController` is a temporary development adapter, not a production upload API.

- It is disabled by default.
- It is enabled only with `MEDIA_SMOKE_TEST_ENABLED=true`.
- Both routes are temporarily public on the smoke-test branch so the team can test without a seeded ADMIN account.
- This is intentionally unsafe for shared or deployed environments: enable only on localhost, never expose it publicly, and restore ADMIN authorization or remove the controller before opening the PR.
- Uploads create real Cloudinary assets and MongoDB metadata. There is no smoke-test delete endpoint; remove test assets from the Cloudinary console after testing.

## Enable locally

Add these values to the backend's local `.env` file (do not commit it):

```dotenv
MEDIA_SMOKE_TEST_ENABLED=true
CLOUDINARY_URL=cloudinary://API_KEY:API_SECRET@CLOUD_NAME
```

Alternatively, configure the three separate Cloudinary credential variables. Restart the backend after changing the environment.

The multipart request limit is configured for a single upload up to the Media module's global limit, with a little overhead for multipart boundaries:

- `MEDIA_MAX_UPLOAD_SIZE_BYTES`: 25 MiB default.
- `MEDIA_MAX_IMAGE_UPLOAD_SIZE_BYTES`: 10 MB default.
- `MEDIA_MAX_MULTIPART_REQUEST_SIZE_BYTES`: 30 MiB default.

## Routes

| Method | Route | Purpose |
|---|---|---|
| `POST` | `/api/v1/media/smoke/uploads` | Upload one file |
| `GET` | `/api/v1/media/smoke/{mediaId}/delivery` | Resolve a delivery URL |

Both routes require ADMIN authentication. Log in through the existing auth flow first and let Postman retain the authentication cookie.

### 1. Upload a public image

In Postman:

- Method: `POST`
- URL: `http://localhost:8080/api/v1/media/smoke/uploads`
- Authorization: **No authentication required during this temporary local testing phase.**
- Body: `form-data`
  - `file`: select a small PNG/JPEG/WEBP file (File type).
  - `accessPolicy`: `PUBLIC` (Text type; optional, defaults to PUBLIC).

Expected response status: `201 Created`.

Example response data:

```json
{
  "data": {
    "mediaId": "internal-media-id",
    "contentType": "image/png",
    "sizeBytes": 12345,
    "accessPolicy": "PUBLIC"
  },
  "timestamp": "2026-11-23T08:00:00Z"
}
```

Save the returned `mediaId`.

### 2. Resolve the public URL

Send:

```http
GET http://localhost:8080/api/v1/media/smoke/{mediaId}/delivery
```

Expected response data:

```json
{
  "data": {
    "url": "https://res.cloudinary.com/...",
    "expiresAt": null,
    "contentType": "image/png"
  },
  "timestamp": "2026-11-23T08:00:00Z"
}
```

Open the returned URL directly in a browser or send a separate HTTP GET request to it. Verify:

- HTTP 200.
- Response content type is the expected image MIME type.
- The downloaded bytes match the original test file.

This last direct request is important: the backend only resolves the URL; the browser/client fetches the media from Cloudinary.

### 3. Verify protected delivery

Repeat the upload request with:

- `file`: a non-sensitive test image or audio file.
- `accessPolicy`: `PROTECTED`.

Resolve the returned `mediaId`. The response should contain a `private_download_url` and non-null `expiresAt`. Fetch that URL directly and verify the response. After the expiry time, verify the old URL is rejected, then call the resolve endpoint again to obtain a fresh URL.

For protected audio, also inspect `Content-Type`, `Content-Disposition`, and HTTP Range/seek behavior in the target browser. Do not infer playback support from a successful upload alone.

## Disable after testing

Set:

```dotenv
MEDIA_SMOKE_TEST_ENABLED=false
```

or remove the variable, since the default is false. Restart the backend. The controller bean will no longer be registered.

## Troubleshooting

- `404` on the smoke routes: confirm `MEDIA_SMOKE_TEST_ENABLED=true` and restart.
- Authentication is intentionally bypassed for these temporary smoke routes. Do not carry this exception into the PR.
- `413` or multipart size exception: check the configured multipart request/file limits and the Media-specific image/audio limits.
- Storage failure: check Cloudinary credentials, account configuration and backend logs. Never paste API secrets or protected delivery URLs into logs/issues.
- `404` resolving a media ID: verify the upload completed and the returned ID is used exactly.

## Cleanup

The smoke routes intentionally do not expose delete. Remove test assets from Cloudinary Media Library after the verification run. The corresponding MongoDB metadata can be cleaned manually in the local development database if required.
