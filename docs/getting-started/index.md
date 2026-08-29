---
title: Getting started
---

# Getting started

Every build, run, test and lint command executes **inside the dev container** —
never directly on the host.

## Prerequisites

- **Docker** + **Docker Compose**
- **Make** — the task runner (`make help` lists every target, grouped and self-documented)
- **Node.js** on the host — only for `rtk` and a few helper scripts

## Dev container

```bash
make dev-up                   # build image + start container (pitchfork auto-starts daemons)
make shell                    # bash inside the container
make dc CMD="<any command>"   # run one command inside the container
```

Daemons are managed by [pitchfork](https://github.com/jdx/pitchfork) (`pitchfork.toml`):
`server` (Ktor, port 8080), `wasm`, `mc_bindings`, `css`, `map`, `admin`,
`storybook` (6006), and `docs` (MkDocs, 8000).

```bash
make dev-restart-server       # rebuild + restart Ktor only — after any server-side change
make build-wasm               # one-shot WASM recompile — after a Kotlin/Wasm client change
```

## Documentation site (this site) locally

```bash
make docs-site-serve           # live-reload on http://localhost:8000
make docs-site-build           # strict one-shot build into ./site
```

The dev container ships MkDocs in an isolated venv (symlinked onto `PATH`). In
`RUN_MODE=HOST`, install it once with `pipx install mkdocs && pipx inject mkdocs
$(sed 's/==.*//' docs/requirements.txt | tail -n +2)` or a plain
`python -m venv` from `docs/requirements.txt`.

## Next

- [Running the apps](running.md)
- [Building the client](building.md)
- [Running tests](testing.md)
- [Contributing](../contributing.md)
