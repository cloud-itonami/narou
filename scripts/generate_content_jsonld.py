#!/usr/bin/env python3
"""Generate JSON-LD content metadata from plain-text source files."""

from __future__ import annotations

import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path


def build_creative_work(source_file: Path, base_url: str) -> dict:
    raw = source_file.read_text(encoding="utf-8")
    lines = [line.strip() for line in raw.splitlines() if line.strip()]
    headline = lines[0] if lines else source_file.stem
    digest = hashlib.sha256(raw.encode("utf-8")).hexdigest()

    return {
        "@type": "CreativeWork",
        "@id": f"{base_url.rstrip('/')}/{source_file.stem}",
        "name": headline,
        "inLanguage": "ja",
        "dateModified": datetime.now(timezone.utc).isoformat(),
        "identifier": source_file.name,
        "sha256": digest,
        "wordCount": len(raw.split()),
        "text": raw,
    }


def generate(source_dir: Path, output_file: Path, base_url: str) -> None:
    files = sorted(source_dir.glob("*.txt"))
    graph = [build_creative_work(file, base_url=base_url) for file in files]

    jsonld = {
        "@context": "https://schema.org/",
        "@type": "Dataset",
        "name": "Narou Generated Content Bundle",
        "dateModified": datetime.now(timezone.utc).isoformat(),
        "distribution": {
            "@type": "DataDownload",
            "encodingFormat": "application/ld+json",
            "contentUrl": str(output_file),
        },
        "hasPart": graph,
    }

    output_file.parent.mkdir(parents=True, exist_ok=True)
    output_file.write_text(json.dumps(jsonld, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generate JSON-LD content for Narou project")
    parser.add_argument("--source-dir", default="projects/etzhayyim-project-narou/content/sources")
    parser.add_argument("--output", default="projects/etzhayyim-project-narou/content/generated/content.bundle.jsonld")
    parser.add_argument("--base-url", default="https://narou.etzhayyim.com/content")
    args = parser.parse_args()

    generate(Path(args.source_dir), Path(args.output), base_url=args.base_url)
