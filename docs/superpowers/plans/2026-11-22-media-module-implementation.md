# Media Module Implementation Plan

> **Status:** Awaiting user approval. This commit contains the plan only; no production code or runtime behavior is changed.
> **Target branch:** `feat/media-module`, based on `dev`.

## Goal

Introduce a reusable, feature-first `media` module that owns MediaAsset persistence and the new Media contract, while keeping upload APIs and business-specific validation inside each consuming feature. Phase 1 implements the module itself only; Topic, Quiz, Assessment, Placement and Speaking are not migrated or integrated.

## Approved design decisions

- One team-managed Cloudinary account and the existing Cloudinary credentials/configuration.
- Each feature will expose its own upload HTTP API when it integrates; Media will not expose a generic upload controller.
- Features call Media in-process through `media.contract.MediaManagement`; no feature accesses Media repositories or Cloudinary classes.
- Media stores one MongoDB `media_assets` document per uploaded asset. Feature documents store `mediaId`, not a provider URL.
- The feature decides `purpose` and `MediaAccessPolicy` (`PUBLIC` / `PROTECTED`). Media persists `purpose` as opaque metadata and must not branch on feature-specific purpose values.
- Public delivery uses Cloudinary `upload` delivery and a stable public URL.
- Protected delivery uses Cloudinary `authenticated` delivery and `private_download_url`, with an expiry returned in the delivery DTO. Token-based CDN delivery is out of scope because the team account is Free.
- The contract accepts `InputStream` plus `sizeBytes`. Media consumes the stream synchronously during `upload`; the stream is not retained.
- Phase 1 exposes upload and resolve only. No public delete capability, reference tracking, Media Library UI, cleanup scheduler or feature migration.
- Authorization remains with the feature that owns the content. The feature must authorize the user before calling `resolve` for protected content.
- Documentation should be concise: group related design material rather than creating one file per small topic.

## Current-code findings and compatibility constraints

The following existing behavior must be preserved:

1. `shared.media.MediaStorage` currently exposes `uploadPrivateAudio(publicId, format, byte[])` and `privateAudioUrl(publicId, format, lifetime)`.
2. `shared.media.CloudinaryMediaStorage` currently uploads WAV/MP3 as `resource_type=video`, `type=authenticated`, `overwrite=false`; it creates private download URLs with `expires_at` and rejects lifetimes above one minute.
3. `shared.media.CloudinaryMediaConfig` supports either `CLOUDINARY_URL` or the individual cloud-name/API-key/API-secret values. It permits the application to start without Cloudinary configured and fails when storage is actually used.
4. `speaking.service.SpeakingSessionService` injects the existing `shared.media.MediaStorage`. Speaking stores its own `SpeakingAudioAsset` records (including `cloudinary_public_id`, format and playback metadata) and uses the legacy upload/playback methods. Do not change this integration or its persisted data in Phase 1.
5. Assessment currently reads local WAV files through `AssessmentMediaImpl`; Placement serves local WAV files through its resource resolver. Leave both flows untouched.
6. The repository uses `shared.BaseDocument`, `shared.EmbeddedDocument`, explicit snake_case `@Field` mappings, feature-first package rules and an ArchUnit `ArchitectureTest`.
7. Cloudinary Java SDK `cloudinary-http5:2.5.0` is already present. MongoDB integration tests use the shared singleton Testcontainers setup in `config.AbstractMongoIntegrationTest`.

### Compatibility approach

Keep the existing `shared.media.MediaStorage` API and its Speaking behavior intact. The new Media implementation must reuse the same Cloudinary account/configuration rather than introduce another credential source or a second independently configured client. During implementation, extract/share the Cloudinary client configuration as needed and provide a new Media-owned storage adapter/port for generic assets. Do not make Speaking depend on `media` and do not migrate `SpeakingAudioAsset` into `media_assets`.

If code inspection reveals that preserving the legacy bean requires a materially different adapter arrangement, stop and report the conflict rather than silently changing Speaking behavior.

## Target module shape

Use the repository's approved feature-first package conventions. The following is a target shape, not a requirement to create empty packages:

