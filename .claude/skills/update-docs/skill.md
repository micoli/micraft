---
name: update-docs
description: Update the MkDocs documentation site and regenerate the parts that are auto-derived from the code (reference tables, slash commands, API routes, releases, embedded Storybook component screenshots). Use after changing a data/config/*.yaml default, a *Constants value, a slash command, a Ktor route, a UI component shown via a {{ story }} tag, or when adding/editing a system page.
---

# Updating the documentation

The site lives in `docs/**` (config `mkdocs.yml`, nav `docs/SUMMARY.md`) and
publishes to <https://micoli.github.io/micraft> via `.github/workflows/docs.yml`.

Three layers, ordered by how automatic they are:

| Layer | Source | Regenerate | Committed? |
|-------|--------|-----------|------------|
| Reference tables `docs/reference/_generated/*.md` | `resources/config/*.yaml`, `resources/blocks/`, `core` constants | `make docs` | yes |
| `systems/slash-commands.md`, `api-routes.md`, `releases.md` | README blocks / GitHub API | at `mkdocs build` time (`scripts/docs/gen_docs.py`) | no (virtual) |
| Hand-written system pages | you | manual edit | yes |

## 1. Always: regenerate + verify

```bash
make docs           # :server:generateCommandsDocs + :server:generateReferenceDocs
make check-docs     # drift check — also runs in CI (ci.yml "Generated docs up to date")
```

`make docs` rewrites:
- the `<!-- BEGIN_COMMANDS -->` block in `README.md` (from the command registry)
- every fragment in `docs/reference/_generated/` (from the bundled config +
  `WorldConstants` / `PlayerConstants`)

If a Ktor route changed, also:

```bash
make dc CMD="./gradlew :server:exportOpenApi"   # rewrites README API block + openapi.yaml
```

## 2. If you changed a game system

Edit the matching hand-written page so **how to play** and **how to configure**
stay accurate. Page ↔ system map:

| Changed | Page(s) |
|---------|---------|
| `items.yaml` | `docs/gameplay/inventory-items.md` |
| `recipes.yaml` | `docs/gameplay/crafting.md` |
| `blocks/` , `block_ids.yaml` | `docs/gameplay/building-blocks.md`, `docs/world/blocks-catalog.md` |
| `biomes.yaml` | `docs/world/biomes.md` |
| `vegetation.yaml` | `docs/world/vegetation.md` |
| `weather.yaml` | `docs/world/weather.md` |
| `houses.yaml` / `roads.yaml` | `docs/world/structures.md` |
| `classes.yaml`, `skills/` | `docs/rpg/classes.md`, `docs/rpg/combat.md` |
| `experience.yaml` | `docs/rpg/progression.md` |
| `combat.yaml` | `docs/rpg/stats.md`, `docs/rpg/combat.md` |
| `weapons.yaml` / `tools.yaml` / armors | `docs/rpg/equipment.md` |
| `npc.yaml`, NPC types | `docs/entities/npcs.md` |
| `vehicles.yaml`, siege | `docs/entities/vehicles.md` |
| `auction.yaml` / `trade.yaml` / `claims.yaml` | `docs/social/*.md` |
| `keybindings.yaml` | `docs/systems/keybindings.md` |
| `server.yaml` | `docs/systems/server-config.md` |
| `groups.yaml`, auth | `docs/systems/auth-rbac.md` |
| a new `/admin` page | `docs/admin/*.md` + `docs/admin/index.md` table |
| a brand-new system | new page under the right section + add to `docs/SUMMARY.md` |

Rule: never describe a feature that is not in the code. If a system is thin,
keep the page short and link to the source. Never write "Minecraft".

## 3. If you added a NEW config file or constant to a reference table

Edit `server/src/main/kotlin/org/micoli/micraft/tools/GenerateReferenceDocs.kt`:

1. Add a `fooPage()` function that parses `resources/config/foo.yaml` (use the
   `yaml(path)` / `YamlNode` helpers already there) and returns `md(title, intro, table(...))`.
2. Register it in `referenceDocPages()` (`put("foo.md", fooPage())`).
3. Reference it from the relevant hand-written page:
   `--8<-- "reference/_generated/foo.md"`.
4. `make docs` to write it, `make dc CMD="./gradlew :server:test --tests '*GenerateReferenceDocsTest'"`,
   then `make check-docs`.

The generator reads the **bundled defaults** (`resources/config/`, `resources/blocks/`),
not `data/config/` — so it is deterministic and side-effect free.

## Component screenshots — `{{ story }}` tags

Any page can embed a live snapshot of a webapp UI component by dropping a tag on
its own line:

```
{{ story "story/game-layout-playerstatusbar--caster" }}
{{ story "game-windows-character--with-stats" caption="Character screen" }}
```

- The argument is the **story id** from Storybook's URL (`?path=/story/<id>`); a
  leading `story/` is optional. Second optional `caption="…"`.
- `scripts/docs/hooks.py` (MkDocs `hooks:`) rewrites the tag to a `<figure>`
  pointing at a committed transparent PNG under `docs/assets/stories/`.
- PNGs + `app/webApp/ts-src/.storybook/stories-manifest.json` are produced by
  `app/webApp/ts-src/scripts/screenshot-stories.mjs` (headless Chromium) and
  **committed** — the docs CI has no browser.

### Adding or changing an embedded component

1. **Need a new story or a better variant?** Add/edit it under
   `app/webApp/ts-src/.stories/game/**` (one `*.stories.tsx` per component,
   `title: "Game/Layout/…"` or `"Game/Windows/…"`). Keep args static; if a story
   uses an interactive `play()`, pass `{ pointerEventsCheck: 0 }` to
   `userEvent.click` so it also renders headless. Then
   `make ts-lint` + `make ts-test-storybook`.
2. Put the `{{ story "…" }}` tag on the relevant hand-written page (id = the
   Storybook URL id; check it in `stories-manifest.json` after step 3).
3. `make docs-screenshots` — renders every tagged story, (re)writes the PNGs and
   the manifest, prunes orphans. Eyeball the new PNG in `docs/assets/stories/`.
4. `make check-docs-screenshots` — re-renders all tagged stories (aborts on a
   render/play error), checks the manifest is fresh and no PNG is missing/orphan.
   Also runs in CI (`ci.yml` TypeScript job).
5. Commit the `.md`, the new/changed `docs/assets/stories/*.png` and
   `stories-manifest.json` together.

A tag pointing at an unknown id or a missing PNG fails `make docs-site-build`
(`--strict`). Pixels are **not** diffed (not reproducible across machines) — the
person editing the story/tag owns regenerating them.

## 4. Preview

```bash
make docs-site-serve     # http://localhost:8000/micraft/  (live reload)
make docs-site-build     # strict one-shot build → ./site  (must be exit 0)
make docs-site-stop
```

`docs-site-build` runs `mkdocs build --strict` — it fails on a dead internal
link, a missing snippet, or a broken nav entry. Run it before finishing.

`scripts/docs/mkdocs.sh` finds mkdocs in `.venv/`, `/opt/mkdocs/`, or on `PATH`.
In `RUN_MODE=HOST`, create the venv once:
`python -m venv .venv && .venv/bin/pip install -r docs/requirements.txt`.

## Checklist

- [ ] `make docs`
- [ ] `make dc CMD="./gradlew :server:exportOpenApi"` — only if a route changed
- [ ] updated the hand-written page(s) for the changed system
- [ ] added new pages to `docs/SUMMARY.md`
- [ ] added/edited a Storybook story + ran `make docs-screenshots` — only if an
      embedded `{{ story }}` component changed
- [ ] `make docs-site-build` → exit 0
- [ ] `make check-docs` → green
- [ ] `make check-docs-screenshots` → green — only if `{{ story }}` tags changed
