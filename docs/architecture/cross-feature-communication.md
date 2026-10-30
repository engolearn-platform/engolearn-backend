# Cross-feature communication

P01-P02 source update (2026-10-30): new Payment contracts follow the DTO/event layout and the Subscription -> Quota runtime now uses durable Outbox/receipt delivery. Payment business consumers remain absent. Historical/current labels below describe the pre-P02 snapshot where indicated; future paid flows must use the same durable boundary. No direct Subscription -> Quota business call is introduced.

Features may depend on one another only through narrow public contracts. The goal is to keep feature internals private and make future extraction possible without rewriting business logic.

This document owns communication mechanisms, caller/provider obligations and the approved feature-dependency graph. Package placement is defined in [package structure](package-structure.md#public-contract-layout); naming and representation are defined in [implementation conventions](implementation-conventions.md#6-public-data-representations). Apply these rules to new and refactored code; existing implementations may still need alignment.

## Allowed communication

1. **Contract call** — call the provider's public capability interface; callers must not access its entities or repositories.
2. **Event** — publish a fact owned by the publishing feature and let the receiving feature react. Prefer events when a direct call would create a backwards dependency.
3. **ID reference** — store another feature's ID as a plain value, not as a cross-feature object relationship. Request details through that feature's contract when needed.

Direct imports of another feature's controller, service, repository, model, or web DTO are forbidden. Features must not read another feature's collection directly.

## When feature A needs capability from feature B

1. Define the smallest operation and data A needs.
2. Declare the public capability and necessary types using the [public contract layout](package-structure.md#public-contract-layout).
3. Implement it inside B, delegating to B's existing internals rather than exposing them to A.
4. Inject the interface into A; remove direct references to B's internals.
5. Update the dependency table below and run `ArchitectureTest`.

```java
// dictionary/contract/VocabularyLookup.java
public interface VocabularyLookup {
    Optional<VocabSenseDto> findSense(String vocabId, String senseId);
}

// dictionary/service/impl/VocabularyLookupImpl.java
@Service
class VocabularyLookupImpl implements VocabularyLookup {
    // Uses dictionary repositories and maps results to contract DTOs.
}

// topic/service/TopicAuthoringService.java
private final VocabularyLookup vocabularyLookup;
```

## Public contract semantics

- Treat the contract as a public interface: document its input meaning, invariants, absence/failure behavior and ordering/transaction requirements where relevant. Use types such as `Optional` when absence is a valid result.
- Publish only capabilities, types and fields needed by real consumers. Check callers before removing/renaming fields or changing their meaning. Contract data MUST remain stable while callers use it; defensive-copy techniques belong in [implementation conventions](implementation-conventions.md#6-public-data-representations).
- Events describe facts already established. Publication timing, delivery guarantees, retries and recovery require explicit decisions; an event declaration alone does not guarantee delivery.
- Public exceptions describe failures callers can act on. MUST NOT encode HTTP status or expose persistence/framework exceptions as the public failure interface. HTTP translation belongs to the caller's HTTP handling.
- The provider owns the contract and its implementation; the receiving feature owns its event handling. Do not expose provider internals merely to make a caller's implementation easier.

## Approved feature dependencies

A new dependency needs an explicit reason and approval. Keep this table in sync with the architecture decisions; dependency arrows must not form a cycle.

| Feature | May depend on, through contract |
|---|---|
| `dictionary` | None |
| `topic` | `dictionary` |
| `user` | `topic` |
| `subscription` | `payment.contract` for the approved future purchase/refund command and result contracts; no current production consumer |
| `quota` | `subscription.contract` (effective entitlements and Subscription-owned facts; current after-commit delivery, durable delivery is the agreed target) |
| `payment` | None; must never depend on Subscription |

The delivered M3 preparation adds only two `payment.contract` records. Subscription owns the
future result consumer and imports Payment-owned command/result types; commands can belong
to the receiving Payment capability even though Subscription initiates them. Payment must
never import Subscription types. New command DTOs use `payment.contract.dto`; new past-tense
fact types use `payment.contract.event` with the `Event` suffix, following implementation
conventions. The existing root records are current declarations until an explicit P01 revision.

The 2026-10-30 agreed target uses durable Outbox/recipient receipts for Subscription -> Payment
commands, Payment -> Subscription results and Subscription -> Quota facts, without a broker
or cross-module transaction. Producer business writes and Outbox records commit locally;
consumer receipts and effects commit locally. Recipient-scoped delivery prevents Payment and
Quota from claiming or acknowledging each other's messages. Existing Trial and scheduled
Free-policy publication must migrate too; no permanent in-memory-only parallel path is intended.
No such delivery runtime is implemented by this documentation update. See the
[canonical target design](../technical/subscription-quota/subscription-payment-technical-design.md),
[ADR-0002](decisions/ADR-0002-durable-subscription-payment-quota-delivery.md) and
[current Payment declarations](../technical/subscription-quota/payment-contract-handoff.md).

`ArchitectureTest` does **not** currently enforce this table as an exact allow-list, so reviewers must check that new dependencies are listed and approved. The complete list of current checks is maintained in [package structure](package-structure.md#what-is-automatically-enforced).

## Future service extraction — not part of current work

When a feature is extracted, its provider may expose a private/internal HTTP adapter and the caller may implement the same capability interface with a REST client. Keep service logic dependent on the interface so the transport can change without rewriting business logic. Do not build internal controllers, REST clients, or shared contract libraries before that extraction is approved.
