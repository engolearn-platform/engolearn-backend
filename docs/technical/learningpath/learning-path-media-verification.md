# Learning Path Media live verification — 2026-11-27

This is the historical step 5 verification record. The subsequently approved
[migration of 30 ACTIVE B1 passages](learning-path-media-migration-2026-11-27.md)
is complete; legacy learner snapshots remain outside that migration.

Scope: workspace AGENT.md §17.13 steps 4–5, authorized by the owner. Backend and
frontend branch: `fix/learning-path-media-with-cloudinary`. This record contains
no credentials, cookies or signed delivery URLs.

## Actual upload retained for use

Uploaded exactly one existing passage recording through the authenticated
management passage API, using the configured real Cloudinary provider:

- Source passage: `LP-PASS-L-B1-V2-001`, Updates to the Number 42 Bus Route.
- Source file: frontend `public/learning-path-audio/b1-v2/listening-001.mp3`.
- Source SHA-256: `72580eeaaacb3c8fbeebb66594c3cbc3b250f793c1088c779a5d413035222785`.
- New DRAFT passage: `LP-PASS-118fbaf8-8105-4aa2-8aaf-876279ff4500`.
- Media ID: `6d649d45-1f23-4e1b-8a70-5a26802a9327`.

The normal local DB retains the useful DRAFT, matching title/transcript and Media
asset. The original ACTIVE passage, existing paths and snapshots were not migrated.
Normal DB path/snapshot counts before and after testing were 7/480. The original
ACTIVE passage remains revision 1 with its original local URL; the retained
managed draft remains DRAFT revision 1. No disposable audio was
uploaded and no Cloudinary asset was deleted. This is one upload, not a bulk migration.
The retained normal-DB draft still requires genuine editorial/listening review
before publication; technical verification does not attest those confirmations.

## Real Cloudinary delivery

- Authorized admin detail resolved a protected `audio/mpeg` preview, HTTP 200.
- Cloudinary download succeeded, HTTP 200, 579692 bytes. The provider re-encoded
  the original 559628-byte MP3, so their binary hashes differ.
- FFprobe reported both recordings as MP3, 48 kHz, mono, 46.581333 seconds.
  Decoding both to 8 kHz mono PCM yielded 372651 samples each; waveform correlation
  was 0.9998951077. This checks recording identity, not human audio-quality approval.
- Range `bytes=2000-3999` returned HTTP 206 with exactly 2000 bytes matching the
  same slice of the full provider download.
- After actual expiry plus three seconds, the old URL returned 401. Resolving a
  new preview returned 200 and its file downloaded with 200. No URL was saved in
  a path, snapshot or source passage.

## Real frontend/backend flow

The frontend at `127.0.0.1:13000` used its real Vite API proxy to backend `:18080`.
A separate local MongoDB replica set on `127.0.0.1:27019` contained only verification
data in `engolearn_lp_media_check_20261127`. The application user cannot create
another database on the normal Mongo instance; its privileges were not expanded.

Copied the existing demo manager/admin accounts, an existing READY assessment and
30 actual B1 Listening for Gist questions/passages. In this isolated copy only,
the assessment owner was assigned to the manager, its profile level was B1 and
the matching Number 42 passage used the uploaded Media ID. No API response was
mocked or intercepted and no recording was assigned to an unrelated passage.

POST by-assessment created path `6b092ad9b0d1ed4d927464e1` and 30 immutable question
snapshots using the actual creation service. The UI opened its detail, started
week 1/day 1, and question 10 played the managed recording. Observed:

- Native playback reached readyState 4, duration 46.581333 and advancing currentTime;
  seeking moved to about 23 seconds, with no media error.
- Paused renewal preserved 29.245011 seconds and stayed paused. Playing renewal
  restored position and resumed playback.
- Actual expiry produced the explicit expired-link message and paused playback;
  manual renewal recovered it while retaining selected answer B.
- UI submission acknowledged nine unanswered questions. Review showed one answered,
  one correct, nine unanswered, answer B, transcript and explanation. Review playback
  and manual renewal both worked, with advancing playback after renewal.
