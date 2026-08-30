---
title: Keybindings
---

# Keybindings

## How it works

Bindings resolve through six layers: the YAML config → the server keybindings API
→ `keyboard.ts` → key events → `LocalPlayerController` → `ClientMessage` /
`IntentCollector`. Any slash command can also be bound to a key.

- Rebind in-game: Preferences → Keybindings (`Cmd/Ctrl+Shift+K`).
- A player's saved bindings live in their player file; defaults come from the
  config below.

{{ story "story/game-windows-preferences--keybindings-tab" caption="Preferences → Keybindings — rebind actions and custom slash commands" }}

| Route | Purpose |
|-------|---------|
| `GET /api/keybindings` | a player's saved bindings (`?player=`) or the default config |
| `PUT /api/admin/players/{name}/keybindings` | overwrite a player's bindings |

## Configuration

`data/config/keybindings.yaml` (bundled default `resources/config/keybindings.yaml`,
schema `keybindings.schema.json`). Keys use DOM `KeyboardEvent.code` names with
optional `Ctrl+` / `Alt+` / `Cmd+` / `Shift+` modifiers; a list gives alternates;
`Key+Key` is a double-tap.

```yaml
movement:
  forward: [KeyW, ArrowUp]
  sneak: [ShiftLeft]
  auto_forward: ["KeyW+KeyW"]
flight:
  fly_toggle: ["Space+Space"]
```

Reload with `/reload`.

--8<-- "reference/_generated/keybindings.md"
