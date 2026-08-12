import { SimMetricBucket } from "../types";
import { BOX_H, BOX_W, maxTotal, SEGMENT_GAP, stackedColumns, stackKeys, TypedPick } from "./metrics";
import { useMemo, useState } from "react";
import { columnAt, dayLabel, niceMax, tooltipRows } from "./metrics";
import { useT } from "../../../i18n";
import { npcColor } from "../types";
import { Hover } from "./Hover";
import { ChartFrame } from "./ChartFrame";
import { PieChart } from "./PieChart";
import { LegendDot } from "./LegendDot";
import { HoverGuide } from "./HoverGuide";
import { HoverCard } from "./HoverCard";

/** Stacked bars per NPC type — one column per time slice. */
export function StackedByType({
  title,
  hint,
  unit,
  buckets,
  pick,
  slots,
}: {
  title: string;
  hint: string;
  /** What one unit of the stack counts, shown next to the total in the hover card. */
  unit: string;
  /** Already narrowed to the visible window. */
  buckets: SimMetricBucket[];
  pick: TypedPick;
  /** Column slots of the window, or null to fit the retained history. */
  slots: number | null;
}) {
  const t = useT();
  const dayAbbrev = t("sim.metrics.dayAbbrev");
  const keys = useMemo(() => stackKeys(buckets, pick), [buckets, pick]);
  const columns = useMemo(() => stackedColumns(buckets, pick, keys), [buckets, pick, keys]);
  const top = niceMax(maxTotal(columns));
  // Width comes from the window's slot count, not from how many buckets exist: that is what makes the
  // chart slide left as it fills rather than squeezing every bar thinner over time.
  const barWidth = BOX_W / (slots ?? Math.max(1, columns.length));
  const latest = columns[columns.length - 1];
  const [hover, setHover] = useState<Hover | null>(null);

  // A bar can be well under a pixel wide with 240 buckets, so hovering is resolved from the pointer's
  // position across the whole chart rather than per-rect: every x lands on a column, with no gaps
  // between bars where the card would flicker off.
  const onMove = (event: React.MouseEvent<SVGSVGElement>) => {
    const box = event.currentTarget.getBoundingClientRect();
    // resolved against the slot grid, so the empty right side of a partly-filled window reports
    // nothing instead of clamping onto the last bar several days away
    const index = columnAt((event.clientX - box.left) / box.width, slots ?? columns.length);
    if (index === null) return;
    setHover({ index, x: event.clientX - box.left, y: event.clientY - box.top });
  };

  // the list shrinks as old buckets are dropped, so a held index can fall off the end
  const hovered = hover ? (columns[hover.index] ?? null) : null;

  return (
    <ChartFrame
      title={title}
      hint={hint}
      aside={keys.length > 0 ? <PieChart keys={keys} buckets={buckets} pick={pick} /> : undefined}
      legend={
        keys.length === 0 ? (
          <span className="text-[10px] text-[#4A5568]">{t("sim.metrics.nothingYet")}</span>
        ) : (
          keys.map((key) => (
            <LegendDot
              key={key}
              color={npcColor(key)}
              label={key}
              value={latest ? (pick(buckets[buckets.length - 1])[key] ?? 0) : 0}
            />
          ))
        )
      }
    >
      <svg
        viewBox={`0 0 ${BOX_W} ${BOX_H}`}
        preserveAspectRatio="none"
        className="h-[90px] w-full"
        onMouseMove={onMove}
        onMouseLeave={() => setHover(null)}
      >
        <line x1={0} y1={BOX_H} x2={BOX_W} y2={BOX_H} stroke="#2E3A4E" strokeWidth={0.5} />
        {hover && hovered && <HoverGuide x={hover.index * barWidth} width={barWidth} />}
        {columns.map((column, i) =>
          column.segments.map((segment) => {
            const height = ((segment.to - segment.from) / top) * BOX_H;
            return (
              <rect
                key={`${column.index}-${segment.key}`}
                x={i * barWidth}
                y={BOX_H - (segment.to / top) * BOX_H}
                width={Math.max(barWidth - 0.3, 0.4)}
                // a sliver of the surface between segments, so the boundary reads even when two
                // neighbours are close in colour — but never at the price of hiding a thin segment
                height={height > SEGMENT_GAP * 2 ? height - SEGMENT_GAP : height}
                fill={npcColor(segment.key)}
              />
            );
          }),
        )}
      </svg>
      {hover && hovered && (
        <HoverCard
          x={hover.x}
          y={hover.y}
          title={dayLabel(hovered.startGameDay, dayAbbrev)}
          summary={`${hovered.total} ${unit}`}
          emptyLabel={t("sim.metrics.nothing")}
          rows={tooltipRows(hovered).map((row) => ({
            label: row.key,
            color: npcColor(row.key),
            value: row.value,
          }))}
        />
      )}
      <div className="mt-0.5 flex justify-between text-[9px] text-[#4A5568]">
        <span>{columns.length > 0 ? dayLabel(columns[0].startGameDay, dayAbbrev) : "—"}</span>
        <span>{t("sim.metrics.max", top)}</span>
        <span>{latest ? dayLabel(latest.startGameDay, dayAbbrev) : "—"}</span>
      </div>
    </ChartFrame>
  );
}
