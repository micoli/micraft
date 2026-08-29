---
title: Screenshots
---

# Screenshots

## How to play

- **`screenshot`** key (`J`) captures the current view.

Captures are uploaded to the server as a base64 PNG (optionally a `data:` URI).

| Route | Purpose |
|-------|---------|
| `POST /api/player/{id}/screenshots` | upload a player screenshot |

`ScreenshotController` handles the endpoint. There is no dedicated
configuration.
