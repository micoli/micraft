# Residents: Disposition, Memories and Barks (2026-09-27)

Status: ready-for-agent

Source: grilling session on `main`. Decision record: `docs/adr/0011-llm-is-read-only-npc-voice.md`.
Vocabulary: `CONTEXT.md` (Resident, Disposition, Disposition band, Memory, Temperament, Bark, Region, Roster,
Quest giver, Game time, Weather cell).

## Problem Statement

NPCs feel robotic. A Quest giver or merchant greets every Character the same way, forgets them as soon as the
server restarts or the Region is parked, never reacts to what happens around it and never comments on the hour
or the weather. The LLM chat can produce nice sentences, but it knows nothing of the game state, and the fixed
behaviours know nothing of the chat: what a Character does to an NPC has no lasting consequence. The LLM is
also small, slow and rate-limited, so it cannot be the answer to "make NPCs feel alive".

## Solution

Quest givers and merchants become **Residents**: social NPCs with a stable identity (Region + NPC type + rank),
a deterministic name and a fixed **Temperament**. Each Resident has a **Disposition** towards each Character
it has dealt with, moved only by deterministic game events (Quest turned in, trade, blows, murder of another
Resident), read as one of five **Disposition bands** (Hostile, Wary, Neutral, Friendly, Devoted), and drifting
back towards Neutral over Game time. It keeps the last five **Memories** (dated facts) about that Character.

Disposition changes prices, greetings and chat tone; a Hostile Resident refuses to talk or trade. Residents say
**Barks**: short templated lines (greeting, reaction to nearby events, remarks on the hour and the weather,
reaction to being hit), shown as a bubble above the Resident and as an "around" chat line, only to Characters
within a short radius, translated per recipient, and rate-limited. The LLM chat receives a small deterministic
context block (band label, time of day, weather, Memories) and is never called for a Hostile Resident. Nothing
outside chat wording depends on the LLM.

## User Stories

1. As a Player, I want the Quest giver of a Region to keep the same name after a server restart, so that the
   World feels persistent.
2. As a Player, I want a merchant to keep the same name after a restart or after its Region is parked and
   reactivated, so that I recognise the people I trade with.
3. As a Player, I want two Residents of the same NPC type to behave differently, so that NPCs do not feel
   cloned.
4. As a Player, I want a Resident to greet me when I come close, so that the World notices me.
5. As a Player, I want a Resident's greeting to depend on what it thinks of me, so that my past actions matter.
6. As a Player, I want a Resident to mention something I did for it (e.g. a Quest I turned in), so that it
   feels like it remembers me.
7. As a Player, I want a Resident to greet me only once per visit, so that it does not repeat itself while I
   stand next to it.
8. As a Player, I want a Resident to greet me again after I leave and come back, so that returning feels
   natural.
9. As a Player, I want turning in a Quest to make its Quest giver like me more, so that helping is rewarded.
10. As a Player, I want trading with a merchant to make it like me a little more, so that loyal customers are
    recognised.
11. As a Player, I want trade gains capped per in-game day, so that the system cannot be farmed by spamming
    small trades.
12. As a Player, I want hitting a Resident to make it like me much less, so that aggression has consequences.
13. As a Player, I want killing a Resident to make the Residents who witnessed it distrust me strongly, so that
    murder has social consequences.
14. As a Player, I want killing wild NPCs near a Resident to have no effect on its Disposition, so that hunting
    stays a normal activity.
15. As a Player, I want a grudge or a friendship to fade slowly if I stay away, so that one accidental blow
    does not ruin a relationship forever.
16. As a Player, I want Friendly and Devoted Residents to give me better prices, so that good relations pay off.
17. As a Player, I want Wary Residents to charge me more, so that bad relations cost something.
18. As a Player, I want a Hostile Resident to refuse to talk or trade with me, with a spoken line explaining it,
    so that the refusal is clear and in character.
19. As a Player, I want to see my Disposition band and its progress in the Resident's dialogue and shop window,
    so that I understand why prices or tone changed.
20. As a Player, I want a slash command showing my Disposition band with the Resident I target (bindable to a
    key), so that I can check it without opening a window.
21. As a Player, I want Residents to remark on the time of day and the weather, so that the World feels alive
    around me.
22. As a Player, I want a Resident to react when a Character near it is downed by an NPC, so that it seems
    aware of danger.
23. As a Player, I want a Resident to react when an NPC is killed or a fight happens near it, so that combat
    feels witnessed.
24. As a Player, I want a Resident to react out loud when I hit it, so that my action gets an immediate
    response.
