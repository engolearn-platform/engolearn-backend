# Speaking V2 API

See the [shared contract](../../../../docs/system-design/speaking-v2-contract.md) for request and response fields, stable codes, status values and SPK2 framing.

Topic profile read, save and publish use the Content Manager authority for Topic and Item scopes. Learner REST uses the authenticated User role and verifies ownership. Deleted sessions cannot be retrieved or replayed. History is independent of current Topic visibility and quota.

## Speaking profile authoring

GET and PUT use `/api/management/topics/{topicId}/speaking-profile` or `/api/management/topics/{topicId}/items/{itemId}/speaking-profile`. POST appends `/publish`. The GET projection includes the write revision, draft, stored publication, Speaking publication version, optional source defaults, owning Item groups and resource options. An unreleased scope may have a draft and source defaults. Publish requires an existing release and an explicitly saved draft. An enabled profile is validated against the current exact catalog. Validation failures include field paths and stable reason codes. A stale write revision returns `REQUEST_CONFLICT`.

Resource IDs identify explicit Item occurrences. Authoring uses the saved working Item version, including DRAFT, within the exact scope. An existing selection remains checked if its canonical occurrence still exists. Removed or replaced occurrences stay in the draft as stale references until the editor removes or replaces them. Publish validation and learner admission use the current published catalog, and the learner snapshot deduplicates equivalent text and meaning. At publication and new-session admission, Topic checks the serialized static payload against the application envelope configured by `speaking.context.total-limit-bytes` (768 KiB default, with 256 KiB held for dynamic conversation and generation). A newer learning release can supply current resources to an existing published profile. Missing occurrences return `PROFILE_RESOURCES_STALE` at admission, and over-budget profiles return `CONTEXT_BUDGET_EXCEEDED`. Existing sessions continue to read their saved snapshots.

Only the Speaking profile document is written by these management operations. Topic Learning readiness evaluates its own content and never promotes a Speaking draft.

## Learner admission

New sessions, replacements and Practice Again check the authoritative Topic CEFR against the learner's current CEFR. A published Topic can be practiced before the learner starts Topic Learning. An Item also has to be visible under its parent and accessible under Topic Learning's sequence rules. Availability reports `LEVEL_TOO_HIGH` or `ITEM_LOCKED` for the corresponding admission failure. Create and replace return the same reason as a 403 code. An explicit resume of an unfinished owned session still uses its pinned snapshot and existing controller checks.

## History

| GET under `/api/v1/speaking` | Returned data |
| --- | --- |
| `/sessions` | Existing session page across all owned visible history. An optional `scopeType`, `topicId` and (for TOPIC_ITEM only) `topicItemId` select exact scope. |
| `/history/topics?group=IN_PROGRESS\|COMPLETED\|OTHER` | Topic summaries and full distinct-Topic totals in `totals.inProgress`, `totals.completed` and `totals.other`. Each row carries `topicId`, `title`, `level`, `coverUrl`, `learningStatus`, `learningProgress`, `sessionCount` and `latestSessionCreatedAt`. |
| `/history/topics/{topicId}/sessions` | Summary page across Topic and Item sessions for one parent. The response also includes `topicId`, `title` and `sessionCount`. Each row carries `sessionId`, `scope`, `context` with the pinned titles, `createdAt`, `committedTurnCount`, `speechDurationMs`, `status` and `assessmentStatus`. |

All pages use `items`, `page`, `size` and `hasNext`, with page 0 and size 20 by default. Size ranges from 1 to 100. Invalid scope filters and mixed history requests return 400. The grouped and parent routes accept only their listed query parameters, once each. A parent page past the end retains the header and full count. Session order is newest creation time first and ID descending on ties. Topic order is latest own visible session first and Topic ID ascending on ties.

Topic Learning classifies summaries from the current learner generation. NOT_STARTED and UNKNOWN map to OTHER. Hidden content with established learning status can retain that status. Current title, level, cover and progress require published Topic metadata. Saved parent title and level provide fallback display data. The parent header uses the latest available saved parent title if the published title is unavailable. An owner with zero sessions for that parent receives an empty list, zero count and null title. If grouped metadata cannot be read, the grouped route returns a retryable `503 HISTORY_UNAVAILABLE`. Recent, exact and parent history can still read the learner's saved sessions.

## Session runtime

Creating a session returns the persisted INITIALIZING state and upgrade path without waiting for providers. The upgrade requires an exact configured Origin and the one-time ticket issued on the authenticated REST route. The server consumes the ticket before upgrading and binds the socket to the resulting lease. A reconnect uses a fresh ticket at the same epoch. A takeover increments the epoch. A sealed turn can still settle, and subsequent events use its original server turn ID and sequence.

The server sends `SESSION_STATE` on connection and returns it on `SYNC_SESSION`. To receive live opening audio, clients can connect while the session is INITIALIZING. Reconnect relies on saved opening and turn text with separately authorized playback of available WAV files. Browser playback is outside server delivery state.

The assessment endpoint exposes PENDING and FINALIZING with nullable scores until assessment finalization. READY has all four criteria assessed, PARTIAL has one to three, and UNAVAILABLE has none. Criterion status and reason distinguish insufficient evidence from technical failure. Session history reports the stored assessment status and the owner can open the result without a socket.

## Speaking quota contract

Speaking uses the account-level `SPEAKING_DURATION` allowance for both Topic and Topic Item. The client reads `GET /api/v1/quota/me/usage` and selects the daily item in seconds. The Quota Engine supplies the effective limit, used seconds, remaining seconds, status and window end. The account Speaking History view is the sole Speaking surface for the quota card.

| Code | Where it is returned | Client behavior |
| --- | --- | --- |
| `DAILY_QUOTA_REACHED` | Availability denial, HTTP 429 for a rejected interactive operation, or WebSocket error/terminal session reason | Prevent new audio, follow the existing close/result flow for an exhausted session, and keep history accessible. |
| `QUOTA_NOT_CONFIGURED` | HTTP 503 or WebSocket error when the effective policy lacks the Speaking rule | Show a configuration-specific message and prevent new interaction. |
| `QUOTA_UNAVAILABLE` | HTTP 503 or WebSocket error when Quota availability cannot be read | Show a retryable availability error and prevent new interaction. |
| `QUOTA_SETTLEMENT_PENDING` | Retryable WebSocket error for `START_TURN` while an earlier account obligation is unresolved | Wait for settlement before trying a new turn. |

A reconnect to a closing session may observe its result. Manual end and authorized history, result and deletion operations remain available. The learner Subscription plan response can include nullable `speakingDurationDailyLimitSeconds` from the published plan rule. History offers an upgrade when an on-sale billable option has a higher allowance and links to the existing `/subscription` route.
