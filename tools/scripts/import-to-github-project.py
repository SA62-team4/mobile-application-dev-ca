#!/usr/bin/env python3
"""Import docs/specs/16-kanban-sprint-board.csv into a GitHub Project (v2).

Creates one issue per card (idempotent by issue title) and adds it to the
project board. Re-running is safe: existing issues are reused, not duplicated.

Prerequisite: the gh token needs the 'project' scope:
    gh auth refresh -s project --hostname github.com

Usage:
    python3 tools/scripts/import-to-github-project.py <project-number> <project-owner>
"""
import csv
import json
import subprocess
import sys
import pathlib

REPO = "SA62-team4/mobile-application-dev-ca"  # repo where issues are created
CSV = pathlib.Path("docs/specs/16-kanban-sprint-board.csv")


def run(args):
    return subprocess.run(args, check=True, capture_output=True, text=True).stdout.strip()


def try_run(args):
    p = subprocess.run(args, capture_output=True, text=True)
    return p.returncode, (p.stdout or p.stderr).strip()


def main():
    if len(sys.argv) < 3:
        sys.exit("usage: import-to-github-project.py <project-number> <project-owner>")
    project, owner = sys.argv[1], sys.argv[2]
    rows = list(csv.DictReader(CSV.open()))

    # 1) labels (create-or-update) for epic / component / sprint
    colors = {"Epic": "5319e7", "Component": "0e8a16", "Sprint": "1d76db"}
    for col, color in colors.items():
        for name in sorted({r[col] for r in rows}):
            subprocess.run(
                ["gh", "label", "create", name, "--repo", REPO, "--color", color, "--force"],
                capture_output=True, text=True,
            )

    # 2) idempotency: map existing issue titles -> url
    existing = {
        i["title"]: i["url"]
        for i in json.loads(
            run(["gh", "issue", "list", "--repo", REPO, "--state", "all",
                 "--limit", "500", "--json", "title,url"]) or "[]"
        )
    }

    created = reused = 0
    for r in rows:
        title = f'[{r["Card ID"]}] {r["Title"]}'
        if title in existing:
            url = existing[title]
            reused += 1
        else:
            body = (
                f'**Epic:** {r["Epic"]}\n'
                f'**Component:** {r["Component"]}\n'
                f'**Owner:** {r["Owner"]}\n'
                f'**Sprint:** {r["Sprint"]}\n'
                f'**Status:** {r["Status"]}\n'
                f'**Points:** {r["Points"]}\n'
                f'**Depends On:** {r["Depends On"]}\n'
                f'**REQ IDs:** {r["REQ IDs"]}\n\n'
                f'**Acceptance Criteria:**\n{r["Acceptance Criteria"]}\n'
            )
            url = run([
                "gh", "issue", "create", "--repo", REPO,
                "--title", title, "--body", body,
                "--label", r["Epic"], "--label", r["Component"], "--label", r["Sprint"],
            ])
            created += 1
        rc, msg = try_run(["gh", "project", "item-add", project, "--owner", owner, "--url", url])
        note = "ok" if rc == 0 else f'add-note: {msg.splitlines()[-1] if msg else "n/a"}'
        print(f'{r["Card ID"]:7} {note:>10}  {url}')

    print(f"\nDone. {created} created, {reused} reused, {len(rows)} total cards.")


if __name__ == "__main__":
    main()
