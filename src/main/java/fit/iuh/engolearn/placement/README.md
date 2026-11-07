# Placement start-session reservations

`MongoPlacementAttemptRepository` acquires a session reservation in the same
indexed insert that creates an Attempt. `start_session_key` contains the browser's
Guest Session ID for both Guest and authenticated starts carrying that session.
It is internal concurrency metadata, not ownership: authenticated Attempts still
have `user_id` and no `guest_session_id`; Guest ownership changes only on claim.

The unique partial index `one_reserved_start_session` indexes string-valued
`start_session_key`. Existing Guest and User unique indexes remain unchanged.
No extra collection, Attempt status, transaction, lease or JVM lock is used.

## Start and ownership

Precedence stays: active Guest, User result, active User, claimable Guest. The
reservation lookup is a final fallback, not a change to that ordering.

- If Guest inserts first, authenticated Start resumes that Guest Attempt.
- If authenticated Start inserts first, Guest/another User receives
  `409 PLACEMENT_ACTIVE_ATTEMPT_EXISTS`, without the owned Attempt ID. The owning
  User resumes their Attempt. Guest possession of a session is not authorization
  to read or modify a User Attempt.
- An independent User Attempt without that reservation still blocks Guest claim
  under Policy A; it is not deleted, transferred or silently replaced.

## Reservation lifecycle

| Transition | Reservation |
| --- | --- |
| Guest/User insert carrying a session | Acquired by unique indexed insert |
| Guest `SUBMITTED` / `AUTO_SUBMITTED` | Retained while result is claimable |
| Successful Guest claim | Unset in the ownership/revision CAS update |
| Unclaimed Guest result reaches claim expiry | Unset by conditional update on Start; revision incremented |
| `EXPIRED`, or User finalization | Unset in the finalization/revision CAS update |

Claim expiry is not extended. Expiry release does not change the stored score,
deadline, status or ownership. In-progress attempts continue through the existing
deadline resolver, which preserves `ended_at = deadline` for automatic endings.
There is no separate reservation document to strand if a process stops.

## Existing data and deployment

This is a persistence compatibility change, not an automatic data migration.
Mongo-mode initialization rejects blocking Guest Attempts without a reservation.
It reads data to validate compatibility and never backfills, drops or repairs it.

Before a separately approved deployment against existing data:

1. Stop/drain old Placement writers; mixed old/new writers are unsupported.
2. Back up Placement data and inspect unresolved attempts and index definitions.
3. Explicitly resolve any existing cross-identity conflicts. Do not infer which
   historical User Attempt belonged to a browser: older User Attempts did not
   store that association.
4. Assign `start_session_key = guest_session_id` to blocking unclaimed Guest
   Attempts after resolving duplicate reservations. Historical expired/nonblocking
   Attempts need no key. Check whether old in-progress User Attempts must drain
   before accepting new starts.
5. Verify/create the new unique index, then activate only compatible writers.
   The old `one_blocking_user_attempt` must already have the BSON-string filter.

These are operator prerequisites, not commands executed by this module. Do not
roll back to old writers while reservation-bearing Attempts are unresolved.

## Verification

`PlacementCrossIdentityMongoTest` uses a fresh UUID database, real Mongo writes,
and deterministic pauses immediately before insert. It checks persisted IDs,
ownership, reservations, revision and deadlines, including both winner orders,
Guest submit/claim before the stale User insert, and concurrent User
Start/Claim/Submit. It also verifies the actual unique index and the legacy-data
startup rejection. Test cleanup drops only that freshly allocated test database.
