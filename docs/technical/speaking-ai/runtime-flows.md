# Speaking V2 foundation runtime

1. Availability checks Topic's learner gate and the released profile, then reads Quota. It includes the unfinished session if present.
2. Create validates scope and admission, pins a snapshot and reserves an owner slot before inserting the INITIALIZING session. Replay returns the same session. Replacement records the new snapshot and request ID on the old session in the same write that starts closing. The owner slot transfers only after ENDED.
3. P2 completes the opening asynchronously with `SpeakingRuntime.completeOpening`. This CAS activates the session and persists the opening independently from turn counts.
4. Issue a one-use socket ticket and atomically exchange it for a lease through `consumeTicket`. Socket commands use that lease. Takeover advances the controller epoch and clears the old ticket and connection. A sealed turn retains its work token so it remains settleable by the asynchronous worker.
5. `admit` reserves the next turn sequence. `seal` transitions it to TRANSCRIBING and pins recording metadata and media ID. `transcript` stores original speech text before generation. `settle` commits terminal state, then archives it. Retry uses the same ID and original transcript.
6. Closing blocks new admission and sets the cutoff. Receiving audio is discarded. A sealed turn can finish before the cutoff, after which late work is fenced. Recovery expires elapsed sessions, archives terminal markers, settles old closing sessions, and reconciles pending owner reservations.
7. Assessment remains independent of session end. P3 will consume durable FINALIZING state and persist a single finalized result.

P2 owns provider job dispatch, WebSocket framing, memory, actual media acquisition and Quota charge recording. Session and turn state transitions continue to use the P1 fences.
