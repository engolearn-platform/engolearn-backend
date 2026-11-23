# Speaking AI backend technical entry

These pages describe the implementation shared by backend runtime profiles `dev` and `prod`. Start with the module map to find code and tests by task, or read the runtime flows to follow a request across components.

| Page | Use it for |
| --- | --- |
| [Module map](module-map.md) | Component ownership, task entry points and test locations |
| [Runtime flows](runtime-flows.md) | Realtime turns, opening, completion and connection recovery |
| [Module contract](module-contract.md) | Integration boundaries, managed-only flow, providers, runtime configuration and local setup |
| [REST and WebSocket contract](api-contract.md) | Routes, DTOs, handshake, events and errors |
| [Data model](data-model.md) | Mongo ownership, lifecycle states, snapshots and audio retention |
| [Business mapping](../../features/speaking-with-ai/README.md) | SPK-UC-001–010 coverage and gaps against the business specification |

The [shared opening contract](../../../../docs/system-design/speaking-session-opening-contract.md) and [v0.8.2](../../../../docs/system-design/speaking-demo-contract-v0.8.2-addendum.md) and [v0.8.3](../../../../docs/system-design/speaking-demo-contract-v0.8.3-addendum.md) addenda record accepted dev/demo design. The addenda distinguish contract acceptance from runtime rollout. See the [frontend handoff](../../../../engolearn-frontend/docs/features/speaking-with-ai/README.md) for client behavior.
