import type { SimMetricBucket } from "../types";
import { useT } from "../../../i18n";
import { useMemo, useState } from "react";
import { COUNTER_SERIES, CounterSeries, dayLabel, niceMax, slotIndexOf } from "./metrics";
import { ChartFrame } from "./ChartFrame";
import { LegendDot } from "./LegendDot";
import { defineChart, lineY } from "@tanstack/charts";
import { scalePoint } from "@tanstack/charts/scales/point";
import { scaleLinear } from "@tanstack/charts/scales/linear";
import { tooltip } from "@tanstack/charts/tooltip";
import { Chart } from "@tanstack/charts/react";

interface CounterRow {
  slot: number;
  series: string;
  value: number;
}

/** Multi-line chart over the counter series, with per-series toggles. */
export function Counters({ buckets, slots }: { buckets: SimMetricBucket[]; slots: number | null }) {
  const t = useT();
  const dayAbbrev = t("sim.metrics.dayAbbrev");
  const [hidden, setHidden] = useState<ReadonlySet<CounterSeries["key"]>>(new Set());

  const lines = COUNTER_SERIES.filter((series) => !hidden.has(series.key));
  const slotIndex = useMemo(() => slotIndexOf(buckets, slots), [buckets, slots]);
  const dayBySlot = useMemo(() => {
    const map = new Map<number, number>();
    buckets.forEach((bucket, i) => map.set(slotIndex[i], bucket.startGameDay));
    return map;
  }, [buckets, slotIndex]);

  // scaled on what is shown, so hiding the busiest series actually zooms in on the rest
  const top = niceMax(Math.max(0, ...lines.flatMap((series) => buckets.map((bucket) => bucket[series.key]))));
  const last = buckets[buckets.length - 1];
  const slotCount = slots ?? Math.max(1, buckets.length);

  const toggle = (key: CounterSeries["key"]) =>
    setHidden((current) => {
      const next = new Set(current);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });

  const rows = useMemo<CounterRow[]>(
    () =>
      lines.flatMap((series) =>
        buckets.map((bucket, i) => ({ slot: slotIndex[i], series: series.key, value: bucket[series.key] })),
      ),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [buckets, slotIndex, hidden],
  );

  const definition = useMemo(() => {
    const domain = Array.from({ length: slotCount }, (_, i) => i);
    return defineChart({
      marks: [
        lineY(rows, {
          x: "slot",
          y: "value",
          z: "series",
          color: "series",
          strokeWidth: 1,
        }),
      ],
      x: {
        scale: () => scalePoint<number>().domain(domain).padding(0.05),
        axis: {
          ticks: {
            format: (value: number) => {
              const day = dayBySlot.get(value);
              return day === undefined ? "" : dayLabel(day, dayAbbrev);
            },
          },
        },
      },
      y: {
        scale: () => scaleLinear().domain([0, top]),
        grid: true,
      },
      color: { domain: lines.map((series) => series.key), range: lines.map((series) => series.color) },
      focus: "group-x",
      tooltip,
    });
  }, [rows, lines, top, slotCount, dayBySlot, dayAbbrev]);

  return (
    <ChartFrame
      title={t("sim.metrics.eventsPerSlice")}
      hint={t("sim.metrics.max", top)}
      legend={COUNTER_SERIES.map((series) => (
        <button
          key={series.key}
          type="button"
          onClick={() => toggle(series.key)}
          title={t(hidden.has(series.key) ? "sim.metrics.show" : "sim.metrics.hide")}
          className={"cursor-pointer" + (hidden.has(series.key) ? " opacity-30" : "")}
        >
          <LegendDot color={series.color} label={t(series.labelKey)} value={last ? last[series.key] : 0} />
        </button>
      ))}
    >
      {buckets.length === 0 ? (
        <div className="flex h-[90px] items-center justify-center text-[10px] text-[#4A5568]">
          {t("sim.metrics.nothingYet")}
        </div>
      ) : (
        <Chart definition={definition} height={90} ariaLabel={t("sim.metrics.eventsPerSlice")} />
      )}
    </ChartFrame>
  );
}
