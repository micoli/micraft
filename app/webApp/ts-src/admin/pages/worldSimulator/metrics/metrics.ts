import type { TranslationKey } from "../../../i18n";
import {
  npcColorSlot,
  type NpcTuning,
  type SimMetricBucket,
  type SimMetrics,
  type SimSpawn,
  type SimulationConfig,
} from "../types";

/** Same ceiling as the server keeps, so the client never holds more history than exists. */
export const METRIC_HISTORY = 240;

/** A counter series the charts can draw. */
export interface CounterSeries {
  key: "attacks" | "gestations" | "births" | "birthsBlocked" | "matings" | "spawns" | "fed" | "hungry" | "evolutions";
  labelKey: TranslationKey;
  color: string;
}

export const COUNTER_SERIES: CounterSeries[] = [
  { key: "attacks", labelKey: "sim.counter.attacks", color: "#FB923C" },
  { key: "gestations", labelKey: "sim.counter.gestations", color: "#E879F9" },
  { key: "births", labelKey: "sim.counter.births", color: "#22D3EE" },
  { key: "birthsBlocked", labelKey: "sim.counter.birthsBlocked", color: "#78716C" },
  { key: "matings", labelKey: "sim.counter.matings", color: "#F472B6" },
  { key: "spawns", labelKey: "sim.counter.spawns", color: "#38BDF8" },
  { key: "fed", labelKey: "sim.counter.fed", color: "#4ADE80" },
  { key: "hungry", labelKey: "sim.counter.hungry", color: "#FACC15" },
  { key: "evolutions", labelKey: "sim.counter.evolutions", color: "#818CF8" },
];

/**
 * Fold an incoming batch into the history.
 *
 * Same-index buckets replace rather than add up: the server re-sends the bucket that was still open
 * when it last pushed, so adding would double-count everything that landed in it.
 */
export function mergeBuckets(
  previous: readonly SimMetricBucket[],
  incoming: readonly SimMetricBucket[],
  capacity: number = METRIC_HISTORY,
): SimMetricBucket[] {
  if (incoming.length === 0) return previous as SimMetricBucket[];
  const byIndex = new Map<number, SimMetricBucket>();
  for (const bucket of previous) byIndex.set(bucket.index, bucket);
  for (const bucket of incoming) byIndex.set(bucket.index, bucket);
  const merged = [...byIndex.values()].sort((a, b) => a.index - b.index);
  return merged.length > capacity ? merged.slice(merged.length - capacity) : merged;
}

/** Replace the whole history — used on a snapshot, which carries the server's full series. */
export function replaceBuckets(metrics: SimMetrics | null | undefined): SimMetricBucket[] {
  return metrics ? mergeBuckets([], metrics.buckets) : [];
}

/** Game days on screen at once. Wider windows exist in the selector; the history is never dropped. */
export const DEFAULT_WINDOW_GAME_DAYS = 10;

/** 0 = the whole retained history. */
export const WINDOW_OPTIONS: { days: number; labelKey: TranslationKey }[] = [
  { days: 10, labelKey: "sim.metrics.window10" },
  { days: 30, labelKey: "sim.metrics.window30" },
  { days: 0, labelKey: "sim.metrics.windowAll" },
];

/**
 * Column slots the chart is divided into for a window of [windowGameDays], or null for "fit whatever
 * is retained".
 *
 * A fixed slot count is what makes the chart slide: bars keep the same width and a new one appears at
 * the right edge as the oldest leaves on the left. Sizing bars off the data count instead would make
 * every bar shrink as history accumulates, so the shape of a busy period would change under the eye
 * without anything having happened.
 */
export function slotsFor(bucketGameDays: number, windowGameDays: number): number | null {
  if (windowGameDays <= 0 || bucketGameDays <= 0) return null;
  return Math.max(1, Math.round(windowGameDays / bucketGameDays));
}

