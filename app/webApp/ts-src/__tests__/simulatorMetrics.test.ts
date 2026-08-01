import { describe, expect, it } from "vitest";
import {
  COUNTER_SERIES,
  columnAt,
  counterRowsAt,
  counterValues,
  dayLabel,
  linePoints,
  maxTotal,
  mergeBuckets,
  niceMax,
  pickAlive,
  pickDeaths,
  replaceBuckets,
  slotsFor,
  stackKeys,
  stackedColumns,
  tooltipRows,
  windowOf,
} from "../admin/simulator/metrics";
import {
  DEFAULT_POPULATION_CAP,
  MAX_NPCS_PER_FRAME_CEILING,
  frameCapFor,
  type SimMetricBucket,
} from "../admin/simulator/types";

function bucket(index: number, over: Partial<SimMetricBucket> = {}): SimMetricBucket {
  return {
    index,
    startGameDay: index * 0.25,
    tick: index * 100,
    deathsByType: {},
    aliveByType: {},
    attacks: 0,
    gestations: 0,
    births: 0,
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
  it("orders types by total, biggest first", () => {
    const buckets = [
      bucket(1, { deathsByType: { goat: 1, wolf: 5 } }),
      bucket(2, { deathsByType: { goat: 2, rabbit: 1 } }),
    ];
    expect(stackKeys(buckets, pickDeaths)).toEqual(["wolf", "goat", "rabbit"]);
  });

  it("breaks ties by name so the stack order cannot flicker", () => {
    const buckets = [bucket(1, { aliveByType: { wolf: 2, goat: 2 } })];
    expect(stackKeys(buckets, pickAlive)).toEqual(["goat", "wolf"]);
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

describe("counterValues / linePoints", () => {
  it("extracts one counter across the series", () => {
    expect(counterValues([bucket(1, { attacks: 2 }), bucket(2, { attacks: 5 })], "attacks")).toEqual([2, 5]);
  });

  it("maps values into the box with y flipped", () => {
    expect(linePoints([0, 10], 100, 50, 10)).toBe("0,50 100,0");
  });

  it("draws a single value as a flat segment", () => {
    // a one-point polyline renders nothing at all
    expect(linePoints([5], 100, 50, 10)).toBe("0,25 100,25");
  });

  it("returns nothing for an empty series", () => {
    expect(linePoints([], 100, 50, 10)).toBe("");
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

describe("linePoints on a slot grid", () => {
  it("spaces points by the window's slots, not by how many exist", () => {
    // 5 slots over 100 px = one point every 25 px, so a half-filled window stops mid-box
    expect(linePoints([0, 0, 0], 100, 10, 1, 5)).toBe("0,10 25,10 50,10");
  });

  it("still spans the box when the window covers the whole history", () => {
    expect(linePoints([0, 0, 0], 100, 10, 1, null)).toBe("0,10 50,10 100,10");
  });

  it("draws one value as a short segment rather than across the window", () => {
    expect(linePoints([1], 100, 10, 1, 5)).toBe("0,0 25,0");
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

describe("columnAt", () => {
  it("maps a fraction of the width to a column", () => {
    expect(columnAt(0, 4)).toBe(0);
    expect(columnAt(0.3, 4)).toBe(1);
    expect(columnAt(0.99, 4)).toBe(3);
  });

  it("clamps just past either edge instead of losing the hover", () => {
    // a pointer one pixel past the last bar should still describe that bar
    expect(columnAt(1, 4)).toBe(3);
    expect(columnAt(1.4, 4)).toBe(3);
    expect(columnAt(-0.1, 4)).toBe(0);
  });

  it("has nothing to point at in an empty chart", () => {
    expect(columnAt(0.5, 0)).toBeNull();
  });
});

describe("tooltipRows", () => {
  it("lists the stack items, biggest first", () => {
    const column = stackedColumns([bucket(1, { aliveByType: { goat: 2, wolf: 9, duck: 5 } })], pickAlive, [
      "goat",
      "wolf",
      "duck",
    ])[0];
    expect(tooltipRows(column)).toEqual([
      { key: "wolf", value: 9 },
      { key: "duck", value: 5 },
      { key: "goat", value: 2 },
    ]);
  });

  it("breaks ties by name so rows cannot swap between renders", () => {
    const column = stackedColumns([bucket(1, { aliveByType: { wolf: 3, goat: 3 } })], pickAlive, ["wolf", "goat"])[0];
    expect(tooltipRows(column).map((r) => r.key)).toEqual(["goat", "wolf"]);
  });

  it("is empty for a column where nothing happened", () => {
    expect(tooltipRows(stackedColumns([bucket(1)], pickDeaths, ["goat"])[0])).toEqual([]);
  });
});

describe("counterRowsAt", () => {
  const series = COUNTER_SERIES.filter((s) => s.key === "attacks" || s.key === "births" || s.key === "fed");

  it("lists the counters of a slice, biggest first", () => {
    const rows = counterRowsAt(bucket(1, { attacks: 3, births: 7, fed: 1 }), series);
    expect(rows.map((r) => [r.series.key, r.value])).toEqual([
      ["births", 7],
      ["attacks", 3],
      ["fed", 1],
    ]);
  });

  it("drops the counters at zero", () => {
    const rows = counterRowsAt(bucket(1, { attacks: 2 }), series);
    expect(rows.map((r) => r.series.key)).toEqual(["attacks"]);
  });

  it("only reports the series it was given, so the card matches the drawn lines", () => {
    const rows = counterRowsAt(bucket(1, { attacks: 2, gestations: 9 }), series);
    expect(rows.map((r) => r.series.key)).toEqual(["attacks"]);
  });

  it("has nothing to show off the end of the series", () => {
    expect(counterRowsAt(undefined, series)).toEqual([]);
  });
});

describe("dayLabel", () => {
  it("trims trailing zeroes", () => {
    expect(dayLabel(3)).toBe("j 3");
    expect(dayLabel(3.5)).toBe("j 3.5");
    expect(dayLabel(3.25)).toBe("j 3.25");
  });
});
