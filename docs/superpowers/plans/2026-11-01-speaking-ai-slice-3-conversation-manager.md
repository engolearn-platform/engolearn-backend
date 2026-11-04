# Speaking AI Slice 3 — Conversation Manager Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a pure deterministic Conversation Manager over validated turn proposals and pinned conversation state while preserving the existing legacy completion policy.

**Architecture:** Add an immutable `ConversationStateView` with one shared completion-policy calculation, a `DialogueDecision` internal domain result, and a stateless `ConversationManager`. Adapt the existing service's private completion checks to delegate to the shared calculation; do not wire the manager into runtime orchestration in Slice 3.

**Tech Stack:** Java 21, Spring Boot 4.1.1, JUnit Jupiter 6, existing speaking model packages.

**Spec:** `docs/superpowers/specs/2026-11-01-speaking-ai-slice-3-conversation-manager-design.md`, based on Reviewed Artifact 02 and `Note/speaking-with-AI/redesign-inside/02-conversation-manager-decision-fixtures.json`.

## Global Constraints

- Preserve exactly seven Dialogue Acts; `WRAP_UP` is not allowed in normal decisions.
- Backend owns completion, status, termination reason, counters, and accepted evidence; model proposals never mutate them.
- Preserve `REQUIRED_GOALS_V2` and legacy/null `LEGACY_RATIO_V1` behavior, including BR-F1.40.
- Invalid and evaluation-error outcomes are recovery/terminal outcomes, not normal acts.
- Spoken `CLOSING_UTTERANCE` is not a UI End command.
- No database, network, clock, LLM, FE, WebSocket, or Slice 2 gateway dependency.

## Review Focus

- A required goal accepted in the current proposal must not remain a target for `GUIDE_GOAL`; test CM-17.
- Required milestone with Optional goals remaining must stay ACTIVE; test CM-10.
- All goals before the minimum valid-turn threshold must stay ACTIVE and reject `WRAP_UP`; test CM-11.
- A terminal decision must suppress a proposed normal reply; test CM-12 and CM-22/23.
- A legacy/null snapshot must use its pinned ratio policy instead of V2 Required flags; test the shared policy adapter.

### Task 1: Shared conversation state and completion policy

**Files:**
- Create: `src/main/java/fit/iuh/engolearn/speaking/model/ConversationStateView.java`
- Modify: `src/main/java/fit/iuh/engolearn/speaking/model/SpeakingContentSnapshot.java`
- Modify: `src/main/java/fit/iuh/engolearn/speaking/service/SpeakingSessionService.java`
- Test: `src/test/java/fit/iuh/engolearn/speaking/model/ConversationStateViewTest.java`

**Interfaces:**
- Consumes: `SpeakingSession`, `SpeakingContentSnapshot`, `SpeakingGoal`.
- Produces: `ConversationStateView.from(SpeakingSession)`, `completionFor(SpeakingContentSnapshot)`, and immutable completion metrics for the manager and service.

- [x] Write failing tests for V2 Required+minimum completion, V2 all-goals auto-end, and legacy/null ratio completion with different Required flags.
- [x] Run the focused test and observe failure because the shared state view/policy does not exist.
- [x] Implement the immutable state view and one completion calculation; make `SpeakingSessionService.complete()` and `allGoalsAndTurns()` delegate to it without changing their external behavior.
- [x] Run the focused test and the existing `SpeakingSessionServiceTest`.
- [x] Commit `feat(speaking): centralize conversation completion policy`.

### Task 2: Pure dialogue decision contract and manager

**Files:**
- Create: `src/main/java/fit/iuh/engolearn/speaking/model/DialogueDecision.java`
- Modify: `src/main/java/fit/iuh/engolearn/speaking/model/ValidatedTurnProposal.java`
- Modify: `src/main/java/fit/iuh/engolearn/speaking/service/SpeakingDraftValidator.java`
- Create: `src/main/java/fit/iuh/engolearn/speaking/service/ConversationManager.java`
- Test: `src/test/java/fit/iuh/engolearn/speaking/service/ConversationManagerTest.java`

**Interfaces:**
- Consumes: `ValidatedTurnProposal`, `ConversationStateView`, `SpeakingContentSnapshot`, and a system trigger.
- Produces: `ConversationManager.decide(ValidatedTurnProposal, ConversationStateView, SpeakingContentSnapshot, ConversationManager.SystemTrigger) -> DialogueDecision`.

- [x] Write failing tests for recovery/error counters, explicit End, spoken stop, answer-first act priority, target-after-evidence, `WRAP_UP` denial, safe no-reply, and auto-end suppression.
- [x] Run the focused test and observe failure because the manager/decision types do not exist.
- [x] Implement the minimum pure decision algorithm: terminal/recovery precedence, projected accepted evidence and valid count, shared completion metrics, normal-act validation, and deterministic reasons; add compatibility constructors/accessors so Slice 1 validator tests remain valid.
- [x] Update `SpeakingDraftValidator` only to construct the richer proposal outcome; do not change its locked schema/evidence behavior.
- [x] Run `ConversationManagerTest` and `SpeakingDraftValidatorContractTest`.
- [x] Commit `feat(speaking): add pure conversation manager decisions`.

### Task 3: CM fixture coverage and regression verification

**Files:**
- Modify: `src/test/java/fit/iuh/engolearn/speaking/service/ConversationManagerTest.java`
- Create: `docs/testing/speaking-ai-slice-3-manager-results.md`

**Interfaces:**
- Consumes: the manager API from Task 2 and `02-conversation-manager-decision-fixtures.json` CM-01–CM-30.
- Produces: parameterized deterministic coverage for all fixture IDs and a truthful Slice 3 verification note.

- [x] Add a fixture loader assertion for every CM-01–CM-30 ID and parameterized assertions for the pure decision families represented by the fixtures; document orchestration-only cases as deferred.
- [x] Run the parameterized test and resolve missing behavior through focused regression assertions.
- [x] Refine only the pure manager/state view where a fixture exposes a contract mismatch; do not add persistence behavior.
- [x] Run the focused Slice 3 tests and full backend `./mvnw.cmd verify`.
- [x] Record actual results, including Docker/runtime limitations, in the results note and commit `test(speaking): cover slice 3 manager fixtures`.

## Final Verification

- [x] Focused: `./mvnw.cmd -q -Dtest=ConversationStateViewTest,ConversationManagerTest,SpeakingDraftValidatorContractTest,SpeakingSessionServiceTest test`.
- [x] Full backend: `./mvnw.cmd verify`.
- [x] Inspect `git status --short`, `git diff --check`, and the final diff to confirm no Slice 2 files or shared artifacts changed.
