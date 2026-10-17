# Cross-feature communication

Goal: features stay loosely coupled so any of them could later be extracted into its own service
without rewriting business logic.

## Three allowed ways for feature A to depend on feature B

1. **Synchronous query/command through B's `contract` interface.**
   A injects `B.contract.SomeInterface`. Spring wires B's implementation. A never sees B's entities.
2. **Event published by B (or A), consumed by the other.**
   Use Spring `ApplicationEventPublisher`. The event class lives in the publisher's `contract` package.
   Use this for "something happened" and for any dependency that would otherwise point backwards.
3. **Reference by id.**
   A stores B's id as a plain value. No object reference, no join, no cross-feature relationship
   mapping. When A needs details it calls B's contract.

Anything else (importing B's service/repository/model, reading B's collection/table directly) is forbidden.

## Recipe: A needs something from B

1. Work out exactly what A needs (which fields, which operation). Do not expose more.
2. Add a small interface and DTO in `b.contract`.
3. Implement the interface in `b.service`, delegating to existing services/repositories and mapping
   entity to contract DTO.
4. Inject the interface in A. Delete the old direct import.

```java
// b/contract/TopicQuery.java
public interface TopicQuery {
    Optional<TopicSummaryDto> findSummary(String topicId);
}

// b/service/TopicQueryImpl.java
@Service
class TopicQueryImpl implements TopicQuery { /* uses TopicRepository, maps to dto */ }

// a/service/SomeService.java
private final TopicQuery topicQuery;   // the only thing A knows about Topic
```

Design contract methods as if the call could be slow or fail someday: return `Optional` or throw a
contract-level exception with business meaning. Do not leak persistence exceptions.

## Allowed dependencies between features

Keep this table up to date. A new arrow needs a reason. Arrows must not form a cycle.

| Feature | May depend on (via contract) |
|---|---|
| dictionary | (none) |
| topic | dictionary |
| user | topic |

## Future: extracting a feature into a service (do NOT build this now)

When a feature is split out, the plan is:

- Provider: add an internal controller (for example `controller/internal/*InternalController`, path prefix
  `/internal/**`, not exposed publicly) that only translates HTTP into calls on its own contract interface.
- Caller: copy the interface and DTOs it needs into its own code (rename to fit its context), and add a
  `restclient` package with an implementation that calls the provider over HTTP. Its service layer does
  not change because it still talks to the interface.
- The two sides then agree on the HTTP contract, not on shared code.

Until that day, do not create internal controllers, REST clients, or shared contract jars.
