"""MkDocs build hook: expand ``{{ story "<id>" [caption="…"] }}`` tags into a
figure showing the committed Storybook snapshot.

``<id>`` is the value from Storybook's URL (``?path=/story/<id>``) — a leading
``story/`` or ``/`` is optional, e.g.::

    {{ story "story/game-layout-playerstatusbar--caster" }}
    {{ story "game-layout-playerstatusbar--caster" caption="Caster HUD" }}

Snapshots and the manifest are produced out of band by
``app/webApp/ts-src/scripts/screenshot-stories.mjs`` (``make docs-screenshots``)
and committed under ``docs/assets/stories/`` — the docs CI has no browser, so this
hook only *references* them and fails the (strict) build if one is missing.
"""

from __future__ import annotations

import json
import re
from pathlib import Path

from mkdocs.exceptions import PluginError

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "app/webApp/ts-src/.storybook/stories-manifest.json"
ASSETS = ROOT / "docs/assets/stories"
REPO_BLOB = "https://github.com/micoli/micraft/blob/main/app/webApp/ts-src"

TAG_RE = re.compile(
    r'\{\{\s*story\s+"(?P<id>[^"]+)"'
    r'(?:\s+caption="(?P<caption>[^"]*)")?'
    r"\s*\}\}"
)

_manifest_cache: dict | None = None


def _norm_id(raw: str) -> str:
    return re.sub(r"^/?story/", "", raw.strip()).lstrip("/")


def _manifest() -> dict:
    global _manifest_cache
    if _manifest_cache is None:
        if not MANIFEST.exists():
            raise PluginError(
                f"{MANIFEST.relative_to(ROOT)} not found — run `make docs-screenshots`"
            )
        _manifest_cache = json.loads(MANIFEST.read_text(encoding="utf-8"))
    return _manifest_cache


def _figure(story_id: str, caption: str, source: str, rel_prefix: str) -> str:
    src_link = f' · <a href="{REPO_BLOB}/{source}">source</a>' if source else ""
    return (
        '<figure class="story-shot" markdown="span">\n'
        f"  ![{caption}]({rel_prefix}assets/stories/{story_id}.png){{ loading=lazy }}\n"
        f"  <figcaption>{caption}{src_link}</figcaption>\n"
        "</figure>"
    )


def on_page_markdown(markdown: str, page, config, files, **kwargs) -> str:
    rel_prefix = "../" * page.file.src_path.replace("\\", "/").count("/")

    def replace(m: re.Match) -> str:
        story_id = _norm_id(m.group("id"))
        manifest = _manifest()
        entry = manifest.get(story_id)
        if entry is None:
            stem = story_id.split("--")[0]
            near = [k for k in manifest if k.startswith(stem)][:6]
            hint = f" — did you mean: {', '.join(near)}" if near else ""
            raise PluginError(
                f'{page.file.src_path}: unknown story id "{story_id}"{hint}. '
                "Run `make docs-screenshots`."
            )
        if not (ASSETS / f"{story_id}.png").exists():
            raise PluginError(
                f"{page.file.src_path}: missing docs/assets/stories/{story_id}.png — "
                "run `make docs-screenshots`."
            )
        caption = m.group("caption") or f"{entry['title']} — {entry['name']}"
        return _figure(story_id, caption, entry.get("source", ""), rel_prefix)

    return TAG_RE.sub(replace, markdown)
