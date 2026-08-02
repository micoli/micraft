import { useT } from "../i18n";

interface Props {
  onZoom: (factor: number) => void;
  onFitAll: () => void;
}

const ZOOM_STEP = 1.4;

const BUTTON =
  "flex h-7 w-7 items-center justify-center rounded bg-[#1A222C]/90 text-[13px] text-[#C7D2FE] " +
  "border border-[#2E3A4E] hover:bg-[#3C50E0]/70 hover:text-white";

/** Zoom controls, overlaid on the arena itself rather than parked in a side panel. */
export function ArenaControls({ onZoom, onFitAll }: Props) {
  const t = useT();
  return (
    <div className="absolute right-2 top-2 z-10 flex flex-col items-end gap-1.5">
      <div className="flex flex-col gap-1">
        <button type="button" title={t("sim.controls.zoomIn")} onClick={() => onZoom(ZOOM_STEP)} className={BUTTON}>
          +
        </button>
        <button
          type="button"
          title={t("sim.controls.zoomOut")}
          onClick={() => onZoom(1 / ZOOM_STEP)}
          className={BUTTON}
        >
          −
        </button>
        <button type="button" title={t("sim.controls.fitAll")} onClick={onFitAll} className={BUTTON}>
          {/* four inward corners: the usual "fit to view" mark */}
          <svg viewBox="0 0 16 16" className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth={1.6}>
            <path d="M6 2H2v4M10 2h4v4M6 14H2v-4M10 14h4v-4" strokeLinecap="round" />
          </svg>
        </button>
      </div>
    </div>
  );
}
