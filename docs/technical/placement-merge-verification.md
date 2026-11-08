# Placement merge and live integration verification

Verified on 2026-11-08 (Asia/Bangkok).

## Revisions and scope

- BE integration branch: `merge/placement-guest-user-main`, from feature `da46ecd` with main `0f41690`.
- FE: `merge/placement-guest-user-main`, commit `f1737cc`.
- Resolve the two architecture-root conflicts by retaining both `placement` and `assessment`.
- Preserve existing Placement/Auth contracts. Original main and feature branches are unchanged.

## Real runtime

- FE Vite on `127.0.0.1:3000`, proxy `/api` to BE `127.0.0.1:8082`.
- Dedicated disposable Docker MongoDB replica set at `127.0.0.1:27027`, database `engolearn_merge_check`.
- Placement persistence `mongo`, real Auth cookie/session repositories and registration transactions enabled.
- Existing V3 twenty-question DRAFT snapshot imported unchanged into the isolated database; `dev` and `allow-draft=true` enabled only for this runtime. No fake identity, result, score or claim inserted.
- These checks do not modify the team's existing database or publish the DRAFT test set.

## Browser acceptance

1. Landing CTA opens the real Placement start page as Guest.
2. Guest starts an attempt, answers three Reading questions and submits.
3. Submitted page shows login/register actions and no score or level.
4. Register from that page creates a real account; navigation returns through submitted and auto-claim to the original result.
5. Result shows 3/20, A1, three correct and seventeen unanswered; browser reload preserves the result. Review displays saved selections.
6. Logout, start/submit a second Guest attempt, then select login. An incorrect password stays on the form with an error. Correct login returns to the existing original result, matching the backend USER_HAS_RESULT policy; it does not overwrite it with the second Guest score.
7. Mongo readback confirms the original attempt has a user owner, SUBMITTED, correct_count=3, placement_level=A1 and claim_expires_at exactly two hours after ended_at.

## Real HTTP acceptance

28/28 assertions passed through the FE proxy. These exercise real backend requests and browser-style cookie jars: Guest result denied with 401, unrelated session denied with 404, incorrect password rejected, existing-account login claims the original Guest attempt, result/review access, idempotent repeated claim, logout denial and score continuity after login again.

## Maven verification

- Merged checkout: `mvnw.cmd verify`: {'tests': 681, 'failures': 7, 'errors': 48, 'skipped': 28}.
- Placement: 132 tests, all passed. ArchitectureTest: nine checks passed.
- Main baseline exported into a separate directory and run with the same original `.env` configuration via `spring.config.import`: {'tests': 549, 'failures': 7, 'errors': 48, 'skipped': 28}.
- Failing test identities added by this merge compared with that baseline: 0.

Full verify is not green. The report distinguishes the live Placement flow from the repository-wide suite failures; no checks were disabled or weakened. The first baseline attempt lacked `.env` and failed resolving AUTH_JWT_SECRET; only the rerun with the matching configuration is used above.

## Remaining boundaries

- Native Listening audio playback and actual waiting through the two-hour claim expiry were not browser-tested in this run. Reading questions suffice to exercise the auth/claim path.
- Auth requires a working Mongo transaction manager/replica set for registration after main's Subscription account initialization changes.
- Placement defaults to sample/in-memory persistence unless `engolearn.placement.persistence.mode=mongo` is configured; the verified runtime explicitly used Mongo.
- FE restricts Placement to USER after login, while Placement controllers/services currently consume only the authenticated user ID. Backend role enforcement for ADMIN/CONTENT_MANAGER is a separate gap; this merge does not change role policy.
- Landing still contains copy saying Placement is being prepared even though the route is now live; this is a copy follow-up.
- No remote push or deployment performed.
