import { beforeEach, describe, expect, it } from "vitest";
import {
  buildMetricsExport,
  dayLabel,
  maxTotal,
  mergeBuckets,
  niceMax,
  pickAlive,
  pickDeaths,
  replaceBuckets,
  slotIndexOf,
  slotsFor,
  stackKeys,
  stackedColumns,
  windowOf,
} from "../admin/pages/worldSimulator/metrics/metrics";
import {
  DEFAULT_POPULATION_CAP,
  MAX_NPCS_PER_FRAME_CEILING,
  frameCapFor,
  npcColorSlot,
  resetNpcColors,
  type SimMetricBucket,
  type SimulationConfig,
} from "../admin/pages/worldSimulator/types";

function bucket(index: number, over: Partial<SimMetricBucket> = {}): SimMetricBucket {
  return {
    index,
    startGameDay: index * 0.25,
    tick: index * 100,
    deathsByType: {},
    ageDeathsByType: {},
    killDeathsByType: {},
    starvationsByType: {},
    birthsByType: {},
    evolutionsByType: {},
    aliveByType: {},
    meanHungerByType: {},
    meanAgeRatioByType: {},
    adultShareByType: {},
    starvingShareByType: {},
    pregnantShareByType: {},
    attacks: 0,
    gestations: 0,
    births: 0,
    birthsBlocked: 0,
    matings: 0,
    spawns: 0,
    fed: 0,
    hungry: 0,
    evolutions: 0,
    ...over,
  };
}

describe("mergeBuckets", () => {
  it("appends new buckets in index order", () => {
    const merged = mergeBuckets([bucket(1), bucket(2)], [bucket(3)]);
    expect(merged.map((b) => b.index)).toEqual([1, 2, 3]);
  });

  it("replaces a bucket of the same index instead of adding to it", () => {
    // the server re-sends the bucket that was still open, so adding would double-count it
    const merged = mergeBuckets([bucket(1, { attacks: 3 })], [bucket(1, { attacks: 5 })]);
    expect(merged).toHaveLength(1);
    expect(merged[0].attacks).toBe(5);
  });

  it("reorders a batch that arrives out of order", () => {
    const merged = mergeBuckets([], [bucket(4), bucket(2), bucket(3)]);
    expect(merged.map((b) => b.index)).toEqual([2, 3, 4]);
  });

  it("keeps the newest buckets once capped", () => {
    const merged = mergeBuckets([bucket(1), bucket(2), bucket(3)], [bucket(4)], 2);
    expect(merged.map((b) => b.index)).toEqual([3, 4]);
  });

  it("returns the previous list untouched for an empty batch", () => {
    const previous = [bucket(1)];
    expect(mergeBuckets(previous, [])).toBe(previous);
  });
});

describe("replaceBuckets", () => {
  it("drops whatever was there before", () => {
    // a restart reuses the socket: merging would leave the old arena's history on the chart
    expect(replaceBuckets({ bucketGameDays: 0.25, buckets: [bucket(9)] }).map((b) => b.index)).toEqual([9]);
  });

  it("treats a missing payload as an empty series", () => {
    expect(replaceBuckets(null)).toEqual([]);
    expect(replaceBuckets(undefined)).toEqual([]);
  });
});

describe("stackKeys", () => {
  beforeEach(() => resetNpcColors());

  it("orders types by palette slot, so touching segments are palette-neighbours", () => {
    const buckets = [
      bucket(1, { deathsByType: { goat: 1, wolf: 5 } }),
      bucket(2, { deathsByType: { goat: 2, rabbit: 1 } }),
    ];
    // goat and wolf claim their slots from the first bucket, rabbit from the second
    expect(stackKeys(buckets, pickDeaths)).toEqual(["goat", "wolf", "rabbit"]);
  });

  it("keeps its order when the populations trade places", () => {
    const early = [bucket(1, { aliveByType: { goat: 1, wolf: 9 } })];
    const later = [bucket(1, { aliveByType: { goat: 9, wolf: 1 } })];
    expect(stackKeys(early, pickAlive)).toEqual(stackKeys(later, pickAlive));
  });

  it("gives every type a distinct slot", () => {
    const buckets = [bucket(1, { aliveByType: { goat: 1, wolf: 1, rabbit: 1 } })];
    const slots = stackKeys(buckets, pickAlive).map(npcColorSlot);
    expect(new Set(slots).size).toBe(3);
  });

  it("is empty when nothing was recorded", () => {
    expect(stackKeys([bucket(1)], pickDeaths)).toEqual([]);
  });
});