25. As a Player, I want Barks shown as a bubble above the Resident, so that I see who is speaking in the World.
26. As a Player, I want Barks also in my "around" chat, so that I can read them again.
27. As a Player, I want only Barks from Residents near me, so that my chat is not flooded by distant NPCs.
28. As a Player, I want a crowd of Residents not to flood me with Barks, so that my screen and chat stay
    readable.
29. As a Player, I want Barks in my own language even when other nearby Characters use another, so that every
    Player understands them.
30. As a Player, I want a Resident's Temperament to colour its Barks (gruff, cheerful, fearful…), so that the
    same event sounds different from different Residents.
31. As a Player chatting with a Resident, I want its replies to reflect what it thinks of me, the hour, the
    weather and what I did for it, so that the conversation feels grounded.
32. As a Player, I want insulting a Resident in chat to get an in-character reply without silently changing my
    Disposition, so that the rules stay predictable (ADR 0011).
33. As a Player, I want Residents to keep working (greetings, prices, refusals, Barks) when the LLM is down, so
    that the game does not depend on it.
34. As a Player, I want my relationships to survive a server restart, so that my history with Residents is
    kept.
35. As a Player, I want a Resident that disappeared after a Roster reshuffle to still remember me if it comes
    back, so that a config change does not wipe my history.
36. As an Admin, I want a slash command to set a Character's Disposition with a Resident (with autocompletion
    of Resident and Character), so that I can debug and set up tests.
37. As an Admin, I want every threshold, price modifier, drift speed, trade cap, Bark radius and rate limit in
    YAML config, so that I can tune them without a rebuild.
38. As a content author, I want Bark lines as translation keys with variants per trigger, Temperament and
    Disposition band, so that I can write and translate them without code changes.
39. As a developer, I want Disposition rules testable without a World, network or LLM, so that tuning is safe.
40. As an E2E test author, I want the Disposition band and received Barks exposed in the E2E snapshot, fed by
    the same server messages as the UI, so that player-level tests can assert them.

## Implementation Decisions

- **LLM boundary (ADR 0011)**: the LLM only produces chat wording. Disposition is never written from LLM output;
  Barks are never generated; a Hostile Resident's refusal is a templated Bark and makes no LLM call.
- **Resident identity**: a Resident is keyed by Region + NPC type + rank within that Region (1st, 2nd…
  merchant of that type). The Resident's name and Temperament are derived deterministically from the World seed
  and that key, replacing today's random name for Quest givers and merchants. The runtime NPC id stays random;
  Disposition is keyed by the Resident key, never by the NPC id. Wild NPCs and Pets are not Residents.
- **Temperament**: a small fixed set (start with 3, e.g. gruff, cheerful, fearful), drawn from the seed. It
  selects Bark variants and adds one line of tone to the chat context.
- **Resident relations module (new, pure, deep)**: no I/O, no World access. Input: current relation (score,
  Memories, last contact, trade gains of the current in-game day), a game event, the Game time and the tuning.
  Output: updated relation. Also answers: Disposition band, progress within the band, price modifier, whether
  the Resident refuses, and whether the entry has drifted back to Neutral and can be dropped. Events: Quest
  turned in, trade, hit by the Character, witnessed murder of another Resident. Drift towards Neutral is
  computed lazily from Game time elapsed since last contact.
- **Bands**: Hostile / Wary / Neutral / Friendly / Devoted over a hidden score starting at Neutral. Thresholds
  and per-band price modifiers are config. Hostile refuses dialogue and trade.
- **Memories**: FIFO of the last 5 dated facts (kind, Game time, optional subject such as a Quest), per
  Character per Resident.
- **Persistence**: relations are stored in the Character's save (Resident key → score, Memories, last contact,
  daily trade gains), loaded and saved with the Character. Orphan entries (Resident no longer exists) are kept;
  entries that drifted back to Neutral with no Memory worth keeping are dropped on save. No World-side store, no
  migration.
- **Event wiring**: a new hook "Character hits an NPC" is added alongside the existing NPC subsystem callbacks
  (NPC killed, NPC damaged by NPC, Character downed by NPC). Quest turn-in and shop transactions notify the
  relations of the concerned Resident. Murder of a Resident updates the Disposition of each witnessing Resident
  (within the Bark radius) towards the killer, for online Characters only.
