#!/usr/bin/env python3
"""Preflight or register Assessment WAV files through the feature-owned API."""
import argparse
import getpass
from pathlib import Path
import re
import sys
import urllib.parse
import wave

from assessment_import import ADMIN, Api, ApiError, documents, draft_content, wav


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
