# Media Module — Testing and Operations

## Automated tests

The module tests are designed not to call Cloudinary:

- `MediaContentInspectorTest`: signature recognition (including rejection of AAC/ADTS and invalid MPEG headers as MP3), MIME mismatch, unsupported content, image-specific limit and bounded stream behavior.
- `MediaManagementImplTest`: upload result, public/protected resolution, missing IDs and best-effort compensation.
- `MediaAssetRepositoryIntegrationTest`: Mongo persistence and snake_case mapping using the shared singleton MongoDB Testcontainers base. Docker is required for this integration test.
- `ArchitectureTest`: approved feature root and contract/package dependency rules.

Suggested commands from the repository root:

```powershell
./mvnw -Dtest=MediaContentInspectorTest,MediaManagementImplTest test
./mvnw -Dtest=MediaAssetRepositoryIntegrationTest test
./mvnw -Dtest=ArchitectureTest test
./mvnw -Pdev-verification test
```

Use `mvnw.cmd` instead of `./mvnw` in Windows PowerShell if the wrapper script is not executable.

## Manual HTTP end-to-end smoke test

For a complete request path through Spring MVC, MediaManagement, Cloudinary, MongoDB and back to Cloudinary delivery, use the temporary [HTTP smoke-test guide](smoke-test-guide.md). The controller is disabled by default and requires ADMIN authentication when enabled.

## Manual Cloudinary Free-account smoke test

Use a non-sensitive test asset and the team's configured account:

1. Upload a small public PNG and confirm the returned public URL loads in a browser.
2. Upload a small protected WAV/MP3 and confirm Cloudinary reports an authenticated asset.
3. Resolve the protected asset and open the returned private download URL in a browser.
4. Inspect response status, `Content-Type`, `Content-Disposition` and whether the browser can play the audio inline.
5. Send a byte-range request and inspect whether Cloudinary returns `206 Partial Content` with a valid `Content-Range`; test seeking in the actual target browser.
6. Wait until the reported expiry and verify the old URL is rejected.
7. Resolve again and verify a new URL works.

The repository changes do not themselves prove these external account behaviors. Record actual observations before marking protected audio delivery production-ready.

## Operational notes

- Application startup without Cloudinary credentials is supported for local/test workflows; media operations require credentials.
- Keep Cloudinary credentials in local `.env` or deployment secret storage, never in source control.
- Do not log signed/private delivery URLs, signatures or credentials.
- A `mediaId` is not an authorization token. The owning feature must authorize access before resolving protected media.
- If MongoDB persistence fails after upload, inspect structured logs for a compensation failure. A failed compensation may leave an orphaned Cloudinary asset.
- Phase 1 has no public delete, reference tracking or orphan cleanup job. Replacing a feature reference does not remove the old asset.
- Public URL generation assumes assets remain immutable; upload IDs are generated uniquely and overwrite is disabled.
