---
title: Macros
---

# Macros

## Overview

`MacroExecutor` evaluates scripted macro sequences — chains of commands with
JEXL-evaluated conditions and variables. Macros are reachable through
`MacrosController` HTTP routes.

| Route | Purpose |
|-------|---------|
| `GET /api/macros/context` | variables available to the macro JEXL context |

## Configuration

Macro definitions are loaded server-side. The available context variables (player
position, target, inventory, time, …) are enumerated by
`GET /api/macros/context` — query it on a running server for the current list.
