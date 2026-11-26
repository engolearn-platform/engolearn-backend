# Integrate role authorization

Use the [security authorization rules](../../architecture/security-authorization.md) to identify the HTTP and service boundaries. Use the feature owner's approved role requirement for each operation.

## Protect a service operation

1. Add an explicit authenticated or role-based HTTP matcher when introducing a protected endpoint. Check overlapping matchers in `SecurityConfig`, especially any specific public methods above broader rules.
2. Annotate the public entry point on the Spring-managed service implementation. The following illustrates a Content Manager operation for a hypothetical Topic integration.

   ```java
   import org.springframework.security.access.prepost.PreAuthorize;

   // Inside a Spring-managed service implementation
   @PreAuthorize("hasRole('CONTENT_MANAGER')")
   public void publishTopic(String topicId) {
       // Existing business logic
   }
   ```

3. Call the service through the injected bean. Apply resource ownership and other business conditions using trusted caller identity where the operation requires them.

The [authority table](../../architecture/security-authorization.md#identity-and-authorities) explains why both Content Manager and Admin can call the example. For a minimum role use `hasRole('USER')`, `hasRole('CONTENT_MANAGER')`, or `hasRole('ADMIN')`. To express alternative role requirements, use `@PreAuthorize("hasAnyRole('CONTENT_MANAGER', 'ADMIN')")`. In this hierarchy that specific OR expression is equivalent to requiring Content Manager.

## Add authorization tests

Use a focused HTTP test with the real security chain and a controller that calls the injected service bean. Include an anonymous request, a valid session with insufficient authority, and a valid session whose role satisfies the requirement through inheritance. Exercise the denied case before the method body can change state. Add cases for approved ownership restrictions when applicable. See `RoleAuthorizationIntegrationTest` for a self-contained fixture that uses signed cookies and isolates the persistence boundary. Run the [verification runbook](../../runbooks/rbac-verification.md) after editing security configuration.

| Symptom | What to check |
| --- | --- |
| Annotation has no effect | Confirm `@EnableMethodSecurity` is loaded and that the caller uses the injected Spring proxy. Check for calls from inside the same bean, manual construction, or an unadvisable method. |
| Guest receives success on a protected path | Check the first matching HTTP rule and confirm that the request reaches the annotated service entry point. |
| Logged-in caller receives 401 | Check the access cookie, session expiry or revocation, and whether the account still exists and is unlocked. |
| Higher role receives 403 | Check the current account, the principal's effective authorities, the exact matcher and method expression, and any separate ownership or business restriction. `hasRole('CONTENT_MANAGER')` takes the unprefixed role name. |
| Authorization denial becomes 500 | Check MVC exception advice for generic handlers that intercept Spring Security exceptions. |
