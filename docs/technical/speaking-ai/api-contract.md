# Speaking V2 API

See the [shared contract](../../../../docs/system-design/speaking-v2-contract.md) for request and response fields, stable codes, status values and SPK2 framing.

Topic profile read, save and publish use the Content Manager authority for Topic and Item scopes. Learner REST uses the authenticated User role and verifies ownership. Deleted sessions cannot be retrieved or replayed. History is independent of current Topic visibility and quota.

Creating a session returns the persisted INITIALIZING state and upgrade path without waiting for providers. The upgrade requires an exact configured Origin and the one-time ticket issued on the authenticated REST route. The server consumes the ticket before upgrading and binds the socket to the resulting lease. A reconnect uses a fresh ticket at the same epoch. A takeover increments the epoch. A sealed turn can still settle, and subsequent events use its original server turn ID and sequence.

The server sends `SESSION_STATE` on connection and returns it on `SYNC_SESSION`. To receive live opening audio, clients can connect while the session is INITIALIZING. Reconnect relies on saved opening and turn text with separately authorized playback of available WAV files. Browser playback is outside server delivery state.

The assessment endpoint exposes PENDING and FINALIZING with nullable scores until assessment finalization.
