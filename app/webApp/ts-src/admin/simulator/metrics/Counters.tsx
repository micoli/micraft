import type { SimMetricBucket } from "../types";
import { useT } from "../../i18n";
import { useMemo, useState } from "react";
import {
  BOX_H,
  BOX_W,
  columnAt,
  COUNTER_SERIES,
  counterRowsAt,
  CounterSeries,
  counterValues,
  dayLabel,
  linePoints,
  niceMax,
} from "./metrics";
import { Hover } from "./Hover";
import { ChartFrame } from "./ChartFrame";
import { LegendDot } from "./LegendDot";
import { HoverGuide } from "./HoverGuide";
import { HoverCard } from "./HoverCard";

/** Multi-line chart over the counter series, with per-series toggles. */
export function Counters({ buckets, slots }: { buckets: SimMetricBucket[]; slots: number | null }) {
  const t = useT();
  const dayAbbrev = t("sim.metrics.dayAbbrev");
  const [hidden, setHidden] = useState<ReadonlySet<CounterSeries["key"]>>(new Set());

  // every series is computed, then filtered for display: toggling a legend entry is a render concern
  // and should not throw away the extracted values
  const all = useMemo(
    () => COUNTER_SERIES.map((series) => ({ series, values: counterValues(buckets, series.key) })),
    [buckets],
  );
  const lines = all.filter((line) => !hidden.has(line.series.key));
  // scaled on what is shown, so hiding the busiest series actually zooms in on the rest
  const top = niceMax(Math.max(0, ...lines.flatMap((line) => line.values)));
  const last = buckets[buckets.length - 1];

  const toggle = (key: CounterSeries["key"]) =>
    setHidden((current) => {
      const next = new Set(current);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });

  const [hover, setHover] = useState<Hover | null>(null);
  const slotWidth = BOX_W / (slots ?? Math.max(1, buckets.length));

  // same slot grid as the stacked charts, so hovering the same x on any of the three describes the
  // same slice of game time
  const onMove = (event: React.MouseEvent<SVGSVGElement>) => {
    const box = event.currentTarget.getBoundingClientRect();
    const index = columnAt((event.clientX - box.left) / box.width, slots ?? buckets.length);
    if (index === null) return;
    setHover({ index, x: event.clientX - box.left, y: event.clientY - box.top });
  };

  const hoveredBucket = hover ? (buckets[hover.index] ?? null) : null;
  // only the shown series: the card must agree with the lines actually drawn
  const hoveredRows = counterRowsAt(
    hoveredBucket ?? undefined,
    lines.map((line) => line.series),
  );

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
      <svg
        viewBox={`0 0 ${BOX_W} ${BOX_H}`}
        preserveAspectRatio="none"
        className="h-[90px] w-full"
        onMouseMove={onMove}
        onMouseLeave={() => setHover(null)}
      >
        <line x1={0} y1={BOX_H} x2={BOX_W} y2={BOX_H} stroke="#2E3A4E" strokeWidth={0.5} />
        {hover && hoveredBucket && <HoverGuide x={hover.index * slotWidth} width={slotWidth} />}
        {lines.map((line) => (
          <polyline
            key={line.series.key}
            points={linePoints(line.values, BOX_W, BOX_H, top, slots)}
            fill="none"
            stroke={line.series.color}
            strokeWidth={1}
            vectorEffect="non-scaling-stroke"
          />
        ))}
      </svg>
      {hover && hoveredBucket && (
        <HoverCard
          x={hover.x}
          y={hover.y}
          title={dayLabel(hoveredBucket.startGameDay, dayAbbrev)}
          summary={t(
            "sim.metrics.unitEvents",
            hoveredRows.reduce((sum, row) => sum + row.value, 0),
          )}
          emptyLabel={t("sim.metrics.nothing")}
          rows={hoveredRows.map((row) => ({
            label: t(row.series.labelKey),
            color: row.series.color,
            value: row.value,
          }))}
        />
      )}
    </ChartFrame>
  );
}