/**
 * The [slots] most recent buckets — the visible window. Older ones stay in the caller's history,
 * they are just off-screen to the left; null shows everything retained.
 */
export function windowOf(buckets: readonly SimMetricBucket[], slots: number | null): SimMetricBucket[] {
  if (slots === null || buckets.length <= slots) return buckets as SimMetricBucket[];
  return buckets.slice(buckets.length - slots);
}

export type TypedPick = (bucket: SimMetricBucket) => Record<string, number>;

export const pickDeaths: TypedPick = (bucket) => bucket.deathsByType;
export const pickAgeDeaths: TypedPick = (bucket) => bucket.ageDeathsByType;
export const pickKillDeaths: TypedPick = (bucket) => bucket.killDeathsByType;
export const pickStarvations: TypedPick = (bucket) => bucket.starvationsByType;
export const pickKillers: TypedPick = (bucket) => bucket.killsByKillerType ?? {};
export const pickEvolutions: TypedPick = (bucket) => bucket.evolutionsByType ?? {};
export const pickLevelUps: TypedPick = (bucket) => bucket.levelUpsByType ?? {};
export const pickAlive: TypedPick = (bucket) => bucket.aliveByType;
export const pickMeanHunger: TypedPick = (bucket) => bucket.meanHungerByType ?? {};
export const pickStarvingShare: TypedPick = (bucket) => bucket.starvingShareByType ?? {};
export const pickAdultShare: TypedPick = (bucket) => bucket.adultShareByType ?? {};

/**
 * NPC types present in the series, in palette order.
 *
 * The stack is drawn in this order, so this is what decides which segments end up touching — and the
 * palette only guarantees that *neighbouring* slots are easy to tell apart. Ordering by slot is
 * therefore what makes the guarantee real on screen.
 *
 * It also rules out the alternative, ordering by total: that would make a type's position, and with
 * it the colour of its neighbours, depend on how the run is going, repainting the chart as the
 * populations trade places. Slots never move once handed out.
 */
export function stackKeys(buckets: readonly SimMetricBucket[], pick: TypedPick): string[] {
  const present = new Set<string>();
  for (const bucket of buckets) {
    for (const type of Object.keys(pick(bucket))) present.add(type);
  }
  const keys = [...present];
  // Claim the slots before sorting, never inside the comparator: a type new to the palette takes the
  // next free slot on its first lookup, and the order in which sort() visits elements is not
  // specified — claiming there would make the result depend on the engine.
  const slots = new Map(keys.map((type) => [type, npcColorSlot(type)]));
  return keys.sort((a, b) => (slots.get(a) ?? 0) - (slots.get(b) ?? 0));
}

export interface StackSegment {
  key: string;
  from: number;
  to: number;
}

export interface StackedColumn {
  index: number;
  startGameDay: number;
  total: number;
  segments: StackSegment[];
}

/** One column per bucket, each split into cumulative `[from, to)` bands in [keys] order. */
export function stackedColumns(
  buckets: readonly SimMetricBucket[],
  pick: TypedPick,
  keys: readonly string[],
): StackedColumn[] {
  return buckets.map((bucket) => {
    const values = pick(bucket);
    const segments: StackSegment[] = [];
    let cursor = 0;
    for (const key of keys) {
      const value = values[key] ?? 0;
      // zero-height bands would still cost a DOM node per bucket per type
      if (value <= 0) continue;
      segments.push({ key, from: cursor, to: cursor + value });
      cursor += value;
    }
    return { index: bucket.index, startGameDay: bucket.startGameDay, total: cursor, segments };
  });
}

/** Tallest stack in the series; 0 when nothing happened. */
export function maxTotal(columns: readonly StackedColumn[]): number {
  return columns.reduce((max, column) => Math.max(max, column.total), 0);
}

/**
 * A round upper bound at or above [value], so the y axis reads 40 rather than 37 and stops twitching
 * on every push. Never returns 0: a flat-zero chart still needs a height to divide by.
 */
