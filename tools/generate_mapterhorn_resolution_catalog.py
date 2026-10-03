#!/usr/bin/env python3
"""Refresh Tellus's compact Mapterhorn source-resolution catalog."""

from __future__ import annotations

import argparse
import json
import math
import os
from pathlib import Path
import tempfile
from urllib.request import Request, urlopen


ATTRIBUTION_URL = "https://download.mapterhorn.com/attribution.json"
DOWNLOAD_URLS_URL = "https://download.mapterhorn.com/download_urls.json"
DEFAULT_OUTPUT = Path("src/main/resources/tellus/elevation/mapterhorn_source_resolutions.json")
MAX_RESPONSE_BYTES = 2 * 1024 * 1024


def fetch_json(url: str) -> object:
    request = Request(url, headers={"User-Agent": "Tellus-Mapterhorn-Catalog-Generator/1.0"})
    with urlopen(request, timeout=30) as response:
        length = response.headers.get("Content-Length")
        if length is not None and int(length) > MAX_RESPONSE_BYTES:
            raise ValueError(f"Response from {url} exceeds the safety limit")
        payload = response.read(MAX_RESPONSE_BYTES + 1)
    if len(payload) > MAX_RESPONSE_BYTES:
        raise ValueError(f"Response from {url} exceeds the safety limit")
    return json.loads(payload)


def build_catalog(attribution: object, downloads: object) -> dict[str, object]:
    if not isinstance(attribution, list) or not isinstance(downloads, dict):
        raise ValueError("Unexpected Mapterhorn metadata shape")
    version = downloads.get("version")
    if not isinstance(version, str) or not version:
        raise ValueError("Mapterhorn download metadata does not contain a version")

    sources: dict[str, float] = {}
    for item in attribution:
        if not isinstance(item, dict):
            raise ValueError("Unexpected Mapterhorn attribution entry")
        source = item.get("source")
        resolution = item.get("resolution")
        if not isinstance(source, str) or not source:
            raise ValueError("Mapterhorn attribution entry has no source ID")
        if isinstance(resolution, bool) or not isinstance(resolution, (int, float)):
            raise ValueError(f"Mapterhorn source {source} has no numeric resolution")
        resolution = float(resolution)
        if not math.isfinite(resolution) or resolution <= 0:
            raise ValueError(f"Mapterhorn source {source} has an invalid resolution")
        if source in sources:
            raise ValueError(f"Duplicate Mapterhorn source ID: {source}")
        sources[source] = resolution

    if sources.get("glo30") != 30.0:
        raise ValueError("Mapterhorn catalog is missing the 30 m global source")
    return {
        "version": version,
        "attribution_url": ATTRIBUTION_URL,
        "coverage_tilejson_url": "https://single-archive-tiles.mapterhorn.com/coverage.json",
        "sources": dict(sorted(sources.items())),
    }


def write_atomic(path: Path, catalog: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(catalog, indent=2, sort_keys=False) + "\n"
    descriptor, temporary_name = tempfile.mkstemp(prefix=f"{path.name}-", suffix=".tmp", dir=path.parent)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as output:
            output.write(text)
        os.chmod(temporary_name, 0o644)
        os.replace(temporary_name, path)
    finally:
        if os.path.exists(temporary_name):
            os.unlink(temporary_name)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    catalog = build_catalog(fetch_json(ATTRIBUTION_URL), fetch_json(DOWNLOAD_URLS_URL))
    write_atomic(args.output, catalog)
    print(f"Wrote {len(catalog['sources'])} Mapterhorn sources ({catalog['version']}) to {args.output}")


if __name__ == "__main__":
    main()
