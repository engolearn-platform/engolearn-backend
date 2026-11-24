# Speaking V2 data model

| Collection | Ownership and invariants |
| --- | --- |
| `topic_speaking_profiles` | Topic-owned draft and published profiles, keyed by Topic and optional Item. Promotion binds the draft to the current content release. |
| `speaking_v2_sessions` | Speaking-owned snapshot, prompt version, controller and work fences, opening claim, embedded memory and per-session watermark. A partial unique index allows one unfinished session per owner. |
| `speaking_v2_owner_slots` | One CAS reservation per owner. It persists before the first insert and transfers to the replacement after the old session ends. |
| `speaking_v2_turns` | Archived terminal turns, with unique session/sequence and session/client turn ID. The active marker in the session owns admission and in-flight mutations. Archived delivery metadata and charge state use conditional updates. |

Reply, validated optional fields, memory delta and the embedded memory watermark commit in the same session write. Terminal markers are archived by increasing revision and cleared after archival. A retry takes back the same server turn ID and sequence. Committed deltas can restore the memory cache in order. A no-op advances the watermark.

Only MediaManagement IDs and stored timestamps are persisted for audio. Sealed learner audio can attach to the active or archived turn through its evidence token. Playback checks session ownership, deletion and 30-day eligibility before requesting protected MediaManagement delivery. A deleted session is a soft tombstone and continues to reserve its original request ID.

Charge inputs persist on the archived turn before calling Quota. They record the owner, recording occurrence time, measured learner speech, capped successful processing wait and the WAV duration sent to the socket. Quota acknowledgment is independent of the conversation state. Interrupted or uncertain calls replay the same operation key and inputs.
