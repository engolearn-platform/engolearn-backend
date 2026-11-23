# User authentication setup

Local setup for registration, login, refresh and current-session logout through the frontend `/profile` page. Authentication uses real accounts; the `dev` profile does not enable fixture identities.

## Local setup

1. Use a MongoDB replica set or sharded cluster. Registration writes the user, profile and initial subscription account in one transaction. New accounts have unused Trial eligibility and use the published FREE policy until Trial/Premium activation. Quota projections remain lazy and missing counters read as zero usage. No legacy-account backfill is performed. Keep `APP_DB`, `APP_USER` and `APP_PASSWORD` in the ignored local `.env` matched to that service.
2. Set `AUTH_JWT_SECRET` to a private random value of at least 32 bytes; keep it stable across restarts and never commit it. The base application configuration requires this value.
3. Keep `engolearn.mongodb.transactions.enabled=true`. The default configuration enables it, and MongoDB must support transactions. Registration checks for an active transaction before writing account data.
4. Run `.\mvnw.cmd spring-boot:run` from the backend root, start the frontend proxy, then open `/profile` to register or log in.

Use `AUTH_COOKIE_SECURE=false` for local HTTP cookies and `true` for HTTPS deployments.

## Seed the first management accounts

The [management accounts sample](../../tools/mongo-json-importer/samples/management-accounts.importer.json) is included when importing all [MongoDB samples](../../tools/mongo-json-importer/README.md). Use a disposable local database and the importer's default `skip` policy. Reimporting preserves existing documents by ID, including changed passwords and account locks.

```powershell
.\tools\mongo-json-importer\import.ps1 -DryRun
.\tools\mongo-json-importer\import.ps1
```

For a fresh import, use these public demo credentials.

| Role | Email | Password |
| --- | --- | --- |
| `ADMIN` | `admin@example.test` | `EngoLearn-Admin-Demo-2026!` |
| `CONTENT_MANAGER` | `content-manager@example.test` | `EngoLearn-Content-Demo-2026!` |

Start the backend with the default profile and sign in through the frontend `/login`. Both accounts open `/admin/topics` in the management layout.

## Existing data and migration

User accounts live in `users`, with `email`, `password_hash`, `role` and `locked`. Registration also creates a `user_profiles` document keyed by the User ID with initial `cefr_level=A1`. CEFR level remains mutable User-owned data read from MongoDB by Topic; it is not copied into JWTs.

For a database still using `accounts`, inspect and run the [account-to-users migration](../../scripts/migrate-account-collection-to-users.js) against the intended database while application writers are stopped. The script refuses simultaneous source/target collections; reconcile that case deliberately. This migration does not initialize missing profiles. Accounts created before profile support need explicit profile initialization or a targeted, authorized reseed before profile-dependent Topic operations; missing profiles do not silently receive A1.

Canonical email/session IDs require unique indexes. Inspect deployed index state and reconcile duplicates deliberately; do not delete user data to make startup pass.

## Verification

Compare ID/email/role before and after login and reload, then check refresh and logout through `/profile`. Inspect canonical email/password-hash presence in `users` and independent bounded sessions in `refresh_sessions` without exposing hashes or raw tokens in UI/logs. These are operator checks, not a claim that full authentication/security acceptance has been completed.

For role-authorization checks, use [RBAC verification](rbac-verification.md).
