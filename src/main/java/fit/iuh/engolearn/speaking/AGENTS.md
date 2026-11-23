# Speaking with AI agent guide

Speaking owns practice sessions, realtime turns, assessments, opening state,
daily Speaking usage and audio metadata. Topic supplies openable item/version
data, and shared Media handles private audio storage. Backend configuration
wires the WebSocket and required managed conversation flow.
Apply the [backend agent instructions](../../../../../../../AGENTS.md).

## Find the relevant code

1. Start with the [module map][map]. Use the Task Locator to find the owning
   component, related boundaries and focused tests for the task.
2. Read the applicable [runtime flow][flows]. Trace its entry point through
   the owner and persistence/delivery boundaries. Conversation evaluation uses
   the managed composer, Gemini gateway, draft validator and conversation manager.
3. Open only the contracts that govern the change.
   - [Technical index][technical] for the documentation entry points.
   - [REST and WebSocket contract][api] for DTOs, socket events and errors.
   - [Data model][data] for state, snapshots, repositories and playback.
   - [Module contract][contract] for dependencies, providers and runtime configuration.
   - [Business mapping][business] for use cases, accepted design and known
     differences. Follow its links to the authoritative business specification.
4. In an indexed checkout, follow the backend CodeGraph policy. Query the entry
   symbol and its callers/callees first, then inspect the specific branches and
   tests. Without an index, start with the linked source and use targeted `rg`.

## Maintain the owning page

Update the relevant page in the same task when a documented responsibility,
behavior, flow or contract changes. Internal edits that preserve these do not
require documentation changes. Link to the owning page for shared details.

| Change | Where to document it |
| --- | --- |
| Component responsibility or source location | [Module map][map] |
| Runtime stages, ordering or invariants | [Runtime flows][flows] |
| REST or WebSocket contract | [REST and WebSocket contract][api] |
| Persistence model or snapshot | [Data model][data] |
| Runtime configuration or module boundary | [Module contract][contract] |
| Business-to-implementation coverage | [Business mapping][business] |

Keep current behavior distinct from accepted design and unimplemented
requirements. Record material discrepancies on the owning page and retain
the applicable business rule or design reference.

## Verify

- For docs-only changes, check local links, source paths, referenced symbols
  and examples against the current checkout. Read the tests relevant to the
  behavior described and report the checks performed.
- For application changes, use the focused tests in the [module map][map] and
  follow the backend [Definition of Done][done] for change-level verification.

[technical]: ../../../../../../../docs/technical/speaking-ai/README.md
[map]: ../../../../../../../docs/technical/speaking-ai/module-map.md
[flows]: ../../../../../../../docs/technical/speaking-ai/runtime-flows.md
[api]: ../../../../../../../docs/technical/speaking-ai/api-contract.md
[data]: ../../../../../../../docs/technical/speaking-ai/data-model.md
[contract]: ../../../../../../../docs/technical/speaking-ai/module-contract.md
[business]: ../../../../../../../docs/features/speaking-with-ai/README.md
[done]: ../../../../../../../docs/agent/definition-of-done.md
