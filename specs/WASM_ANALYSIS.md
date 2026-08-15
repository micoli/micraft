# Kotlin/Wasm vs Plain JS — Should the web client drop Kotlin/Wasm entirely?

## Scope

This document evaluates **removing Kotlin/Wasm from the web client altogether** — not just rewriting `main.kt`'s bootstrap sequence in TypeScript. `main.kt` itself is a thin ~120-line orchestration file with no business logic (see [Why `main.kt` is not the real question](#why-mainkt-is-not-the-real-question)); the actual question is whether the whole `app/webApp/src/wasmJsMain` client — and its dependency on the shared `core` module — should move to plain JS/TS.

`core/src/commonMain` is ~2540 lines of Kotlin, compiled into **both** the JVM server and the Wasm web client:

```
protocol/  ServerMessage.kt, ClientMessage.kt, ProtobufCodec.kt, MailMessage.kt
physics/   AabbCollider.kt
game/world/  BlockRegistry.kt, ItemRegistry.kt, Chunk.kt, BlockState.kt, BlockType.kt,
             WorldConstants.kt, BlockDefinition.kt, ItemDefinition.kt, RecipeDefinition.kt, ...
player/    Player.kt, PlayerStance.kt, rpg/ (CharacterData, BaseStats, DerivedStats, ...)
npc/       Npc.kt, AnimalStateData.kt, ...
combat/    StatusEffect.kt, ShortcutSlot.kt
quest/     QuestProgress.kt
ui/        LayoutSyncPayload.kt, WidgetType.kt, GameLayout.kt, ...
```

Removing Wasm from the web client means every one of these must either be reimplemented in TypeScript, or replaced by a codegen/schema pipeline — not just the ~120 lines of `main.kt`.

## Why `main.kt` is not the real question

`main.kt` does no computation: it sequences calls into JS bindings (`jsCreateEngine`, `jsSetupKeyboard`, ...) and delegates all real logic to `GameClient`, which itself depends on `core`. Rewriting `main.kt` alone in TS would be trivial and would change nothing about the client/server architecture — the risk and cost of dropping Wasm live entirely in what `GameClient` pulls in from `core`. This document therefore treats "drop `main.kt`'s Wasm-ness" and "drop Wasm from the web client" as the same question, since the former without the latter has no real effect on the codebase's cost/benefit balance.

## What full Wasm removal would require

1. **Protocol codec** (`protocol/ProtobufCodec.kt`, `ServerMessage.kt`, `ClientMessage.kt`) — currently one Kotlin definition compiled to both server (JVM, source of truth) and client (Wasm). Wire format is Protobuf-or-JSON (`MessageEncoding`), chosen per `server.yaml`. Dropping Wasm means:
   - Either hand-writing/maintaining a **second, independent** TS decoder for every `ServerMessage`/`ClientMessage` variant (currently dozens — see the `dispatchMap` in `GameClient.kt:351-791`, one handler per message type), kept in sync by hand with the Kotlin server definitions, or
   - Building a **codegen pipeline** (protobuf schema → TS types, similar in spirit to the existing `make gen-api` OpenAPI generator for REST routes) to keep a single source of truth without hand-syncing. This is real, ongoing infra work, not a one-time port — every new `ServerMessage` field needs the codegen step wired into CI (`make check-openapi`-style drift check) or it silently regresses to hand-sync.
2. **Physics** (`physics/AabbCollider.kt`) — used by both server (authoritative validation) and client (`LocalPlayerController` prediction, `GameClient.kt:111-136` 60fps tick). This is the single highest-risk item: a TS reimplementation that isn't bit-for-bit behaviorally identical to the Kotlin server version reintroduces exactly the client/server prediction divergence bugs the shared-`core` architecture exists to prevent (see CLAUDE.md: "Server authoritative... Client-side prediction... always server-authoritative" for Y). There is no codegen shortcut for physics logic — it would need a hand port plus a cross-language conformance test suite (feed identical inputs to both implementations, diff outputs) to have any confidence it's safe.
3. **Chunk meshing** (`ChunkManager.kt` — client-only, not shared with server, so not itself a Wasm-removal blocker) — but it consumes `core` types (`Chunk`, `BlockType`, `BlockRegistry`, `WorldConstants.CHUNK_SIZE`) that would need TS equivalents regardless. See the perf discussion below — this is also the code most likely to regress performance-wise if ported to hand-written TS.
4. **Registries / world data** (`BlockRegistry`, `ItemRegistry`, `PlainColorRegistry`, `BlockDefinition`, `RecipeDefinition`, `WorldConstants`, `PlayerConstants`) — mostly data classes + lookup logic. Lower risk than physics/protocol (less behaviorally subtle), but still ~15 files to port and keep in sync with server-side changes (e.g. new block type added to `data/resources/blocks/`).
5. **Domain types used across the wire** (`player/rpg/*`, `npc/*`, `combat/*`, `quest/*`) — mostly plain data classes referenced by protocol messages; their shape must match whatever the codec produces, so they're coupled to point 1's solution.

