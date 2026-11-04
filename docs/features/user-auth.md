# User authentication on the default profile

Scope: AUTH-UC-001/002/007 and current-session logout. This setup exercises the user module and FE `/profile`; the DevAuth filter and fixture identities have been removed.

## Local setup

1. Use a MongoDB replica set or sharded cluster. Registration writes a user and a user profile in one transaction; a standalone MongoDB server cannot provide this guarantee. Keep APP_DB/APP_USER/APP_PASSWORD in the ignored local `.env` matched to that service.
2. Set AUTH_JWT_SECRET to a private random value (at least 32 bytes); keep it stable across restarts and never commit it. Base application.yaml requires this value. A private local key was generated in the existing ignored `.env` during this change if absent.
3. Enable `engolearn.mongodb.transactions.enabled=true` in the active runtime configuration. The repository's `dev` profile sets this flag, but the MongoDB server must still be running as a replica set. Without an active Mongo transaction, registration fails before writing either document. The DevAuth filter is removed; the `dev` profile does not enable fixture authentication.
4. Run `.\mvnw.cmd spring-boot:run`. On this Windows machine, Java's default temporary Unix socket path causes HttpClient initialization to fail. Use `.\mvnw.cmd spring-boot:run '-Dspring-boot.run.jvmArguments=-Djdk.net.unixdomain.tmpdir=D:\prj\engo_learn'` if needed. No payment source workaround is required.
5. Run the frontend proxy, then open `/profile`. Use its real login/register form, recheck, reload, refresh and logout controls.

HTTP local cookies use AUTH_COOKIE_SECURE=false; HTTPS deployments must use true. User records are stored in MongoDB's `users` collection with the existing `email`, `password_hash`, `role`, and `locked` fields. Registration also stores one `user_profiles` document keyed by the user ID, initially with `cefr_level=A1`. The CEFR level is mutable user-owned data and is read from MongoDB when topic services need it; it is not copied into JWTs. Existing records from the former `accounts` collection must be migrated with [`scripts/migrate-account-collection-to-users.js`](../../scripts/migrate-account-collection-to-users.js) before running this version. Accounts created before profile support need an explicit profile initialization or targeted reseed before profile-dependent topic operations; the application does not assign A1 implicitly to missing profiles. User-specific unique indexes are required for canonical email/session IDs; startup must report index failure rather than silently accepting duplicate identities. Existing duplicate email data must be reconciled deliberately before creating the unique index; do not delete user data to make startup pass.

## Verification boundary

Compare ID/email/role before and after reload and login. Check Mongo users for canonical email and password hash, and refresh_sessions for independent, bounded sessions. Never expose password hashes or raw session tokens in the UI or logs. Full auth/security acceptance and unrelated legacy regression results are recorded in the workspace Note/authentication reports.
