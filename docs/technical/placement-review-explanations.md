# Placement review explanations — 2026-11-08

User requested enabling per-question explanations. Existing immutable test-set snapshots already store explanation; API review DTO/mapper and FE review currently omit it.

Scope: append optional/null-omitted explanation only to PlacementReviewQuestionResponse and map from assigned snapshot. Existing authorization, ownership, completed-status checks and no-store remain. FE renders a plain-text Giải thích section only for nonblank content; no invented explanations for legacy snapshots. No transcript or answer leakage through attempt/current/result/claim endpoints. No scoring, User level updates, CTA destination, data or dependency changes. Existing local branches only; no push/main changes.

Checks: real HTTP RED before implementation -> GREEN for owned V2 review; anonymous denied, attempt payload excludes explanation. FE build/lint; full BE verify compared by test identity to prior baseline. Browser real test account reading Reading and Listening explanations, mobile layout. Verification details appended after actual execution.

## Verification result

- Live HTTP RED: existing V2 review lacked explanation. GREEN8/8: test User receives20 actual explanations including Listening, unauthenticated review denied, ordinary attempt JSON has no explanation field, review still omits transcript/identity metadata. Test guard checks JSON keys rather than searching a word in English question content.
- Browser: existing dedicated UI test account reviewed Reading question1 and Listening question15; correct snapshot explanations displayed. Mobile390: no horizontal overflow (390/390), three explanation sections for the current page; viewport restored. Screenshot: placement-review-explanation.png in the C: workspace.
- FE build pass6.23s; lint0 errors/72 existing warnings. BE verify686 tests:7 failures,48 errors,29 skips; exact55 failure identities match main baseline with no new failure identities. Placement137 tests:136 pass,1 Windows symlink privilege skip. Full suite remains failing.
- No new accounts/attempts or seed changes in this task; reused test accounts/results from the resume checks, in cookie-isolated loopback UI services3002/8083. Original user sessions were not logged out or claimed. Temporary services are stopped after checks; main3000/8080 remain.
- Existing local FE fix/placement-guest-navbar and BE merge/placement-guest-user-main; no branch creation, push or main changes. Pre-existing application.yaml excluded.
- Historical snapshot with missing/null/blank explanation: optional field, UI omits the box; did not migrate snapshots or fabricate content. Existing null-explanation sample tests passed; no separate legacy-data browser scenario in this run.

## Advice only, not implemented

The Bắt đầu học CTA is still disabled. Recommended destination: /topics after profile-level synchronization, so catalog access uses the real learner level. Registration creates UserProfile.cefrLevel=A1; Placement currently persists only its own attempt placementLevel. UserLevelLookup supplies the profile level to Topic. There is no existing Placement->User level write integration; the approved graph currently lists Placement dependencies=None. A future narrow User capability/event should own atomic monotonic updates, invoked for authenticated completion or successful Guest claim, idempotent by attempt ID. Do not change profile level during a result/review GET. Keep higher existing levels and preserve learning progress.

Evidence: explanation-red.log, explanation-green.log, explanation-fe-build.log, explanation-fe-lint.log, explanation-be-verify.log, explanation-be-comparison.json in the C: workspace. Repeat the real API check via tools/placement/check-review-explanation.py with PLACEMENT_TEST_EMAIL, PLACEMENT_TEST_PASSWORD and PLACEMENT_TEST_ATTEMPT for an existing local V2 test result; no credentials are included in this document.
