import { memo, useMemo, useState } from "react";
import {
  DEFAULT_WINDOW_GAME_DAYS,
  WINDOW_OPTIONS,
  pickAlive,
  pickDeaths,
  pickEvolutions,
  pickKillers,
  pickLevelUps,
  slotsFor,
  windowOf,
} from "./metrics";
import { useT } from "../../../i18n";
import { type SimMetricBucket, type SimulationConfig } from "../types";
import { StackedByType } from "./StackedByType";
import { Counters } from "./Counters";
import { ExportButton } from "./ExportButton";

export interface MetricsPanelProps {
  buckets: SimMetricBucket[];
  bucketGameDays: number;
  /** The attached arena's settings, exported alongside the series. Null before the first snapshot. */
  config: SimulationConfig | null;
}

/**
 * Charts of the arena's history. Memoized: the buckets change once a second while the arena pushes
 * frames twenty times a second, and re-stacking the series on every frame would be wasted work.
 */
export const MetricsPanel = memo(function MetricsPanel({ buckets, bucketGameDays, config }: MetricsPanelProps) {
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
      {/* next to the window selector, but deliberately not bound to it: the export is the whole
          history, whatever slice of it is on screen */}
      <ExportButton buckets={buckets} bucketGameDays={bucketGameDays} config={config} />
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
      <StackedByType
        title={t("sim.metrics.deathsByKillerType")}
        hint={hint}
        unit={t("sim.metrics.unitDeaths")}
        buckets={visible}
        pick={pickKillers}
        slots={slots}
      />
      <StackedByType
        title={t("sim.metrics.levelUpsByType")}
        hint={hint}
        unit={t("sim.metrics.unitLevelUps")}
        buckets={visible}
        pick={pickEvolutions}
        slots={slots}
      />
      <StackedByType
        title={t("sim.metrics.xpLevelUpsByType")}
        hint={hint}
        unit={t("sim.metrics.unitXpLevelUps")}
        buckets={visible}
        pick={pickLevelUps}
        slots={slots}
      />
      <Counters buckets={visible} slots={slots} />
      <p className="text-[10px] leading-relaxed text-[#4A5568]">{t("sim.metrics.footnote")}</p>
    </div>
  );
});