- **Barks**: triggers are greeting (on entering the radius, with enter/exit hysteresis; line chosen from band
  and latest Memory), nearby event (Character downed by NPC, NPC killed, combat), ambient (time-of-day phase
  change, weather change in the Resident's Weather cell), and action on the Resident (hit). Selection:
  trigger × Temperament × band → translation key with variants. Rate limits: per Resident (~1 / 20 s), one
  greeting per visit per Character, per recipient (~1 / 5 s). Only Residents speak Barks in this scope.
- **Delivery**: a new server message for the Bark bubble (Resident NPC id + localized text), sent only to
  Characters within the Bark radius (config, default 24 blocks), plus a `ChatMessage` on the existing "around"
  channel with the Resident as sender. Text is localized server-side per recipient. A small "send to Characters
  within radius" helper is added since none exists.
- **Dialogue and shop**: the existing Resident dialogue and shop messages carry the band and progress; shop
  prices apply the band's price modifier server-side.
- **Chat context**: the chat system prompt gets a deterministic block of at most ~5 lines: band label (never
  the number), Temperament tone, time-of-day phase, local weather, latest Memories. Region Roster hint only if it
  fits the budget. Chat history stays in memory as today.
- **Commands**: `/npc disposition [resident]` (player, defaults to the targeted Resident, bindable, with
  autocompletion) and an admin `/npc disposition set <resident> <character> <value>` with autocompletion. New
  i18n keys go through the translation files for every locale.
- **Config**: a new layered YAML section (ADR 0009) for band thresholds, price modifiers, event deltas, drift
  rate, daily trade cap, Memory count, Bark radius and rate limits. JSON Schemas and generated reference docs
  are regenerated in the same commits as the data classes and constants.
- **Protocol**: new message subclasses take the next free proto ids; codec registries are regenerated, never
  hand-edited.
- **Client**: bubble above the Resident (world space, a few seconds), band + gauge in the dialogue and shop
  widgets, "around" chat line reusing the existing chat UI.

## Testing Decisions

- Good tests assert external behaviour only: messages a Character receives, what a reloaded save contains,
  what the relations module returns. No assertions on internal maps, timers or call counts, except "no LLM
  call" through the existing fake Ollama.
- **Resident relations module**: unit tests for each event delta, band thresholds and progress, price
  modifiers, refusal at Hostile, lazy drift towards Neutral over Game time, daily trade cap and its reset, FIFO
  of 5 Memories, drop-when-neutral. Pure inputs, no fixtures beyond tuning. Prior art: `NpcTuningTest`,
  `NpcChatHistoryStoreTest`.
- **Test world (primary seam)**: `buildGameWorld` + `FakePlayerSession` scenarios: stable Resident name and
  Temperament across two builds with the same seed; greeting Bark on approach (bubble + "around" line), once per
  visit, again after leaving and returning; no Bark beyond the radius; per-recipient language; rate limits with
  an injected clock; hit → Bark + lower band; Quest turn-in → higher band shown in dialogue; Hostile refuses
  dialogue and trade; shop prices follow the band; witness penalty after a Resident murder; save and reload keeps
  relations, orphans kept. Prior art: `GameWorldOnPlayerJoinTest`, `QuestGiverSpawnerTest`, `NpcSellerTest`,
  `WorldPersistenceTest`.
- **Chat**: `NpcChatServiceTest` asserts the context block content (band label, time phase, weather, Memories)
  and that a Hostile Resident produces a refusal without calling the fake Ollama.
- **E2E**: one player-level spec: approach the Quest giver, read the greeting Bark, turn in a Quest, check the
  band with `/npc disposition`, admin-set to Hostile, see the refusal. Assertions only read the E2E snapshot
  (new fields fed by the Bark and dialogue messages). Prior art: `quest-giver-dialog.spec.ts`,
  `npc-shop.spec.ts`.

## Out of Scope

- Rumours and a Region-wide reputation (Disposition spreading between Residents beyond direct witnesses).
- Daily routines driven by Game time (sleeping, working, tavern).
- Needs / utility AI choosing behaviours.
- Head tracking and idle animations.
- Barks from wild NPCs and animals.
- PvP reactions (a Character hurting another Character).
- Trust-gated Quests unlocked by a high Disposition.
- Any LLM use outside chat wording, including tone classification of player messages.
- Faction-based starting Disposition.

## Further Notes

- Renaming Quest givers and merchants to deterministic names changes their names once, on the first start after
  release; existing chat history is in memory only, so nothing is lost.
- ADR 0010 allows Roster reshuffles on config reload: a Resident key may point to a different or no NPC after a
  reshuffle; orphan relations are kept and fade with drift.
- Bark lines multiply by trigger × Temperament × band × locale: start with a minimal set per trigger and let
  authors grow it.
