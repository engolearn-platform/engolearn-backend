# Media Module

## Purpose

The Media feature owns uploaded media metadata and storage-provider integration. It exposes an in-process contract; it does **not** expose a generic REST upload endpoint.

Each consuming feature owns its upload HTTP endpoint, business validation, access decision and authorization. Features store the returned `mediaId` and resolve a delivery URL when building their own API response.

## Architecture and ownership

```text
Feature HTTP API
  -> Feature service (business validation + authorization)
      -> media.contract.MediaManagement
          -> MediaManagementImpl
              -> MediaAssetRepository
              -> MediaStorageProvider
                  -> CloudinaryMediaStorageProvider
                      -> shared CloudinaryMediaClient
```

- Feature: allowed formats/size/duration for its use case, `purpose`, `MediaAccessPolicy`, authorization and media references.
- Media: generic file-signature validation, global upload limit, `media_assets`, provider identifiers, upload and URL resolution.
- Cloudinary adapter: maps Media's provider-neutral policy to Cloudinary resource/delivery types.
- No feature may access `MediaAssetRepository`, Cloudinary SDK classes or provider metadata directly.
- Media does not know Topic, Quiz, Subscription, Assessment, Placement or Speaking business rules.

## Public contract

Package: `fit.iuh.engolearn.media.contract`

`MediaManagement` currently exposes:

| Operation | Purpose |
|---|---|
| `upload(UploadMediaCommandDto)` | Upload one asset and return an internal `mediaId` |
| `resolve(String mediaId)` | Generate a delivery URL from persisted metadata |

There is deliberately no public delete operation in Phase 1. Internal provider destroy is used only as best-effort compensation if upload succeeds but metadata persistence fails.

### UploadMediaCommandDto

- `purpose`: nonblank feature-owned metadata string; opaque to Media.
- `accessPolicy`: `PUBLIC` or `PROTECTED`, chosen by the feature.
- `originalFilename`: optional diagnostic filename.
- `contentType`: declared MIME type; checked against file signature.
- `sizeBytes`: declared size; checked against the actual stream byte count.
- `content`: one-shot `InputStream`, consumed synchronously. The caller owns opening and closing it.

### MediaUploadResultDto

Returns `mediaId`, detected `contentType`, actual `sizeBytes` and `accessPolicy`. Provider IDs and URLs are intentionally not exposed.

### MediaDeliveryDto

Returns `url`, `contentType` and `expiresAt`. Public delivery has `expiresAt=null`; protected delivery has an expiry.

### Contract failures

- `MediaUploadRejectedException`: invalid/unsupported file or size.
- `MediaNotFoundException`: unknown media ID.
- `MediaStorageUnavailableException`: provider or metadata persistence failure.

These exceptions contain no HTTP status. The calling feature translates them in its own HTTP exception handling.

## Persistence

Collection: `media_assets`.

```json
{
  "_id": "internal-media-id",
  "purpose": "LESSON_AUDIO",
  "access_policy": "PROTECTED",
  "original_filename": "lesson.mp3",
  "content_type": "audio/mpeg",
  "size_bytes": 1024000,
  "storage": {
    "provider": "CLOUDINARY",
    "asset_id": "provider-asset-id",
    "public_id": "engolearn/media/internal-media-id",
    "resource_type": "VIDEO",
    "delivery_type": "AUTHENTICATED",
    "format": "mp3",
    "version": 123
  },
  "created_by": "admin-id",
  "created_at": "..."
}
```

The model extends `shared.BaseDocument`; persisted fields use explicit snake_case mappings. Generated delivery URLs, signatures and expirations are never persisted. Feature documents store only the internal `mediaId`, not a Cloudinary URL.

## Supported technical formats

Media currently recognizes file signatures for JPEG, PNG, WEBP, MP3 and WAV. Audio uses Cloudinary `resource_type=video`; this is Cloudinary's resource classification, not an EngoLearn media type.

The global upload limit defaults to 25 MiB. A feature may enforce a smaller use-case-specific limit before calling Media.

## Access and authorization

- `PUBLIC`: stable HTTPS delivery URL.
- `PROTECTED`: Cloudinary authenticated asset and time-limited `private_download_url`.
- Media's `resolve` method does not authorize a user. The caller must authorize access to the owning content before resolving a protected asset.
- A `mediaId` is an identifier, not an authorization token.

See [integration guide](integration-guide.md), [Cloudinary integration](cloudinary-integration.md) and [testing and operations](testing-and-operations.md).
