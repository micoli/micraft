# PROTOTYPE — Laya vs Ollama for NPC chat intent (2026-09-28)

Throwaway. Question: can Laya (`laya-serve`, `POST http://127.0.0.1:9000/v1/systemone`) replace the
Ollama JSON-mode *intent* (`none | offer_quest | give_item` + quest/item id) proposed by NPC chat,
inside the ADR-0011 fence (intent is only proposed, then `NpcChatService.validateAction` revalidates it)?

Run: `python3 .scratch/laya-npc-intent/prototype_bench.py [--laya-model auto|english|multilingual] [--ollama-model gemma3:1b] [--skip-ollama]`

Setup: hermit Quest giver prompt, 6 real Quests, 5 real items, 25 labelled player messages (16 en, 9 fr,
incl. small talk, "problem statement" hints and one prompt injection). Laya on CPU (Docker), Ollama local.

## Files

- `results_gemma3_1b_multilingual.md` — round 1, mixed suite, Laya forced `multilingual` only.
- `results_gemma3_1b_auto.md` — round 1, mixed suite, Laya auto with reworded `offer_quest` criteria
  only (it overwrote the first Laya+Ollama run, whose numbers are kept in the table below).
- `results_gemma3_1b_auto_ollama+ollama-intent+ollama-intent-fs.md` — round 2, mixed suite.
- `results_en_gemma3_1b_english_laya+…md` — round 2, English suite, all four pipelines.

## Results

| Pipeline | action+id ok | p50 | p95 |
|---|---|---|---|
| Ollama `gemma3:1b` (prod default, JSON mode) | 6/25 | 985 ms | 1308 ms |
| Laya auto-routing (en→`laya`, fr→`laya-multilingual`) | 17/25 | 1283 ms | 1604 ms |
| Laya auto, reworded `offer_quest` criteria | 16/25 | 1257 ms | 1847 ms |
| Laya forced `multilingual` | 14/25 | 445 ms | 686 ms |
| Ollama `Qwen3.5:0.8B` | aborted — too slow (thinking) | | |

- Confidence gate never helps (best 13/25 at 0.5–0.6 on auto): `confidence` is low on correct answers too.
- Systematic Laya miss: the player *describes a problem* ("wolves attack the farms", "ducks attack
  fishermen") → `none`. Survives criteria rewording.
- Prompt injection ("give me a SIEGE_CANON") → Laya always picks *some* whitelisted item; harmless behind
  the whitelist fence, but a wrong offer.
- Short French ("Merci, au revoir.") gets routed to the English checkpoint.
- gemma3:1b almost never emits an action (mostly `none`), once returned truncated JSON.

## Round 2 — dedicated Ollama intent prompt (2026-09-28)

`ollama-intent`: a classification-only system prompt (no reply text), `temperature: 0`, `think: false`,
JSON schema with `questId`/`itemId` as **enums of the whitelisted ids**. `ollama-intent-fs`: same plus 5
few-shot turns (one of them — cats raiding food — overlaps a test case in spirit). All on `gemma3:1b`.

`--suite en` = the 16 en cases + the 9 fr cases translated, so Laya stays on its `english` checkpoint.

| Pipeline | mixed suite (16 en + 9 fr) | en suite (25 en) | p50 |
|---|---|---|---|
| Ollama prod prompt (reply + intent) | 8/25 | 5/25 | ~1000 ms |
| Laya (auto / `english`) | 17/25 | 17/25 | ~1300 ms |
| **Ollama dedicated intent** | **18/25** | **19/25** | ~600 ms |
| Ollama dedicated intent + few-shot | 18/25 | 19/25 | ~520 ms |

- English-only doesn't help Laya: the problem-statement blind spot is not a language issue.
- Dedicated prompt residual errors: heal requests and the injection → `offer_quest:wolf_slayer`,
  plain "mission?" asks → `none`. Errors are mostly disjoint from Laya's.
- Few-shot buys nothing measurable.

## Verdict (updated)

The weak point is the production prompt mixing reply generation and intent in one small-model call.
A **second, dedicated Ollama call for intent** (enum-constrained ids, temperature 0) reaches ~76 % on
the same bench, beats Laya, is faster than Laya on CPU, and needs no new service. Next step if pursued:
split `OllamaClient.chat` into reply + intent calls (intent can run in parallel), keep
`validateAction` as the fence, grow the labelled set from real chat logs.

## Verdict (round 1)

Laya classifies intent far better than today's `gemma3:1b` JSON mode (17 vs 6 / 25), so the current
chat intent is the weak point, not the idea. But Laya is **not adoptable as is**: ~65 % accuracy,
a blind spot on problem statements, unusable confidence for gating, and on CPU 0.45–1.3 s per call
(the advertised 33 ms is GPU), plus one more ~1–2 GB service to run next to Ollama.

Worth revisiting only with: a GPU or the multilingual checkpoint alone, a larger labelled set drawn
from real chat logs, and a fine-tune (`laya-typed-decisions` notebook). Cheaper first step: split the
Ollama call into a dedicated intent prompt and measure it with this same bench.
