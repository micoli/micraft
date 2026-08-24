import { useEffect, useMemo, useRef, useState } from "react";
import { useT } from "../../i18n";
import { LegendDot } from "../worldSimulator/metrics/LegendDot";
import { niceMax } from "../worldSimulator/metrics/metrics";
import { defineChart, lineY, ruleY } from "@tanstack/charts";
import { scalePoint } from "@tanstack/charts/scales/point";
import { scaleLinear } from "@tanstack/charts/scales/linear";
import { tooltip } from "@tanstack/charts/tooltip";
import { Chart } from "@tanstack/charts/react";

const HISTORY = 40;

// Fixed hue per known tick phase — never cycled, so a phase keeps its color across polls.
const PHASE_COLORS: Record<string, string> = {
  players: "#38BDF8",
  npc: "#FB923C",
  npcLifecycle: "#F472B6",
  vehicles: "#818CF8",
  worldItems: "#4ADE80",
  statusEffects: "#E879F9",
  regen: "#22D3EE",
  weather: "#FACC15",
  liquid: "#60A5FA",
  vegetation: "#34D399",
  plugins: "#78716C",
};
const FALLBACK_COLOR = "#94A3B8";
const BUDGET_COLOR = "#F59E0B";

interface TickSlice {
  slot: number;
  values: Record<string, number>;
}

interface TickRow {
  slot: number;
  phase: string;
  value: number;
}

/** Multi-line chart of per-phase tick duration over the last polls, styled like the world simulator's "Events per slice". */
export function TickChart({
  phases,
  budgetMs,
  chartHeight = 90,
}: {
  phases: { name: string; avgMs: number }[];
  budgetMs: number;
  chartHeight?: number;
}) {
  const t = useT();
  const [hidden, setHidden] = useState<ReadonlySet<string>>(new Set());
  const [history, setHistory] = useState<TickSlice[]>([]);
  const [showBudget, setShowBudget] = useState(true);
  const slotRef = useRef(0);

  useEffect(() => {
    const values: Record<string, number> = {};
    phases.forEach((p) => (values[p.name] = p.avgMs));
    setHistory((current) => [...current, { slot: slotRef.current++, values }].slice(-HISTORY));
  }, [phases]);

  const names = phases.map((p) => p.name);
  const visible = names.filter((n) => !hidden.has(n));
  const last = history[history.length - 1];

  const toggle = (name: string) =>
    setHidden((current) => {
      const next = new Set(current);
      if (next.has(name)) next.delete(name);
      else next.add(name);
      return next;
    });

  const rows = useMemo<TickRow[]>(
    () => visible.flatMap((name) => history.map((h) => ({ slot: h.slot, phase: name, value: h.values[name] ?? 0 }))),
    [visible, history],
  );

  const top = niceMax(Math.max(budgetMs, ...visible.flatMap((name) => history.map((h) => h.values[name] ?? 0)), 0));
  const domain = useMemo(() => history.map((h) => h.slot), [history]);

  const definition = useMemo(
    () =>
      defineChart({
        marks: [
          lineY(rows, { x: "slot", y: "value", z: "phase", color: "phase", strokeWidth: 1 }),
          ...(showBudget ? [ruleY([budgetMs], { stroke: BUDGET_COLOR, strokeDasharray: "4,3", strokeWidth: 1 })] : []),
        ],
        x: {
          scale: () => scalePoint<number>().domain(domain).padding(0.05),
          axis: { ticks: { format: () => "" } },
        },
        y: { scale: () => scaleLinear().domain([0, top]), grid: true },
        color: { domain: names, range: names.map((n) => PHASE_COLORS[n] ?? FALLBACK_COLOR) },
        focus: "group-x",
        tooltip,
      }),
    [rows, domain, top, names, budgetMs, showBudget],
  );

  const height = chartHeight * 4;

  return (
    <div>
      <div className="mb-1 flex items-center justify-end">
        <label className="flex items-center gap-1.5 text-[10px] text-[#8A99AF] whitespace-nowrap">
          <input
            type="checkbox"
            checked={showBudget}
            onChange={(e) => setShowBudget(e.target.checked)}
            className="accent-[#F59E0B]"
          />
          {t("status.showBudgetLine")}
        </label>
      </div>
      {history.length === 0 ? (
        <div className="flex items-center justify-center text-[10px] text-[#4A5568]" style={{ height }}>
          {t("status.noneCapitalised")}
        </div>
      ) : (
        <Chart definition={definition} height={height} ariaLabel={t("status.tickChart")} />
      )}
      <div className="mt-1.5 flex flex-wrap gap-x-3 gap-y-0.5">
        {names.map((name) => (
          <button
            key={name}
            type="button"
            onClick={() => toggle(name)}
            title={t(hidden.has(name) ? "status.show" : "status.hide")}
            className={"cursor-pointer" + (hidden.has(name) ? " opacity-30" : "")}
          >
            <LegendDot
              color={PHASE_COLORS[name] ?? FALLBACK_COLOR}
              label={name}
              value={last ? Math.round((last.values[name] ?? 0) * 100) / 100 : 0}
            />
          </button>
        ))}
      </div>
    </div>
  );
}
