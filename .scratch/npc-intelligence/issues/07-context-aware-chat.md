# 07: Context-aware Resident chat

**What to build:** When a Player chats with a Resident, the LLM receives a small deterministic context block
(about 5 lines): Disposition band label (never the number), Temperament tone, time-of-day phase from Game time,
local weather, latest Memories; a Region Roster hint only if it fits the budget. Replies reflect how the
Resident sees the Character. The LLM still never writes Disposition (ADR 0011). Spec:
`.scratch/npc-intelligence/spec.md`.

**Blocked by:** 02 (Disposition from Quest turn-in)

**Status:** ready-for-agent

- [ ] System prompt contains the context block; stays within the line budget
- [ ] Band shown as a label, never as a score
- [ ] `NpcChatServiceTest` covers the block for a Resident with Memories, at night, in rain
- [ ] Chat history remains in memory only, unchanged