- A nonexistent provider file, configured only in isolated Media metadata, produced
  the inline playback failure message and an enabled retry action. The original
  metadata was restored; this neither uploaded nor deleted a Cloudinary file.
  Clicking retry then cleared the error and restored readyState 4, duration
  46.581333 and advancing playback without a media error.

Actual audio GET checks through the same frontend proxy returned 200/no-store for
the completed day, 401 for a guest, 404 for another owner or a question outside
the day, and 409 for a not-started day, cancelled path or expired practice day.
Missing snapshot Media returned 404 without legacy fallback; invalid delivery
type returned 503. Editing the source passage did not affect saved snapshot
delivery (200). Business collections were compared before/after read-only calls;
paths, snapshots, source passages and Media metadata did not change. Temporary
negative-case changes were confined to the isolated DB and restored.

The technical publication gate `LEARNER_AUDIO_DELIVERY_UNSUPPORTED` is removed.
Readiness still resolves Media, validates URL/type/expiry, rejects missing assets
without legacy fallback, and requires all professional review confirmations.
Question publication/replacement reuse those checks. Publication is revision-bound;
delivery URLs remain transient.

After packaging the new backend, an additional complete pipeline ran through
real HTTP on the isolated DB: readiness for the uploaded passage returned valid;
publication without confirmations returned 422; publication with the full test
checklist returned 200. Copied the actual matching question, attached that
published passage via PATCH and replaced the original question through the API.
Created a new admin-owned path from an isolated assessment copy, started the
assigned week 1/day 1, renewed its audio, saved answer B, submitted and reviewed.
The result contained the selected/correct B, transcript and fresh audio delivery;
start exposed neither transcript nor answers. No calendar adjustment was needed.
Path: `6b092e2a5ca63e67967874f9`; question:
`LP-Q-162b5279-6a64-4c40-b4a2-1f0f7ea07238`. Checklist confirmations in this test
apply exclusively to isolated verification records and are not human approval
of the retained normal-DB draft. Receipt: `target/live-media-publication-report.json`.

## Verification artifacts and remaining work

Local ignored evidence is under backend `target/live-media-*`: upload receipt,
provider report, access report and isolated setup helpers. The frontend owning
[audio document](../../../../engolearn-frontend/docs/features/learning-path/learner/04-audio-delivery.md)
describes player behavior and UI observations. Automated checks are recorded below
after completion.

Backend automated checks: `./mvnw.cmd "-Dtest=ArchitectureTest,LearningPath*,QuestionPool*IntegrationTest" "-Dsurefire.useManifestOnlyJar=false" verify`
ran 435 tests, failures 0/errors 0/skips 0. Packaging initially failed because
the isolated backend held the jar open on Windows. After stopping that process,
`./mvnw.cmd -DskipTests verify` completed BUILD SUCCESS, including architecture
and packaging checks without repeating the passing tests. Logs:
`target/learning-path-media-step5-verification.log` and
`target/learning-path-media-step5-package.log`. The full repository test suite
was not run. Frontend source was unchanged in step 5; the step 4 build/lint
results remain applicable, supplemented by actual browser verification above.

After verification, the temporary frontend/backend processes were stopped and
the owned disposable MongoDB container/database was removed. The normal backend,
normal database and retained useful Cloudinary file were preserved. Local
verification receipts remain in ignored `target/` for audit.

Bulk migration of local passage/JSON references and any handling of existing
legacy snapshots remain separate work. Preserve recording identity and frozen
content/answers/progress; obtain the owner's migration scope before changing them.
The read-only [audio mapping table](learning-path-audio-mapping-2026-11-27.md)
records the current files, existing Media reuse candidate and legacy snapshots
for owner approval; it does not authorize or execute migration.
Browser coverage here is the in-app Chromium browser, not every browser/device or
mobile network. No real provider outage, forced autoplay denial or human listening
quality assessment is claimed. No commit or push is part of this step.