```text
media/
├── contract/
│   ├── MediaManagement.java
│   ├── dto/
│   │   ├── UploadMediaCommandDto.java
│   │   ├── MediaUploadResultDto.java
│   │   └── MediaDeliveryDto.java
│   ├── enums/
│   │   └── MediaAccessPolicy.java
│   └── exception/                 # only stable caller-actionable failures
├── service/
│   └── MediaStorageProvider.java  # internal provider port, if needed
├── service/impl/
│   └── MediaManagementImpl.java
├── model/
│   ├── MediaAsset.java
│   ├── enums/
│   └── vo/
│       └── MediaStorageMetadataVo.java
├── repository/
│   ├── MediaAssetRepository.java
│   └── MongoMediaAssetRepository.java
└── infra/
    └── cloudinary/
        ├── CloudinaryMediaStorageProvider.java
        └── CloudinaryMediaConfiguration.java
```

Do not create a feature-local service interface that merely duplicates the public `MediaManagement` contract. The contract implementation belongs in `service.impl`. Keep Cloudinary SDK types out of `media.contract`.

## Contract design

### `MediaManagement`

Phase 1 public in-process capability:

- `MediaUploadResultDto upload(UploadMediaCommandDto command)`
- `MediaDeliveryDto resolve(String mediaId)`

No `delete` operation in this phase. A provider-level destroy operation may exist internally only as compensation if Cloudinary upload succeeds but Mongo persistence fails; it must not become a public Media capability.

### `UploadMediaCommandDto`

Proposed fields:

- `purpose`: nonblank string supplied by the calling feature; opaque to Media.
- `accessPolicy`: `PUBLIC` or `PROTECTED`, supplied by the calling feature.
- `originalFilename`: optional diagnostic metadata; never use it as the Cloudinary public ID.
- `contentType`: declared MIME type, treated as untrusted input.
- `sizeBytes`: declared byte count, checked against configured limits and actual stream consumption.
- `content`: `InputStream`, consumed synchronously once during the call.

Document stream ownership explicitly: Media consumes the stream before returning and does not retain it. The caller owns opening/closing the stream. Do not buffer unbounded content in memory.

### `MediaUploadResultDto`

Return only what the feature needs to persist/reference the upload, initially:

- `mediaId`
- verified `contentType`
- `sizeBytes`
- `accessPolicy`

Do not expose Cloudinary `asset_id`, `public_id`, resource type, delivery type, signatures or credentials through the feature contract.

### `MediaDeliveryDto`

Return:

- `url`
- `expiresAt` (null for public media; populated for protected media)
- `contentType`

The contract's Javadoc must state that callers are responsible for authorizing the user's access to the owning content before resolving protected media. Media resolves storage access; it does not evaluate Topic/Quiz/subscription entitlements.

### Contract failures

Expose stable, caller-actionable Media exceptions (for example not found, invalid/inactive asset, upload rejected, storage unavailable) without HTTP status codes or Spring/Cloudinary exception types. Feature controllers translate these through their own HTTP exception handling.

Use the repository's contract naming convention (`Dto` suffix) and keep contract types framework/provider-neutral.

## Persistence design

Create `media_assets` as a root Mongo document extending `shared.BaseDocument`. Use `String id` via the inherited `@Id`; explicitly map every persisted non-ID field with snake_case `@Field`.

Proposed fields:

- `purpose`: string metadata supplied by the feature.
- `access_policy`: `PUBLIC` / `PROTECTED`.
- `status`: initially `ACTIVE`; keep lifecycle scope minimal.
- `content_type`: verified MIME type.
- `size_bytes`: actual uploaded byte count.
- `storage`: embedded `MediaStorageMetadataVo`.
- `created_by`: authenticated actor ID when reliably available from existing server-side identity context; never accept this value from the client.

The embedded storage metadata should contain only provider identifiers/attributes required for delivery and future provider operations:

- `provider` (initially `CLOUDINARY`)
- `asset_id`
- `public_id`
- `resource_type` (`image` for images; Cloudinary `video` for audio)
- `delivery_type` (`upload` for public; `authenticated` for protected)
- `format`
- `version`, when returned by Cloudinary

Do not persist a delivery URL, signed URL, signature, API secret or `expires_at`. Expiration belongs to each generated delivery response, not to the asset. Do not add reference arrays or feature IDs to MediaAsset.

