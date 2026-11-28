"""Opt-in live Gemini memory evaluation on an isolated local demo backend."""
import argparse
import http.cookiejar
import json
import re
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path

BASE = "/api/v1/companion"

class Session:
    def __init__(self, host):
        self.host = host
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.conversations = []

    def request(self, method, path, body=None):
        headers = {"Origin": "http://localhost:3001"}
        data = None
        if body is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(body).encode()
        request = urllib.request.Request(self.host + path, data=data, headers=headers, method=method)
        try:
            with self.opener.open(request, timeout=105) as response:
                return response.status, response.read().decode("utf-8")
        except urllib.error.HTTPError as error:
            return error.code, error.read().decode("utf-8")

    def api(self, method, path, body=None):
        status, text = self.request(method, path, body)
        result = json.loads(text)
        if not 200 <= status < 300:
            raise RuntimeError(f"{method} {path} HTTP {status} {result.get('code', '')}")
        return result.get("data", result)

    def register(self):
        self.api("POST", "/api/v1/auth/register", {
            "email": "memory-eval-" + uuid.uuid4().hex[:12] + "@example.com",
            "password": "Eval-" + uuid.uuid4().hex,
        })
        if self.api("GET", "/api/v1/auth/me").get("role") != "USER":
            raise RuntimeError("Unexpected learner role")

    def setting(self, enabled):
        self.api("PUT", BASE + "/memory/settings", {"automaticMemoryEnabled": enabled})
        assert self.api("GET", BASE + "/memory/settings")["automaticMemoryEnabled"] == enabled

    def memories(self):
        return self.api("GET", BASE + "/memory")["items"]

    def turn(self, prompt):
        conversation = self.api("POST", BASE + "/conversations")["id"]
        self.conversations.append(conversation)
        status, body = self.request("POST", BASE + "/conversations/" + conversation + "/turns", {
            "turnId": str(uuid.uuid4()), "content": prompt, "retry": False,
        })
        if status != 200:
            raise RuntimeError(f"Turn HTTP {status} {json.loads(body).get('code', '')}")
        events = []
        for block in re.split(r"\r?\n\r?\n", body):
            name = re.search(r"^event:\s*(.+)$", block, re.M)
            data = re.search(r"^data:\s*(.+)$", block, re.M)
            if name and data:
                events.append((name.group(1).strip(), json.loads(data.group(1))))
        errors = [d.get("code") for n, d in events if n == "error"]
        completed = [d for n, d in events if n == "completed"]
        if errors or len(completed) != 1:
            raise RuntimeError(f"Terminal stream error {errors}")
        reply = completed[0].get("assistantMessage", {}).get("content", "")
        return [d for n, d in events if n == "memory_operation"], reply

    def cleanup(self):
        for row in self.memories():
            self.api("DELETE", BASE + "/memory/" + row["id"] + "?expectedVersion=" + str(row["version"]))
        for cid in self.conversations:
            self.api("DELETE", BASE + "/conversations/" + cid)
        self.api("POST", "/api/v1/auth/logout")

# Expected behavior is evaluated from events, canonical rows and reply, not an exact string.
# Durable automatic saves are product expectations, not deterministic backend guarantees.
CASES = [
    ("vi_direct_off", False, "Bro nh\u1edb gi\u00f9m tui l\u00e0 tui th\u00edch h\u1ecdc ti\u1ebfng Anh qua podcast nha.", "create", ("podcast",)),
    ("en_direct_off", False, "Could you keep this in mind for later? I prefer short English lessons about astronomy.", "create", ("astronomy",)),
    ("automatic_on", True, "I study English every evening and strongly prefer short lessons about astronomy. This is my usual learning routine.", "create", ("astronomy",)),
    ("automatic_off", False, "I study English every evening and strongly prefer short lessons about astronomy. This is my usual learning routine.", "none", ()),
    ("explicit_do_not_save", True, "Do not save or remember this: I enjoy talking about origami.", "none", ()),
    ("hypothetical", True, "Imagine I were a marine biologist. How would I introduce myself in English?", "none", ()),
    ("third_party", True, "My friend enjoys learning English through chess. Give me a sample sentence about my friend.", "none", ()),
    ("temporary", True, "I am tired today. Please explain this sentence simply.", "none", ()),
    ("negated_preference", False, "Please remember that I do not like learning English through videos.", "negative", ("video",)),
    ("quoted_example", True, 'Translate this example sentence: "I love learning English through opera."', "none", ()),
]

