---
title: Player-to-player trade
---

# Player-to-player trade

## How to play

- **`/trade <playerName>`** — start a trade session with a nearby player.
- **`/tradeoffer <tradeId> <json>`** — update your side of the offer.
- **`/tradeaccept <tradeId>`** — accept the current offer.
- **`/tradecancel <tradeId>`** — cancel.

Both players must accept the same offer state for the swap to commit.

{{ story "story/game-windows-trade--they-offered" caption="Trade window — both offers, awaiting mutual accept" }}

## Configuration

`data/config/trade.yaml` (bundled default `resources/config/trade.yaml`, schema
`trade.schema.json`):

```yaml
maxDistance: 10.0
```

Reload with `/reload`.
