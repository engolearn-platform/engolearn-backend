# Speaking V2 data model

| Collection | Ownership and invariants |
| --- | --- |
| `topic_speaking_profiles` | Topic-owned draft and published profiles, keyed by Topic and optional Item. Profile edits advance a dedicated revision. The promotion binds the draft to its current content release. |
| `speaking_v2_sessions` | Speaking-owned snapshot, lifecycle versions, controller and work fences, and history summary. Unique owner and request ID. A partial unique index on unfinished owner enforces one unfinished session. |
| `speaking_v2_owner_slots` | One CAS reservation per owner. The reservation persists before the first insert and transfers to the replacement after the old session ends. |
| `speaking_v2_turns` | Archived terminal learner turns. Unique session and sequence, plus unique session and client turn ID. An embedded marker in the session owns admission and in-flight mutations. |

Mongo writes use optimistic versions or exact transition predicates. Terminal turn markers are archived by increasing revision and cleared only after a successful archive write. A retry takes back the same turn ID and sequence. DELETED history is a soft tombstone and continues to reserve the original request ID.

Only MediaManagement IDs and stored timestamps are persisted for audio. Audio playback checks session ownership and deletion before requesting MediaManagement delivery. A reference expires after 30 days. P1 data uses fresh V2 collections. Historical collections remain untouched.
