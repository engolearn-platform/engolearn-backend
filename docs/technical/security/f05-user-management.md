# F05 administrator user management

All routes under `/api/v1/admin/users` require a current ADMIN session. Read responses and writes use the standard `ApiResponse.data` envelope and disable caching. A USER or CONTENT_MANAGER target can be changed. Every ADMIN target, including the caller, is read-only.

| Method | Relative path | Request | Result |
|---|---|---|---|
| GET | collection | search, role, status, sort, page, size | items, page, size, totalElements, totalPages |
| GET | /{id} | | Detail |
| POST | collection | name, email, initialPassword, optional role | 201 Detail, no new cookies |
| PATCH | /{id} | name, expectedName, expectedRevision | Detail |
| PATCH | /{id}/role | role, expectedRevision | Detail |
| PATCH | /{id}/status | status, expectedRevision | Detail |
| DELETE | /{id} | expectedRevision query | id |

Summaries contain id, name, email, role, status and UTC createdAt. Details add revision. Legacy name and createdAt may be null. ACTIVE and DISABLED are the visible statuses. Only USER and CONTENT_MANAGER are assignable. Create defaults to USER. Name edits trim and require 1–80 characters. Explicitly send the previously displayed expectedName, including null when unnamed. Requests use the exact nonnegative revision last reviewed. Unknown fields and invalid shapes return VALIDATION_ERROR. Credentials use the existing registration policy and BCrypt. Self-registration and administrator enrollment atomically initialize the same profile and subscription data.

Literal name/email search accepts at most 100 trimmed characters. Sort accepts createdAt or name paired with asc or desc, separated by a comma. The default is createdAt,desc. Name sorting uses Vietnamese case-insensitive collation. Missing keys sort last, then ID resolves ties. Page starts at zero, size defaults to 25 and ranges from 1 to 100, and the maximum offset is 100,000.

Authentication and authorization failures carry AUTHENTICATION_REQUIRED or ACCESS_DENIED. ADMIN_TARGET_READ_ONLY denotes an immutable target. USER_NOT_FOUND also covers tombstones. EMAIL_ALREADY_EXISTS covers canonical email conflicts. USER_STATE_CONFLICT requires reloading the target and obtaining a new decision. An unavailable store or account setup returns 503.

## Deployment and data checks

Run the following read-only queries against the intended database before upgrading. Include every account, including deleted records. The startup check also compares exact Java trim/lowercase normalization using Locale.ROOT. Resolve all discrepancies and index contradictions explicitly, preserving user IDs, references and canonical email reservations. Do not combine or remove accounts automatically.

```javascript
db.users.aggregate([
  {$project: {email: 1, canonical: {$cond: [{$eq: [{$type: "$email"}, "string"]},
    {$toLower: {$trim: {input: "$email"}}}, null]}}},
  {$match: {$expr: {$or: [{$eq: ["$canonical", null]}, {$eq: ["$canonical", ""]}, {$ne: ["$email", "$canonical"]}]}}},
  {$project: {_id: 1}}
])
db.users.aggregate([
  {$match: {email: {$type: "string"}}},
  {$group: {_id: {$toLower: {$trim: {input: "$email"}}}, count: {$sum: 1}}},
  {$match: {count: {$gt: 1}}}, {$project: {_id: 0, count: 1}}
])
db.users.getIndexes()
db.users.find({_id: {$type: "string", $regex: /^[a-fA-F0-9]{24}$/}}, {_id: 1})
db.users.find({$expr: {$not: [{$in: [{$type: "$_id"}, ["objectId", "string"]]}]}}, {_id: 1})
for (const field of ["admin_revision", "session_epoch"]) {
  db.users.find({[field]: {$exists: true}, $or: [
    {$expr: {$not: [{$in: [{$type: "$" + field}, ["int", "long"]]}]}},
    {[field]: {$lt: 0}}
  ]}, {_id: 1})
}
```

Startup verifies existing data and creates or recognizes a full unique ascending email index. A partial or sparse index alone cannot reserve tombstone emails. User management writes require a replica set with transactions enabled. Finish upgrading all authentication and Speaking instances before enabling admin writes. Older instances ignore tombstones and account epochs. Pre-upgrade socket tickets and leases lack the required proof and clients must obtain new tickets.

Spring Data maps a 24-hex account ID to BSON ObjectId when reading by ID. The startup check rejects manually imported 24-hex BSON strings and unsupported BSON ID types. Resolve these rows with the owner of the import, checking linked profile IDs and references for collisions before deployment. Present revision and epoch fields must be nonnegative BSON int or long. Explicit null, negative or incompatible values require manual resolution. Only absent counters use the legacy zero default.

Missing admin_revision and session_epoch default to zero, missing role to USER and missing locked to false. Name remains in user_profiles.display_name. Soft delete retains linked records and email while removing the account from read results. Disable and delete each advance session_epoch. Enable preserves it. Old cookies on all devices remain unusable after re-enable. Roles are read from the live account on every new authorization. Speaking binds tickets and leases to the originating account, refresh session and account epoch. It rechecks on upgrade, connection admission and inbound work. Already admitted work follows existing settlement rules.

Each committed F05 write inserts a user_management_audit record in the same transaction with actor_id, target_id, action, created_at, before/after role and before/after status where applicable. Query this internal collection by target_id or actor_id for traceability. Initial passwords, hashes, cookies and tokens are excluded. No password reset or first-login change is introduced.
