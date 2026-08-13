import { npcColor, SimMetricBucket } from "../types";
import type { TypedPick } from "./metrics";
import { useMemo } from "react";
import { defineChart } from "@tanstack/charts";
import { pie, polar, radialArc } from "@tanstack/charts/polar";
import { Chart } from "@tanstack/charts/react";

interface PieSlice {
  key: string;
  value: number;
}

/** Donut-style pie showing totals by type over the visible window. */
export function PieChart({ keys, buckets, pick }: { keys: string[]; buckets: SimMetricBucket[]; pick: TypedPick }) {
  const totals = useMemo(() => {
    const map: Record<string, number> = {};
    for (const bucket of buckets) {
      const data = pick(bucket);
      for (const key of keys) map[key] = (map[key] ?? 0) + (data[key] ?? 0);
    }
    return map;
  }, [keys, buckets, pick]);

  const slices: PieSlice[] = keys.map((key) => ({ key, value: totals[key] ?? 0 })).filter((entry) => entry.value > 0);
  const grandTotal = slices.reduce((sum, entry) => sum + entry.value, 0);

  const definition = useMemo(() => {
    if (slices.length === 0) return null;
    const pieData = pie(slices, { value: "value" });
    return defineChart({
      marks: [
        polar({
          inset: 2,
          marks: [
            radialArc(pieData, {
              key: (slice) => slice.key,
              fill: (slice) => npcColor(slice.key),
              innerRadius: 16,
              outerRadius: 35,
            }),
          ],
        }),
      ],
      guides: false,
      x: null,
      y: null,
      keyboard: false,
    });
  }, [slices]);

  if (grandTotal === 0 || definition === null) return null;

  return (
    <div className="relative h-[90px] w-[80px] shrink-0 overflow-hidden rounded bg-[#0E1726]">
      <Chart definition={definition} height={90} width={80} ariaLabel="Totals by type" />
      <span className="pointer-events-none absolute inset-0 flex items-center justify-center text-[9px] text-[#8A99AF]">
        {grandTotal}
      </span>
    </div>
  );
}