export function niceMax(value: number): number {
  if (value <= 0) return 1;
  const magnitude = Math.pow(10, Math.floor(Math.log10(value)));
  for (const step of [1, 2, 5, 10]) {
    const candidate = step * magnitude;
    if (candidate >= value) return candidate;
  }
  return 10 * magnitude;
}

/** Values of one counter across the series. */
export function counterValues(buckets: readonly SimMetricBucket[], key: CounterSeries["key"]): number[] {
  return buckets.map((bucket) => bucket[key]);
}

/**
 * Polyline points for [values] inside a [width]×[height] box, y flipped for SVG. A single value is
 * drawn as a flat segment across the box — a one-point polyline would render nothing.
 *
 * [slots] is the window's column count: passing it puts the points on the same grid as the stacked
 * bars, so a partly-filled window draws a line that stops mid-box instead of one stretched across it
 * — and the three charts stay readable against each other.
 */
export function linePoints(
  values: readonly number[],
  width: number,
  height: number,
  max: number,
  slots: number | null = null,
): string {
  if (values.length === 0) return "";
  const scale = (value: number) => height - (value / max) * height;
  const columns = slots ?? values.length;
  if (columns <= 1) return `0,${scale(values[0])} ${width},${scale(values[0])}`;
  const step = width / (columns - 1);
  if (values.length === 1) return `0,${scale(values[0])} ${step},${scale(values[0])}`;
  return values.map((value, i) => `${i * step},${scale(value)}`).join(" ");
}

/**
 * Which column the pointer is over, given its position as a 0..1 fraction of the chart width.
 *
 * Works off a fraction rather than pixels because the chart scales its viewBox to the panel: the
 * caller measures the rendered box, and this stays independent of how wide it ended up. Out-of-range
 * fractions clamp to the end columns instead of returning nothing — a pointer one pixel past the
 * last bar should still describe that bar.
 */
export function columnAt(fraction: number, count: number): number | null {
  if (count <= 0) return null;
  const index = Math.floor(fraction * count);
  return Math.min(count - 1, Math.max(0, index));
}

export interface TooltipRow {
  key: string;
  value: number;
}

/**
 * Stack items of one column, biggest first, zeroes dropped — what the hover card lists. The stack is
 * drawn bottom-up in [keys] order, but a reader wants the dominant type first, and a row of zeroes
 * would push the interesting ones off the card.
 */
export function tooltipRows(column: StackedColumn): TooltipRow[] {
  return column.segments
    .map((segment) => ({ key: segment.key, value: segment.to - segment.from }))
    .sort((a, b) => b.value - a.value || a.key.localeCompare(b.key));
}

/**
 * Counter series of one bucket, biggest first, zeroes dropped — the line chart's hover card. Same
 * rule as [tooltipRows]: a card padded with zeroes pushes the rows that matter out of sight.
 */
export function counterRowsAt(
  bucket: SimMetricBucket | undefined,
  series: readonly CounterSeries[],
): { series: CounterSeries; value: number }[] {
  if (!bucket) return [];
  return (
    series
      .map((entry) => ({ series: entry, value: bucket[entry.key] }))
      .filter((row) => row.value > 0)
      // ties broken on the key, not the rendered label: the order must not change with the locale
      .sort((a, b) => b.value - a.value || a.series.key.localeCompare(b.series.key))
  );
}

/**
 * Game-day label for a bucket, e.g. `d 12.5`. [dayAbbrev] is the translated one-letter day marker —
 * passed in rather than looked up, so this stays a pure function the charts can call per column.
 */
export function dayLabel(startGameDay: number, dayAbbrev: string = "d"): string {
  return `${dayAbbrev} ${startGameDay.toFixed(2).replace(/\.?0+$/, "")}`;
}

// ── Export ────────────────────────────────────────────────────────────────────