Use embedded value conventions from `shared.EmbeddedDocument`. Add only indexes justified by actual queries; lookup in Phase 1 is by Mongo `_id`, so avoid speculative indexes.

## Upload and delivery behavior

### Upload

1. The calling feature performs business authorization and feature-specific rules (allowed subset of formats, use-case size/duration limits).
2. Media validates generic technical constraints: non-empty stream, positive declared size, configured global hard limit, supported actual file signature and consistency between declared and detected MIME type.
3. Media generates its own non-user-controlled media ID and Cloudinary public ID (for example under a neutral `engolearn/media` folder); do not derive storage identity from the original filename or feature-specific purpose.
4. Map detected media category to Cloudinary resource type: image to `image`; audio to `video`. Keep Phase 1 scope to the image/audio formats explicitly supported by the implementation and documented allowlist.
5. Upload with `type=upload` for `PUBLIC`, or `type=authenticated` for `PROTECTED`; use `secure=true` and `overwrite=false`.
6. Persist provider metadata and verified file metadata in MongoDB.
7. Return the internal `mediaId` and safe metadata, not a provider URL.

Because `InputStream` may be one-shot, implement bounded signature inspection and actual-byte counting without reading an unbounded stream into memory. Verify the Cloudinary SDK's InputStream upload support before implementation. Treat `sizeBytes` as a claim to validate, not proof of actual payload size.

A global hard upload limit must be configurable independently of each feature's stricter business limits. The initial default is a plan-review item; do not silently hardcode a large limit.

If Cloudinary upload succeeds but Mongo persistence fails, attempt best-effort provider cleanup using the returned provider identifier, log a structured error if compensation also fails, and propagate a stable Media failure. This compensation is not a user-facing delete feature.

### Resolve

- Load MediaAsset by internal `mediaId`; reject missing or non-active records.
- For `PUBLIC`, generate a secure public delivery URL from persisted provider metadata. Return `expiresAt=null`.
- For `PROTECTED`, use Cloudinary `privateDownload` with `resource_type`, `type=authenticated` and Unix `expires_at`. Return the exact corresponding `expiresAt`.
- Make protected URL lifetime configurable, with a 60-second default and an upper bound no greater than the existing one-minute behavior unless Cloudinary verification proves another safe bound. Do not store generated URLs or expirations.
- Document that Cloudinary `private_download_url` is delivered through the authenticated API rather than CDN-cached delivery. This is an accepted Phase 1 trade-off for the Free account.

### Required real-account verification

Before declaring protected delivery complete, perform a manual smoke test against the team's configured Cloudinary Free account using a non-sensitive test audio asset:

- authenticated upload succeeds;
- returned private download URL is usable by a browser/audio client;
- verify response content type and inline playback behavior;
- verify HTTP Range/seek behavior (including status/headers where supported);
- verify access is rejected after expiry;
- verify the chosen expiry value is honored.

Mocked tests cannot prove these Cloudinary delivery properties. If the real-account test shows that playback/Range is unsuitable, stop and report the limitation; do not silently replace the approved delivery mechanism with a proxy or a paid-plan feature.

## Implementation tasks

### Task 0 — Baseline and focused discovery

- Confirm `feat/media-module` still starts at the current `dev` head; do not rebase or merge unrelated branches without approval.
- Run the smallest useful baseline verification and record pre-existing failures separately.
- Inspect all usages of `shared.media.MediaStorage`, the Speaking audio save/playback lifecycle, Cloudinary configuration, Mongo conventions and test patterns.
- Confirm whether the current `CloudinaryMediaStorage` is the only legacy consumer path and identify any hidden configuration assumptions.

**Exit:** compatibility strategy is confirmed; no source changes yet.

### Task 1 — Establish Media feature boundary and contracts

- Add `media` to the approved feature roots in `ArchitectureTest`.
- Add the feature to package-structure and module-map documentation.
- Add the approved `media` dependency row to cross-feature communication (initially no feature consumers).
- Create the contract, DTOs, policy enum, public failure types and Javadoc semantics.
- Ensure no Cloudinary/Spring persistence types leak into the contract.

**Exit:** ArchitectureTest package rules pass; contract compiles.

### Task 2 — Add persistence model and repository

