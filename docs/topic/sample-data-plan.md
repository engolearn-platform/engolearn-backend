# Artifact bổ sung dữ liệu mẫu Topic Learning

Status: planning only, approved scope; no import script or DB writes have been executed by this artifact. This is stored sample content served by the real backend, not a fixture endpoint or a mocked FE response.

## Reader and purpose

For the data author (a dedicated subagent may prepare files if the implementation agent chooses). Read business F02/F03/F04 + F07 publish rules, topic-learning-schema-v1.json and learner-runtime-mapping.md in the business repo; read BE model/VOs and current admin publish serialization. Follow [completion design](../../openspec/changes/complete-topic-learning-flow/design.md) for real persistence mapping. Do not import illustrative JSON directly as production-shaped documents.

## Deliverables to create during implementation

Proposed paths, not files that exist yet:

- `tools/topic-sample-data/import.mjs`: CLI runner with dry-run default and explicit apply; use installed tooling/native Mongo driver already available, no new Maven/app dependency.
- `tools/topic-sample-data/content.json`: deterministic, human-readable authored content with English/Vietnamese text; field mapping performed by the runner, no placeholders like word1 or lorem ipsum.
- `tools/topic-sample-data/manifest.json`: namespace, pack revision, content hash, deterministic IDs, expected counts and scenario coverage.
- `tools/topic-sample-data/README.md`: prerequisite/connection instructions, exact dry-run/apply/verify commands and output, how to select a stored learner profile and inspect topics through FE3000/proxy.
- A short generation/import report distinguishing offline validation from DB verification. Do not claim import succeeded merely because files were generated.

CLI contract: `node tools/topic-sample-data/import.mjs --database <explicit-db> --dry-run` plans only; `--apply` opts into import; `--verify` reads the exact manifest IDs/counts. Read URI/credentials from an explicit environment variable such as TOPIC_SAMPLE_MONGO_URI, never command-line credentials or committed .env. Refuse an omitted DB, known production target or an unexpected existing namespace collision. Do not print secrets.

## Minimum content pack

Use namespace `engolearn-topic-learning-sample-v1`, deterministic 24-hex IDs checked for collision, and clearly label sample topics in their descriptions/manifest. Content should be coherent enough to assess answer meanings, not merely shape-valid.

| Content | Quantity and validation |
|---|---|
| Published topics | One A2 and one B1, with distinct names and complete release manifests |
| Visible items | Five per topic, ordered, each with a real Vietnamese scenario/title and duration |
| Vocabulary | Three per item; English word/IPA and Vietnamese meaning; optional quickCheck only where correctly authored |
| Expressions | Two purposes per item, each with two examples and Vietnamese usage notes, labels on each example; store two groups with one purpose per group (two examples per group) so each group stays within the 1–3-pattern limit to verify flattened navigation |
| FILL_BLANK | Three per item, exactly one blank, four valid bank entries including one correct answer, complete sentence/translation/explanation/grammar note |
| SITUATIONAL_CHOICE | Three per item, Vietnamese scenario, exactly four English choices with one unambiguous correct response and Vietnamese context/politeness explanation |
| Version scenario | Prepare a separate MAJOR-v2 payload for one item, not automatically publish it; include instructions for publish after an actual completed attempt to assess new-content behavior |
| Hide scenario | After core import, optionally add a sixth visible item through the actual admin publish flow, then hide one so at least five remain; do not bypass publish minimums or modify pinned content directly |

The core pack has 10 items and 60 formal quiz questions. A sixth item is optional, separately identified; do not count it as core. Current business requires vocab UK/US audio when publishing. Use genuine available project media, or explicitly list missing required media as a content blocker; do not invent URLs or silently weaken validation. Formal quiz audio/images are optional and may be omitted; UI hides absent controls. Do not auto-download media or assume permissions to reuse arbitrary remote audio.

Create local profiles A1/A2/B1 through the actual local-profile API if needed, reuse existing named profiles by manifest ID on rerun and list returned IDs. Profile creation/selection is a real DB operation and belongs to explicit execution instructions. Do not generate completed progress, quiz submissions, scores, admin users or auth tokens. Completion/retry scenarios must be reached by real learning API actions, not preloaded result documents.

## Import invariants

1. Default dry-run reads/plans only. Apply writes only namespaced content/profile records identified by manifest; no drop/delete/reset, no upsert of arbitrary existing topic IDs.
2. Prevalidate all counts, translations, IDs, one-correct-answer constraints, release membership/version references and storage mapping before writing.
3. Prefer existing admin create/publish APIs if they support the real content schema. If direct Mongo import is necessary, use identical persisted shape, stage draft documents first, verify references/counts, publish the release pointer last. A partially prepared pack must not become learner-visible.
4. Rerunning the same revision/hash produces no duplicate topic/item/version/profile. An ID already holding different data causes a conflict report and no overwrite. A changed pack uses a new revision/version; never edits a version referenced by a learner attempt.
5. No direct writes to topic_learner_runtime or user_topic_* progress/attempt collections. Profiles only through their supported setup route. No mutation of the user's existing O san bay attempt or existing content.
6. Interrupted import is resumable from manifest-owned staged records. Do not promise multi-document atomic import on standalone; validate all owned records before switching the public pointer. No automatic cleanup deletion.
7. Verify via real BE GET topics/detail and one owned attempt content/current-quiz when the implementation supports them. Log topic/item IDs, counts and response statuses; redact URI/credentials.

## Coverage and bounded verification

- A1 viewer cannot start A2; A2 can start A2 but not B1; B1 can start both. These are stored profiles, not header/client overrides.
- One valid item allows wrong -> reload -> correct, replay, final completion, result and next-item unlock.
- One topic allows true full-topic completion/retry. Do not run it repeatedly just to expand test output.
- Hidden/version changes use admin mutation in an explicit manual scenario after the core flow; script does not automatically hide or update an item being learned.

## Done criteria

Files and commands exist; offline validation passes; dry-run prints exact intended IDs/counts; optional explicit import/rerun/verify observations are reported honestly. The implementation agent hands the script to the user for import by default. Preparing this artifact does not authorize silent import into an arbitrary database.
