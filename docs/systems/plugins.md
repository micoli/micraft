---
title: Plugins
---

# Plugins

## Overview

Plugins extend the server at runtime:

- Implement the `PluginCommand` interface; place the file under
  `plugins/<name>/server/`.
- Plugins are discovered at startup via ClassGraph. They can register tick
  handlers and commands.
- UUID collision detection runs at startup — duplicate command ids abort the boot.

Plugin commands appear in the [slash-command list](slash-commands.md) under
"Plugin commands" automatically (`/goto`, `/kick`, `/npc`, `/rbac:*`, `/summon`,
`/teleport`, `/who`, `/yield`, `/adduser`).

Example plugin: `plugin-examples:hello-world`
(`make build-plugin-examples-hello-world`).

## Configuration

No YAML — plugins are code, dropped into `plugins/`. A plugin that needs config
accesses `context` (`context.authProvider`, `context.i18n`, …).
