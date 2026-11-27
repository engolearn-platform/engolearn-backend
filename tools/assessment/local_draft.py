#!/usr/bin/env python3
"""Prepare an isolated demo source and create its editable draft through the admin API."""
import argparse
from collections import Counter
from getpass import getpass
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys
from urllib.parse import urlsplit
import wave

from assessment_import import ADMIN, Api, ApiError, draft_content, wav

SAMPLES = Path(__file__).resolve().parents[1] / "mongo-json-importer" / "samples"
SERIES = "PFC-LOCAL-DEMO"
SECTIONS = {"L01": "LISTENING", "L03": "LISTENING",
            "R01": "READING", "R02": "READING", "R04": "READING"}
# Each row corresponds to one block in the agreed v2 matrix.
BLOCKS = [
    ("LISTENING_SINGLE", 10, (2, 2, 3, 3)),
    ("LISTENING_PASSAGE", 1, (1, 3, 1, 0)), ("LISTENING_PASSAGE", 1, (0, 0, 3, 2)),
    ("READING_SINGLE", 10, (1, 2, 3, 4)),
    ("READING_PASSAGE", 1, (5, 0, 0, 0)), ("READING_PASSAGE", 1, (0, 5, 0, 0)),
    ("READING_PASSAGE", 1, (0, 1, 4, 0)), ("READING_PASSAGE", 1, (0, 0, 1, 4)),
]


def require(condition, message):
    if not condition:
        raise ValueError(message)


def string(value, maximum):
    return isinstance(value, str) and bool(value.strip()) and len(value) <= maximum


def template(folder):
    folder.mkdir(parents=True, exist_ok=False)
    source = json.loads((SAMPLES / "assessment-demo.json").read_text())["collections"]["assessment_papers"][0]
    paper = {key: source[key] for key in ("rules_version", "skills", "units")}
    paper.update(series=SERIES, name="Đề thực hành của nhóm")
    (folder / "paper.json").write_text(json.dumps(paper, ensure_ascii=False, indent=2), encoding="utf-8")
    (folder / "wav").mkdir()
    for unit in paper["units"]:
        if unit.get("audio_path"):
            name = unit["audio_path"]
            shutil.copyfile(SAMPLES / "assessment-media" / name, folder / "wav" / name)


def load_pack(folder):
    raw = json.loads((folder / "paper.json").read_text(encoding="utf-8-sig"))
    require(raw.get("rules_version") == 2 and string(raw.get("name"), 180), "Cần đề v2 và tên đề.")
    series = raw["series"]
    require(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,79}", series), "Mã đề sai.")
    skills = raw["skills"]
    require(len(skills) == 5 and {s["code"]: s["section"] for s in skills} == SECTIONS,
            "Cần đúng 2 mã Nghe và 3 mã Đọc đã kết nối.")
    for s in skills:
        require(string(s.get("name"), 180) and (s.get("example") is None or isinstance(s["example"],str) and len(s["example"]) <= 1000), f"Kỹ năng {s['code']} sai mô tả.")
    units = raw["units"]
    require(len(units) == 26, "Cần đúng 26 đơn vị.")
    root = (folder / "wav").resolve(strict=True)
    used = Counter()
    files = {}
    index = number = 0
    cleaned = []
    for kind, count, distribution in BLOCKS:
        levels = Counter()
        for _ in range(count):
            u = units[index]
            index += 1
            listening = kind.startswith("LISTENING")
            require(u["kind"] == kind and len(u["questions"]) == (1 if kind.endswith("SINGLE") else 5),
                    f"Đơn vị {index}: sai loại hoặc số câu.")
            passage = u.get("passage")
            require(passage is None or isinstance(passage, str) and len(passage) <= 12000, f"Đơn vị {index}: đoạn sai.")
            if kind == "READING_PASSAGE":
                require(string(passage, 12000), f"Đơn vị {index}: thiếu đoạn đọc.")
            unit = {"kind": kind, "passage": passage, "questions": []}
            if listening:
                name, digest = u["audio_path"], u["audio_sha256"]
                require(isinstance(name, str) and not Path(name).is_absolute() and ".." not in Path(name).parts,
                        f"Đơn vị {index}: cần đường dẫn WAV tương đối.")
                require(isinstance(digest, str) and re.fullmatch(r"[0-9a-fA-F]{64}", digest), f"Đơn vị {index}: sai SHA.")
                digest, path = wav(root, name, digest, 28 if kind.endswith("SINGLE") else 120)
                require(path not in files.values(), f"Đơn vị {index}: dùng lại cùng tệp WAV.")
                # Keep separate physical paths, even when two recordings have identical bytes.
                local_name = f"pfc-import-{index}-{digest}.wav"
                files[local_name] = path
                unit.update(audio_path=local_name, audio_sha256=digest)
            else:
                require(not any(u.get(k) for k in ("audio_path","audio_sha256","audio_media_id")), f"Đơn vị {index}: phần Đọc có audio.")
            for q in u["questions"]:
                number += 1
                code = q["skill_code"]
                options = q["options"]
                require(type(q["number"]) is int and q["number"] == number and q["level"] in ("A1", "A2", "B1", "B2")
                        and SECTIONS.get(code) == kind.split("_")[0], f"Câu {number}: sai số, mức hoặc kỹ năng.")
                require(string(q["prompt"], 2000) and string(q["explanation"], 2000)
                        and isinstance(options, list) and len(options) == 4 and all(string(v, 600) for v in options)
                        and type(q["correct_option"]) is int and 0 <= q["correct_option"] <= 3,
                        f"Câu {number}: kiểm đề bài, bốn lựa chọn, đáp án và lời giải.")
                transcript = q.get("transcript")
                require((transcript is None or isinstance(transcript, str) and len(transcript) <= 4000)
                        and (not listening or string(transcript, 4000)), f"Câu {number}: sai transcript.")
                unit["questions"].append({k: q.get(k) for k in ("number","level","skill_code","prompt","options","correct_option","explanation","transcript")})
                used[code] += 1
                levels[q["level"]] += 1
            cleaned.append(unit)
        require(tuple(levels[level] for level in ("A1", "A2", "B1", "B2")) == distribution,
                f"Khối kết thúc tại câu {number}: sai phân bố mức A1–B2.")
    require(all(used[c] == 10 for c in SECTIONS), "Mỗi kỹ năng phải có đúng 10 câu.")
    paper = dict(series=series, name=raw["name"], rules_version=2, version=1, demonstration=True, published=False,
                 skills=[{k:s.get(k) for k in ("code","section","name","example")} for s in skills], units=cleaned)
    fingerprint = hashlib.sha256(json.dumps(paper, sort_keys=True, ensure_ascii=False).encode()).hexdigest()
    paper["_id"] = "local-import-" + fingerprint
    return paper, files


