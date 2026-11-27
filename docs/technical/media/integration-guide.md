# Media Module — Feature Integration Guide

This is the primary document for developers integrating Topic, Quiz or another feature with Media. Read this before adding a feature-owned upload endpoint.

## Integration rules

1. Add an upload endpoint owned by your feature. Do not add or call a generic Media REST endpoint.
2. Validate feature-specific rules in your feature (format subset, maximum size, duration, ownership/admin permissions).
3. Choose `purpose` and `MediaAccessPolicy` in the feature. Media stores purpose as opaque metadata and does not switch on it. Enforce the 10 MB image cap required by Cloudinary Free (audio has a 25 MiB Media-wide cap) or choose a stricter feature-specific limit.
4. Call `MediaManagement`; never access Media repositories, Cloudinary classes or provider IDs.
5. Persist the returned `mediaId` in your feature document.
6. When returning content to a client, authorize access to that content first, then call `resolve(mediaId)` and include the resulting URL in your feature response.
7. The caller opens and closes the upload stream. Media consumes it synchronously and does not close or retain it.

For actions that require an available WAV before committing state, authorize the owning
content first and call `verifyWavDelivery(mediaId)` outside the database transaction.
Media requests the first 12 bytes using a fresh provider URL and checks the WAV signature.
It accepts a full or ranged successful response, with bounded connect/read timeouts and
redirects disabled. The check returns no URL and does not change the normal delivery or
renewal contract. Upload validation and listening in the browser cover the rest of the WAV.

## Upload example

A feature service can accept JDK values rather than depending on Spring's `MultipartFile`:

```java
@Service
@RequiredArgsConstructor
public class TopicMediaService {
    private final MediaManagement mediaManagement;

    public MediaUploadResultDto uploadTopicImage(
            String filename,
            String contentType,
            long sizeBytes,
            InputStream content) {
        // Topic-owned rules belong here:
        // - validate allowed image types
        // - enforce the Topic image size limit
        // - authorize the admin and target Topic/use case

        return mediaManagement.upload(new UploadMediaCommandDto(
                "TOPIC_IMAGE",
                MediaAccessPolicy.PUBLIC,
                filename,
                contentType,
                sizeBytes,
                content
        ));
    }
}
```

The feature's HTTP controller owns the multipart boundary and stream lifecycle:

```java
@PostMapping("/api/v1/admin/topics/media")
public ApiResponse<MediaUploadResultDto> upload(@RequestParam("file") MultipartFile file)
        throws IOException {
    try (InputStream content = file.getInputStream()) {
        return ApiResponse.ok(topicMediaService.uploadTopicImage(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                content
        ));
    }
}
```

Adapt the `ApiResponse` factory method and controller signature to the existing feature conventions. The example illustrates the boundary, not a ready-made endpoint.

For protected content, the feature supplies `MediaAccessPolicy.PROTECTED`. Do not accept an arbitrary access policy from the browser; derive it from feature-owned rules.

## Persist the reference

Persist `MediaUploadResultDto.mediaId()` in the feature's own document:

```java
item.setAudioMediaId(uploadResult.mediaId());
```

Do not persist `MediaDeliveryDto.url()`, Cloudinary `public_id`, `asset_id`, or an expiring URL in a feature document.

## Resolve during a feature read

Resolve URLs inside the feature's read service while building its response:

```java
public TopicItemResponse getItem(String topicId, String itemId, String userId) {
    TopicItem item = repository.findItem(topicId, itemId);

    // Required before resolving a protected asset:
    topicAccess.checkCanRead(userId, topicId);

    MediaDeliveryDto audio = item.getAudioMediaId() == null
            ? null
            : mediaManagement.resolve(item.getAudioMediaId());

    return mapper.toResponse(item, audio);
}
```

Map the delivery result into the feature's web response. The frontend receives a ready-to-use URL; it does not call Cloudinary's API or the Media contract directly.

For public content, the URL has no expiry. For protected content, return `expiresAt` with the URL. If it expires, the feature should expose an authorized way to fetch the content again; repeat the feature authorization before calling `resolve`. Do not create a generic unauthenticated `/media/{id}/access` endpoint.

## Error handling

Translate Media contract exceptions at the feature's HTTP boundary:

- `MediaUploadRejectedException`: map to the feature's validation response.
- `MediaNotFoundException`: map to the feature's not-found behavior.
- `MediaStorageUnavailableException`: map to a temporary service/storage failure.

Use the repository's existing ProblemDetail + `code` conventions. Do not expose Cloudinary exception details to the frontend.

## Current Phase 1 limitations

- No public delete capability. Replacing a reference does not delete the old asset.
- No reference tracking or orphan cleanup. Do not assume an unreferenced media is automatically removed.
- No generic Media REST controller or Media Library UI.
- Speaking and Assessment still require their owner integrations. Placement has a feature-owned adapter/migration on this branch; see [handoff and verification](../placement/README.md).
