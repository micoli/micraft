# 01: Resident identity

**What to build:** Quest givers and merchants become Residents. Each gets a Resident key (Region + NPC type +
rank of that type within the Region), and a name and Temperament derived deterministically from the World seed
and that key. A Player sees the same Quest giver and merchant names after a server restart and after a Region is
parked and reactivated. Wild NPCs and Pets are not Residents. The runtime NPC id stays random; later tickets key
everything on the Resident key. Spec: `.scratch/npc-intelligence/spec.md`.

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] Quest givers and merchants expose a Resident key; wild NPCs and Pets have none
- [x] Two World builds with the same seed give each Resident the same name and Temperament
- [x] A Resident keeps its name through park / respawn of its Region
- [x] Temperament is one of a small fixed set (3 to start), drawn from the seed
- [x] Test-world test (`buildGameWorld` + `FakePlayerSession`) covers stability across two builds
- [x] `CONTEXT.md` terms used as is (Resident, Temperament)