/** What the arena was set up with, so a run can be read without guessing its rules. */
export interface MetricsExportSetup {
  halfSize: number;
  seed: number;
  zoneLevel: number;
  gameDayDurationSeconds: number;
  /** Game days the run was bounded to; 0 = unbounded. */
  maxGameDays: number;
  populationCap: number;
  vegetationDensity: number;
  autoSpawnEnabled: boolean;
  initialSpawns: SimSpawn[];
  npcTuning: NpcTuning;
  npcDefinitionOverrides: Record<string, unknown>;
}

/** Per-type outcome of a run, which is what a balancing pass actually reads. */
export interface MetricsExportTypeTotals {
  type: string;
  /** Highest population the type reached in any one slice. */
  peakAlive: number;
  /** Population in the last slice it appeared in. */
  finalAlive: number;
  /** Mean population over the slices where it was present. */
  meanAlive: number;
  /** Every death of this type, whatever the cause — the sum of the three below. */
  deaths: number;
  ageDeaths: number;
  kills: number;
  starvations: number;
  births: number;
  evolutions: number;
  /** Slices the type was alive in, out of the exported span. */
  slicesPresent: number;
}

export interface MetricsExport {
  format: "micraft.simulation.metrics";
  version: 1;
  setup: MetricsExportSetup | null;
  span: {
    bucketGameDays: number;
    slices: number;
    firstGameDay: number;
    lastGameDay: number;
    gameDays: number;
    lastTick: number;
  };
  totals: {
    byType: MetricsExportTypeTotals[];
    counters: Record<CounterSeries["key"], number>;
  };
  /** One entry per time slice, oldest first — the three charts, as numbers. */
  slices: {
    gameDay: number;
    tick: number;
    alive: Record<string, number>;
    meanHunger: Record<string, number>;
    starvingShare: Record<string, number>;
    adultShare: Record<string, number>;
    deaths: Record<string, number>;
    ageDeaths: Record<string, number>;
    kills: Record<string, number>;
    starvations: Record<string, number>;
    counters: Record<CounterSeries["key"], number>;
  }[];
}

const emptyCounters = (): Record<CounterSeries["key"], number> =>
  Object.fromEntries(COUNTER_SERIES.map((series) => [series.key, 0])) as Record<CounterSeries["key"], number>;

/**
 * Everything the three charts are drawn from, as one JSON-ready object.
 *
 * Written for a reader rather than for a plotter: the raw slices are there, but so are the setup that
 * produced them and the per-type totals, because "wolves wiped out the goats" is a question about the
 * tuning that was in force, not about a series of pixels. Whoever reads this back cannot see the
 * screen — the numbers have to carry their own context.
 *
 * The whole retained history is exported, not the window on screen: narrowing the view is a reading
 * aid, and silently exporting less than was asked for would be a trap.
 */
