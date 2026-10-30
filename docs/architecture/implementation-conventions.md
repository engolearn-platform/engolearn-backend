# Backend implementation conventions

These rules apply to new code and code being refactored. Existing code is not automatically compliant. Inspect relevant callers, persistence mappings and tests before using an existing class as an example. Do not expand a task into unrelated cleanup.

**MUST / MUST NOT** are requirements. Package ownership and approved feature dependencies remain defined by [package structure](package-structure.md) and [cross-feature communication](cross-feature-communication.md). Explain material deviations before introducing them; reuse approval already given in the task.

## 1. Names

| Kind | Required convention | Example |
|---|---|---|
| Java package | Lowercase, no underscores | `controller.exceptionhandler`, `service.impl` |
| Java class/interface | PascalCase | `TopicResetService`, `TopicResetServiceImpl` |
| Java field/method | camelCase | `completedAt`, `resetGeneration()` |
| Stored Mongo field | Explicit snake_case mapping | `@Field("completed_at")` |
| HTTP DTO | `Request` / `Response` suffix | `ResetTopicRequest`, `ResetTopicResponse` |
| Contract DTO | `Dto` suffix | `EffectiveEntitlementDto` |
| Public event | Past-tense fact + `Event` suffix | `EntitlementChangedEvent` |
| Embedded persistence value | `Vo` suffix | `ResetReceiptVo` |
| Persistence access | `Repository` suffix | `TopicLearnerStateRepository` |
| Scheduled trigger | `Scheduler` suffix | `SubscriptionActivationScheduler` |

Names MUST describe the responsibility; a generic `Helper`, `Manager` or `Processor` name does not justify a class. Package destinations and allowed dependencies are defined only in [package structure](package-structure.md).

## 2. Controllers, validation and exceptions

