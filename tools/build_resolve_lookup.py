#!/usr/bin/env python3
"""Build Tellus's bundled RESOLVE Ecoregions 2017 lookup.

The source shapefile is intentionally not shipped with the mod. This tool
rasterizes its ecoregion IDs at a fixed geographic resolution, run-length
encodes each latitude row, and compresses the resulting lookup with XZ.

Required Python packages: numpy, pyogrio, rasterio, shapely
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import lzma
import os
import struct
import tempfile
import time
from pathlib import Path

import numpy as np
import pyogrio
from rasterio.features import rasterize
from rasterio.transform import from_origin
from shapely.geometry import box


MAGIC = b"TRSL"
FORMAT_VERSION = 2
NO_DATA_ID = 65535
DEFAULT_ARCSECONDS = 30
DEFAULT_STRIPE_ROWS = 1024


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("shapefile", type=Path, help="Path to Ecoregions2017.shp")
    parser.add_argument("lookup_output", type=Path, help="Destination .bin.xz resource")
    parser.add_argument("metadata_output", type=Path, help="Destination metadata CSV resource")
    parser.add_argument(
        "--arcseconds",
        type=int,
        default=DEFAULT_ARCSECONDS,
        help="Raster cell size in arc-seconds (default: 30)",
    )
    parser.add_argument(
        "--stripe-rows",
        type=int,
        default=DEFAULT_STRIPE_ROWS,
        help="Rows rasterized per working stripe (default: 1024)",
    )
    return parser.parse_args()


def validate_source(frame, source_info: dict) -> None:
    if source_info.get("crs") != "EPSG:4326":
        raise ValueError(f"Expected EPSG:4326 source data, got {source_info.get('crs')!r}")

    required = {"ECO_ID", "ECO_NAME", "BIOME_NUM", "BIOME_NAME", "REALM", "LICENSE"}
    missing = required.difference(frame.columns)
    if missing:
        raise ValueError(f"Source data is missing required columns: {sorted(missing)}")

    if frame.empty or frame.geometry.isna().any() or frame.geometry.is_empty.any():
        raise ValueError("Source data must contain non-empty geometry for every ecoregion")
    if frame.ECO_ID.isna().any() or frame.ECO_ID.duplicated().any():
        raise ValueError("ECO_ID values must be present and unique")

    ids = frame.ECO_ID.astype(int)
    if (ids < 0).any() or (ids >= NO_DATA_ID).any():
        raise ValueError(f"ECO_ID values must be in the range 0..{NO_DATA_ID - 1}")


def build_lookup(
    frame,
    raw_lookup_path: Path,
    raw_runs_path: Path,
    arcseconds: int,
    stripe_rows: int,
) -> tuple[int, int, int]:
    if arcseconds <= 0 or 3600 % arcseconds != 0:
        raise ValueError("arcseconds must be a positive divisor of 3600")
    if stripe_rows <= 0:
        raise ValueError("stripe-rows must be positive")

    cells_per_degree = 3600 // arcseconds
    width = 360 * cells_per_degree
    height = 180 * cells_per_degree
    if width > 65535:
        raise ValueError("The row-run format requires a width no greater than 65535 cells")

    resolution = 1.0 / cells_per_degree
    spatial_index = frame.sindex
    geometries = list(frame.geometry)
    eco_ids = frame.ECO_ID.astype(int).to_numpy()
    source_ids = set(int(value) for value in eco_ids)
    seen_ids: set[int] = set()
    row_offsets = [0]
    run_count = 0
    transition_mask = np.empty(width, dtype=bool)
    started = time.perf_counter()

    with raw_runs_path.open("wb") as runs:
        for stripe_number, start_row in enumerate(range(0, height, stripe_rows)):
            stripe_height = min(stripe_rows, height - start_row)
            top = 90.0 - start_row * resolution
            bottom = top - stripe_height * resolution
            feature_indices = spatial_index.query(
                box(-180.0, bottom, 180.0, top),
                predicate="intersects",
            )

            # STRtree query order is unspecified. Preserve the source record
            # order so overlaps (notably Antarctic Rock and Ice) resolve in the
            # same deterministic order as the authoritative shapefile.
            shape_values = [
                (geometries[index], int(eco_ids[index]))
                for index in sorted(int(value) for value in feature_indices)
            ]
            transform = from_origin(-180.0, top, resolution, resolution)
            values = rasterize(
                shape_values,
                out_shape=(stripe_height, width),
                transform=transform,
                fill=NO_DATA_ID,
                dtype=np.uint16,
                all_touched=False,
            )

            # Keep center-point classification wherever it exists. Fill only
            # empty cells touched by a source polygon so narrow islands and
            # land coordinates whose cell center falls offshore remain covered.
            touched = rasterize(
                shape_values,
                out_shape=(stripe_height, width),
                transform=transform,
                fill=NO_DATA_ID,
                dtype=np.uint16,
                all_touched=True,
            )
            empty = values == NO_DATA_ID
            values[empty] = touched[empty]
            seen_ids.update(int(value) for value in np.unique(values) if value != NO_DATA_ID)

            for row in values:
                transition_mask[0] = True
                np.not_equal(row[1:], row[:-1], out=transition_mask[1:])
                starts = np.flatnonzero(transition_mask).astype("<u2", copy=False)
                ids = row[starts].astype("<u2", copy=False)
                packed = np.empty((len(starts), 2), dtype="<u2")
                packed[:, 0] = starts
                packed[:, 1] = ids
                runs.write(packed.tobytes())
                run_count += len(starts)
                row_offsets.append(run_count)

            if stripe_number % 5 == 0 or start_row + stripe_height == height:
                elapsed = time.perf_counter() - started
                print(
                    f"rows={start_row + stripe_height}/{height} "
                    f"runs={run_count} elapsed={elapsed:.1f}s",
                    flush=True,
                )

    missing_ids = source_ids.difference(seen_ids)
    if missing_ids:
        raise ValueError(f"Raster lookup omitted ECO_ID values: {sorted(missing_ids)}")
    if len(row_offsets) != height + 1:
        raise AssertionError("Incorrect row-offset count")

    header = struct.pack(
        "<4sHHIIII",
        MAGIC,
        FORMAT_VERSION,
        arcseconds,
        width,
        height,
        run_count,
        NO_DATA_ID,
    )
    offsets = np.asarray(row_offsets, dtype="<u4").tobytes()
    with raw_lookup_path.open("wb") as output, raw_runs_path.open("rb") as runs:
        output.write(header)
        output.write(offsets)
        while chunk := runs.read(1024 * 1024):
            output.write(chunk)

    return width, height, run_count


def write_metadata(frame, output_path: Path) -> None:
    ordered = frame.sort_values("ECO_ID")
    with output_path.open("w", encoding="utf-8", newline="") as output:
        writer = csv.writer(output, lineterminator="\n")
        writer.writerow(["eco_id", "eco_name", "biome_num", "biome_name", "realm", "license"])
        for row in ordered.itertuples(index=False):
            writer.writerow(
                [
                    int(row.ECO_ID),
                    str(row.ECO_NAME),
                    int(row.BIOME_NUM),
                    str(row.BIOME_NAME),
                    str(row.REALM),
                    str(row.LICENSE),
                ]
            )


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    args = parse_args()
    source_info = pyogrio.read_info(args.shapefile)
    frame = pyogrio.read_dataframe(
        args.shapefile,
        columns=["ECO_ID", "ECO_NAME", "BIOME_NUM", "BIOME_NAME", "REALM", "LICENSE"],
    )
    validate_source(frame, source_info)

    args.lookup_output.parent.mkdir(parents=True, exist_ok=True)
    args.metadata_output.parent.mkdir(parents=True, exist_ok=True)
    started = time.perf_counter()
    with tempfile.TemporaryDirectory(prefix="tellus-resolve-lookup-") as temporary:
        temporary_root = Path(temporary)
        raw_lookup = temporary_root / "resolve_ecoregions_2017.bin"
        raw_runs = temporary_root / "resolve_ecoregions_2017.runs"
        compressed_lookup = temporary_root / args.lookup_output.name
        metadata = temporary_root / args.metadata_output.name

        width, height, run_count = build_lookup(
            frame,
            raw_lookup,
            raw_runs,
            args.arcseconds,
            args.stripe_rows,
        )
        with raw_lookup.open("rb") as source, lzma.open(
            compressed_lookup,
            "wb",
            format=lzma.FORMAT_XZ,
            preset=6,
        ) as compressed:
            while chunk := source.read(1024 * 1024):
                compressed.write(chunk)
        write_metadata(frame, metadata)

        os.replace(compressed_lookup, args.lookup_output)
        os.replace(metadata, args.metadata_output)

    elapsed = time.perf_counter() - started
    print(
        f"complete width={width} height={height} runs={run_count} "
        f"lookup_bytes={args.lookup_output.stat().st_size} "
        f"metadata_bytes={args.metadata_output.stat().st_size} "
        f"elapsed={elapsed:.1f}s"
    )
    print(f"lookup_sha256={sha256(args.lookup_output)}")
    print(f"metadata_sha256={sha256(args.metadata_output)}")


if __name__ == "__main__":
    main()
