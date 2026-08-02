import { memo, useMemo, useState } from "react";
import {
  COUNTER_SERIES,
  DEFAULT_WINDOW_GAME_DAYS,
  WINDOW_OPTIONS,
  columnAt,
  counterRowsAt,
  counterValues,
  dayLabel,
  linePoints,
  maxTotal,
  niceMax,
  pickAlive,
  pickDeaths,
  slotsFor,
  stackKeys,
  stackedColumns,
  tooltipRows,
  windowOf,
  type CounterSeries,
  type TypedPick,
} from "./metrics";
import { useT } from "../i18n";
import { npcColor, type SimMetricBucket } from "./types";

interface Props {
  buckets: SimMetricBucket[];
  bucketGameDays: number;
}

const BOX_W = 300;
const BOX_H = 90;

function ChartFrame({
  title,
  hint,
  children,
  legend,
}: {
  title: string;
  hint: string;
  children: React.ReactNode;
  legend: React.ReactNode;
}) {
  return (
    <div className="rounded-lg border border-[#2E3A4E] bg-[#1A222C] p-2.5">
      <div className="mb-1 flex items-baseline gap-2">
        <p className="flex-1 text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF]">{title}</p>
        <span className="text-[10px] text-[#4A5568]">{hint}</span>
      </div>
      {/* relative: hover cards are positioned against this box */}
      <div className="relative rounded bg-[#0E1726] p-1.5">{children}</div>
      <div className="mt-1.5 flex flex-wrap gap-x-3 gap-y-0.5">{legend}</div>
    </div>
  );
}

function LegendDot({ color, label, value }: { color: string; label: string; value?: number }) {
  // Faded when the newest slice has nothing for this entry: a busy arena carries a dozen legend
  // entries and the ones still moving should be the ones that read.
  const idle = value === 0;
  return (
    <span className={"flex items-center gap-1 text-[10px] text-[#8A99AF]" + (idle ? " opacity-20" : "")}>
      <span className="inline-block h-2 w-2 rounded-sm" style={{ background: color }} />
      {label}
      {value !== undefined && <span className="text-white">{value}</span>}
    </span>
  );
}

interface CardRow {
  label: string;
  color: string;
  value: number;
}

/** Hover card shared by the stacked and line charts, so all three read the same way. */
function HoverCard({
  x,
  y,
  title,
  summary,
  rows,
  emptyLabel,
}: {
  x: number;
  y: number;
  title: string;
  summary: string;
  rows: CardRow[];
  emptyLabel: string;
}) {
  // flipped to the left of the pointer near the right edge, where a card would be clipped otherwise
  const flip = x > 190;
  return (
    <div
      className="pointer-events-none absolute z-10 min-w-[110px] rounded-md border border-[#2E3A4E] bg-[#1A222C] px-2 py-1.5 text-[10px] shadow-lg"
      style={{ left: flip ? undefined : x + 12, right: flip ? 8 : undefined, top: y + 8 }}
    >
      <div className="mb-1 flex items-baseline gap-2">
        <span className="font-semibold text-white">{title}</span>
        <span className="text-[#8A99AF]">{summary}</span>
      </div>
      {rows.length === 0 && <div className="text-[#4A5568]">{emptyLabel}</div>}
      {rows.map((row) => (
        <div key={row.label} className="flex items-center gap-1.5">
          <span className="inline-block h-2 w-2 shrink-0 rounded-sm" style={{ background: row.color }} />
          <span className="flex-1 text-[#8A99AF]">{row.label}</span>
          <span className="text-white">{row.value}</span>
        </div>
      ))}
    </div>
  );
}

interface Hover {
  /**
   * Position in the column list, not the column itself: the series is rebuilt every time the server
   * pushes, so holding the object would leave the card showing a bucket that no longer exists.
   */
  index: number;
  /** Pointer position inside the chart box, in pixels. */
  x: number;
  y: number;
}

/** Vertical guide marking the hovered slice; the same mark on all three charts. */
function HoverGuide({ x, width }: { x: number; width: number }) {
  return <rect x={x} y={0} width={Math.max(width, 0.8)} height={BOX_H} fill="#C7D2FE" opacity={0.12} />;
}

/** Stacked bars per NPC type — one column per time slice. */
function StackedByType({
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
          column.segments.map((segment) => (
            <rect
              key={`${column.index}-${segment.key}`}
              x={i * barWidth}
              y={BOX_H - (segment.to / top) * BOX_H}
              width={Math.max(barWidth - 0.3, 0.4)}
              height={((segment.to - segment.from) / top) * BOX_H}
              fill={npcColor(segment.key)}
            />
          )),
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

/** Multi-line chart over the counter series, with per-series toggles. */
function Counters({ buckets, slots }: { buckets: SimMetricBucket[]; slots: number | null }) {
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

/**
 * Charts of the arena's history. Memoized: the buckets change once a second while the arena pushes
 * frames twenty times a second, and re-stacking the series on every frame would be wasted work.
 */
export const MetricsPanel = memo(function MetricsPanel({ buckets, bucketGameDays }: Props) {
  const t = useT();
  const [windowDays, setWindowDays] = useState(DEFAULT_WINDOW_GAME_DAYS);
  const slots = slotsFor(bucketGameDays, windowDays);
  // the rest of the history stays in `buckets`, off-screen to the left, and comes back with "all"
  const visible = useMemo(() => windowOf(buckets, slots), [buckets, slots]);

  const selector = (
    <div className="flex shrink-0 items-center gap-1">
      <span className="text-[10px] text-[#4A5568]">{t("sim.metrics.window")}</span>
      {WINDOW_OPTIONS.map((option) => (
        <button
          key={option.days}
          type="button"
          onClick={() => setWindowDays(option.days)}
          className={
            "rounded px-1.5 py-0.5 text-[10px] " +
            (windowDays === option.days ? "bg-[#3C50E0] text-white" : "bg-[#2E3A4E] text-[#8A99AF] hover:text-white")
          }
        >
          {t(option.labelKey)}
        </button>
      ))}
      <span className="ml-auto text-[10px] text-[#4A5568]">{t("sim.metrics.slicesInMemory", buckets.length)}</span>
    </div>
  );

  if (buckets.length === 0) {
    return <p className="text-[11px] text-[#4A5568]">{t("sim.metrics.noData")}</p>;
  }
  const hint = t("sim.metrics.sliceHint", bucketGameDays);

  return (
    <div className="flex h-full flex-col gap-2 overflow-auto">
      {selector}
      <StackedByType
        title={t("sim.metrics.aliveByType")}
        hint={hint}
        unit={t("sim.metrics.unitAlive")}
        buckets={visible}
        pick={pickAlive}
        slots={slots}
      />
      <StackedByType
        title={t("sim.metrics.deathsByType")}
        hint={hint}
        unit={t("sim.metrics.unitDeaths")}
        buckets={visible}
        pick={pickDeaths}
        slots={slots}
      />
      <Counters buckets={visible} slots={slots} />
      <p className="text-[10px] leading-relaxed text-[#4A5568]">{t("sim.metrics.footnote")}</p>
    </div>
  );
});