- Every public endpoint method of a `@RestController` MUST return `ApiResponse<T>`, with an explicit web response DTO as its payload. MUST NOT use persistence entities, BSON documents or `Map` as the endpoint payload. A DTO may contain a typed map when the contract needs dynamic keys.
- Known request shapes MUST use typed DTOs; validate bodies with `@Valid` and Bean Validation constraints. HTTP shape validation failures return `400` `ProblemDetail`.
- Controllers adapt HTTP details such as headers/status and invoke business interfaces. Follow the [dependency policy](package-structure.md#dependency-policy).
- Controllers validate input shape; business logic enforces preconditions and transitions. MUST NOT repeat HTTP null/blank/range validation in business logic. Non-HTTP entry points still enforce their own input invariants.
- HTTP exception handlers use `@RestControllerAdvice` and return `ProblemDetail`. Per-controller advice MUST explicitly identify its target controllers; placement and the accepted legacy location are defined in [package structure](package-structure.md).

## 3. Business logic and injection

- Business capabilities MUST have interfaces; implementations use the `Impl` suffix. Follow the interface/implementation placement and dependency rules in [package structure](package-structure.md).
- Invoke collaborating business capabilities through their interfaces; MUST NOT call another implementation's static methods.
- Required Spring dependencies MUST be `private final` fields with Lombok `@RequiredArgsConstructor`. MUST NOT use field `@Autowired`, handwritten constructors that only assign dependencies, or null fallbacks for required dependencies. Constructors with additional configuration behavior require an explicit design rather than mechanical removal.
- A class MUST own a cohesive capability. One public method is valid. A new wrapper that only forwards identical arguments/results without isolating a responsibility or dependency needs justification; do not create one business class per endpoint by default.
- Business logic used by only one implementation stays private there by default. Extract it only when it has a distinct responsibility or actual reuse. Pure helpers MUST NOT become Spring beans without a dependency/lifecycle need.
- Shared business decisions MUST have one owner. Move them behind a business interface or into the domain type that owns the state, rather than accessing another implementation's helpers.

## 4. Persistence, mapping and scheduling

- Repository interfaces exposed to business logic MUST use typed, operation-specific parameters/results, not `Query`, `Criteria`, `Update` or generic collection APIs. Persistence placement is defined in [package structure](package-structure.md).
- Business logic checks preconditions before persistence; repositories preserve the conditions needed to perform the requested operation atomically.
- Refactoring persistence MUST preserve transaction participation, owner/version predicates, write ordering and idempotency behavior unless an explicit behavior change is agreed.
- Mappers MUST be stateless transformations of prepared data. Business logic loads data and checks authorization/state before mapping; dependency restrictions are defined in [package structure](package-structure.md#dependency-policy).
- Mappers may calculate response-only counts or percentages. Selecting the authoritative result, deciding completion or allowing a transition belongs to business logic/domain types. MUST NOT hide these responsibilities in a `Presenter` that also reads data.
- Aggregation functions MUST return a result rather than silently mutate an input collection by default. An intentional in-place operation MUST make that behavior explicit in its name and interface.
- Document-owned indexes belong on model annotations where supported; configuration controls index creation. Business implementations MUST NOT create/drop indexes, repair old data or run migrations.
- Scheduled triggers use Spring `@Scheduled` and invoke business interfaces; eligibility rules and processing belong behind those interfaces. Use the adapter/configuration placement in [package structure](package-structure.md). Do not add retry, locking or distributed scheduling mechanisms without a concrete requirement.

## 5. Persistence models and embedded values

- Root Mongo documents MUST be classes annotated with `@Document`, extending `shared.BaseDocument`, with Lombok `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor` and `@SuperBuilder`. MUST NOT redeclare inherited ID/audit fields; omit a redundant all-args constructor if the class has no declared fields.
- Embedded persistence values MUST be classes implementing `shared.EmbeddedDocument`, with Lombok `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor` and `@Builder`. Package and file placement are defined in [package structure](package-structure.md).
- Records MUST NOT be used for persistence documents or embedded values. This restriction does not decide the representation of web DTOs or contract types.
- Every persisted non-ID field MUST declare `@Field("snake_case")`, including single-word fields and version fields. Static/non-persisted fields do not need mappings; IDs use `@Id`, and non-persisted instance fields use `@Transient` where applicable.
- Use Lombok getters/setters and generated construction as appropriate; callers use normal getters/builders. MUST NOT use `@Data` indiscriminately or silently lose equality behavior when converting a record.
- Finite application-controlled persisted states/classifications MUST use enums. Externally defined or open-ended values may remain strings when justified. Keep persisted enum values stable during refactoring; distinguish public/internal enum placement using [package structure](package-structure.md#public-contract-layout).
- Changing stored field names, ID representations or embedded types MUST include a data-compatibility decision. For disposable dev data, an agreed targeted reset/reseed may replace migration; do not reset unrelated feature data.

Reusing a public contract type in persistence couples storage to that public interface. Inspect stored representations and consumers before changing such types or packages; the allowed dependencies remain defined in [package structure](package-structure.md#dependency-policy).

## 6. Public data representations

Contract package roles are defined in [package structure](package-structure.md#public-contract-layout); semantic and compatibility obligations are defined in [cross-feature communication](cross-feature-communication.md#public-contract-semantics).

- Apply the suffixes in [Names](#1-names). Name complex input by purpose, for example `ActivateTrialCommandDto`, rather than treating it as an HTTP request.
- Protect public collection ownership with defensive copies and immutable representations as appropriate. Do not assume that a record or a Lombok-generated constructor automatically copies a collection or protects mutable nested values.
- A class/record choice for contracts is not prescribed here; persistence-model rules MUST NOT be applied to them mechanically.

## 7. Verification and adoption

- Before adding a feature, inspect representative code across the relevant layers and apply these rules; existing code, including Topic, is not a blanket exception or proof of compliance.
- Review package placement, dependency direction and data contracts as well as behavior. Do not call work compliant merely because `ArchitectureTest` passes.
- Automated coverage and its limitations are maintained only in [package structure](package-structure.md#what-is-automatically-enforced).
- MUST NOT add tests merely to mirror package moves, generated constructors or interface extraction. Verify consequential persistence/concurrency behavior using the [working agreement](../agent/working-agreement.md); completion and reporting criteria remain in the [Definition of Done](../agent/definition-of-done.md).
