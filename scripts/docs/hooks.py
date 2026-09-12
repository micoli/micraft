"""MkDocs build hook: expand ``{{ story "<id>" [caption="…"] }}`` tags into a
figure showing the committed Storybook snapshot.

``<id>`` is the value from Storybook's URL (``?path=/story/<id>``) — a leading
``story/`` or ``/`` is optional, e.g.::

    {{ story "story/game-layout-playerstatusbar--caster" }}
    {{ story "game-layout-playerstatusbar--caster" caption="Caster HUD" }}

Add ``onlyIfMissing`` to skip re-rendering an expensive story (e.g. full entity/NPC
previews) once its PNG is committed — ``screenshot-stories.mjs`` then only (re)renders
it the first time, and under ``--check`` only verifies the file is present::

    {{ story "admin-components-npcpreview--wolf" onlyIfMissing }}

``<id>`` may also carry a Storybook URL args override (``id&args=key:value;…``,
the same serialization Storybook itself uses for permalinks) to snapshot one
story under several control values without a named export per variant, e.g.::

    {{ story "admin-components-bbmodelanimationviewer--fixed-angle&args=modelName:wolf" caption="Wolf" }}

The base id (before ``&``) must still be a real, registered story — only its
args are overridden. Each distinct full tag string gets its own screenshot.

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
    r"(?:\s+onlyIfMissing)?"
    r'(?:\s+caption="(?P<caption>[^"]*)")?'
    r"\s*\}\}"
)

_manifest_cache: dict | None = None


def _norm_id(raw: str) -> str:
    return re.sub(r"^/?story/", "", raw.strip()).lstrip("/")


def _split_id(story_id: str) -> tuple[str, str]:
    """Splits "<base-id>&args=…" into (base-id, args-query); args-query is "" if absent."""
    base, sep, query = story_id.partition("&")
    return base, query if sep else ""


def _sanitize(story_id: str) -> str:
    """Filesystem/URL-safe filename stem — must match screenshot-stories.mjs's sanitize()."""
    return re.sub(r"[^A-Za-z0-9._-]+", "_", story_id)


def _manifest() -> dict:
    global _manifest_cache
    if _manifest_cache is None:
        if not MANIFEST.exists():
            raise PluginError(
                f"{MANIFEST.relative_to(ROOT)} not found — run `make docs-screenshots`"
            )
        _manifest_cache = json.loads(MANIFEST.read_text(encoding="utf-8"))
    return _manifest_cache


def _figure(filename_stem: str, caption: str, source: str, rel_prefix: str) -> str:
    src_link = f' · <a href="{REPO_BLOB}/{source}">source</a>' if source else ""
    return (
        '<figure class="story-shot" markdown="span">\n'
        f"  ![{caption}]({rel_prefix}assets/stories/{filename_stem}.png){{ loading=lazy }}\n"
        f"  <figcaption>{caption}{src_link}</figcaption>\n"
        "</figure>"
    )


def on_page_markdown(markdown: str, page, config, files, **kwargs) -> str:
    rel_prefix = "../" * page.file.src_path.replace("\\", "/").count("/")

    def replace(m: re.Match) -> str:
        story_id = _norm_id(m.group("id"))
        base_id, _args_query = _split_id(story_id)
        manifest = _manifest()
        entry = manifest.get(base_id)
        if entry is None:
            stem = base_id.split("--")[0]
            near = [k for k in manifest if k.startswith(stem)][:6]
            hint = f" — did you mean: {', '.join(near)}" if near else ""
            raise PluginError(
                f'{page.file.src_path}: unknown story id "{base_id}"{hint}. '
                "Run `make docs-screenshots`."
            )
        filename_stem = _sanitize(story_id)
        if not (ASSETS / f"{filename_stem}.png").exists():
            raise PluginError(
                f"{page.file.src_path}: missing docs/assets/stories/{filename_stem}.png — "
                "run `make docs-screenshots`."
            )
        caption = m.group("caption") or f"{entry['title']} — {entry['name']}"
        return _figure(filename_stem, caption, entry.get("source", ""), rel_prefix)

    return TAG_RE.sub(replace, markdown)
