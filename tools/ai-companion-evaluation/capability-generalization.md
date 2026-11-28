# Capability generalization ? rich learner exploratory suite

This suite is distinct from the approved Phase 1 fixture evaluation. It measures natural learner requests on the synthetic rich-learner account through the actual UI. The case manifest is frozen before a live run; do not change tool descriptions, model settings, or case criteria during the run. It is not a new release gate. Do not treat this suite as a workflow specification for tools.

## Procedure

- Verify the synthetic account, current fixture truth, current Vietnam date, BE/FE revision, model and quota before execution. The dated expectations assume the Nov 28, 2026 rich-learner fixture; if the date or learner data changed, record the changed oracle before running rather than silently reuse stale facts.
- Use an already authenticated dedicated Playwright browser, one fresh conversation per case. No real learner data, dev-auth bypass, or direct writes. Keep memory mutation evaluation in the existing isolated memory suite. Do not intentionally trigger writes in this read-oriented suite.
- Capture user input, complete assistant response, per-case safe tool names/status and round counts, end-to-end latency, and account-level quota before/after. Distinguish missing traces from no calls; avoid logging private arguments, credentials or hidden values. Store raw evidence under ignored target/.
- Score semantically against actual fixture truth and each case's rubric; no fixed tool name, order or exact phrasing requirement. PASS = sufficiently grounded, complete and actionable; PARTIAL = materially incomplete but substantially correct; FAIL = material false assertion, intent substitution or access violation; TIMEOUT/BLOCKED = no evaluable response or unmet prerequisite. Record hard safety failures separately. Do not retry merely to improve a score.
- Review capability understanding, composition/planning, constraint following, output grounding and tool restraint independently. A provider SUCCESS status is not a semantic pass. One sample per question is exploratory, not a reliability estimate; a new suite has no paired historical baseline.
- Do not add case-specific instructions to the shared system prompt or tool descriptions merely to improve the score. Use failures to inform diagnosis, not to prescribe a step-by-step workflow.

Previous capability-only evaluation: 7/9 new cases PASS, 2/9 PARTIAL; original nine strict cases 0 PASS, 7 PARTIAL, 1 FAIL, 1 TIMEOUT. These are separate observations, not an aggregate 7/9 release pass. The user accepted the new-case result and authorized the AI-branch merge. This suite adds new scenarios without changing runtime behavior.

The existing `multi-tool-fixture-rubric.md` and isolated memory/negative tests remain separate. No production readiness claim is made.