## Cost of full removal

- **Duplication, not elimination, of logic** — every item above still needs to exist somewhere; "removing Wasm" only moves where it's written (Kotlin/JVM-shared → hand-written TS), it doesn't remove the requirement to keep client and server behaviorally consistent. The current architecture gets that consistency "for free" from the compiler (same source, two targets); a TS port would need to earn it back via either codegen (protocol only, not physics) or discipline + tests (everything else).
- **Physics is the crux** — of everything listed, `AabbCollider` is the one piece where a subtly-wrong port is a silent, hard-to-detect bug (client predicts differently than server validates → visible jitter/rubber-banding only under specific movement edge cases). This is the strongest argument *against* full removal, independent of the `main.kt`-level performance argument made earlier in this analysis.
- **Ongoing maintenance tax, not just migration cost** — every future `core` change (new block type, new protocol message, tuned physics constant) currently updates one file and both targets recompile from it. Post-removal, the same change requires a deliberate "also update the TS side" step, with no compiler to catch a missed one (only integration tests, if they exist and cover the changed path).
- **Toolchain friction removed** — the disadvantages already noted in this analysis (Kotlin/Wasm experimental API, `make dev-reset-wasm`/`make build-wasm` friction, larger binary, harder debugging) genuinely go away. This is real and not to be dismissed — it's the actual pain the team feels day to day, versus the physics/protocol risk which is latent until it bites.
- **Perf regression risk for chunk meshing** — `renderRow`/`computeFaceAO` (see prior perf section) are written the way they are — precomputed `ByteArray(256)` flag tables instead of `HashMap` lookups, no per-face object allocation, explicit `ns/face` budget — specifically to avoid JS JIT deopt/megamorphic-dispatch costs. A straightforward TS port risks losing these guarantees unless someone deliberately preserves the same low-level discipline (typed arrays, no polymorphic dispatch in the hot path) — which is possible in hand-written JS, but is not free; it requires someone to know why the Kotlin code looks the way it does and replicate that discipline, not just transliterate the logic.

## Kotlin/JS as a middle ground

Kotlin/JS (compiling `core` + client to plain JS instead of Wasm) keeps the single-source-of-truth property for protocol/physics/registries while dropping the Wasm-specific costs (binary size, instantiation, `ExperimentalWasmJsInterop`). See the earlier "Cost of compiling to JavaScript" discussion in this analysis: it trades Wasm-specific friction for JIT-dependent perf and a real (if smaller than a full TS rewrite) migration effort, and still needs the same `external`/interop boilerplate against BabylonJS. It does **not** require reimplementing physics/protocol by hand — the compiler still emits both targets from one source — so it avoids the crux risk (point 2 above) that a full hand-written-TS removal carries. If the goal is specifically "get off Wasm the runtime" rather than "get off Kotlin the shared-source-of-truth," Kotlin/JS is a materially safer target than a full TS rewrite.

## Recommendation

**Do not fully remove Kotlin/Wasm (or Kotlin as the client's implementation language) from the web client.** The toolchain friction is real but bounded and already absorbed by existing `make` tooling; the risk of a hand-maintained TS physics/protocol port introducing silent client/server divergence is open-ended and hits exactly the failure mode the shared-`core` architecture was built to prevent. If the Wasm *runtime* specifically (not the shared-Kotlin-source model) is the pain point, Kotlin/JS is the option to evaluate next — it keeps the single-source-of-truth guarantee for protocol and physics while dropping Wasm's binary-size/instantiation/experimental-API costs. Full removal down to independent hand-written TS should be reconsidered only if the team is willing to fund an ongoing conformance-test investment (physics cross-language diff tests, protocol codegen with CI drift checks) large enough to replace what the Kotlin compiler currently guarantees for free.
