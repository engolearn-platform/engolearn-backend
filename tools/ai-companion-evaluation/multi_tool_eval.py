"""Bounded opt-in Phase 1 evaluator. Dry run performs no network I/O."""
import argparse
import json
import os
import re
import uuid
from datetime import datetime, timezone
from pathlib import Path

from memory_eval import BASE, Session

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
TARGET = ROOT / "target"
HOST = "http://localhost:8082"
NAMES = (
    "findLearningTopics", "getTopicLearningProgress", "getLearningPathProgress",
    "getAssessmentHistory", "getLatestAssessmentResult", "searchMemory",
    "createMemory", "updateMemory", "deleteMemory", "UNKNOWN_TOOL",
)
TRACE = re.compile(
    r"^.*\bCompanionTurnServiceImpl\s*:\s*Companion tool call: tool=(" + "|".join(NAMES) +
    r"), decision=(invoke|result|skip|error), stage=(provider_response|tool_handler|tool_budget|"
    r"tool_executor|tool_timeout|tool_execution), code=(TOOL_CALL_RECEIVED|CALL_LIMIT_REACHED|"
    r"EXECUTOR_SATURATED|TOOL_TIMEOUT|TOOL_EXECUTION_FAILED|SUCCESS|NO_DATA|FORBIDDEN|"
    r"INVALID_ARGUMENTS|CONFLICT|ERROR|EMPTY_RESULT|MALFORMED_RESULT|UNKNOWN_RESULT)"
    r"(?:, round=(\d{1,3}), ordinal=(\d{1,3}), elapsedMs=(\d{1,9}))?\s*$",
    re.M,
)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def load_cases():
    manifest = json.loads((HERE / "multi-tool-fixtures.json").read_text(encoding="utf-8"))
    approved = (HERE / "multi-tool-evaluation-scenarios.md").read_text(encoding="utf-8")
    prompts = dict(re.findall(r"^\| ((?:GUID|DISC|REFL)-0[1-3]) \| [^|\n]+ \| ([^|\n]+?) \|$", approved, re.M))
    expected_ids = {f"{group}-0{i}" for group in ("GUID", "DISC", "REFL") for i in range(1, 4)}
    require(set(prompts) == expected_ids, "exact approved prompt inventory missing")
    require(set(manifest["accounts"]) == set("ABCD"), "expected A/B/C/D fixtures")
    cases = {}
    for item in manifest["cases"] + manifest["negatives"]:
        case = dict(item)
        cid = case["id"]
        require(cid not in cases, "duplicate case")
        require(case["account"] in manifest["accounts"], "unknown fixture owner")
        require(1 <= case["repetitions"] <= 3, "unbounded repetition")
        require(bool(case["expected"]), "empty rubric")
        if cid in prompts:
            require(bool(case["forbidden"]), "missing forbidden behavior")
            case["prompt"] = prompts[cid]
        require(bool(case["prompt"]), "empty prompt")
        cases[cid] = case
    require({c["id"] for c in manifest["cases"]} == expected_ids, "scenario inventory mismatch")
    require(len(manifest["cases"]) == 9 and len(manifest["negatives"]) >= 6, "incomplete rubric")
    for cid in ("GUID-01", "GUID-02", "GUID-03", "REFL-03", "NEG-RESTRICTED", "NEG-OUTAGE", "NEG-INJECTION"):
        require(cases[cid]["repetitions"] == 3, "missing independent high-risk repetitions")
    require(manifest["accounts"]["A"]["topic"]["completedTopicItems"] == 3, "fixture count mismatch")
    require(manifest["accounts"]["B"]["path"]["completedPaths"] == 0, "empty fixture mismatch")
    a, b, c = manifest["accounts"]["C"]["assessment"]
    require(a["paper"] == b["paper"] != c["paper"] and a["total"] == b["total"] > 0,
            "comparison fixture mismatch")
    return manifest, cases


def safe_trace(log, offset):
    try:
        if not log or not log.is_file():
            return None
        if not offset <= log.stat().st_size <= offset + 512 * 1024:
            return None  # rotated or excess output; never silently truncate evidence
        with log.open("rb") as stream:
            stream.seek(offset)
            content = stream.read(512 * 1024).decode("utf-8", errors="replace")
    except OSError:
        return None
    return [dict(zip(("tool", "decision", "stage", "code", "round", "ordinal", "elapsedMs"), m.groups()))
            for m in TRACE.finditer(content)]


