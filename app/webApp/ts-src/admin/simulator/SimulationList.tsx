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
  return (
    <div className="rounded-lg border border-[#2E3A4E] bg-[#1A222C] p-3">
      <div className="mb-2 flex items-center gap-2">
        <p className="flex-1 text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF]">
          Simulations en cours
        </p>
        <button
          type="button"
          onClick={onRefresh}
          className="rounded bg-[#2E3A4E] px-2 py-0.5 text-[10px] text-[#C7D2FE] hover:bg-[#3C50E0]/60"
        >
          Rafraîchir
        </button>
      </div>

      {simulations.length === 0 && <p className="text-[11px] text-[#4A5568]">Aucune — « Démarrer » en crée une.</p>}

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
                  {simulation.npcCount}
                  {simulation.populationCap > 0 ? `/${simulation.populationCap}` : ""} NPC · jour{" "}
                  {simulation.gameDay.toFixed(1)} ·{" "}
                  {simulation.paused ? "en pause" : `${Math.round(simulation.realTps)} t/s`}
                </p>
                <p className="text-[10px] text-[#4A5568]">
                  {simulation.halfSize * 2}×{simulation.halfSize * 2} blocs · {simulation.viewers} spectateur(s) ·{" "}
                  {simulation.id.slice(0, 8)}
                </p>
              </div>
              {attached ? (
                <span className="shrink-0 rounded bg-[#3C50E0] px-2 py-0.5 text-[10px] text-white">suivie</span>
              ) : (
                <button
                  type="button"
                  onClick={() => onAttach(simulation.id)}
                  className="shrink-0 rounded bg-[#2E3A4E] px-2 py-0.5 text-[10px] text-[#C7D2FE] hover:bg-[#3C50E0]/60"
                >
                  Se connecter
                </button>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
