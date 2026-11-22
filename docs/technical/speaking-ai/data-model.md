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

Exact BSON field names and enum values live under [`speaking/model`](../../../src/main/java/fit/iuh/engolearn/speaking/model). Collection, BSON and enum identifiers are persistence compatibility surface.

## Ownership and lifecycle

Every learner REST operation resolves the actor via `CurrentUserProvider`. `SpeakingSessionService` enforces learner ownership through repository filters or explicit checks on the loaded record. Session start uses the create request ID as the session ID and pins the item/config version, goals, completion policy, context, roles and resources. Later Topic or configuration edits leave existing snapshots intact.

- [`SpeakingSession`](../../../src/main/java/fit/iuh/engolearn/speaking/model/SpeakingSession.java) is open in `ACTIVE` and `INTERRUPTED`. `closing_at` and `closing_reason` track shutdown within an open state. `COMPLETED` and `INCOMPLETE` are terminal and store `end_reason` separately. Service checks and the partial unique index admit one open session per learner.
- [`SpeakingTurn`](../../../src/main/java/fit/iuh/engolearn/speaking/model/SpeakingTurn.java) starts `PENDING` with the finalized transcript and sequence. Settlement can write `SUCCEEDED`, `INVALID` or `FAILED`. At the closing deadline a remaining `PENDING` turn stays in that state. `UNRESOLVED` is part of the accepted design described in the [business mapping](../../features/speaking-with-ai/README.md).
- Managed settlement writes the assessment, turn and session as separate records. The turn stores the transcript directly. The current implementation has no durable payload-blob path or transaction covering all these writes.

## Snapshots and audio

The session service projects [`SpeakingSessionResponse`](../../../src/main/java/fit/iuh/engolearn/speaking/dto/response/SpeakingSessionResponse.java) from the persisted session, ordered turns, assessments and audio metadata. This response supplies REST detail and a reconnect snapshot. `revision` is a Mongo optimistic version and `controllerEpoch` tracks control changes. Tickets, current sockets, uncommitted PCM and interim transcripts are held in memory. See [connection recovery](runtime-flows.md#4-connection-recovery).

The opening state lives on the session. Opening audio uses a session-level `AI_OPENING` asset, while `LEARNER_RECORDING` and `AI_REPLY` assets attach to turns. Learner recording and opening playback enforce a 30-day window. The turn's AI reply playback path checks ownership and asset presence without that expiry check. All playback URLs are signed for 60 seconds. These are access limits. Delete operations set `deleted_at` after an open session settles and exclude the session from history and owner lookups. Soft deletion and playback expiry do not physically delete the Mongo documents or Cloudinary files.

## Neighbor-owned data

| Owner | Speaking boundary |
| --- | --- |
| Topic | `SpeakingItemLookup.findOpenableItem(itemId, learnerId)` supplies item facts; Topic persistence remains Topic-owned. |
| Media | `MediaStorage` uploads private audio and issues short-lived private URLs; underlying media storage remains Media-owned. |
| Speech providers | STT/TTS/evaluation providers are called through ports; provider credentials and external data are not Speaking-owned Mongo collections. |

No claim is made that externally configured Mongo indexes, storage credentials, provider accounts or production cleanup jobs are present or healthy.
