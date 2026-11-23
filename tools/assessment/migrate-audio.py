#!/usr/bin/env python3
"""Preflight or register Assessment WAV files through the feature-owned API."""
import argparse
import getpass
import hashlib
import http.cookiejar
import json
from pathlib import Path
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
import wave

MAX_BYTES = 4_000_000
ADMIN = "/api/v1/assessment/admin"


def documents(path):
    with path.open(encoding="utf-8-sig") as source:
        payload = json.load(source)
    collections = payload["collections"]
    return collections.get("assessment_papers", []), [
        item["paper"] for item in collections.get("assessment_attempts", [])
    ]


def wav(root, relative, expected, limit):
    path = (root / relative).resolve(strict=True)
    if not path.is_relative_to(root) or path.suffix.lower() != ".wav":
        raise ValueError(f"Invalid WAV path under the media directory: {relative}")
    if not 0 < path.stat().st_size <= MAX_BYTES:
        raise ValueError(f"Invalid file size: {relative}")
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    if expected is not None and digest != expected.lower():
        raise ValueError(f"SHA-256 differs: {relative}")
    with wave.open(str(path)) as reader:
        frames, rate = reader.getnframes(), reader.getframerate()
        frame_size = reader.getnchannels() * reader.getsampwidth()
        if not 0 < frames <= limit * rate or len(reader.readframes(frames)) != frames * frame_size:
            raise ValueError(f"Truncated or too long (limit {limit}s): {relative}")
    return digest, path


def preflight(args):
    root = args.media_dir.resolve(strict=True)
    all_papers, history = [], []
    for manifest in args.manifests:
        papers, snapshots = documents(manifest)
        all_papers.extend(papers)
        history.extend(snapshots)

    assets = {}
    for paper in all_papers + history:
        version = paper.get("rules_version", 0)
        for item in paper["units"]:
            kind = item["kind"]
            if not kind.startswith("LISTENING") or item.get("audio_media_id"):
                continue
            expected = item.get("audio_sha256")
            if not isinstance(expected, str) or not re.fullmatch(r"[0-9a-fA-F]{64}", expected):
                raise ValueError("Missing SHA-256 for a legacy Listening unit")
            limit = (28 if version == 2 else 50) if kind == "LISTENING_SINGLE" else 120
            digest, path = wav(root, item["audio_path"], expected, limit)
            assets[digest] = path

    sample = wav(root, args.sound_check, None, 50) if args.sound_check else None
    if args.draft_series:
        if len(all_papers) != 1 or all_papers[0].get("rules_version") != 2:
            raise ValueError("Creating a draft requires exactly one rules-version 2 paper")
        if all_papers[0].get("demonstration"):
            raise ValueError("Use a reviewed paper approved for prod, separate from the dev-only fixture")
        if not args.draft_name:
            raise ValueError("--draft-name is required when creating a draft")
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,79}", args.draft_series):
            raise ValueError("Invalid draft series")
    if not assets and not sample and not args.draft_series:
        raise ValueError("No local audio references or sound-check WAV found")
    return all_papers, assets, sample


class ApiError(Exception):
    def __init__(self, status, code, path):
        self.status = status
        super().__init__(f"{path}: HTTP {status} {code}")


