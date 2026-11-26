# Verify role authorization

Run these commands from the backend repository. Consult the [authorization rules](../architecture/security-authorization.md) for expected policy and the [integration guide](../technical/security/rbac-integration.md) for adding a boundary.

## Run security checks

```powershell
.\mvnw.cmd '-Dtest=RoleAuthorizationIntegrationTest,SecurityIntegrationTest,AuthenticatedUserPrincipalTest,AccessCookieAuthFilterTest,AuthControllerTest,UserAuthServiceImplTest' test
.\mvnw.cmd verify
```

On macOS or Linux, use `./mvnw` in place of `.\mvnw.cmd`. Run the full verification with Docker available for the MongoDB Testcontainers suites. Inspect the failures and skips in `target/surefire-reports/`.

## Confirm the HTTP and proxy behavior

1. Inspect `RoleAuthorizationIntegrationTest` in the Surefire reports. The fixture calls a role-gated service through an injected Spring bean using the production cookie filter chain. Confirm guest receives 401, authenticated USER receives 403 for Content Manager operations, and Content Manager receives 403 for Admin operations. Check `application/problem+json` and the response body's status.
2. Confirm Content Manager can call the USER and Content Manager-or-Admin operations and Admin can call operations at all three levels. The fixture's test URLs exist only in the MVC test.
3. Verify that the same valid cookie loses access after the stored account's role is lowered. Check that a locked account with a valid session cookie receives 401.
4. After editing the filter chain, review the matcher order and run the endpoint regression tests for every affected namespace. Compare public and protected behavior before and after the change. Exercise login, refresh, `/api/v1/auth/me`, and logout through the cookie flow using the [auth runbook](user-auth.md).

## Diagnose common failures

| Observed result | Check |
| --- | --- |
| Guest gets 200 | Inspect the first matching rule and the call into the Spring proxy. Confirm the test sends no cookies. |
| Authenticated caller gets 401 | Confirm token configuration, session validity, current account state, and cookie forwarding through the client or proxy. |
| Inherited role gets 403 | Check effective principal authorities, role expression, matcher precedence, and any ownership checks on the fixture or operation. |
| Testcontainers errors before tests execute | Check `docker info` and enable access to the Docker engine. The focused security checks can run without Mongo containers. Record any skipped or unverified checks. |
