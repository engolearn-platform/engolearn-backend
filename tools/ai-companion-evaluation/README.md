# Live AI Companion memory evaluation

This opt-in evaluation uses real Gemini through the running local Companion API. It is not part of normal Maven tests and does not replace deterministic backend checks.

## Run

Use a disposable local demo database with the current Companion quota policy, Mongo replica set, separate Qdrant collection and configured Gemini credentials. Start the backend on localhost:8082 with the isolated database and allow origin localhost:3001. Confirm that port 8082 is not connected to shared or production data. The runner registers a new synthetic account per scenario and calls real Gemini, consuming quota and potentially billable provider usage.

From the backend root (Python 3.10+; standard library only):

    python tools/ai-companion-evaluation/memory_eval.py --isolated-demo --report target/memory-evaluation.json

To rerun selected scenarios:

    python tools/ai-companion-evaluation/memory_eval.py --isolated-demo --case automatic_off --case cross_chat_recall_update_forget --report target/memory-evaluation-recheck.json

Reports include synthetic inputs, canonical memory contents, response text, events and checks. Keep them out of production telemetry; do not use real learner data. The runner removes each registered account's synthetic memories and conversations and logs out; synthetic account records remain in the isolated database. A nonzero exit indicates a REVIEW/ERROR or cleanup problem, not necessarily a deterministic backend regression. Inspect the report before classifying failures.

The suite covers Vietnamese/English direct requests, automatic ON/OFF, no-save instructions, hypotheticals, third-party and temporary information, negated preferences, quoted examples, and cross-chat recall/update/forget. Keyword checks are a lightweight smoke rubric; review semantic correctness manually. Because the provider is probabilistic, passing one run does not establish reliable intent classification or user consent.

See [the memory validation decision](../../docs/technical/ai-companion/memory-proposal-validation.md) for limitations and verification history.

## Phase 1 multi-tool evaluation

See [fixture and execution rubric](multi-tool-fixture-rubric.md) and [fixture manifest](multi-tool-fixtures.json). Validate the exact approved prompts and manifest without network calls:

    python tools/ai-companion-evaluation/multi_tool_eval.py --dry-run

Dry-run validates the fixture specification only, not database provisioning, Gemini, or semantic acceptance. Real runs require an independently established disposable local backend, synthetic accounts, normal quota and entitlement, and safe existing credentials. Raw evidence belongs in ignored target/, never tracked files.
