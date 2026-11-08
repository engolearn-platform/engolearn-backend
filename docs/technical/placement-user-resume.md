# Placement User resume — 2026-11-08

Approved in chat: show the User resume state within /placement like the supplied design. Keep shared shell and existing timer/claim policy. Work only on existing local branches; no push/main changes.

## Design / implementation steps

1. Add GET /api/v1/placement/attempts/current. It never starts an attempt or changes its original deadline. Resolve existing deadlines using the existing BE policy; this may finalize an elapsed attempt. Reuse existing start selection priority: active browser Guest, valid User result, active User, claimable Guest. Empty state is data=null; no new Guest cookie. Return a safe progress summary only (no correct answers, score or level).
2. Summary fields: attemptId,status,deadlineAt,serverNow,totalQuestions,answeredCount,nextSection,resumeOrder,lastSavedAt,claimRequired. lastSavedAt comes only from persisted answer timestamps. Return EXPIRED for an empty active attempt first resolved by this request; subsequent lookup may have no current attempt. No schema migration.
3. User /placement loads this read endpoint, shows loading/retry on failure and never silently falls back to creating a test. Existing attempt shows progress/time, continue button; finished shows result/claim button; expired offers a fresh start. Only an explicit Start action uses POST. Guest entry remains unchanged.
4. Reuse the server-offset countdown. At zero or focus refresh current status from BE; do not reset the timer or post Start on page load. Continue uses the original attempt URL and a resume question order; the attempt view opens the first unanswered question page. All answered -> open final page for review. Existing regular attempt navigation remains unchanged.
5. UI follows existing tokens, cards and buttons, responsive at mobile390. Shared sidebar remains. Last-save label omitted without a timestamp. No fake counters/timestamps.
6. Verify live HTTP against real FE proxy/BE/Mongo: absence doesn't create, progress persistence, ownership isolation, stable deadline/attempt ID, submit->result; browser resume/reload and mobile. Run FE build/lint/auth-return and required BE verify; compare baseline errors. Add/update owning docs, commit locally only; leave pre-existing application.yaml unstaged.

## Verification ledger

Implemented and checked below. Scope: Placement only; full BE baseline remains failing.

## Verified result — 2026-11-08

- Real HTTP regression: RED before endpoint existed; GREEN32/32 through FE3000 -> BE8080 -> real Mongo. Tests cover no creation on lookup, original ID/deadline, persisted answer count/timestamp, resumeOrder, foreign-browser denial, submit/current/result and logout. Mongo stores milliseconds; deadline comparisons normalize to that precision (the first live run failed a raw string precision comparison and was corrected).
- Browser via separate loopback origin127.0.0.2:3002 -> dedicated BE8083, cookie isolated from user's localhost/127.0.0.1 sessions: new account -> start -> answer8 -> leave -> current8/20,40% and original countdown -> continue to page7–9 with question9 unanswered -> reload preserves answers/countdown -> submit -> same result -> entry shows Xem kết quả. Mobile390 has no horizontal overflow (390/390); viewport restored.
- FE final build pass4.78s; lint0 errors/72 baseline warnings; auth-return4/4 pass. No new dependencies.
- BE mvnw verify:686 tests,7 failures,48 errors,29 skips. Comparison against existing main baseline: same55 failure identities, no new failures. Placement137 tests:136 pass,1 Windows symlink privilege skip. Full BE suite remains failing and is not reported as pass.
- Independent read-only source/diff review: no substantive finding in selection extraction, ownership, safe response fields, original deadline, stale-request handling or resume routing.
- Public GET response verified: data=null without a current attempt, Cache-Control:no-store, no Set-Cookie. GET never creates a new attempt; it can finalize an elapsed existing attempt under existing deadline policy.
- No seed/schema migration, existing answer rewrite or user-session logout. Verification created2 API test accounts/attempts (one during the timestamp-precision diagnostic) and1 separate UI test account/attempt. The successful API account is resume-live-539bb99c17@example.test, attempt e96a590a-9fcd-46ae-833b-ab634146c846. UI account resume-ui-1794144504002@example.test, attempt8dc79135-06a8-4f0b-af18-7fc823de176b. Test data retained for inspection.
- Limits: did not wait15 minutes for a live expiry scenario; existing deadline tests passed, but the new endpoint expiry path was reviewed rather than separately exercised with elapsed live time. UI network-error/retry and all-20-answered resume were not browser-tested. No mock/fixture integration proof was used.
- Changes remain on existing local FE fix/placement-guest-navbar and BE merge/placement-guest-user-main. No new branches, fetch, push or main updates. Existing application.yaml remains unstaged. Temporary3002/8083 services are stopped after verification; original3000/8080 services remain.

Evidence outside Git: C: workspace resume-red.log, resume-green.log, resume-be-verify.log, resume-be-comparison.json, resume-fe-build-final.log, resume-fe-lint-final.log, resume-auth-test.log and placement-user-resume.png.

## Repeat the real API acceptance check

With FE3000 and BE8080 running against local DEV Mongo (creates disposable test accounts and attempts):

```powershell
$env:PLACEMENT_LIVE_ALLOW_WRITES='1'
python tools/placement/check-current-attempt.py
```

Optional PLACEMENT_CHECK_BASE selects localhost/127.0.0.1 only. The script uses Python standard library; it is an optional verification tool, not a runtime dependency. Test accounts are not automatically deleted.
