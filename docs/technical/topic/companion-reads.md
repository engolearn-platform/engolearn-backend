# Topic Companion current-state reads (MVP option A)

The owning module exposes `topic.contract.TopicCompanionReads` with trusted caller identity; these are not HTTP endpoints. Companion must never source the learner ID from model arguments.

| Method | Result |
| --- | --- |
| `discover(String learnerId, TopicDiscoveryQueryDto query)` | `TopicDiscoveryPageDto`: eligible not-started, not-owned published topics in catalog order, with at most one eligible first item. Keyword is literal title search; omitted level uses learner CEFR, explicit level does not bypass eligibility. |
| `resume(String learnerId, TopicPageQueryDto query)` | `TopicResumePageDto`: current in-progress topics in access-time order (null last, topic ID tie-break), progress, optional verified normal learning attempt, optional eligible unstarted item. No saved position or retry attempt is exposed. |
| `counts(String learnerId)` | `TopicCompletionCountsDto`: separate completed topics and completed visible items (including incomplete topics), scope CURRENT_VISIBLE_CURRENT_GENERATION; no lifetime or aggregate total. |

Queries carry a limit of 1–5 and optional opaque cursor of at most 512 characters. Discovery query also carries optional keyword (trimmed 1–80 characters) and CEFR enum. Pagination revalidates the owner, mode, level and filter and requires the cursor anchor to remain eligible. Cursor is not authorization. Catalog publication/manifest and learner state are checked on each invocation. Invalid owner input, null query, invalid limit/keyword/cursor, malformed cursor, mismatched owner/mode/filter/level or missing eligible anchor raise the provider-owned `topic.contract.exception.TopicCompanionInvalidQueryException`; Companion maps only this typed failure to INVALID_ARGUMENTS. Database or inconsistent-state failures remain distinct errors, never invalid arguments, zero or no data. Existing HTTP browse exception behavior is unchanged. Missing learner profile is rejected by existing learner policy.

Only metadata, identifiers, current visible progress and recorded topic access are returned. The provider does not call media delivery, start, reconcile or update state. Action availability is read-time evidence and the app must revalidate before an action. Titles and descriptions are clipped to 160 and 240 UTF-16 units respectively, preserving surrogate pairs; S4 must enforce the 12,000 UTF-8 byte envelope and safe error mapping.

Option A deliberately reads the existing full matching catalog and scoped owner history, not a bounded DB query or transactional snapshot. COMPLETE means the observed query population was resolved, not a consistency guarantee across concurrent changes or pages. No scalability/latency guarantee; retain the existing tool timeout. Recent activity and exact saved position are deferred; S4 must not register RECENT as supported or invent a position. G4 query/index design remains deferred technical debt.
