#!/usr/bin/env python3
"""Shared manifest, WAV and authenticated API operations for Assessment imports."""
import hashlib
import http.cookiejar
import json
import re
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


class ApiError(Exception):
    def __init__(self, status, code, path):
        self.status = status
        self.code = code
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
