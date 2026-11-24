# Speaking V2 API

See the [shared contract](../../../../docs/system-design/speaking-v2-contract.md) for request and response fields, stable codes, status values and WebSocket framing.

Topic profile read, save and publish use the same Content Manager authority for Topic and Item scopes. Learner REST uses the existing authenticated User role and identity. Requests for another owner's session are denied. Deleted sessions cannot be retrieved or replayed. History and stored assessments are independent of current Topic visibility and quota.

Session creation returns the persisted INITIALIZING session and a WebSocket path without waiting for provider work. P1 covers issue and consumption of tickets as durable control primitives. WebSocket upgrade, opening dispatch and provider orchestration are scheduled for P2. The assessment endpoint exposes PENDING and FINALIZING with nullable scores until P3 adds finalized results.