- Add `MediaAsset` and `MediaStorageMetadataVo` using BaseDocument/EmbeddedDocument conventions.
- Add enums for persisted finite classifications; keep `purpose` open-ended.
- Add typed repository operations needed for lookup by media ID.
- Verify Mongo field names, ID mapping and collection discovery through the existing bootstrap.
- Add Mongo integration coverage using the repository's shared Testcontainers setup.

**Exit:** stored document matches the approved schema; no feature collection is accessed.

### Task 3 — Add Cloudinary provider adapter without breaking Speaking

- Reuse existing Cloudinary SDK 2.5.0 and credentials.
- Share one configured Cloudinary client/configuration between the new generic Media adapter and the legacy `MediaStorage` adapter.
- Preserve legacy `MediaStorage` method signatures and Speaking behavior.
- Implement generic upload, public URL generation, authenticated private download URL generation and internal compensation destroy.
- Cover provider option mapping, missing configuration, provider errors and URL lifetime validation with mocked tests.

**Exit:** existing Speaking code compiles unchanged and its legacy behavior remains covered.

### Task 4 — Implement MediaManagement service

- Enforce global technical validation and bounded stream handling.
- Map `MediaAccessPolicy` to Cloudinary delivery type.
- Upload to Cloudinary, persist MediaAsset, and compensate on persistence failure.
- Resolve public/protected delivery from stored metadata.
- Keep feature authorization outside Media.
- Do not add REST controllers or integrate any feature.

**Exit:** service tests cover upload/resolve success and relevant failure paths.

### Task 5 — Verification and documentation

- Run focused unit tests, Media Mongo integration tests (when Docker is available), `ArchitectureTest`, and the repository's agreed verification profile.
- Run the manual Cloudinary Free-account smoke test described above and record observed playback/Range/expiry behavior.
- Add/update the grouped docs:
  - `docs/technical/media/README.md`: architecture, boundaries, data model, contract and integration guidance.
  - `docs/technical/media/cloudinary-integration.md`: configuration, upload mapping, public/protected delivery, expiry and known Free-plan trade-offs.
  - `docs/technical/media/testing-and-operations.md`: test approach, local setup, smoke test and troubleshooting.
- Update `docs/architecture/package-structure.md`, `cross-feature-communication.md` and `module-map.md`.
- Update `.env.example` and `application.yaml` only for newly introduced configuration, with safe defaults and no secrets.
- Report changed files, test commands/results, manual verification results and known limitations.

**Exit:** Media Module is usable through its internal contract, documented, and existing feature behavior is unchanged.

## Acceptance criteria

- [ ] A feature can upload a supported public or protected media asset through `MediaManagement` without knowing Cloudinary.
- [ ] The returned `mediaId` is persisted in a `media_assets` document with snake_case fields and provider metadata.
- [ ] Public resolve returns a stable HTTPS URL with no expiry.
- [ ] Protected resolve returns a time-limited `private_download_url` and its exact expiry.
- [ ] No delivery URL, signature, credential or expiry is persisted in MongoDB.
- [ ] Feature-specific `purpose` is stored as opaque metadata; Media contains no per-feature switch/case.
- [ ] Stream size and actual file content are validated without unbounded buffering.
- [ ] Cloudinary/Mongo partial failure is handled with best-effort compensation.
- [ ] Speaking continues using the legacy `MediaStorage` API unchanged; Assessment and Placement local media remain unchanged.
- [ ] No feature upload endpoint, feature migration, public delete API, reference tracking, cleanup scheduler or Media Library UI is introduced.
- [ ] Architecture rules, targeted tests and documentation are updated and verified.

## Explicitly out of scope

- Topic/Quiz/Assessment/Placement/Speaking upload API integration.
- Migration of existing local or Speaking audio assets.
- Public REST endpoints owned by Media.
- Media Library UI, asset browsing/search and reuse workflows.
- Delete/detach lifecycle, reference counting and scheduled orphan cleanup.
- Token-based CDN delivery or paid Cloudinary plan features.
- Backend proxy/streaming endpoints.
- New microservice or new storage provider.

## Approval gate

This plan is the only intended change in the current review step. **Do not begin implementation until the user explicitly approves this plan.**
