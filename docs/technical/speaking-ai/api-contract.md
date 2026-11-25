# Speaking V2 API

See the [shared contract](../../../../docs/system-design/speaking-v2-contract.md) for request and response fields, stable codes, status values and SPK2 framing.

Topic profile read, save and publish use the Content Manager authority for Topic and Item scopes. Learner REST uses the authenticated User role and verifies ownership. Deleted sessions cannot be retrieved or replayed. History is independent of current Topic visibility and quota.

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
