import { describe, expect, it } from "vitest";
import {
  ALL_NPC_TYPES,
  filterEvents,
  npcTypesInEvents,
  type SimEvent,
  type SimEventType,
} from "../admin/simulator/types";

function event(seq: number, type: SimEventType, npcType?: string): SimEvent {
  return { seq, tick: seq, gameDay: 0, type, message: `e${seq}`, npcType };
}

const EVENTS: SimEvent[] = [
  event(1, "BIRTH", "goat"),
  event(2, "DEATH", "wolf"),
  event(3, "HUNGRY", "goat"),
  event(4, "SYSTEM"),
  event(5, "ATTACK", "wolf"),
];

const ALL_EVENT_TYPES = new Set<SimEventType>(["BIRTH", "DEATH", "HUNGRY", "SYSTEM", "ATTACK"]);

describe("event log filtering", () => {
  it("keeps everything when no filter is narrowed", () => {
    expect(filterEvents(EVENTS, ALL_EVENT_TYPES).map((e) => e.seq)).toEqual([1, 2, 3, 4, 5]);
  });

  it("filters by event family", () => {
    const families = new Set<SimEventType>(["BIRTH", "DEATH"]);
    expect(filterEvents(EVENTS, families).map((e) => e.seq)).toEqual([1, 2]);
  });

  it("filters by NPC type", () => {
    expect(filterEvents(EVENTS, ALL_EVENT_TYPES, "goat").map((e) => e.seq)).toEqual([1, 3]);
    expect(filterEvents(EVENTS, ALL_EVENT_TYPES, "wolf").map((e) => e.seq)).toEqual([2, 5]);
  });

  it("combines both filters", () => {
    const families = new Set<SimEventType>(["HUNGRY", "ATTACK"]);
    expect(filterEvents(EVENTS, families, "wolf").map((e) => e.seq)).toEqual([5]);
  });

  it("hides rows belonging to no NPC once a type is picked", () => {
    const system = filterEvents(EVENTS, ALL_EVENT_TYPES, "goat").filter((e) => e.type === "SYSTEM");
    expect(system).toEqual([]);
    expect(filterEvents(EVENTS, ALL_EVENT_TYPES, ALL_NPC_TYPES)).toHaveLength(5);
  });

  it("returns nothing for a type nobody emitted", () => {
    expect(filterEvents(EVENTS, ALL_EVENT_TYPES, "camel")).toEqual([]);
  });

  it("returns rows oldest-first by default", () => {
    expect(filterEvents(EVENTS, ALL_EVENT_TYPES).map((e) => e.seq)).toEqual([1, 2, 3, 4, 5]);
  });

  it("returns rows newest-first on request, which is how the log is read", () => {
    expect(filterEvents(EVENTS, ALL_EVENT_TYPES, ALL_NPC_TYPES, true).map((e) => e.seq)).toEqual([5, 4, 3, 2, 1]);
  });

  it("keeps newest-first while filtering", () => {
    const families = new Set<SimEventType>(["BIRTH", "HUNGRY"]);
    expect(filterEvents(EVENTS, families, "goat", true).map((e) => e.seq)).toEqual([3, 1]);
  });

  it("does not disturb the caller's array", () => {
    const source: SimEvent[] = [...EVENTS];
    filterEvents(source, ALL_EVENT_TYPES, ALL_NPC_TYPES, true);
    expect(source.map((e) => e.seq)).toEqual([1, 2, 3, 4, 5]);
  });

  it("lists the NPC types present, sorted and deduplicated", () => {
    expect(npcTypesInEvents(EVENTS)).toEqual(["goat", "wolf"]);
    expect(npcTypesInEvents([])).toEqual([]);
    expect(npcTypesInEvents([event(1, "SYSTEM")])).toEqual([]);
  });
});
