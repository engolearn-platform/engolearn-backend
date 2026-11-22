# Security authorization rules

**Owner** Backend architecture · **Status** Required · **Adopted** 2026-11-23

Developers and coding agents **MUST** follow these rules when creating or changing a protected HTTP endpoint or service operation. The [package rules](package-structure.md) and [cross-feature communication policy](cross-feature-communication.md) also apply.

**MUST** marks a requirement. **SHOULD** marks a recommendation. **MAY** marks an option.

## Identity and authorities

Authentication establishes the caller's identity. Authorization decides whether that identity can perform an operation. The role hierarchy is `ADMIN > CONTENT_MANAGER > USER`.

| Account role | Granted authorities |
| --- | --- |
| `USER` | `ROLE_USER` |
| `CONTENT_MANAGER` | `ROLE_USER`, `ROLE_CONTENT_MANAGER` |
| `ADMIN` | `ROLE_USER`, `ROLE_CONTENT_MANAGER`, `ROLE_ADMIN` |

- Authorization **MUST** use the authorities on the trusted Spring Security authentication. Cookie authentication **MUST** validate the session and derive authorities from the current account. A locked account, invalid session, or missing account cannot establish an authenticated principal. Guest is the absence of authentication and has no persisted role.
- The centralized principal mapping **MUST** grant inherited authorities consistently for HTTP and method checks. Features **MUST NOT** implement their own role hierarchy or derive privileges from headers, request bodies, client-supplied identities, or decoded token claims.
- Features **MUST** obtain the current caller's identity through the applicable security contract, such as `CurrentUserProvider` for service-side identity. Ownership, entitlements, and business eligibility **MUST** be enforced separately using the verified caller and the target resource. A role grant alone does not establish those permissions.

## HTTP boundaries

- Changes **MUST** identify the approved public and protected paths and HTTP methods. Protected paths **MUST** have an explicit authenticated or role-based matcher before any overlapping broader rule that would allow the request. The first matching rule applies. Review exact paths, descendants, and intentionally public methods together.
- Each defined authorization boundary **MUST** deny access when its approved requirements are missing or unmet. Public exceptions **MUST** be explicit when they overlap a protected namespace. A feature **MUST NOT** rely on the global fallback to express its authorization policy.
- The existing `anyRequest().permitAll()` fallback preserves legacy behavior for unmatched requests. Changing it **MUST** have a separate impact review and approval for affected endpoint owners. Review and test any approved matcher changes against existing public routes.
- HTTP security **MUST** return `401` with `ProblemDetail` when a protected request cannot establish authentication and `403` with `ProblemDetail` when an authenticated caller lacks the required authority. Preserve the existing response contract. MVC exception advice **MUST NOT** swallow Spring Security exceptions or turn security denials into generic `500` responses.

## Method boundaries

- Role-gated service operations **MUST** enforce the approved role at their Spring-managed entry point with `@PreAuthorize`. Use `hasRole('CONTENT_MANAGER')` for one role and `hasAnyRole('CONTENT_MANAGER', 'ADMIN')` for OR semantics. Supply account role names without the `ROLE_` prefix. Select the minimum approved role when inheritance covers the higher roles.
- Callers **MUST** enter through the Spring proxy. Put authorization on a public method of an injected Spring bean. Self-invocation, private or final methods that the proxy cannot advise, and manually instantiated services **MUST NOT** be used as authorization boundaries.
- Teams **SHOULD** protect the service operation closest to the protected action so alternative HTTP or in-process callers receive the same enforcement. HTTP matchers **MAY** enforce a coarse route boundary while the service applies a more specific approved requirement.

## Verification

- A change to authorization **MUST** test unauthenticated, insufficient-role, and authorized requests, including applicable inherited-role grants. Method-level tests **MUST** invoke a Spring-managed proxy. Also exercise any ownership checks required by the feature.
- A change to authentication-to-authority mapping **MUST** cover every account role, a role change on a subsequent request with the same valid cookie, and a locked account. An HTTP policy change **MUST** verify `401`, `403`, and the affected public and protected paths.
- Tests **SHOULD** use isolated fixtures for shared infrastructure. Architecture review verifies the placement and correct use of new boundaries. The automated security tests exercise runtime enforcement.

The existing method security, cookie filter, and cumulative principal authorities provide the infrastructure for these rules. The role requirements of existing business endpoints remain owned by their features. See the [integration guide](../technical/security/rbac-integration.md) and [verification runbook](../runbooks/rbac-verification.md).
