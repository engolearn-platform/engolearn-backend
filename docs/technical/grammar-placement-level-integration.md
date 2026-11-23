> Quy tắc mở cấp cập nhật 23/11/2026: hoàn thành tối thiểu70% chủ đề đủ điều kiện của cấp trước, làm tròn lên bằng số nguyên. Chủ đề phải hoàn thành đủ tất cả bài Published; mỗi bài vẫn3/5. Giữ mọi unlock đã lưu và quyền từ profile; không tự nâng level User. Xem [thiết kế và kiểm chứng](grammar-unlock-70-review.md).

# Grammar / Placement profile integration — 23/11/2026

## Approved MVP scope
The owner approved connecting the existing Placement-updated User profile to Grammar, reviewing/publishing Grammar content, then acceptance testing both modules. Preserve the team's monotonic profile policy and avoid User schema or other consumer changes.

User owns user_profiles.cefr_level, exposes UserLevelLookup; Placement promotes only on owned finalization/Guest claim. Grammar reads that contract without writing User data. This is an explicit MVP adaptation of BR-39.10/BR-F1.9, not an implementation of distinct latest-result and lifetime-maximum fields. Those remain deferred; do not relabel cefr_level as the latest test result. In this MVP currentLevel means the persisted profile level capped to Grammar's A1–B2 range. Missing profile/level means A1; provider failures must propagate, never grant speculative access. C1/C2 map to B2. No new public API or profile collection/field.

On roadmap, inside the existing per-learner transaction/lock, persist DEFAULT A1 then profile-based levels through the capped level with reason PLACEMENT; evaluate existing completion unlocks afterward. Union only: never delete a prior unlock, never complete a lesson due to Placement. Roadmap returns the capped profile level and newly granted non-default levels. Direct topic/theory/practice authorization also accepts levels <= profile, so deep links work before the first roadmap visit; lesson sequencing still applies. Only roadmap/completion evaluation persists unlocks, never unprotected background tasks.

Keep threshold 3/5 and existing learning layouts/guards. No FE redesign is needed because roadmap already consumes currentLevel/unlocked. Published material alone is visible to learners.

## Implementation plan
1. Add real HTTP/Mongo regression tests to existing PlacementUserLevelIntegrationTest: fresh A1; real Placement submit→A2; direct published A2 access before roadmap; persisted PLACEMENT unlock; no automatic lesson progress; C1/C2 capped B2 without lowering User. Watch failure before production edits.
2. Inject UserLevelLookup into GrammarLearnerServiceImpl, map levels explicitly, update profile-aware authorization and monotonic evaluation; add PLACEMENT enum value. Document grammar→user.contract dependency and owning feature docs. No User/Topic/Placement production source changes.
3. Review all current canonical A1–B2 content against trustworthy references and actual admin checklist; reconcile live content before touching it. Correct only identified defects in corresponding live draft and canonical seed, preserving all IDs/progress. Ready/publish only reviewed complete topics through versioned Admin API; retain any unresolved topic as Draft and report it. Never seed-replace the live DB.
4. Real FE proxy→fresh BE→Mongo acceptance for profile/Grammar, Guest claim, ownership, 401/403, passing 3/5 and failed retry; FE build/lint/auth and BE verify on disposable test DB. Explain skips/unrelated failures; scoped independent final review. No push/merge main/dev.

## Boundaries
- Changed production code: grammar/service/impl/GrammarLearnerServiceImpl.java; grammar/model/enums/GrammarUnlockReason.java.
- Integration regression: placement/service/PlacementUserLevelIntegrationTest.java.
- Documents: this design, architecture dependency table, Grammar feature README/runtime and FE grammar learning doc.
- Data: existing versioned Grammar drafts only after review; lifecycle through Admin services. Preserve learner histories, published revisions and User records.

## Execution complete

Implemented with the existing monotonic User profile contract. Real local proxy acceptance82/82; scoped tests30/30; full BE still has the same61 failures/errors outside these modules. All43 topics/106 lessons are now published in local Mongo; seed remainsDraft. Detailed evidence: grammar-placement-level-result.md.
