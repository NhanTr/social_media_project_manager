#!/usr/bin/env python3
"""Validate checked-in environment templates without reading real secrets."""

from __future__ import annotations

from pathlib import Path
import argparse
import sys
from urllib.parse import urlparse


REQUIRED_KEYS = (
    "SPRING_PROFILES_ACTIVE",
    "JWT_SECRET",
    "INTERNAL_API_KEY",
    "POSTGRES_DB",
    "POSTGRES_USER",
    "POSTGRES_PASSWORD",
)
SAMPLE_SECRET_KEYS = (
    "JWT_SECRET",
    "INTERNAL_API_KEY",
    "POSTGRES_USER",
    "POSTGRES_PASSWORD",
    "MINIO_ROOT_USER",
    "MINIO_ROOT_PASSWORD",
    "ADMIN_USERNAME",
    "ADMIN_PASSWORD",
)
SUPPORTED_PROFILES = {"dev", "staging", "prod"}


def _parse_env(path: Path) -> tuple[dict[str, str], list[str]]:
    values: dict[str, str] = {}
    errors: list[str] = []
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as error:
        return {}, [f"cannot read file: {error}"]

    for line_number, raw_line in enumerate(lines, start=1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            errors.append(f"line {line_number} must use KEY=VALUE")
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        if not key:
            errors.append(f"line {line_number} has an empty key")
            continue
        if key in values:
            errors.append(f"{key} is declared more than once")
            continue
        values[key] = value.strip()
    return values, errors


def _is_placeholder(value: str) -> bool:
    normalized = value.strip().lower()
    return (
        normalized.startswith("<")
        and normalized.endswith(">")
        or normalized.startswith("change-me")
    )


def validate_file(path: Path) -> list[str]:
    values, errors = _parse_env(path)

    for key in REQUIRED_KEYS:
        if not values.get(key):
            errors.append(f"{key} is required")

    profile = values.get("SPRING_PROFILES_ACTIVE", "").lower()
    if profile and profile not in SUPPORTED_PROFILES:
        errors.append("SPRING_PROFILES_ACTIVE must be dev, staging, or prod")

    if profile in {"staging", "prod"}:
        gateway_url = values.get("GATEWAY_PUBLIC_URL", "")
        if not gateway_url:
            errors.append("GATEWAY_PUBLIC_URL is required")
        else:
            parsed_url = urlparse(gateway_url)
            if parsed_url.scheme != "https" or not parsed_url.hostname:
                errors.append("GATEWAY_PUBLIC_URL must be an absolute HTTPS URL")
            elif parsed_url.hostname in {"localhost", "127.0.0.1"}:
                errors.append("GATEWAY_PUBLIC_URL must not target localhost")

    for key in SAMPLE_SECRET_KEYS:
        value = values.get(key)
        if value and not _is_placeholder(value):
            errors.append(f"{key} must be a placeholder in a sample file")

    return errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("env_files", nargs="+", type=Path)
    arguments = parser.parse_args(argv)

    failed = False
    for path in arguments.env_files:
        errors = validate_file(path)
        if errors:
            failed = True
            for error in errors:
                print(f"{path}: {error}", file=sys.stderr)
        else:
            print(f"{path}: valid")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
