# Grammar coverage expansion implementation plan

> Execute sequential integration with superpowers:executing-plans. Independent level drafting uses isolated artifact files, then full parent review. Reuse D checkouts; no commit/push/main.

**Goal:** Fill verified instructional gaps in existing A1-B2 Grammar, using credible sources and the shared seed format.
**Architecture:** Content-only additions through real Admin API with version checks; existing model/UI/layout retained. Keep all additions DRAFT.
**Tech Stack:** Existing Spring Boot DTO/checklist, MongoDB replica set, FE Vite proxy; no dependencies.
**Spec:** ../../technical/grammar-coverage-expansion.md

## Constraints/review focus
No duplicate or undemonstrated objectives; no false claim CEFR prescribes one grammar list. No source-copying exercises. One valid MCQ answer, constrained FIB, no ungrammatical correction of legitimate variants. No old progress or immutable revisions overwritten. New topics only where grouping is justified; <=10 active lessons/topic. Current 3 published A1 lessons remain published; new children remain draft.

## Tasks
- [x] Inventory current lessons against official reference coverage; document gaps and intentional exclusions.
- [x] Author A1/A2/B1/B2 additions, check all sources and review semantics/answer keys.
- [x] Validate all new lessons via production DTO/checklist/normalizer; no DB writes before PASS.
- [x] Back up local Grammar; create only missing Topic/Lesson via FE proxy Admin API; manifest prevents duplication on resume.
- [x] Read back full API/DB, verify old content/progress/sessions/revisions and new draft states.
- [x] Merge additions into existing canonical shared importer seed/counter, update docs/source coverage and verify links/identities/counts.

Result: see docs/technical/grammar-coverage-result.md. All additions remain DRAFT; previous65 lessons and learner state verified unchanged.
