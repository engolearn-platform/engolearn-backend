# Speaking V2 backend

The [shared Speaking V2 contract](../../../../docs/system-design/speaking-v2-contract.md) governs authoring, learner REST, realtime commands and assessment. The backend implements Topic profiles, immutable snapshots, opening and turns, fenced WebSocket control, conversation memory, protected audio and measured quota. Assessment finalization uses the independent handoff described in the runtime flows.

| Page | Contents |
| --- | --- |
| [Module map](module-map.md) | Source ownership and tests |
| [Runtime flows](runtime-flows.md) | Opening, streaming, settlement, billing and recovery |
| [Module contract](module-contract.md) | Dependencies and runtime settings |
| [REST and WebSocket contract](api-contract.md) | Shared wire contract and security |
| [Data model](data-model.md) | Collections, indexes, updates and retention |
