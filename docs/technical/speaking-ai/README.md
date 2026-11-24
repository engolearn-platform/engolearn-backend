# Speaking V2 backend

The [shared Speaking V2 contract](../../../../docs/system-design/speaking-v2-contract.md) governs authoring, learner REST, realtime commands and assessment. The backend foundation implements Topic profiles, learner access, immutable scope snapshots, session and turn persistence, control lease primitives and history. Realtime provider orchestration and asynchronous assessment are the next implementation phases.

| Page | Contents |
| --- | --- |
| [Module map](module-map.md) | Source ownership and tests |
| [Runtime flows](runtime-flows.md) | Creation, replacement, control and turn settlement |
| [Module contract](module-contract.md) | Dependencies and runtime settings |
| [REST and WebSocket contract](api-contract.md) | Shared data and backend delivery status |
| [Data model](data-model.md) | Collections, indexes, updates and retention |
