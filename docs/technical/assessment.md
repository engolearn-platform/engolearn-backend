# Periodic assessment — learner F01/F02

Scope: preparation, transactional start, forward-only taking, submission, grading,
history, result and immutable review. Business sources: BR-PFC-F1.1–11 and
BR-PFC-F2.1–6 in the supplied business-docs bundle. User-approved clarification:
a single listening question may advance while its audio is playing; the old audio stops.
The learner may end the reading preview early at questions 11, 16, 21 and 26.
Completing question 45 advances immediately to 46; question 50 opens the existing
submission confirmation in FE, and confirmed final Next saves and grades immediately.
Listening lead cannot be manually skipped. These transitions replace the completed-group wait.
Premium is two assessment starts per month, supplied by Subscription/Quota policy.

## Structure and ownership

Feature root: `fit.iuh.engolearn.assessment`. Business interfaces live in `service`,
implementations in `service.impl`; Mongo predicates and mutations live in `repository`.
Schedulers are technical triggers in `infra.scheduling`, development adapters in
`infra.dev`, HTTP advice in `controller.exceptionhandler`, wiring/index creation in
`config`. Controllers use typed web DTOs and `ApiResponse`; errors use `ProblemDetail`.
No new Maven dependency or module. Pure timing/grading functions are owned feature utilities.

Root documents extend `BaseDocument`; embedded values implement `EmbeddedDocument`.
All owned non-ID fields have explicit snake_case mappings. Closed attempt/phase/unit/
section/band values use enums. Level labels remain strings from the upstream curriculum
vocabulary; paper validation restricts question levels to A1/A2/B1/B2. Public contracts
and web records are separate from persistence. Public skill collections are copied.
`AssessmentMediaImpl` deliberately converts its configured directory to a normalized Path
in its constructor; other required Spring dependencies use Lombok constructor injection.

Collections: assessment_papers, assessment_attempts, assessment_profiles,
assessment_gates, assessment_grant_revocations. Document index definitions are on the
models; `AssessmentIndexConfig` creates only feature storage and indexes. The per-user
gate serializes starts/actions with real MongoDB replica-set transactions. Retry is bounded;
operation request/command IDs make replays idempotent, including uncertain commit outcomes.
Quota consumption joins the same transaction; denial or failed attempt/profile storage rolls back.

The attempt embeds an `AssessmentPaperSnapshotVo`, never the indexed paper root document.
Question/option objects are copied. This both preserves the original review and prevents
paper-version uniqueness indexes from accidentally applying to every attempt snapshot.
History uses database pagination and projects only row metadata, without loading questions.

## API

Base `/api/v1/assessment`. All routes require the existing authenticated principal and
send `Cache-Control: no-store`. Owner lookup is mandatory; another owner receives 404.
Taking operations also require the in-memory `X-Assessment-Binding` document capability.
JSON bodies reject missing/unknown fields, numeric coercion, malformed UUIDs and bodies
above 4 KiB. This parser is limited to assessment, without changing other feature settings.

| Method / suffix | Body / purpose |
|---|---|
| GET /preparation | Baseline, independent 21-day cooldown/month quota, active conflict, levels |
| GET /sound-check | Authenticated WAV sample, no usage |
| POST /attempts | UUID requestId/binding, confirmed=true, soundChecked=true; same request replays |
| GET /attempts?page=0&size=20 | Owner terminal history; max page size 100 |
| GET /attempts/{id} | Taking state, requires binding |
| PUT /attempts/{id}/answer | question 1..50, sequence 0..JS-safe integer, choice null/0..3, UUID commandId |
| POST /attempts/{id}/next | Same answer body; final save, lock and advance atomically |
| POST /attempts/{id}/finish-stage | unitIndex 10..13, phase READING_PREVIEW, UUID commandId; binding; end the current reading preview once |
| POST /attempts/{id}/submit | confirmed=true, binding; grade at most once |
| POST /attempts/{id}/abandon | confirmed=true, owner; also usable for old active conflicts |
| POST /attempts/{id}/audio | unitIndex 0..25, UUID issueId, binding; one issuance/unit, identical retry allowed |
| POST /attempts/{id}/audio-failure | unitIndex, reason LOAD_FAILED/PLAYBACK_FAILED/AUTOPLAY_BLOCKED, binding |
| GET /attempts/{id}/result | READY only; BE sends two or five skill rows according to grants |
| GET /attempts/{id}/review | READY only; original selected answers, key, explanations and transcripts |
| GET /attempts/{id}/review/audio/{index} | READY owner only; replay permitted |

