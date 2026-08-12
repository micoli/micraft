import { npcColor, SimMetricBucket } from "../types";
import type { TypedPick } from "./metrics";
import { useMemo } from "react";

const PIE_R = 35;
const PIE_CX = 40;
const PIE_CY = 45;

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

  const grandTotal = Object.values(totals).reduce((s, v) => s + v, 0);
  if (grandTotal === 0) return null;

  const slices = keys
    .map((key) => ({ key, value: totals[key] ?? 0 }))
    .filter((e) => e.value > 0)
    .reduce<{ key: string; value: number; start: number; end: number; sweep: number }[]>((acc, e) => {
      const prevAngle = acc.length > 0 ? acc[acc.length - 1].end : -Math.PI / 2;
      const sweep = (e.value / grandTotal) * 2 * Math.PI;
      acc.push({ key: e.key, value: e.value, start: prevAngle, end: prevAngle + sweep, sweep });
      return acc;
    }, []);

  return (
    <svg viewBox="0 0 80 90" className="h-[90px] w-[80px] shrink-0 rounded bg-[#0E1726]">
      {slices.map(({ key, start, end, sweep }) => {
        if (sweep >= 2 * Math.PI - 0.001) {
          return <circle key={key} cx={PIE_CX} cy={PIE_CY} r={PIE_R} fill={npcColor(key)} />;
        }
        const x1 = PIE_CX + PIE_R * Math.cos(start);
        const y1 = PIE_CY + PIE_R * Math.sin(start);
        const x2 = PIE_CX + PIE_R * Math.cos(end);
        const y2 = PIE_CY + PIE_R * Math.sin(end);
        const large = sweep > Math.PI ? 1 : 0;
        const d = `M ${PIE_CX} ${PIE_CY} L ${x1.toFixed(2)} ${y1.toFixed(2)} A ${PIE_R} ${PIE_R} 0 ${large} 1 ${x2.toFixed(2)} ${y2.toFixed(2)} Z`;
        return <path key={key} d={d} fill={npcColor(key)} />;
      })}
      <circle cx={PIE_CX} cy={PIE_CY} r={PIE_R * 0.45} fill="#0E1726" />
      <text x={PIE_CX} y={PIE_CY + 3} textAnchor="middle" fill="#8A99AF" fontSize={9}>
        {grandTotal}
      </text>
    </svg>
  );
}
