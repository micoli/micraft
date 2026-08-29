---
title: Contributing
---

# Contributing

1. Fork and create a feature branch.
2. Follow **Conventional Commits** (`feat:`, `fix:`, `chore:`, …) — subject
   ≤ 72 chars, body ≤ 10 lines.
3. Run `make code-standard` before opening a PR.
4. Changes under `server/src/main/` require a new or updated test in
   `server/src/test/`.
5. New user-visible strings go in **both** `data/config/i18n/en.yaml` and
   `fr.yaml`.
6. Update the relevant JSON Schema in `server/src/main/resources/schemas/` in the
   same commit as a YAML-backed data-class change (`make gen-schemas`, checked by
   `make check-schemas`).
7. After changing a `data/config/*.yaml` default or a `*Constants` value, run
   `make docs` so the [reference tables](reference/index.md) stay in sync
   (checked by `make check-docs`).

## Editing the docs site

- Pages: `docs/**`, nav: `docs/SUMMARY.md`, config: `mkdocs.yml`.
- Preview: `make docs-site-serve` → <http://localhost:8000>.
- Strict build: `make docs-site-build`.
- The **Releases**, **Slash commands** and **API routes** pages are generated at
  build time by `scripts/docs/gen_docs.py` — edit the source (`README.md`), not
  the rendered page.

## Credits

- **Fantasy name generation** — NPC names use syllable data and logic from
  [FyefoxxM/fantasy-name-generator](https://github.com/FyefoxxM/fantasy-name-generator),
  inspired by
  [Day 7: Fantasy Name Generator](https://jdookeran.medium.com/day-7-fantasy-name-generator-c2b4458b13f7)
  by J. Dookeran.