describe("stackedColumns", () => {
  it("stacks values cumulatively in key order", () => {
    const columns = stackedColumns([bucket(1, { deathsByType: { goat: 2, wolf: 3 } })], pickDeaths, ["goat", "wolf"]);
    expect(columns[0].segments).toEqual([
      { key: "goat", from: 0, to: 2 },
      { key: "wolf", from: 2, to: 5 },
    ]);
    expect(columns[0].total).toBe(5);
  });

  it("skips types absent from a bucket", () => {
    const columns = stackedColumns([bucket(1, { deathsByType: { goat: 1 } })], pickDeaths, ["goat", "wolf"]);
    expect(columns[0].segments.map((s) => s.key)).toEqual(["goat"]);
  });

  it("keeps a column per bucket even when empty, so the x axis stays continuous", () => {
    const columns = stackedColumns([bucket(1), bucket(2)], pickDeaths, ["goat"]);
    expect(columns).toHaveLength(2);
    expect(columns[0].total).toBe(0);
  });
});

describe("maxTotal / niceMax", () => {
  it("finds the tallest stack", () => {
    const columns = stackedColumns(
      [bucket(1, { aliveByType: { goat: 4 } }), bucket(2, { aliveByType: { goat: 7, wolf: 2 } })],
      pickAlive,
      ["goat", "wolf"],
    );
    expect(maxTotal(columns)).toBe(9);
  });

  it("rounds the axis up to a readable bound", () => {
    expect(niceMax(37)).toBe(50);
    expect(niceMax(9)).toBe(10);
    expect(niceMax(120)).toBe(200);
  });

  it("never returns zero, since callers divide by it", () => {
    expect(niceMax(0)).toBe(1);
    expect(niceMax(-3)).toBe(1);
  });
});

describe("slotsFor / windowOf", () => {
  it("splits a 10-day window into one slot per bucket", () => {
    expect(slotsFor(0.25, 10)).toBe(40);
    expect(slotsFor(0.5, 10)).toBe(20);
  });

  it("returns null for the whole history", () => {
    expect(slotsFor(0.25, 0)).toBeNull();
  });

  it("keeps only the most recent buckets once the window is full", () => {
    const buckets = [1, 2, 3, 4, 5].map((i) => bucket(i));
    expect(windowOf(buckets, 3).map((b) => b.index)).toEqual([3, 4, 5]);
  });

  it("shows what exists while the window is still filling", () => {
    const buckets = [bucket(1), bucket(2)];
    // the chart grows from the left instead of stretching two bars across the box
    expect(windowOf(buckets, 40)).toBe(buckets);
  });

  it("shows everything retained when no window is set", () => {
    const buckets = [bucket(1), bucket(2)];
    expect(windowOf(buckets, null)).toBe(buckets);
  });

  it("leaves the caller's history untouched, so nothing is lost by narrowing the view", () => {
    const buckets = [1, 2, 3, 4].map((i) => bucket(i));
    windowOf(buckets, 2);
    expect(buckets).toHaveLength(4);
  });
});

describe("slotIndexOf", () => {
  it("right-aligns buckets against a wider slot grid, so a partly-filled window slides in from the right", () => {
    expect(slotIndexOf([bucket(1), bucket(2)], 5)).toEqual([3, 4]);
  });

  it("fills every slot once the window is full", () => {
    expect(slotIndexOf([bucket(1), bucket(2), bucket(3)], 3)).toEqual([0, 1, 2]);
  });

  it("indexes from zero when showing the whole retained history", () => {
    expect(slotIndexOf([bucket(1), bucket(2)], null)).toEqual([0, 1]);
  });
});

describe("frameCapFor", () => {
  it("matches the population cap, so a full arena viewed whole is not truncated", () => {
    expect(frameCapFor(DEFAULT_POPULATION_CAP)).toBe(DEFAULT_POPULATION_CAP);
    expect(frameCapFor(300)).toBe(300);
  });

  it("stops at the ceiling for a very large arena", () => {
    expect(frameCapFor(50_000)).toBe(MAX_NPCS_PER_FRAME_CEILING);
  });

  it("treats an uncapped arena as the ceiling rather than as unlimited", () => {
    // 0 means "no population cap"; sending unlimited NPCs per frame is what floods the socket
    expect(frameCapFor(0)).toBe(MAX_NPCS_PER_FRAME_CEILING);
    expect(frameCapFor(-1)).toBe(MAX_NPCS_PER_FRAME_CEILING);
  });
});

describe("dayLabel", () => {
  it("trims trailing zeroes", () => {
    expect(dayLabel(3)).toBe("d 3");
    expect(dayLabel(3.5)).toBe("d 3.5");
    expect(dayLabel(3.25)).toBe("d 3.25");
  });

  it("takes the day marker from the caller, so the charts follow the UI locale", () => {
    expect(dayLabel(3.5, "j")).toBe("j 3.5");
  });
});

