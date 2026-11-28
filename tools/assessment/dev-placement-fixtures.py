#!/usr/bin/env python3
"""Print the local-only fixture subset used by the Placement-to-Assessment demo."""

import argparse
import shutil
from local_draft import load_pack
import hashlib
import json
from pathlib import Path
from urllib.parse import urlsplit

SAMPLES = Path(__file__).resolve().parent.parent / "mongo-json-importer" / "samples"


def collections(name):
    return json.loads((SAMPLES / name).read_text(encoding="utf-8"))["collections"]


def verify_wav(directory, name, digest):
    path = directory / name
    if path.suffix != ".wav" or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
        raise ValueError(f"WAV hash mismatch: {name}")


def placement_fixture():
    catalog = collections("placement/placement-demo-catalog.importer.json")
    # Form 01 and the portable copy share question IDs. Use the bundled WAVs in dev.
    test_set = next(form for form in catalog["placement_test_sets"]
                    if form["_id"] == "d8680b52-9272-4142-8b08-395c14b4980e"
                    and form["status"] == "PUBLISHED")
    bank = {question["_id"]: question for question in catalog["placement_questions"]}
    questions = [bank[question_id] for question_id in test_set["question_ids"]]
    portable = collections("placement-v3-test-sets-v2.importer.json")["placement_test_sets"][0]
    local_audio = {item["id"]: item["audio"] for item in portable["items"] if item["skill"] == "LISTENING"}
    for item in test_set["items"]:
        if item["skill"] != "LISTENING":
            continue
        question = bank[item["id"]]
        asset = local_audio[item["id"]]
        url = urlsplit(asset["url"])
        prefix = "/api/v1/placement/media/"
        name = Path(url.path).name
        if url.scheme or url.netloc or url.path != prefix + name:
            raise ValueError(f"Expected a packaged Placement WAV for {item['id']}")
        verify_wav(SAMPLES / "placement-media", name, name.removesuffix(".wav"))
        if item["audio"]["duration_seconds"] != asset["duration_seconds"]:
            raise ValueError(f"Placement WAV duration differs for {item['id']}")
        item["audio"] = asset
        question.pop("audio_media_id", None)
        question["audio_url"] = url.path
    return {"placement_test_sets": [test_set], "placement_questions": questions}


def periodic_fixture():
    papers = collections("assessment-demo.json")["assessment_papers"]
    for paper in papers:
        if not paper["demonstration"] or not paper["published"] or paper["rules_version"] != 2:
            raise ValueError("Expected published version-2 Assessment fixtures for dev")
        for unit in paper["units"]:
            if unit["kind"].startswith("LISTENING"):
                verify_wav(SAMPLES / "assessment-media", unit["audio_path"], unit["audio_sha256"])
    return {"assessment_papers": papers}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--draft-pack", type=Path)
    parser.add_argument("--output-media", type=Path)
    args = parser.parse_args()
    subscription = collections("subscription-development-sample.json")
    subscription.pop("subscription_accounts")
    paths = collections("learning-path-demo.json")
    fixtures = placement_fixture() | periodic_fixture() | subscription | {
        name: paths[name] for name in ("question_pool", "question_pool_passages")
    }
    if args.draft_pack:
        paper, files = load_pack(args.draft_pack)
        if any(p["series"] == paper["series"] for p in fixtures["assessment_papers"]):
            raise ValueError("Mã đề nhập trùng đề demo hiện có.")
        shutil.copytree(SAMPLES / "assessment-media", args.output_media)
        for name, source in files.items():
            shutil.copyfile(source, args.output_media / name)
            verify_wav(args.output_media, name, name.split("-")[-1][:-4])
        fixtures["assessment_papers"].append(paper)
    # Use the team's disposable management accounts to verify F03 UI permissions.
    fixtures.update(collections("management-accounts.importer.json"))
    fixtures.update(collections("micro-skills.json"))
    print(json.dumps(fixtures, ensure_ascii=True))


if __name__ == "__main__":
    main()