class Api:
    def __init__(self, base):
        parsed = urllib.parse.urlsplit(base)
        if parsed.scheme != "https" and not (parsed.scheme == "http" and parsed.hostname in {"localhost", "127.0.0.1"}):
            raise ValueError("Use an HTTPS origin, or a loopback HTTP dev origin")
        if not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:
            raise ValueError("Use a base origin without credentials, a query or a fragment")
        self.base = base.rstrip("/")
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def request(self, path, method="GET", body=None, content_type="application/json"):
        if body is not None and not isinstance(body, bytes):
            body = json.dumps(body, ensure_ascii=False).encode()
        request = urllib.request.Request(
            self.base + path, method=method, data=body,
            headers={"Content-Type": content_type, "Accept": "application/json"},
        )
        try:
            with self.opener.open(request, timeout=90) as response:
                content = response.read()
                return json.loads(content)["data"] if content else None
        except urllib.error.HTTPError as failure:
            try:
                code = json.loads(failure.read()).get("code", "")
            except (UnicodeError, ValueError):
                code = ""
            if not re.fullmatch(r"[A-Z0-9_]{0,80}", code or ""):
                code = ""
            raise ApiError(failure.code, code, path) from None

    def upload(self, digest, path, sound_check=False):
        boundary = f"EngoLearn-{uuid.uuid4().hex}"
        fields = f"--{boundary}\r\nContent-Disposition: form-data; name=\"sha256\"\r\n\r\n{digest}\r\n"
        if sound_check:
            fields += f"--{boundary}\r\nContent-Disposition: form-data; name=\"soundCheck\"\r\n\r\ntrue\r\n"
        header = (fields + f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"assessment.wav\"\r\n"
                  "Content-Type: audio/wav\r\n\r\n").encode()
        body = header + path.read_bytes() + f"\r\n--{boundary}--\r\n".encode()
        data = self.request(ADMIN + "/media", "POST", body, f"multipart/form-data; boundary={boundary}")
        if data["sha256"] != digest:
            raise ValueError("Server did not register the expected digest")


def draft_content(paper, name):
    questions = ("level", "prompt", "options", "explanation", "transcript")
    units = [{
        "kind": unit["kind"],
        "passage": unit.get("passage"),
        "sourceAudioUnit": None,
        "audioSha256": unit["audio_sha256"].lower() if unit["kind"].startswith("LISTENING") else None,
        "questions": [
            {**{key: question.get(key) for key in questions},
             "skillCode": question["skill_code"], "correctOption": question["correct_option"]}
            for question in unit["questions"]
        ],
    } for unit in paper["units"]]
    skills = [{key: skill.get(key) for key in ("code", "name", "example", "section")} for skill in paper["skills"]]
    return {"name": name, "skills": skills, "units": units}


def stage_draft(api, args, paper):
    drafts = ADMIN + "/drafts"
    path = drafts + "/" + urllib.parse.quote(args.draft_series, safe="")
    content = draft_content(paper, args.draft_name)
    try:
        draft = api.request(path)
    except ApiError as error:
        if error.status != 404:
            raise
        draft = api.request(drafts, "POST", {"series": args.draft_series, "name": args.draft_name})
    if all(draft.get(key) == value for key, value in content.items()):
        print("The draft already contains the requested content.")
        return
    if draft["skills"] or draft["units"]:
        raise ValueError("Existing draft contains content. Review it in the editor to avoid overwriting changes")
    api.request(path, "PUT", {**content, "revision": draft["revision"]})
    print("Draft saved. Open the editor to preview, review and publish.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifests", type=Path, nargs="+", help="Importer JSON with assessment_papers or assessment_attempts")
    parser.add_argument("--media-dir", type=Path, required=True)
    parser.add_argument("--sound-check", help="Relative WAV to register as the sound check")
    parser.add_argument("--draft-series", help="Stage the checked paper under a new or empty draft series")
    parser.add_argument("--draft-name")
    parser.add_argument("--apply", action="store_true", help="Upload after a successful preflight")
    parser.add_argument("--base-url")
    parser.add_argument("--email")
    args = parser.parse_args()
    try:
        papers, assets, sample = preflight(args)
        if args.draft_series:
            draft_content(papers[0], args.draft_name)
        print(f"Preflight passed. {len(assets)} distinct Listening files, sound check {'included' if sample else 'not requested'}.")
        if not args.apply:
            return 0
        if not args.base_url or not args.email:
            raise ValueError("--apply requires --base-url and --email")
        api = Api(args.base_url)
        secret = getpass.getpass("Content staff password: ")
        api.request("/api/v1/auth/login", "POST", {"email": args.email, "password": secret})
        del secret
        for index, (digest, path) in enumerate(assets.items(), start=1):
            api.upload(digest, path)
            print(f"Registered {index}/{len(assets)}: {digest}")
        if sample:
            api.upload(*sample, sound_check=True)
            print("Sound check registered.")
        if args.draft_series:
            stage_draft(api, args, papers[0])
        return 0
    except (ApiError, KeyError, OSError, TypeError, ValueError, wave.Error) as error:
        print(f"Stopped: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
