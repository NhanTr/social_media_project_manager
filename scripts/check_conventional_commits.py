#!/usr/bin/env python3
"""Validate commit subjects in a Git revision range."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import argparse
import re
import subprocess


CONVENTIONAL_SUBJECT = re.compile(
    r"^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)"
    r"(\([^)]+\))?!?: .+$"
)


@dataclass(frozen=True)
class InvalidCommit:
    sha: str
    subject: str


def find_invalid_commits(
    base: str,
    head: str,
    *,
    cwd: Path | None = None,
) -> list[InvalidCommit]:
    result = subprocess.run(
        [
            "git",
            "log",
            "--format=%H%x00%s%x00",
            f"{base}..{head}",
        ],
        cwd=cwd,
        check=True,
        capture_output=True,
    )
    fields = result.stdout.decode("utf-8", errors="replace").split("\x00")
    commits = zip(fields[0::2], fields[1::2])

    invalid: list[InvalidCommit] = []
    for sha, subject in commits:
        sha = sha.strip()
        if not sha:
            continue
        if subject.startswith("Merge "):
            continue
        if not CONVENTIONAL_SUBJECT.fullmatch(subject):
            invalid.append(InvalidCommit(sha=sha, subject=subject))
    return invalid


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base", help="base revision excluded from validation")
    parser.add_argument("head", help="head revision included in validation")
    arguments = parser.parse_args(argv)

    invalid = find_invalid_commits(arguments.base, arguments.head)
    if not invalid:
        print(f"All commits in {arguments.base}..{arguments.head} are conventional.")
        return 0

    print("Invalid Conventional Commit subjects:")
    for commit in invalid:
        displayed_subject = commit.subject or "<empty>"
        print(f"- {commit.sha[:12]} {displayed_subject}")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
