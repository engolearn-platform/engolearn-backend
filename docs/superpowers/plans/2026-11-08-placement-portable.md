# Portable Placement Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline, task by task.

**Goal:** Make a clone runnable for teammates with real Auth, Mongo and Listening.
**Architecture:** Checked-in canonical seed and hash-named media; Spring MVC media
resources; immutable V2 snapshot; explicit local profile and safe setup preflight.
**Tech Stack:** Java 21, Spring Boot/MVC, MongoDB replica set, PowerShell, Node.
**Spec:** docs/superpowers/specs/2026-11-08-placement-portable-design.md

## Global Constraints
No new dependencies, no V1 mutation, no overwrite import, no committed credentials.
Preserve the existing application.yaml edit; real HTTP/Mongo proves integration.

## Review Focus
Traversal/symlink escapes; byte-range/HEAD; multiple codes and mixed statuses;
existing differing input IDs; missing external authoring bank in a clean clone.

### Task 1: BE media and snapshot selection
- [x] Add failing tests for static WAV HEAD/Range/path boundaries and latest version.
- [x] Implement feature-owned media config/resolver and latest-per-code filtering.
- [x] Run targeted tests; Expected: media/version tests and ArchitectureTest pass.

### Task 2: Self-contained data package
- [x] Copy and checksum 96 original WAVs; retain V1 byte content.
- [x] Generate V2 relative-URL snapshot from checked-in seed; remove default parent-bank dependency.
- [x] Adapt validator and source hash metadata; Expected: build/check passes in an exported tree.

### Task 3: Local setup and configuration
- [x] Add placement-local profile, safe preflight/apply scripts and complete .env.example.
- [x] Remove only our earlier three global Spring toggles from local ignored .env.
- [x] Update onboarding docs; Expected: root tests no longer inherit local Placement switches.

### Task 4: Fresh runtime, review and delivery
- [x] Verify fresh replica-set import, real Guest/Auth/claim/media through FE.
- [x] Run full verify and distinguish baseline failures; request a fresh final review.
- [x] Commit scoped changes and push integration branch for review when possible.
