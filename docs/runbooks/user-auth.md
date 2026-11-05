# User authentication setup

Local setup for registration, login, refresh and current-session logout through the frontend `/profile` page. Authentication uses real accounts; the `dev` profile does not enable fixture identities.

## Local setup

1. Use a MongoDB replica set or sharded cluster. Registration writes the user and profile in one transaction. Keep `APP_DB`, `APP_USER` and `APP_PASSWORD` in the ignored local `.env` matched to that service.
2. Set `AUTH_JWT_SECRET` to a private random value of at least 32 bytes; keep it stable across restarts and never commit it. The base application configuration requires this value.
3. Enable `engolearn.mongodb.transactions.enabled=true` in the active configuration. The `dev` profile sets this flag, but MongoDB must still support transactions. Registration rejects execution without an active transaction before writing either document.
4. Run `.\mvnw.cmd spring-boot:run` from the backend root, start the frontend proxy, then open `/profile` to register or log in.

Use `AUTH_COOKIE_SECURE=false` for local HTTP cookies and `true` for HTTPS deployments.

## Existing data and migration

User accounts live in `users`, with `email`, `password_hash`, `role` and `locked`. Registration also creates a `user_profiles` document keyed by the User ID with initial `cefr_level=A1`. CEFR level remains mutable User-owned data read from MongoDB by Topic; it is not copied into JWTs.

For a database still using `accounts`, inspect and run the [account-to-users migration](../../scripts/migrate-account-collection-to-users.js) against the intended database while application writers are stopped. The script refuses simultaneous source/target collections; reconcile that case deliberately. This migration does not initialize missing profiles. Accounts created before profile support need explicit profile initialization or a targeted, authorized reseed before profile-dependent Topic operations; missing profiles do not silently receive A1.

Canonical email/session IDs require unique indexes. Inspect deployed index state and reconcile duplicates deliberately; do not delete user data to make startup pass.

## Verification

Compare ID/email/role before and after login and reload, then check refresh and logout through `/profile`. Inspect canonical email/password-hash presence in `users` and independent bounded sessions in `refresh_sessions` without exposing hashes or raw tokens in UI/logs. These are operator checks, not a claim that full authentication/security acceptance has been completed.
