#!/usr/bin/env python3
"""Pin the canonical Linux catalog into the reproducible Android source tree."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "app/src/main/assets/catalog/v2/catalog.json"
LOCK = ROOT / "app/src/main/assets/catalog/v2/catalog.lock.json"
REPOSITORY = "anaxonda/uttermux-linux"


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def validate(data: bytes) -> dict:
    document = json.loads(data)
    if document.get("schemaVersion") != 2:
        raise ValueError("catalog schemaVersion must be 2")
    variants = document.get("variants", [])
    voices = document.get("voices", [])
    variant_ids = {item.get("id") for item in variants}
    if None in variant_ids or len(variant_ids) != len(variants):
        raise ValueError("catalog has empty or duplicate variant IDs")
    voice_ids = [item.get("id") for item in voices]
    if None in voice_ids or len(set(voice_ids)) != len(voice_ids):
        raise ValueError("catalog has empty or duplicate voice IDs")
    if any(item.get("variantId") not in variant_ids for item in voices):
        raise ValueError("catalog voice refers to an unknown variant")
    if not document.get("provenance", {}).get("curatedSha256"):
        raise ValueError("catalog provenance is missing")
    return document


def atomic_write(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        try:
            os.unlink(temporary)
        except FileNotFoundError:
            pass


def check() -> None:
    data = CATALOG.read_bytes()
    document = validate(data)
    lock = json.loads(LOCK.read_text(encoding="utf-8"))
    if lock.get("schemaVersion") != 1 or lock.get("repository") != REPOSITORY:
        raise ValueError("catalog lock identity is invalid")
    if lock.get("sha256") != digest(data):
        raise ValueError("catalog lock SHA-256 does not match the embedded catalog")
    if lock.get("provenance") != document.get("provenance"):
        raise ValueError("catalog lock provenance does not match the embedded catalog")
    commit = lock.get("commit", "")
    if len(commit) != 40 or any(character not in "0123456789abcdef" for character in commit):
        raise ValueError("catalog lock does not contain a full Git commit")


def fetch_json(url: str) -> dict:
    request = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json", "User-Agent": "UtterMux catalog sync"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--ref", default="main")
    parser.add_argument("--source", type=Path, help="use a local catalog instead of the network")
    parser.add_argument("--commit", help="full source commit required with --source")
    args = parser.parse_args()
    if args.check:
        check()
        return 0
    if args.source:
        if not args.commit:
            parser.error("--commit is required with --source")
        commit, data = args.commit, args.source.read_bytes()
    else:
        commit = fetch_json(f"https://api.github.com/repos/{REPOSITORY}/commits/{args.ref}")["sha"]
        with urllib.request.urlopen(f"https://raw.githubusercontent.com/{REPOSITORY}/{commit}/catalog/v2/catalog.json", timeout=30) as response:
            data = response.read()
    document = validate(data)
    lock = {
        "schemaVersion": 1,
        "repository": REPOSITORY,
        "commit": commit,
        "path": "catalog/v2/catalog.json",
        "sha256": digest(data),
        "provenance": document["provenance"],
    }
    atomic_write(CATALOG, data)
    atomic_write(LOCK, (json.dumps(lock, indent=2, sort_keys=True) + "\n").encode())
    check()
    print(f"Pinned {REPOSITORY}@{commit} ({len(document['variants'])} variants, {len(document['voices'])} voices)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
