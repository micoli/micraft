# issues-server

Local browser for the backlog in `.scratch/` (efforts, specs, maps, issues — see `docs/agents/issue-tracker.md`)
the ADRs in `docs/adr/` and the domain glossary `CONTEXT.md`, with WYSIWYG editing (MDXEditor). Files are re-read on every request: edit the markdown,
refresh the page.

- `server/` — zero-dependency Node HTTP server (Node ≥ 24, TypeScript run natively), JSON API + built client.
- `client/` — React + Vite UI.

## Run

```bash
make issues          # pitchfork daemon `issues`: npm ci + build client + serve on http://127.0.0.1:4380
make issues-stop
make issues-install  # host only, then:
make issues-dev      # API on :4380 + Vite with HMR on http://127.0.0.1:4381
```

The daemon follows `RUN_MODE`. In the dev container its `node_modules` live in the `issues-server-node-modules`
volume (Linux binaries, host copy untouched) and port 4380 is published on `127.0.0.1` only. The server listens on
`0.0.0.0` inside a container (`/.dockerenv`), `127.0.0.1` elsewhere.

Env: `ISSUES_PORT` (4380), `ISSUES_HOST` (see above), `ISSUES_ROOT` (repo root, relative to `server/`).

## API

| Route | Returns |
|-------|---------|
| `GET /api/efforts` | Efforts (`.scratch/<slug>/`) with spec title, issue count, per-Status counts |
| `GET /api/labels` | Every label key → value → occurrence count |
| `GET /api/items?kind=&effort=&q=&label.<Key>=<value>` | Item summaries (no body); `kind` ∈ `issue`, `spec`, `map`, `adr`, `doc`, `context` |
| `GET /api/items/<url-encoded path>` | One item with its markdown body and `version`, e.g. `/api/items/docs%2Fadr%2F0001-….md` |
| `PUT /api/items/<url-encoded path>` | Saves `{ version, title, labels, body }`; `409` + current item if the file changed since `version` |

Labels are the `Key: value` lines between an item's `# Title` and its first section (`Status`, `Type`,
`Blocked by`, `Strength`…), or the YAML frontmatter of an ADR. `Blocked by: 02, 03` is resolved to sibling issues;
`blocked` is true when the item is open and one of them is not closed. `CONTEXT.md` (`context`) has no labels:
its intro lines are body text, and the editor hides the label form.

## Editing

**Edit** on an item opens the title, a label form (`Status`, `Type`…, with suggestions from existing values) and the
body in MDXEditor (rich text, or raw markdown via the source toggle). The server rewrites the file as
`# Title`, the label lines (or the ADR frontmatter), then the body. Only items the server listed can be written.

- `version` is the SHA-256 of the file when it was loaded: a save over a file an agent changed meanwhile is refused,
  the editor offers *reload from disk* or *overwrite*.
- MDXEditor runs with `suppressHtmlProcessing` (plain markdown, not MDX), so `<id>` or `{` in text do not break parsing.
- Saving normalises a few details: labels move to the top, `[` in text may be escaped as `\[`, list continuation
  indents are reset. Diff before committing.
- Writes are accepted only with a `localhost`/`127.0.0.1` `Host` header and `Content-Type: application/json`, which
  blocks DNS rebinding and cross-site form posts. Do not expose the server beyond the loopback interface.

## Checks

```bash
npm test && npm run typecheck
```