def run_case(host, case):
    name, enabled, prompt, expectation, keywords = case
    session = Session(host)
    registered = False
    result = {"case": name, "automatic": enabled, "input": prompt, "expected": expectation}
    try:
        session.register()
        registered = True
        session.setting(enabled)
        events, reply = session.turn(prompt)
        rows = session.memories()
        result["actions"] = [x["action"] for x in events]
        result["contents"] = [x["content"] for x in rows]
        result["reply"] = reply
        text = " ".join(result["contents"]).lower()
        if expectation == "none":
            ok = not rows and not events
        else:
            ok = bool(rows) and "CREATED" in result["actions"] and all(word in text for word in keywords)
            if expectation == "negative":
                ok = ok and bool(re.search(r"dislike|does not|doesn't|not like|avoid|not enjoy|prefers not|not a fan", text))
        result["status"] = "PASS" if ok else "REVIEW"
    except Exception as error:
        result["status"] = "ERROR"
        result["error"] = str(error)
    finally:
        if registered:
            try:
                session.cleanup()
            except Exception as error:
                result["cleanup_error"] = str(error)
    return result

def run_recall(host):
    session = Session(host)
    registered = False
    result = {"case": "cross_chat_recall_update_forget", "expected": "create, recall, update, delete"}
    try:
        session.register()
        registered = True
        session.setting(False)
        events, _ = session.turn("Please remember my favorite English listening exercise is hearing amber bells at Willow Pier.")
        created = next(x for x in events if x["action"] == "CREATED")
        mid = created["memoryId"]
        row = next(x for x in session.memories() if x["id"] == mid)
        events, reply = session.turn("What unusual sound and location do I like for English listening practice?")
        result["recall_reply"] = reply
        recall_ok = "amber bells" in reply.lower() and "willow pier" in reply.lower() and not events
        events, _ = session.turn("Bro, update that saved listening preference: now I prefer silver bells at Cedar Pier.")
        updated = next(x for x in events if x["action"] == "UPDATED" and x["memoryId"] == mid)
        current = next(x for x in session.memories() if x["id"] == mid)
        update_ok = current["version"] == row["version"] + 1 and "silver bells" in current["content"].lower()
        events, _ = session.turn("Please forget my saved English listening preference about silver bells at Cedar Pier.")
        delete_ok = any(x["action"] == "DELETED" and x["memoryId"] == mid for x in events) and not session.memories()
        result["checks"] = {"recall": recall_ok, "update": update_ok, "chat_forget": delete_ok}
        result["status"] = "PASS" if all(result["checks"].values()) else "REVIEW"
    except Exception as error:
        result["status"] = "ERROR"
        result["error"] = str(error)
    finally:
        if registered:
            try:
                session.cleanup()
            except Exception as error:
                result["cleanup_error"] = str(error)
    return result

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--isolated-demo", action="store_true", required=True, help="Confirm port 8082 uses disposable demo DB and real Gemini.")
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--case", action="append", help="Run only the named cases (repeatable).")
    args = parser.parse_args()
    host = "http://localhost:8082"
    if Session(host).api("GET", "/actuator/health").get("status") != "UP":
        raise SystemExit("Demo backend not healthy")
    results = []
    selected = set(args.case or [case[0] for case in CASES] + ["cross_chat_recall_update_forget"])
    unknown = selected - {case[0] for case in CASES} - {"cross_chat_recall_update_forget"}
    if unknown:
        parser.error("Unknown cases: " + ", ".join(sorted(unknown)))
    for case in CASES:
        if case[0] not in selected:
            continue
        result = run_case(host, case)
        results.append(result)
        print(case[0], result["status"], result.get("actions", []), result.get("error", ""), flush=True)
        args.report.write_text(json.dumps(results, ensure_ascii=True, indent=2), encoding="utf-8")
    if "cross_chat_recall_update_forget" in selected:
        result = run_recall(host)
        results.append(result)
        print(result["case"], result["status"], result.get("checks", {}), result.get("error", ""), flush=True)
        args.report.write_text(json.dumps(results, ensure_ascii=True, indent=2), encoding="utf-8")
    return 0 if all(x["status"] == "PASS" and "cleanup_error" not in x for x in results) else 1

if __name__ == "__main__":
    sys.exit(main())
