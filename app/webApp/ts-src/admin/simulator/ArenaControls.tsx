import type { RendererKind } from "./arenaView";

interface Props {
  renderer: RendererKind;
  onRenderer: (kind: RendererKind) => void;
  onZoom: (factor: number) => void;
  onFitAll: () => void;
}

const ZOOM_STEP = 1.4;

const BUTTON =
  "flex h-7 w-7 items-center justify-center rounded bg-[#1A222C]/90 text-[13px] text-[#C7D2FE] " +
  "border border-[#2E3A4E] hover:bg-[#3C50E0]/70 hover:text-white";

const ICON = "h-3.5 w-3.5";

/**
 * The two renderers, told apart by what they actually are: a vector path with its handles versus a
 * grid of pixels. Text labels cost width on top of the map they overlay.
 */
const RENDERERS: { kind: RendererKind; title: string; icon: React.ReactNode }[] = [
  {
    kind: "svg",
    title: "SVG : un nœud DOM par NPC, inspectable",
    icon: (
      <svg viewBox="0 0 16 16" className={ICON} fill="none" stroke="currentColor" strokeWidth={1.4}>
        {/* a curve with its control handles: the vector view */}
        <path d="M3 12c2-6 8-6 10 0" strokeLinecap="round" />
        <rect x="1.5" y="10.5" width="3" height="3" rx="0.5" fill="currentColor" stroke="none" />
        <rect x="11.5" y="10.5" width="3" height="3" rx="0.5" fill="currentColor" stroke="none" />
      </svg>
    ),
  },
  {
    kind: "canvas",
    title: "Canvas : un seul nœud, tient les arènes très peuplées",
    icon: (
      <svg viewBox="0 0 16 16" className={ICON} fill="currentColor" stroke="none">
        {/* a pixel grid: the raster view */}
        {[2, 6.5, 11].map((x) =>
          [2, 6.5, 11].map((y) => <rect key={`${x}-${y}`} x={x} y={y} width={3} height={3} rx={0.4} />),
        )}
      </svg>
    ),
  },
];

/** Zoom and renderer choice, overlaid on the arena itself rather than parked in a side panel. */
export function ArenaControls({ renderer, onRenderer, onZoom, onFitAll }: Props) {
  return (
    <div className="absolute right-2 top-2 z-10 flex flex-col items-end gap-1.5">
      <div className="flex flex-col gap-1">
        <button type="button" title="Zoomer" onClick={() => onZoom(ZOOM_STEP)} className={BUTTON}>
          +
        </button>
        <button type="button" title="Dézoomer" onClick={() => onZoom(1 / ZOOM_STEP)} className={BUTTON}>
          −
        </button>
        <button type="button" title="Voir toute l'arène" onClick={onFitAll} className={BUTTON}>
          {/* four inward corners: the usual "fit to view" mark */}
          <svg viewBox="0 0 16 16" className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth={1.6}>
            <path d="M6 2H2v4M10 2h4v4M6 14H2v-4M10 14h4v-4" strokeLinecap="round" />
          </svg>
        </button>
      </div>

      {/* stacked, so the whole overlay is one narrow column with the zoom buttons */}
      <div className="flex flex-col overflow-hidden rounded border border-[#2E3A4E]">
        {RENDERERS.map(({ kind, title, icon }) => (
          <button
            key={kind}
            type="button"
            onClick={() => onRenderer(kind)}
            // the icons carry no words, so the explanation lives in the tooltip
            title={title}
            aria-label={title}
            aria-pressed={renderer === kind}
            className={
              "flex h-7 w-7 items-center justify-center transition-colors " +
              (renderer === kind ? "bg-[#3C50E0] text-white" : "bg-[#1A222C]/90 text-[#8A99AF] hover:text-white")
            }
          >
            {icon}
          </button>
        ))}
      </div>
    </div>
  );
}
