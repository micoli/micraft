"""Build-time documentation generation for MkDocs (mkdocs-gen-files hook).

Produces pages that are derived from other sources and therefore never committed:

* ``systems/slash-commands.md`` / ``api-routes.md`` — extracted from the auto-generated
  blocks in ``README.md`` (themselves kept current by ``:server:checkCommandsDocs`` /
  ``:server:checkOpenApi`` in CI).
* ``releases.md`` — rendered from the GitHub Releases API (graceful fallback offline).

Reference tables under ``docs/reference/_generated/`` are NOT handled here: they are
produced by ``:server:generateReferenceDocs`` and committed, then pulled into pages
through ``pymdownx.snippets``.
"""

from __future__ import annotations

import json
import os
import urllib.error
import urllib.request
from pathlib import Path

import mkdocs_gen_files

REPO = "micoli/micraft"
ROOT = Path(__file__).resolve().parents[2]
README = ROOT / "README.md"


def _extract_block(text: str, begin: str, end: str) -> str:
    start = text.find(begin)
    stop = text.find(end)
    if start < 0 or stop < 0:
        raise RuntimeError(f"markers {begin!r}/{end!r} not found in README.md")
    return text[start + len(begin) : stop].strip()


def gen_from_readme() -> None:
    text = README.read_text(encoding="utf-8")

    commands = _extract_block(text, "<!-- BEGIN_COMMANDS -->", "<!-- END_COMMANDS -->")
    with mkdocs_gen_files.open("systems/slash-commands.md", "w") as fd:
        fd.write("---\ntitle: Slash commands\n---\n\n")
        fd.write("# Slash commands\n\n")
        fd.write(
            "Every in-game action except movement is a slash command. Commands are "
            "discovered at runtime — a class implementing `CommandHandler` (or "
            "`PluginCommand`) appears here automatically. Each argument can carry "
            "autocompletion, and any command can be bound to a key (see "
            "[Keybindings](keybindings.md)).\n\n"
        )
        fd.write(commands)
        fd.write("\n\n> This table is generated from the server command registry.\n")
    mkdocs_gen_files.set_edit_path("systems/slash-commands.md", "README.md")

    routes = _extract_block(
        text, "<!-- BEGIN_API_ROUTES -->", "<!-- END_API_ROUTES -->"
    )
    with mkdocs_gen_files.open("api-routes.md", "w") as fd:
        fd.write("---\ntitle: HTTP API routes\n---\n\n")
        fd.write("# HTTP API routes\n\n")
        fd.write(
            "Machine-readable spec: [`server/openapi/openapi.yaml`]"
            "(https://github.com/micoli/micraft/blob/main/server/openapi/openapi.yaml), "
            "browsable at `/api/docs` (Redoc) on a running server. Both the table below "
            "and the YAML are generated from the same annotated Ktor routes.\n\n"
        )
        fd.write(routes)
        fd.write("\n")
    mkdocs_gen_files.set_edit_path("api-routes.md", "README.md")


def _fetch_releases() -> list[dict] | None:
    url = f"https://api.github.com/repos/{REPO}/releases?per_page=100"
    req = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json"})
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:  # noqa: S310
            return json.load(resp)
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, OSError):
        return None


def gen_releases() -> None:
    releases = _fetch_releases()
    with mkdocs_gen_files.open("releases.md", "w") as fd:
        fd.write("---\ntitle: Releases\n---\n\n# Releases\n\n")
        if not releases:
            fd.write(
                "!!! note\n"
                "    Release notes are rendered at build time from the GitHub API. "
                "None are available right now — see the "
                f"[releases page](https://github.com/{REPO}/releases).\n"
            )
            mkdocs_gen_files.set_edit_path("releases.md", "scripts/docs/gen_docs.py")
            return
        published = [r for r in releases if not r.get("draft")]
        for r in published:
            title = r.get("name") or r.get("tag_name") or "untitled"
            date = (r.get("published_at") or r.get("created_at") or "")[:10]
            tag = r.get("tag_name", "")
            fd.write(f"## {title}\n\n")
            meta = [f"`{tag}`"] if tag else []
            if date:
                meta.append(date)
            if r.get("prerelease"):
                meta.append("_pre-release_")
            fd.write(" · ".join(meta) + f" · [GitHub]({r.get('html_url', '')})\n\n")
            body = (r.get("body") or "").strip()
            fd.write((body or "_No description._") + "\n\n")
            assets = r.get("assets") or []
            if assets:
                fd.write("**Assets:** ")
                fd.write(
                    ", ".join(
                        f"[{a['name']}]({a['browser_download_url']})" for a in assets
                    )
                )
                fd.write("\n\n")
            fd.write("---\n\n")
    mkdocs_gen_files.set_edit_path("releases.md", "scripts/docs/gen_docs.py")


gen_from_readme()
gen_releases()
