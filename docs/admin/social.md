---
title: Social
---

# Social

The **Social** admin section (`/admin/social`) offers full CRUD over the three
social subsystems, each on its own tab. Sub-entities (members, ranks, faction
definitions) are added/removed inline with player-name autocomplete.

## Groups

Ephemeral player parties (max 5, never persisted, dissolved once no member is
online). Admin can create a group led by an online player, add/remove online
members, and disband.

## Guilds

Persisted (`data/world/<world>/guilds.yaml`). Admin can create a guild (owner
resolved from any known player, online or offline), rename / retag / set MOTD,
add/remove members, change a member's rank, and disband. Online members receive a
live `GuildSync`; offline members have their player file updated in place.

## Factions

Config-backed. Admin can also make any player (online or offline) **join** a
faction — bypassing the in-game change cooldown — or **leave** it, and see the
current member list per faction.

Once edited from the admin UI the faction list is persisted to
`data/world/<world>/factions.yaml`, which then takes precedence over the
`factions:` section of `data/config/server.yaml`. Editing global settings
(`enabled`, `friendlyFire`, `changeCooldownSeconds`, `spawnRingRadius`) or a
definition triggers the same `applyConfig` + `reconcile` path as `/reload`.

| Route | Purpose |
|-------|---------|
| `GET /api/admin/social/online-players` | connected player names (group autocomplete) |
| `GET/POST /api/admin/social/groups` | list / create |
| `DELETE /api/admin/social/groups/{id}` | disband |
| `POST/DELETE /api/admin/social/groups/{id}/members[/{playerId}]` | add / remove member |
| `GET/POST /api/admin/social/guilds` | list / create |
| `PUT/DELETE /api/admin/social/guilds/{id}` | edit / disband |
| `POST/DELETE /api/admin/social/guilds/{id}/members[/{playerId}]` | add / remove member |
| `PUT /api/admin/social/guilds/{id}/members/{playerId}` | set rank |
| `GET /api/admin/social/factions` | settings + definitions |
| `PUT /api/admin/social/factions/settings` | global settings |
| `POST /api/admin/social/factions` · `DELETE /api/admin/social/factions/{id}` | upsert / delete definition |
| `GET /api/admin/social/factions/{id}/members` | affiliated players (online + persisted) |
| `POST/DELETE /api/admin/social/factions/{id}/members[/{playerId}]` | join (no cooldown) / leave |
| `GET /api/admin/social/players` | canonical player names (name-field autocomplete) |
