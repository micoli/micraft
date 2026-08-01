import type { SimMetricBucket, SimMetrics } from "./types";

/** Same ceiling as the server keeps, so the client never holds more history than exists. */
export const METRIC_HISTORY = 240;

/** A counter series the charts can draw. */
export interface CounterSeries {
  key: "attacks" | "gestations" | "births" | "matings" | "spawns" | "fed" | "hungry" | "evolutions";
  label: string;
  color: string;
}

export const COUNTER_SERIES: CounterSeries[] = [
  { key: "attacks", label: "attaques", color: "#FB923C" },
  { key: "gestations", label: "gestations", color: "#E879F9" },
  { key: "births", label: "naissances", color: "#22D3EE" },
  { key: "matings", label: "accouplements", color: "#F472B6" },
  { key: "spawns", label: "apparitions", color: "#38BDF8" },
  { key: "fed", label: "repas", color: "#4ADE80" },
  { key: "hungry", label: "faims", color: "#FACC15" },
  { key: "evolutions", label: "évolutions", color: "#818CF8" },
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
export const WINDOW_OPTIONS: { days: number; label: string }[] = [
  { days: 10, label: "10 j" },
  { days: 30, label: "30 j" },
  { days: 0, label: "tout" },
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
export const pickAlive: TypedPick = (bucket) => bucket.aliveByType;

/**
 * NPC types present in the series, biggest total first.
 *
 * Ordering by total, not alphabetically: the stack is drawn in this order, so the dominant type sits
 * at the bottom and the thin ones stay visible on top instead of being shuffled around as the run
 * goes on. Ties fall back to the name so the order cannot flicker between two equal types.
 */
export function stackKeys(buckets: readonly SimMetricBucket[], pick: TypedPick): string[] {
  const totals = new Map<string, number>();
  for (const bucket of buckets) {
    for (const [type, value] of Object.entries(pick(bucket))) {
      totals.set(type, (totals.get(type) ?? 0) + value);
    }
  }
  return [...totals.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0])).map(([type]) => type);
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
  return series
    .map((entry) => ({ series: entry, value: bucket[entry.key] }))
    .filter((row) => row.value > 0)
    .sort((a, b) => b.value - a.value || a.series.label.localeCompare(b.series.label));
}

/** Game-day label for a bucket, e.g. `j 12.5`. */
export function dayLabel(startGameDay: number): string {
  return `j ${startGameDay.toFixed(2).replace(/\.?0+$/, "")}`;
}
