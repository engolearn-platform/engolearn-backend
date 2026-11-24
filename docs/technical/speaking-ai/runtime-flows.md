# Speaking AI runtime flows

These flows describe the Speaking runtime shared by `dev` and `prod`. Use the [module map](module-map.md) to locate source and tests, the [API contract](api-contract.md) for wire details, the [data model](data-model.md) for persisted state, and the [module contract](module-contract.md) for provider boundaries.

## 1. Realtime speaking turn

**Purpose.** Commit a learner's finalized speech, assess it, and deliver the result and any reply audio. The entry point is `SpeakingSocketHandler` on an established Speaking socket.

1. `START_TURN` checks the controller, session limits, opening state and connection's busy flag. `SpeakingRealtimeService` starts a turn on the existing `SpeechStreamPort` stream. It buffers bounded PCM and forwards frames to AssemblyAI. `INTERIM_TRANSCRIPT` is provisional.
2. `END_OF_TURN` waits for the adapter's finalized text. `AssemblyAiSpeechStream` uses an already finalized result when available or requests an endpoint and waits up to the configured finalization timeout (six seconds by default). STT failure produces `TURN_FAILED`. A blank final produces `TURN_EMPTY` without committing a turn. `CANCEL_TURN` discards an active recording before end processing starts.
3. The realtime service rechecks the current socket and epoch before calling `SpeakingSessionService.commit`. The session service writes the nonblank transcript as `PENDING`, advances the committed count and rejects a new commit while another evaluation is pending. Realtime records learner usage and emits `TURN_ACCEPTED` before evaluation.
4. `SpeakingConversationComposer` builds the request from pinned content and recent successful exchanges. `GeminiSpeakingAiGateway` sends the Prompt V2 request, `SpeakingDraftValidator` produces a validated proposal, and `ConversationManager` decides the allowed dialogue and projected state. `SpeakingSessionService.settleConversationTurn` rechecks the epoch, turn state and counters before persisting.
5. Realtime delivers a result snapshot (`TURN_RESULT` or `PROCESSING_ERROR`) to the eligible controller, then attempts the learner recording upload. If the managed decision authorizes a nonblank reply and the session still permits delivery, Cartesia synthesizes it and realtime emits `AI_AUDIO_READY` after saving audio metadata. `AUDIO_ERROR` and `TTS_ERROR` report audio failures separately from turn settlement. A terminal or fenced result does not trigger reply synthesis.

