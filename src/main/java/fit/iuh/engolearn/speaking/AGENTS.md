# Speaking V2 agent guide

Follow the [backend agent instructions](../../../../../../../AGENTS.md), the [Speaking backend entry](../../../../../../../docs/technical/speaking-ai/README.md), and the accepted [shared contract](../../../../../../../../docs/system-design/speaking-v2-contract.md). Use the module map and runtime flow linked from the backend entry before editing.

Topic owns profile authoring and learner access. Speaking owns sessions, turns, realtime orchestration and assessments. Quota owns daily usage policy, and MediaManagement owns storage and delivery. Update the owning documentation with behavior and persistence changes. Verify Mongo fences on a disposable database.
