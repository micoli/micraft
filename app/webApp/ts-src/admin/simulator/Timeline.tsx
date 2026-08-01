import type { SimStats } from "./types";

const PRESETS = [
  { label: "⏸", tps: 0, title: "pause" },
  { label: "×1", tps: 20, title: "temps réel (20 ticks/s)" },
  { label: "×5", tps: 100, title: "5 fois plus vite" },
  { label: "×20", tps: 400, title: "20 fois plus vite" },
  { label: "×100", tps: 2000, title: "100 fois plus vite" },
  { label: "max", tps: 5000, title: "au plus vite" },
];

interface Props {
  stats: SimStats;
  disabled: boolean;
  onSpeed: (tps: number) => void;
  onStep: (count: number) => void;
}

/** Speed presets, fine slider and manual stepping, plus what the simulation actually achieves. */
export function Timeline({ stats, disabled, onSpeed, onStep }: Props) {
  return (
    <div className="rounded-lg border border-[#2E3A4E] bg-[#1A222C] p-3">
      <div className="mb-2 flex items-center justify-between">
        <span className="text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF]">Timeline</span>
        <span className="text-[11px] text-[#8A99AF]">
          tick <span className="text-white">{stats.tick.toLocaleString("fr-FR")}</span> · jour{" "}
          <span className="text-white">{stats.gameDay.toFixed(2)}</span> ·{" "}
          <span className={stats.paused ? "text-[#FACC15]" : "text-emerald-400"}>
            {stats.paused ? "en pause" : `${stats.realTps.toFixed(0)} ticks/s réels`}
          </span>
        </span>
      </div>

      <div className="mb-2 flex flex-wrap gap-1.5">
        {PRESETS.map((preset) => (
          <button
            key={preset.label}
            type="button"
            title={preset.title}
            disabled={disabled}
            onClick={() => onSpeed(preset.tps)}
            className={
              "rounded px-2.5 py-1 text-[11px] font-medium transition-colors disabled:opacity-40 " +
              (stats.configuredTps === preset.tps
                ? "bg-[#3C50E0] text-white"
                : "bg-[#2E3A4E] text-[#C7D2FE] hover:bg-[#3C50E0]/60")
            }
          >
            {preset.label}
          </button>
        ))}
        <button
          type="button"
          disabled={disabled}
          onClick={() => onStep(1)}
          className="rounded bg-[#2E3A4E] px-2.5 py-1 text-[11px] text-[#C7D2FE] hover:bg-[#3C50E0]/60 disabled:opacity-40"
        >
          +1 tick
        </button>
        <button
          type="button"
          disabled={disabled}
          onClick={() => onStep(100)}
          className="rounded bg-[#2E3A4E] px-2.5 py-1 text-[11px] text-[#C7D2FE] hover:bg-[#3C50E0]/60 disabled:opacity-40"
        >
          +100 ticks
        </button>
      </div>

      <label className="flex items-center gap-2 text-[11px] text-[#8A99AF]">
        <span className="w-20 shrink-0">vitesse fine</span>
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
        <span className="w-24 shrink-0 text-right text-white">{stats.configuredTps} t/s</span>
      </label>
    </div>
  );
}