describe("buildMetricsExport", () => {
  const config: SimulationConfig = {
    halfSize: 40,
    groundY: 7,
    wallHeight: 4,
    ticksPerSecond: 200,
    seed: 7,
    zoneLevel: 3,
    maxNpcs: 0,
    populationCap: 500,
    maxNpcsPerFrame: 500,
    vegetationDensity: 0.05,
    gameDayDurationSeconds: 60,
    maxGameDays: 30,
    npcTuning: {} as never,
    npcDefinitionOverrides: {},
    initialSpawns: [{ type: "goat", count: 4 }],
    players: [],
    autoSpawnEnabled: true,
  };

  it("carries the arena settings, so a run can be read without them being retyped", () => {
    const out = buildMetricsExport([bucket(1)], 0.25, config);
    expect(out.setup?.seed).toBe(7);
    expect(out.setup?.maxGameDays).toBe(30);
    expect(out.setup?.initialSpawns).toEqual([{ type: "goat", count: 4 }]);
  });

  it("reports no setup when nothing is attached yet", () => {
    expect(buildMetricsExport([bucket(1)], 0.25, null).setup).toBeNull();
  });

  it("spans to the end of the last slice, not to its start", () => {
    // three 0.25-day slices starting at 0.25 cover 0.25 → 1.00
    const out = buildMetricsExport([bucket(1), bucket(2), bucket(3)], 0.25, config);
    expect(out.span.slices).toBe(3);
    expect(out.span.gameDays).toBeCloseTo(0.75);
  });

  it("sums the counters over the whole history", () => {
    const out = buildMetricsExport([bucket(1, { births: 2 }), bucket(2, { births: 3, attacks: 1 })], 0.25, config);
    expect(out.totals.counters.births).toBe(5);
    expect(out.totals.counters.attacks).toBe(1);
    expect(out.totals.counters.evolutions).toBe(0);
  });

  it("summarises each type: peak, final, mean and deaths", () => {
    const out = buildMetricsExport(
      [
        bucket(1, { aliveByType: { goat: 2 }, deathsByType: { goat: 1 } }),
        bucket(2, { aliveByType: { goat: 6 } }),
        bucket(3, { aliveByType: { goat: 4 }, deathsByType: { goat: 2 } }),
      ],
      0.25,
      config,
    );
    const goat = out.totals.byType.find((entry) => entry.type === "goat");
    expect(goat).toEqual({
      type: "goat",
      peakAlive: 6,
      finalAlive: 4,
      meanAlive: 4,
      deaths: 3,
      ageDeaths: 0,
      kills: 0,
      starvations: 0,
      births: 0,
      evolutions: 0,
      slicesPresent: 3,
    });
  });

  it("splits deaths per cause, which is what a balance pass reads", () => {
    const out = buildMetricsExport(
      [
        bucket(1, {
          aliveByType: { goat: 5 },
          deathsByType: { goat: 3 },
          ageDeathsByType: { goat: 1 },
          killDeathsByType: { goat: 1 },
          starvationsByType: { goat: 1 },
          birthsByType: { goat_baby: 2 },
          evolutionsByType: { goat_baby: 1 },
        }),
      ],
      0.25,
      config,
    );
    const goat = out.totals.byType.find((entry) => entry.type === "goat");
    expect(goat?.deaths).toBe(3);
    expect(goat?.ageDeaths).toBe(1);
    expect(goat?.kills).toBe(1);
    expect(goat?.starvations).toBe(1);

    const baby = out.totals.byType.find((entry) => entry.type === "goat_baby");
    expect(baby?.births).toBe(2);
    expect(baby?.evolutions).toBe(1);

    expect(out.slices[0].ageDeaths).toEqual({ goat: 1 });
    expect(out.slices[0].starvations).toEqual({ goat: 1 });
  });

  it("keeps reading a bucket recorded before the cause split", () => {
    // deathsByType stays the source of the total, so an older export still summarises
    const legacy = { ...bucket(1, { deathsByType: { wolf: 2 } }) } as Partial<SimMetricBucket>;
    delete legacy.ageDeathsByType;
    delete legacy.killDeathsByType;
    delete legacy.starvationsByType;

    const out = buildMetricsExport([legacy as SimMetricBucket], 0.25, config);
    const wolf = out.totals.byType.find((entry) => entry.type === "wolf");
    expect(wolf?.deaths).toBe(2);
    expect(wolf?.ageDeaths).toBe(0);
  });

  it("keeps a type that only ever died, so a wipe-out is still visible", () => {
    const out = buildMetricsExport([bucket(1, { deathsByType: { wolf: 3 } })], 0.25, config);
    const wolf = out.totals.byType.find((entry) => entry.type === "wolf");
    expect(wolf?.deaths).toBe(3);
    expect(wolf?.peakAlive).toBe(0);
  });

  it("emits one slice per bucket, oldest first", () => {
    const out = buildMetricsExport([bucket(1, { aliveByType: { goat: 1 } }), bucket(2)], 0.25, config);
    expect(out.slices.map((s) => s.tick)).toEqual([100, 200]);
    expect(out.slices[0].alive).toEqual({ goat: 1 });
  });

  it("survives an empty history rather than reporting a negative span", () => {
    const out = buildMetricsExport([], 0.25, config);
    expect(out.span).toMatchObject({ slices: 0, gameDays: 0, lastTick: 0 });
    expect(out.totals.byType).toEqual([]);
  });

  it("round-trips through JSON, which is the whole point", () => {
    const out = buildMetricsExport([bucket(1, { aliveByType: { goat: 1 } })], 0.25, config);
    expect(JSON.parse(JSON.stringify(out))).toEqual(out);
  });
});
