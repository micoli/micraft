---
status: accepted
---

# The LLM is a read-only NPC voice

NPCs chat through a small local LLM (Ollama) that is slow, rate-limited and may be offline. Making NPCs feel
less robotic (Disposition, Barks, context-aware chat) must not depend on it. The LLM only speaks: it receives a
context built deterministically from game state (Game time, weather, Disposition, recent events) and returns
text. Its only effects on the World are the existing validated chat actions. Disposition changes only on
deterministic game events (Quest turned in, purchase, attack, Pet killed…), never on what the LLM makes of a
player's words; Barks come from templates, not generation.

## Considered options

- **LLM classifies player tone to move Disposition**: richer reactions to insults or politeness, but
  non-deterministic, untestable, trivially gamed by prompt, and dead when Ollama is down.
- **LLM generates Barks or summarises NPC memory**: more variety, but adds latency and load outside chat for
  text that templates cover well enough.

## Consequences

- Every NPC behaviour except chat wording works, and is testable, with the LLM disabled.
- A player insulting an NPC in chat gets an in-character reply but no lasting Disposition change.
- Chat prompts grow a deterministic context block; its size must stay small for small models.
