# Cross-feature communication

Features may depend on one another only through narrow public contracts. The goal is to keep feature internals private and make future extraction possible without rewriting business logic.

## Allowed communication

1. **Contract call** — call a capability interface in the provider's `<feature>.contract`. The implementation stays in the provider's `service`; callers must not access its entities or repositories.
2. **Event** — publish an event from the publisher feature and let the other feature react to it. Put the event type in the publisher's `contract` package. Prefer events when a direct call would create a backwards dependency.
3. **ID reference** — store another feature's ID as a plain value, not as a cross-feature object relationship. Request details through that feature's contract when needed.

Direct imports of another feature's controller, service, repository, model, or web DTO are forbidden. Features must not read another feature's collection directly.

## When feature A needs capability from feature B

1. Define the smallest operation and data A needs.
2. Add the interface and any stable DTOs under `B.contract` (DTOs go in `B.contract.dto`).
3. Implement the interface in `B.service`, delegating to B's existing internals.
4. Inject the interface into A; remove direct references to B's internals.
5. Update the dependency table below and run `ArchitectureTest`.

Design contract results and errors as public API: use types such as `Optional` where appropriate and expose business-level exceptions rather than persistence exceptions.

```java
// dictionary/contract/VocabularyLookup.java
public interface VocabularyLookup {
    Optional<VocabSenseDto> findSense(String vocabId, String senseId);
}

// dictionary/service/VocabularyLookupImpl.java
@Service
class VocabularyLookupImpl implements VocabularyLookup {
    // Uses dictionary repositories and maps results to contract DTOs.
}

// topic/service/TopicAuthoringService.java
private final VocabularyLookup vocabularyLookup;
```

## Approved feature dependencies

A new dependency needs an explicit reason and approval. Keep this table in sync with the architecture decisions; dependency arrows must not form a cycle.

| Feature | May depend on, through contract |
|---|---|
| `dictionary` | None |
| `topic` | `dictionary` |
| `user` | `topic` |
| `subscription` | None |
| `quota` | `subscription` (effective entitlements and after-commit events) |

`ArchitectureTest` checks that cross-feature code dependencies target a contract and that dependency cycles do not exist. It does **not** currently enforce this table as an exact allow-list, so reviewers must check that new dependencies are listed and approved.

## Future service extraction — not part of current work

When a feature is extracted, its provider may expose a private/internal HTTP adapter and the caller may implement the same capability interface with a REST client. Keep service logic dependent on the interface so the transport can change without rewriting business logic. Do not build internal controllers, REST clients, or shared contract libraries before that extraction is approved.