export function buildMetricsExport(
  buckets: readonly SimMetricBucket[],
  bucketGameDays: number,
  config: SimulationConfig | null,
): MetricsExport {
  const first = buckets[0];
  const last = buckets[buckets.length - 1];

  const counters = emptyCounters();
  for (const bucket of buckets) {
    for (const series of COUNTER_SERIES) counters[series.key] += bucket[series.key];
  }

  // one pass per type rather than per bucket per type: the history holds a few hundred slices
  const seen = new Map<
    string,
    {
      peak: number;
      final: number;
      sum: number;
      slices: number;
      deaths: number;
      ageDeaths: number;
      kills: number;
      starvations: number;
      births: number;
      evolutions: number;
    }
  >();
  const of = (type: string) => {
    const existing = seen.get(type);
    if (existing) return existing;
    const fresh = {
      peak: 0,
      final: 0,
      sum: 0,
      slices: 0,
      deaths: 0,
      ageDeaths: 0,
      kills: 0,
      starvations: 0,
      births: 0,
      evolutions: 0,
    };
    seen.set(type, fresh);
    return fresh;
  };
  for (const bucket of buckets) {
    for (const [type, alive] of Object.entries(bucket.aliveByType)) {
      const totals = of(type);
      totals.peak = Math.max(totals.peak, alive);
      totals.sum += alive;
      totals.slices++;
      // the last slice the type was actually in, not the last slice of the run
      totals.final = alive;
    }
    for (const [type, deaths] of Object.entries(bucket.deathsByType)) of(type).deaths += deaths;
    // and per cause: "the wolves ate everything" and "everything died of old age" are opposite
    // balance problems that a single deaths column cannot tell apart. Read defensively — a bucket
    // recorded before the split carries the total only.
    for (const [type, n] of Object.entries(bucket.ageDeathsByType ?? {})) of(type).ageDeaths += n;
    for (const [type, n] of Object.entries(bucket.killDeathsByType ?? {})) of(type).kills += n;
    for (const [type, n] of Object.entries(bucket.starvationsByType ?? {})) of(type).starvations += n;
    for (const [type, n] of Object.entries(bucket.birthsByType ?? {})) of(type).births += n;
    for (const [type, n] of Object.entries(bucket.evolutionsByType ?? {})) of(type).evolutions += n;
  }

  return {
    format: "micraft.simulation.metrics",
    version: 1,
    setup: config
      ? {
          halfSize: config.halfSize,
          seed: config.seed,
          zoneLevel: config.zoneLevel,
          gameDayDurationSeconds: config.gameDayDurationSeconds,
          maxGameDays: config.maxGameDays,
          populationCap: config.populationCap,
          vegetationDensity: config.vegetationDensity,
          autoSpawnEnabled: config.autoSpawnEnabled,
          initialSpawns: config.initialSpawns,
          npcTuning: config.npcTuning,
          npcDefinitionOverrides: config.npcDefinitionOverrides,
        }
      : null,
    span: {
      bucketGameDays,
      slices: buckets.length,
      firstGameDay: first?.startGameDay ?? 0,
      lastGameDay: last?.startGameDay ?? 0,
      // a slice covers its own width, so the span runs to the end of the last one
      gameDays: buckets.length === 0 ? 0 : last.startGameDay - first.startGameDay + bucketGameDays,
      lastTick: last?.tick ?? 0,
    },
    totals: {
      byType: [...seen.entries()]
        .map(([type, totals]) => ({
          type,
          peakAlive: totals.peak,
          finalAlive: totals.final,
          meanAlive: totals.slices > 0 ? Number((totals.sum / totals.slices).toFixed(2)) : 0,
          deaths: totals.deaths,
          ageDeaths: totals.ageDeaths,
          kills: totals.kills,
          starvations: totals.starvations,
          births: totals.births,
          evolutions: totals.evolutions,
          slicesPresent: totals.slices,
        }))
        .sort((a, b) => b.peakAlive - a.peakAlive || a.type.localeCompare(b.type)),
      counters,
    },
    slices: buckets.map((bucket) => ({
      gameDay: bucket.startGameDay,
      tick: bucket.tick,
      alive: bucket.aliveByType,
      deaths: bucket.deathsByType,
      meanHunger: bucket.meanHungerByType ?? {},
      starvingShare: bucket.starvingShareByType ?? {},
      adultShare: bucket.adultShareByType ?? {},
      ageDeaths: bucket.ageDeathsByType ?? {},
      kills: bucket.killDeathsByType ?? {},
      starvations: bucket.starvationsByType ?? {},
      counters: Object.fromEntries(COUNTER_SERIES.map((series) => [series.key, bucket[series.key]])) as Record<
        CounterSeries["key"],
        number
      >,
    })),
  };
}

export const BOX_W = 300;
export const BOX_H = 90;

/** Surface showing between two stacked segments. The box is 90 units tall over 90 px, so this is px. */
export const SEGMENT_GAP = 1.5;
