# Phase 1 AI Companion evaluation ? design checkpoint

Status: proposed baseline, discussed 2026-11-26. Do not treat this as a completed evaluation or a release gate already agreed.

## Scope and method
Feature freeze M1?M4; evaluate actual backend with real Gemini on disposable synthetic learners, not mocked output. Reuse the existing memory_eval.py and prior smoke scenarios. Six groups, 42 proposed scenarios: persona/instruction following (5), learner adaptation (5), long-term memory (14), business tool calling (6), screen awareness (6), multi-turn/cross-capability (6). Include positive and negative scenarios.

Each case records ID/objective, fixture/preconditions, one or more inputs, expected behavior, forbidden behavior, verification method, evidence/result. Lock criteria before baseline runs. Record model/config, run ID and actual evidence, not exact expected prose.
- Deterministic assertions: inspect observed events, tool name/arguments where observable, canonical records and ownership, admission, retry/idempotency, quota and side effects. Do not infer absent tool calls from absent success events.
- Semantic review: compare response and memory meaning against user input, source data and explicit rubric. Human review initially, no LLM judge infrastructure required. A valid paraphrase passes; unsupported inference, invented data and misleading claims fail.
- Both may be needed in one scenario. Model output is probabilistic; a deterministic checker of an observed action is not proof the model always chooses that action. Report PASS/PARTIAL/FAIL/BLOCKED; keep security/privacy/unauthorized side effects as hard failures regardless of aggregate score.
- Repeat high-risk cases at least three independent times with reset fixtures; report observed pass counts and sample size, not an estimate of production reliability. Capture only synthetic content, redact secrets, keep raw reports out of version control.

## Scenario inventory
Persona: new learner, stable identity across topics, truthfulness, supportive without dependency, conflicting instructions.
Adaptation: paired A1/B2 question, practical-example preference, current request overrides memory, absent level.
Memory: explicit VI/EN, auto ON/OFF, no-save, hypothetical, third-party, temporary, negation, quotation, cross-chat recall/update/forget, sensitive data, duplicate/conflict, screen content is not learner memory.
Business tools: assessment lookup, skill arguments, no irrelevant tool, no assessment data, preview/full visibility, unavailable tool.
Screen: overview, dialogue, vocabulary, expressions, missing facts, no attachment. Backend authorization and invalid/expired references also require deterministic integration tests.
Cross-capability: multi-turn references, CEFR+memory, memory+assessment, memory+screen, official vs inferred data, full M1?M4 learner journey.

## Example
Automatic Memory OFF + fresh account, ordinary statement about a learning goal. Assert successful turn, no canonical new memory, no committed memory-operation event; separately inspect proposed tool behavior if trace available. Review response for helpfulness and no false claim of saving. A rejected tool proposal is a model-behavior failure even if backend enforcement passed. If trace is absent, model tool-choice outcome is unknown.
Explicit preference for practical examples: assert owner/category/committed record; separately assess faithful meaning (not 'dislikes theory'). A syntactically valid record is not necessarily semantically correct.

## Execution order
Design -> baseline -> classify failure -> targeted correction -> regression -> release decision.
Quota admission must work first. Runtime policy publication/activation and snapshots must be coherent; never mutate historical snapshots or bypass quota merely to get a passing eval.
