# Speaking AI persistence and ownership

Speaking owns the following Mongo documents. The collection annotations and [`SpeakingMongoIndexes`](../../../src/main/java/fit/iuh/engolearn/config/SpeakingMongoIndexes.java) are declarations; they do not prove index creation on any deployed database. `BaseDocument` supplies the inherited identity/audit fields where used.

| Collection / model | Owned data and declared constraints |
| --- | --- |
| `speaking_item_configs` / `SpeakingItemConfig` | Topic item/version binding, published goals, roles/context, opening side, minimum valid turns and revision/status. A unique index is declared on `topic_item_version_id`. |
| `speaking_sessions` / `SpeakingSession` | Learner ownership, pinned content/config snapshot, lifecycle status, started/ended/deleted times, revision and controller epoch; contains opening and progress/state snapshots. A partial unique index serializes a string-valued `open_learner_id` across open sessions. |
| `speaking_turns` / `SpeakingTurn` | Session, sequence, finalized transcript, commit time, valid flag, evaluation state and AI reply. Unique `(session_id, sequence)`. |
| `speaking_assessments` / `SpeakingAssessmentRecord` | Assessment evidence and feedback associated by turn ID, session and learner, including criteria version, goals/resources/corrections/hints. The repository identifies a record by turn ID. |
| `speaking_audio_assets` / `SpeakingAudioAsset` | Session/turn association, kind and private media storage reference/metadata. Bytes are handled by shared `MediaStorage`, not stored as public content by the API. |
| `speaking_usage_segments` / `SpeakingUsageSegment` | Learner/day usage intervals used for daily usage accounting. |

Exact field names and enum values are defined in the linked model source under [`speaking/model`](../../../src/main/java/fit/iuh/engolearn/speaking/model). Do not rename collection, BSON field, DTO or enum identifiers as part of the conversation-manager refactor.

## Ownership and lifecycle

Every learner REST operation resolves the actor via `CurrentUserProvider`; repository predicates combine the actor ID with the requested session/item where needed. A session pins its content snapshot so later Topic edits do not silently rewrite in-progress context. Topic is queried through the `SpeakingItemLookup` contract to establish that an item can be opened; Speaking does not own Topic item data. Media storage is likewise behind `MediaStorage` and playback is served only after session/turn/learner ownership validation.

Session start uses the create request ID as the session ID and pins the item/config version. Only one active/interrupted open session is admitted per learner by service checks plus the partial unique index. Turns persist their sequence, finalized transcript and evaluation state. In manager mode the validated draft and decision drive separate writes to the assessment, turn and session records. The current implementation has no durable transcript blob or transaction covering all these writes. The [business mapping](../../features/speaking-with-ai/README.md) records closing and recovery gaps.

Delete operations set `deleted_at` after an open session has settled. History and owner lookups exclude soft-deleted sessions; the implementation does not cascade a physical delete. A learner recording's 30-day playback deadline and the signed URL's 60-second lifetime are access limits, not Mongo or Cloudinary deletion schedules.

The controller ticket is an in-memory, short-lived capability bound to learner, session and epoch; it is not a persisted auth token. A newly accepted controller fences the older one. Session revision/epoch checks protect writes from stale tabs. These guards are part of the lifecycle contract even though the ticket itself is not a Mongo document.

## Neighbor-owned data

| Owner | Speaking boundary |
| --- | --- |
| Topic | `SpeakingItemLookup.findOpenableItem(itemId, learnerId)` supplies item facts; Topic persistence remains Topic-owned. |
| Media | `MediaStorage` uploads private audio and issues short-lived private URLs; underlying media storage remains Media-owned. |
| Speech providers | STT/TTS/evaluation providers are called through ports; provider credentials and external data are not Speaking-owned Mongo collections. |

No claim is made that externally configured Mongo indexes, storage credentials, provider accounts or production cleanup jobs are present or healthy.