Start with the [realtime and evidence tasks](module-map.md#task-locator) for code and tests. See the [socket contract](api-contract.md#socket-handshake-and-messages) for event payloads.

## 2. Opening turn

**Purpose.** Provide opening guidance from the pinned session content and, when AI opens, generate the first line and optional audio. Entry points are create/replace and opening retry on `SpeakingController`.

1. `SpeakingSessionService.create` pins the Topic item version and published Speaking config. A learner-initiated opening starts at `NOT_REQUIRED`, so the learner can start a turn after connection and normal admission checks.
2. An AI-initiated opening starts at `GENERATING`. After saving the new session, the session service publishes `OpeningRequested`. `SpeakingOpeningService` calls `GeminiSpeakingOpeningGenerator` asynchronously for a reply and English hint. Missing output, provider failure or its 10-second generation-stage timeout moves the opening to `FAILED`.
3. The session service persists the reply and hint as `SYNTHESIZING` before Cartesia and private media storage run. Settled audio metadata yields `READY`. Failed or timed-out audio yields `TEXT_ONLY` and preserves the text. The opening audio stage has a 20-second budget for TTS and storage. These stage budgets remain distinct from the configured provider HTTP timeouts. For AI openings, only `READY` and `TEXT_ONLY` allow learner turns.
4. Retry requires an active, non-closing session, the current epoch, quota and a failed opening. The service deduplicates retry request IDs and starts a fresh attempt. Attempt ID, expiry and session checks fence stale callbacks. Closing cancels an opening that still blocks turns. Persisted changes can be delivered as `OPENING_UPDATED`; a later snapshot also exposes the opening state.

Openings and hints do not create assessed turns. See the [opening routes](api-contract.md#rest-routes) and [opening state and playback](data-model.md#snapshots-and-audio).

## 3. Session completion

**Purpose.** Stop interaction, settle eligible pending work and persist the final status and reason. Entry points include REST end, managed dialogue decisions, session limits, repeated evaluation failures, replacement, deletion and startup recovery.

1. `ConversationStateView` distinguishes the required-goal milestone, completion eligibility and automatic ending. For current `REQUIRED_GOALS_V2` snapshots, required goals plus the minimum valid turns qualify for `COMPLETED` when ending. Automatic ending also requires all optional goals. Historical ratio-based snapshots keep their pinned policy. A spoken goodbye may produce a short reply while the session stays open; explicit control comes from the REST end action.
2. `SpeakingSessionService.finish` preserves the first closing reason and time, blocks new turns and cancels a blocking opening. The 30th committed turn starts turn-limit closing at commit. `ACTIVE` or `INTERRUPTED` remains the persisted status during closing.
3. If no turn is `PENDING`, `finalizeClosing` can finish immediately. Otherwise it waits up to ten seconds from the first close. Managed service-level settlement allows `LEARNER_ENDED` and `TURN_LIMIT` inside that window, but the current realtime evaluator also requires `closingAt == null` before and after composition. This can skip the 30th turn's evaluation or drop an in-flight result after an explicit end.
4. `finalizeClosing` uses recorded progress to set `COMPLETED` or `INCOMPLETE`, clears the open-learner key and publishes `SessionEndedEvent`. Realtime can deliver a terminal snapshot as `SESSION_ENDED`. Pending turns at the deadline remain `PENDING` in this implementation. The managed settlement path rejects results for terminal sessions.

The [business mapping](../../features/speaking-with-ai/README.md#f01--spk-uc-001004) records the BR-F1.23 gap. In `SpeakingConversationRuntimeTest`, distinguish direct session-service settlement tests from the realtime controller and delivery tests.

## 4. Connection recovery

**Purpose.** Restore persisted state to the current controller and prevent obsolete sockets or pending callbacks from changing it. Entry points are socket close/open, REST resume and startup recovery.

1. When the current socket closes, `SpeakingRealtimeService.close` removes it and closes the STT stream if no active peer remains. After ten seconds without an active socket, it asks `SpeakingSessionService.interruptIfDisconnected` to move the same learner's `ACTIVE` session to `INTERRUPTED` if the epoch still matches. Loss of a socket does not call REST end.
2. A reconnect can request a fresh ticket for the same epoch. After interruption, REST resume restores `ACTIVE`, clears the disconnect time and advances `controllerEpoch`, with replay protection for the latest resume request ID. The new socket consumes a ticket and opens STT before it receives `CONNECTED` with the persisted snapshot.
3. `SpeakingRealtimeService.open` fences the previous socket when a replacement is installed, even at the same epoch. It sends `CONTROLLER_CHANGED` to that socket when possible. Socket activity checks current connection identity and the persisted epoch. Closing or replacing a controller attempts to recover its in-flight managed turn. If the turn was committed and is still `PENDING` on an open session, the session service can mark it `FAILED` without adding evidence or increasing the evaluation-failure counter. Uncommitted PCM and interim text are connection-local and must be spoken again.
4. With recovery enabled, application startup sends open sessions through ordinary closing with `SERVICE_RESTART` unless already closing. The expiry sweep closes aged sessions. Recovery uses persisted snapshots, while current sockets, tickets and locks live in the backend process. Startup does not replay provider work.

See [ticket and event semantics](api-contract.md#socket-handshake-and-messages), [snapshot ownership](data-model.md#snapshots-and-audio) and [runtime settings](module-contract.md#runtime-configuration).
