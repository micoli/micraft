---
title: Auction house
---

# Auction house

## How to play

**`/auction`** opens the auction house. List an item for a fixed duration, bid on
others' listings; on close the item goes to the highest bidder and the seller is
paid minus a duration-based tax. Payouts and unsold items are returned via
[Mail](mail.md).

{{ story "story/game-windows-auctionhouse--basic" caption="Auction house — active listings, bids and buy-now" }}

Admins can inspect and force-cancel listings:

| Route | Purpose |
|-------|---------|
| `GET /api/admin/auctions` | all listings, any status |
| `POST /api/admin/auctions/{id}/force-cancel` | return item to seller, refund top bidder, no tax |

## Configuration

`data/config/auction.yaml` (bundled default `resources/config/auction.yaml`,
schema — see [config files](../reference/config-files.md)):

```yaml
tax12h: 3
tax24h: 6
tax48h: 10
tax96h: 15
maxActiveListingsPerPlayer: 10
```

Taxes are percentages keyed by listing duration. Reload with `/reload`.
