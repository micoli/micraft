import { useT } from "../i18n";
import type { SimulationInfo } from "./types";

interface Props {
  simulations: SimulationInfo[];
  attachedId: string | null;
  onAttach: (id: string) => void;
  onRefresh: () => void;
}

/**
 * Arenas running on the server, whoever started them. An arena outlives the tab that created it, so
 * this is how you get back to one — or watch the same one as a colleague.
 */
export function SimulationList({ simulations, attachedId, onAttach, onRefresh }: Props) {
  const t = useT();
  return (
    <div className="rounded-lg border border-[#2E3A4E] bg-[#1A222C] p-3">
      <div className="mb-2 flex items-center gap-2">
        <p className="flex-1 text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF]">
          {t("sim.list.title")}
        </p>
        <button
          type="button"
          onClick={onRefresh}
          className="rounded bg-[#2E3A4E] px-2 py-0.5 text-[10px] text-[#C7D2FE] hover:bg-[#3C50E0]/60"
        >
          {t("common.refresh")}
        </button>
      </div>

      {simulations.length === 0 && <p className="text-[11px] text-[#4A5568]">{t("sim.list.empty")}</p>}

      {simulations.map((simulation) => {
        const attached = simulation.id === attachedId;
        return (
          <div
            key={simulation.id}
            className={
              "mb-1.5 rounded border p-2 last:mb-0 " +
              (attached ? "border-[#3C50E0] bg-[#3C50E0]/10" : "border-[#2E3A4E] bg-[#0E1726]")
            }
          >
            <div className="flex items-start gap-2">
              <div className="min-w-0 flex-1">
                <p className="truncate text-[11px] font-medium text-white" title={simulation.name}>
                  {simulation.name}
                </p>
                <p className="text-[10px] text-[#8A99AF]">
                  {t(
                    "sim.list.stats",
                    `${simulation.npcCount}${simulation.populationCap > 0 ? `/${simulation.populationCap}` : ""}`,
                    simulation.gameDay.toFixed(1),
                    simulation.paused
                      ? t("sim.timeline.paused")
                      : t("sim.timeline.tps", Math.round(simulation.realTps)),
                  )}
                </p>
                <p className="text-[10px] text-[#4A5568]">
                  {t(
                    "sim.list.geometry",
                    simulation.halfSize * 2,
                    simulation.halfSize * 2,
                    simulation.viewers,
                    simulation.id.slice(0, 8),
                  )}
                </p>
              </div>
              {attached ? (
                <span className="shrink-0 rounded bg-[#3C50E0] px-2 py-0.5 text-[10px] text-white">
                  {t("sim.list.viewing")}
                </span>
              ) : (
                <button
                  type="button"
                  onClick={() => onAttach(simulation.id)}
                  className="shrink-0 rounded bg-[#2E3A4E] px-2 py-0.5 text-[10px] text-[#C7D2FE] hover:bg-[#3C50E0]/60"
                >
                  {t("sim.list.connect")}
                </button>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
