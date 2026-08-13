import { SimMetricBucket } from "../types";
import { dayLabel, maxTotal, niceMax, slotIndexOf, stackedColumns, stackKeys, TypedPick } from "./metrics";
import { useMemo } from "react";
import { useT } from "../../../i18n";
import { npcColor } from "../types";
import { ChartFrame } from "./ChartFrame";
import { PieChart } from "./PieChart";
import { LegendDot } from "./LegendDot";
import { barY, defineChart, stack } from "@tanstack/charts";
import { scaleBand } from "@tanstack/charts/scales/band";
import { scaleLinear } from "@tanstack/charts/scales/linear";
import { tooltip } from "@tanstack/charts/tooltip";
import { Chart } from "@tanstack/charts/react";

interface StackRow {
  slot: number;
  key: string;
  value: number;
}

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
  const slotCount = slots ?? Math.max(1, columns.length);
  const slotIndex = useMemo(() => slotIndexOf(buckets, slots), [buckets, slots]);
  // maps each slot back to the game day it covers, so the axis and tooltip can read a day rather
  // than a raw column index — the slot grid is a rendering device, not something a reader should see
  const dayBySlot = useMemo(() => {
    const map = new Map<number, number>();
    columns.forEach((column, i) => map.set(slotIndex[i], column.startGameDay));
    return map;
  }, [columns, slotIndex]);
  const latest = columns[columns.length - 1];

  const rows = useMemo<StackRow[]>(
    () =>
      columns.flatMap((column, i) =>
        column.segments.map((segment) => ({
          slot: slotIndex[i],
          key: segment.key,
          value: segment.to - segment.from,
        })),
      ),
    [columns, slotIndex],
  );

  const definition = useMemo(() => {
    const domain = Array.from({ length: slotCount }, (_, i) => i);
    return defineChart({
      marks: [
        barY(rows, {
          x: "slot",
          y: "value",
          z: "key",
          color: "key",
          layout: stack({ order: keys }),
          inset: 0.5,
        }),
      ],
      x: {
        scale: () => scaleBand<number>().domain(domain).padding(0.1),
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
      color: { domain: keys, range: keys.map((key) => npcColor(key)) },
      focus: "group-x",
      tooltip,
    });
  }, [rows, keys, top, slotCount, dayBySlot, dayAbbrev]);

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
      {columns.length === 0 ? (
        <div className="flex h-[90px] items-center justify-center text-[10px] text-[#4A5568]">
          {t("sim.metrics.nothingYet")}
        </div>
      ) : (
        <Chart definition={definition} height={90} ariaLabel={title} />
      )}
      <div className="mt-0.5 flex justify-between text-[9px] text-[#4A5568]">
        <span>{columns.length > 0 ? dayLabel(columns[0].startGameDay, dayAbbrev) : "—"}</span>
        <span>
          {t("sim.metrics.max", top)} {unit}
        </span>
        <span>{latest ? dayLabel(latest.startGameDay, dayAbbrev) : "—"}</span>
      </div>
    </ChartFrame>
  );
}
