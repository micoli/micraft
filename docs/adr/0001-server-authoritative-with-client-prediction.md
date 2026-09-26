---
status: accepted
---

# Server-authoritative simulation with client-side prediction

The server owns the truth for every Character. Clients send a **Move intent**, never a position, and the server
replies with the resolved state. The main reason is a single source of truth for everything that interacts with
movement (NPCs, combat, Claims, persistence); resistance to cheating comes second. To hide latency, the client runs
**Prediction** on all three axes and **Reconciliation** toward the server, with a per-axis correction policy:
XZ is soft-corrected through a remaining-error vector, and Y is soft-corrected within a tolerance and snapped
beyond one block, because a vertical mispredict (sinking into or floating above a block) is far more visible than
a horizontal drift. We accepted keeping the client and server movement in lock-step as the price.

## Consequences

- Every movement modifier must apply identically on both sides, or the Rec XZ / Rec Y HUD metrics climb (past
  regressions: swimming slowdown, flying stance). See ADR-0002.
- A server tick spike shows up as rubber-banding, not as desync.