def live_case(case, repeat, evidence_path, log):
    # This is an operator attestation, NOT proof of isolation. Do not run against a shared service.
    evidence = json.loads(evidence_path.read_text(encoding="utf-8"))
    for key in ("isolated_disposable_db", "isolated_qdrant", "synthetic_only", "normal_quota_entitlement",
                "existing_approved_gemini_budget", "fresh_fixture", "exclusive_traffic"):
        require(evidence.get(key) is True, "missing isolation/fixture attestation: " + key)
    require(evidence.get("case") == case["id"] and evidence.get("repeat") == repeat,
            "fixture evidence is for a different run")
    require(evidence.get("account") == case["account"], "fixture owner mismatch")
    require(bool(evidence.get("model")) and bool(evidence.get("fixture_snapshot_ref")),
            "missing model or canonical fixture truth reference")
    email = os.environ.get("MULTI_TOOL_EVAL_EMAIL", "")
    password = os.environ.get("MULTI_TOOL_EVAL_PASSWORD", "")
    require(email.startswith("multi-tool-eval-") and email.endswith("@example.com") and bool(password),
            "synthetic account credentials unavailable")
    require(not log or log.is_file(), "trace log unavailable")
    TARGET.mkdir(exist_ok=True)
    run = str(uuid.uuid4())
    report = {
        "runId": run, "case": case["id"], "repeat": repeat,
        "fixture": case["account"], "model": evidence["model"],
        "fixtureSnapshotRef": evidence["fixture_snapshot_ref"],
        "time": datetime.now(timezone.utc).isoformat(),
        "prompt": case["prompt"], "expected": case["expected"],
        "forbidden": case.get("forbidden"),
        "disposition": "BLOCKED", "semanticReview": "UNKNOWN",
        "canonicalAfter": "UNKNOWN", "quotaLedger": "UNKNOWN",
        "toolSelection": "UNKNOWN", "observedTrace": None,
    }
    path = TARGET / f"multi-tool-eval-{run}.json"
    session = Session(HOST)
    authenticated = False
    offset = log.stat().st_size if log else 0
    try:
        session.api("POST", "/api/v1/auth/login", {"email": email, "password": password})
        authenticated = True
        me = session.api("GET", "/api/v1/auth/me")
        require(me.get("role") == "USER" and me.get("email") == email, "unexpected identity")
        session.setting(False)
        offset = log.stat().st_size if log else 0
        # Existing Session.turn uses the actual SSE API and requires one terminal completion.
        memory_events, reply = session.turn(case["prompt"])
        report.update(disposition="PARTIAL", terminal="completed", response=reply,
                      memoryOperationCount=len(memory_events))
    except Exception as exc:
        # No raw exception/body, credentials or ids in console/report.
        report["failureType"] = type(exc).__name__
    finally:
        report["observedTrace"] = safe_trace(log, offset)
        if report["observedTrace"]:
            report["toolSelection"] = "OBSERVED_NAMES_AND_CODES_ONLY"
        # Never delete arbitrary learner memories. Only delete conversations created by this runner.
        if authenticated:
            try:
                for cid in session.conversations:
                    session.api("DELETE", BASE + "/conversations/" + cid)
                session.api("POST", "/api/v1/auth/logout")
                report["conversationCleanup"] = "PASS"
            except Exception as exc:
                report["conversationCleanup"] = type(exc).__name__
        path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{case['id']} repeat {repeat}: {report['disposition']}; evidence {path.name}")
    return 0 if report["disposition"] == "PARTIAL" and report.get("conversationCleanup") == "PASS" else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--dry-run", action="store_true")
    mode.add_argument("--isolated-demo", action="store_true")
    parser.add_argument("--case")
    parser.add_argument("--repeat", type=int, default=1)
    parser.add_argument("--evidence", type=Path)
    parser.add_argument("--trace-log", type=Path)
    args = parser.parse_args()
    manifest, cases = load_cases()
    if args.dry_run:
        print(f"PASS: 9 approved prompts, {len(manifest['accounts'])} fixture definitions, "
              f"{len(manifest['negatives'])} negatives, "
              f"{sum(c['repetitions'] for c in cases.values())} planned independent runs; "
              "no network, no fixture provisioning, no semantic verdict")
        return 0
    if args.case not in cases or not args.evidence:
        parser.error("live mode requires a single valid --case and --evidence")
    case = cases[args.case]
    if not 1 <= args.repeat <= case["repetitions"]:
        parser.error("repeat outside approved bound")
    try:
        return live_case(case, args.repeat, args.evidence, args.trace_log)
    except (OSError, ValueError, KeyError) as exc:
        # Preflight failure is not a provider failure; no model call.
        print(f"BLOCKED: preflight {type(exc).__name__}; no live verdict")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