Server deadlines are authoritative, normalized to Mongo millisecond precision. Timing is
60s/single, reading preview 90/120/150/240s then shared 300s, listening passage 120s lead
then 180s if unfinished. Ending a reading preview early starts a fresh shared 300s
answer window. Listening lead still expires naturally into its existing shared 180s
answer window if unfinished. Unused preview time is discarded.
Final Next on a group advances immediately regardless of remaining lead/answer time;
confirmed final Next on question 50 saves, locks and grades immediately without a waiting screen.
Finish-stage is scoped to the exact unit/phase and idempotent command; a replay cannot
extend a deadline or end a later group's lead. Expired stages settle before commands.
No carry-forward time. Scheduler/reads/actions catch up using actual elapsed deadlines;
a late abandon cannot replace a result. No answers means NO_RESULT; saved wrong answers
can produce a valid zero/A1. Latest current level changes on valid completion, highest
never decreases, and opening results does not regrade. Answer sequences and command IDs
protect autosave/Next races; state revisions let the FE discard older responses.

The binding, answers and official audio are not persisted by the browser. A new document
cannot resume. Pagehide/SPA departure attempts authenticated keepalive abandonment;
visibility changes do not abandon. If abrupt close prevents delivery, server time runs
until automatic submission. There is no pause/resume or clock-bypass endpoint.

## Media and data lifecycle

`engolearn.assessment.media-directory` defaults to `./assessment-media`. Immutable PCM WAV
files are SHA-256 checked, <=4 MB each, <=50 seconds/single and <=120 seconds/passage;
12 distinct official paths, plus sound-check.wav. Validate all paper/media resources before
quota consumption. Normal official playback has no replay/seek/rate controls; review can replay.
Audio failure reports do not extend time, issue another attempt or return quota. Client
reports are observations, not authority for charging or refund. Ordinary browser audio
cannot enforce DRM against a modified client or external recording.

Never overwrite a published version/media file or remove assets referenced by attempts.
This feature does not implement F03 administration. Its schema is a new, versioned delivery;
older experimental camelCase assessment data must be inspected/migrated deliberately.
Do not rename/drop collections or rebuild conflicting indexes automatically. Demo v2 uses
its own new database, leaving earlier demo/team databases untouched.

## Integration and release boundaries

- New main already contains Subscription/Quota contracts. Assessment depends one-way on
  them, and never reads their collections. It does not write User or Learning Path state.
- A real Placement module must supply `AssessmentBaselineSource`. Absence is Optional.empty
  and blocks starts. `assessment_dev_baselines` is used only in `dev & !prod`; demonstration
  papers are excluded from production taking/history/review and public result consumers.
- Authentication reuses `CurrentUserProvider` and shared HTTP/Auth. The provided main has
  local fixture authentication, not a complete production sign-in mechanism. Prod disables
  fixture authentication. Supply the team's real identity integration before public release.
- Full access source currently uses policyScope + ':' + policySnapshotId + ':' + effectiveAt.
  EffectiveAt is the period start in the current provider. Grants already given persist
  after normal expiry. Trusted integration calls `AssessmentGrantRevocation` with the exact
  correlated source for a verified refund, preserving other sources and all scores.
- The supplied `feat/payment` branch includes purchase/refund/VNPay code, but is not merged
  into this main. Assessment does not merge/modify that teammate branch. Its owner must
  agree the durable refund-to-resource contract and wire the exact source revocation.
  An entitlement expiry event alone must not be treated as a refund. No public manual
  refund/revoke or fake-success endpoint is added here.
- Premium 2 is configured in the separate isolated demo. In a real environment publish
  the approved new Premium policy/offer through the existing ADMIN configuration flow,
  including its displayed benefits; retain already issued period snapshots according to
  Subscription rules. Do not edit an already published manifest ID or reset used counts.

Before release: integrate real identity/baseline/refund delivery; professionally review
exam content; configure HTTPS, Mongo replica set/transaction permissions, immutable media,
secrets, backup/restore, monitoring and retained assets; run full CI and browser acceptance
on staging. Per-second taking polling currently serializes/writes state; measure expected
concurrency and size deployment accordingly before claiming capacity or availability.

## Verification

Final command: `./mvnw verify`. Feature checks: `AssessmentRulesTest`,
`AssessmentMediaTest`, `AssessmentMongoIntegrationTest`, `ArchitectureTest`.
Mongo tests run only when ASSESSMENT_TEST_MONGO_URI points to a real replica set; they use
the dedicated engolearn_pfc_test DB and clear only test-owned assessment/subscription/quota
collections there. They verify concurrency, rollback after consumption, fifty-question
journey, binding/ownership, temporal edges, snapshots, input shapes, audio and access grants.
No fake assessment HTTP or in-memory Mongo replacement is used. Delivery verification
records actual results and unresolved full-suite failures separately from manual checks.