def stage(api, paper):
    # The editor represents inherited WAVs by unit index. Verify their exact source
    # even when an existing draft already has matching text and indices.
    page = 0
    while True:
        rows = api.request(f"{ADMIN}/papers?page={page}")
        source = next((r for r in rows if r["series"] == paper["series"]), None)
        if source:
            require(source["id"] == paper["_id"], "Nguồn hoặc phiên bản đã thay đổi. Giữ nguyên dữ liệu.")
            break
        require(len(rows) == 25, "Chưa có nguồn của gói trong DB demo. Khởi động demo với PFC_DRAFT_PACK.")
        page += 1
    path = ADMIN + "/drafts/" + paper["series"]
    try:
        result = api.request(path)
    except ApiError as error:
        if error.status != 404:
            raise
        try:
            result = api.request(ADMIN + "/drafts", "POST", dict(series=paper["series"], name=paper["name"], expectedSourceId=paper["_id"]))
        except ApiError as conflict:
            if conflict.status != 409 or conflict.code != "DRAFT_EXISTS":
                raise
            result = api.request(path)
    expected = draft_content(paper, paper["name"])
    for i, u in enumerate(expected["units"]):
        if u["audioSha256"]:
            u.update(sourceAudioUnit=i, audioSha256=None)
    require(result["version"] == 2 and all(result.get(k) == v for k, v in expected.items()),
            "Nháp đã khác nội dung gói. Giữ nguyên bản đang có.")
    print(f"Nháp {result['series']} v{result['version']} · revision {result['revision']} · /admin/assessment")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("template", "check", "stage"))
    parser.add_argument("folder", type=Path)
    parser.add_argument("--origin", default="http://localhost:28300")
    parser.add_argument("--email", default="content-manager@example.test")
    args = parser.parse_args()
    try:
        if args.action == "template":
            template(args.folder)
        paper, files = load_pack(args.folder)
        print(f"{paper['series']}: 50 câu, 26 đơn vị, {len(files)} WAV; {paper['_id']}")
        if args.action == "stage":
            origin = urlsplit(args.origin)
            require(origin.hostname in {"localhost", "127.0.0.1"} and origin.path in {"", "/"},
                    "Cần origin loopback của demo.")
            api = Api(args.origin)
            api.request("/api/v1/auth/login", "POST", {"email": args.email, "password": getpass("Mật khẩu quản lý demo: ")})
            stage(api, paper)
    except (ApiError, OSError, EOFError, ValueError, KeyError, AttributeError, IndexError, TypeError, wave.Error) as error:
        sys.exit(f"Dừng: {error}")


if __name__ == "__main__":
    main()
