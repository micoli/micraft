import { useI18n, type TranslationKey } from "../../i18n";
import type { SimStats } from "./types";

const PRESETS: { label?: string; labelKey?: TranslationKey; tps: number; titleKey: TranslationKey }[] = [
  { label: "⏸", tps: 0, titleKey: "sim.timeline.presetPause" },
  { label: "×1", tps: 20, titleKey: "sim.timeline.presetRealtime" },
  { label: "×5", tps: 100, titleKey: "sim.timeline.preset5x" },
  { label: "×20", tps: 400, titleKey: "sim.timeline.preset20x" },
  { label: "×100", tps: 2000, titleKey: "sim.timeline.preset100x" },
  { labelKey: "sim.timeline.presetMaxLabel", tps: 5000, titleKey: "sim.timeline.presetMax" },
];

interface Props {
  stats: SimStats;
  disabled: boolean;
  onSpeed: (tps: number) => void;
  onStep: (count: number) => void;
}

/** Speed presets, fine slider and manual stepping, plus what the simulation actually achieves. */
export function Timeline({ stats, disabled, onSpeed, onStep }: Props) {
  const { locale, t } = useI18n();
  return (
    <div className="rounded-lg border border-[#2E3A4E] bg-[#1A222C] p-3">
      <div className="mb-2 flex items-center justify-between">
        <span className="text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF]">
          {t("sim.timeline.title")}
        </span>
        <span className="text-[11px] text-[#8A99AF]">
          {t("sim.timeline.tick")} <span className="text-white">{stats.tick.toLocaleString(locale)}</span> ·{" "}
          {t("sim.timeline.day")} <span className="text-white">{stats.gameDay.toFixed(2)}</span> ·{" "}
          <span className={stats.paused ? "text-[#FACC15]" : "text-emerald-400"}>
            {stats.paused ? t("sim.timeline.paused") : t("sim.timeline.realTps", stats.realTps.toFixed(0))}
          </span>
        </span>
      </div>

      <div className="mb-2 flex flex-wrap gap-1.5">
        {PRESETS.map((preset) => (
          <button
            key={preset.tps}
            type="button"
            title={t(preset.titleKey)}
            disabled={disabled}
            onClick={() => onSpeed(preset.tps)}
            className={
              "rounded px-2.5 py-1 text-[11px] font-medium transition-colors disabled:opacity-40 " +
              (stats.configuredTps === preset.tps
                ? "bg-[#3C50E0] text-white"
                : "bg-[#2E3A4E] text-[#C7D2FE] hover:bg-[#3C50E0]/60")
            }
          >
            {preset.label ?? t(preset.labelKey!)}
          </button>
        ))}
        <button
          type="button"
          disabled={disabled}
          onClick={() => onStep(1)}
          className="rounded bg-[#2E3A4E] px-2.5 py-1 text-[11px] text-[#C7D2FE] hover:bg-[#3C50E0]/60 disabled:opacity-40"
        >
          {t("sim.timeline.stepOne")}
        </button>
        <button
          type="button"
          disabled={disabled}
          onClick={() => onStep(100)}
          className="rounded bg-[#2E3A4E] px-2.5 py-1 text-[11px] text-[#C7D2FE] hover:bg-[#3C50E0]/60 disabled:opacity-40"
        >
          {t("sim.timeline.stepHundred")}
        </button>
      </div>

      <label className="flex items-center gap-2 text-[11px] text-[#8A99AF]">
        <span className="w-20 shrink-0">{t("sim.timeline.fineSpeed")}</span>
        <input
          type="range"
          min={0}
          max={5000}
          step={20}
          value={stats.configuredTps}
          disabled={disabled}
          onChange={(e) => onSpeed(Number(e.target.value))}
          className="flex-1 accent-[#3C50E0]"
        />
        <span className="w-24 shrink-0 text-right text-white">{t("sim.timeline.tps", stats.configuredTps)}</span>
      </label>
    </div>
  );
}
