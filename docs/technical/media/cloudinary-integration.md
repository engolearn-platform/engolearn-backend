# Media Module — Cloudinary Integration

## Configuration

Media and the existing Speaking adapter share one `CloudinaryMediaClient` and the existing Cloudinary credentials.

Configure either:

- `CLOUDINARY_URL`, or
- `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY` and `CLOUDINARY_API_SECRET`.

Never commit real credentials. If Cloudinary is not configured, the application can start, but upload/delivery operations fail with `MediaStorageUnavailableException`.

Additional settings:

| Environment variable | Default | Meaning |
|---|---:|---|
| `MEDIA_MAX_UPLOAD_SIZE_BYTES` | `26214400` | Global technical limit (25 MiB) |
| `MEDIA_PROTECTED_URL_LIFETIME` | `60s` | Protected URL lifetime; accepted range 1–60 seconds |

A feature can apply a lower business-specific limit before calling Media.

## Upload mapping

| EngoLearn policy | Cloudinary delivery type | Cloudinary resource type |
|---|---|---|
| `PUBLIC` image | `upload` | `image` |
| `PUBLIC` audio | `upload` | `video` |
| `PROTECTED` image | `authenticated` | `image` |
| `PROTECTED` audio | `authenticated` | `video` |

Cloudinary classifies audio under `resource_type=video`. The feature contract does not expose that provider detail.

Media generates its own Cloudinary public ID under `engolearn/media/`; the original filename is not used as a storage identifier. Upload uses `overwrite=false`.

## Delivery

### Public

Media constructs a secure HTTPS delivery URL from persisted provider metadata. The URL is not stored in MongoDB and `expiresAt` is null.

### Protected

Media uploads with Cloudinary `type=authenticated` and resolves with the SDK's `privateDownload` / `private_download_url` mechanism, passing:

- persisted Cloudinary `public_id`;
- file `format`;
- `resource_type`;
- `type=authenticated`;
- `expires_at` as a Unix timestamp in seconds.

The exact expiry sent to Cloudinary is returned in `MediaDeliveryDto.expiresAt`. The default is 60 seconds and the implementation rejects configured values outside 1–60 seconds.

This delivery path uses Cloudinary's authenticated download API rather than ordinary CDN-cached delivery. That is the accepted Phase 1 trade-off for the team's Free account. It may have different bandwidth/cache characteristics from CDN token delivery. Token-based CDN access is out of scope unless the account plan changes.

## Failure compensation

If Cloudinary upload succeeds but MongoDB metadata persistence fails, Media attempts an internal Cloudinary destroy using the returned provider identifiers. If cleanup also fails, Media logs the failure with the internal media ID and propagates a storage failure. This is best-effort compensation, not a public delete capability.

## Known verification requirement

A successful SDK call alone does not prove browser playback behavior. Before using protected audio in a production feature, perform the real-account smoke test in [testing and operations](testing-and-operations.md), especially inline playback, HTTP Range/seek and expiration. Do not assume Range behavior without observing the actual response.
