#!/usr/bin/env python3
"""Simple interval scheduler for JSON-LD content generation."""

from __future__ import annotations

import argparse
import subprocess
import time
from datetime import datetime, timezone
from pathlib import Path


def run_generator(generator_script: Path, source_dir: Path, output: Path, base_url: str) -> int:
    command = [
        "python3",
        str(generator_script),
        "--source-dir",
        str(source_dir),
        "--output",
        str(output),
        "--base-url",
        base_url,
    ]

    result = subprocess.run(command, check=False)
    return result.returncode


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Run JSON-LD generator on a fixed interval")
    parser.add_argument("--interval-seconds", type=int, default=3600)
    parser.add_argument("--runs", type=int, default=0, help="0 means run forever")
    parser.add_argument("--source-dir", default="projects/etzhayyim-project-narou/content/sources")
    parser.add_argument("--output", default="projects/etzhayyim-project-narou/content/generated/content.bundle.jsonld")
    parser.add_argument("--base-url", default="https://narou.etzhayyim.com/content")
    parser.add_argument(
        "--generator-script",
        default="projects/etzhayyim-project-narou/scripts/generate_content_jsonld.py",
    )
    args = parser.parse_args()

    iteration = 0
    while True:
        iteration += 1
        started_at = datetime.now(timezone.utc).isoformat()
        code = run_generator(
            generator_script=Path(args.generator_script),
            source_dir=Path(args.source_dir),
            output=Path(args.output),
            base_url=args.base_url,
        )

        status = "ok" if code == 0 else f"error({code})"
        print(f"[{started_at}] run={iteration} status={status}")

        if args.runs > 0 and iteration >= args.runs:
            break

        time.sleep(args.interval_seconds)
