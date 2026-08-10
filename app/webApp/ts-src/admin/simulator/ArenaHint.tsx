import { useT } from "../i18n";

/** Bottom-left hint: arena size, scale, and what the mouse does. */
export function ArenaHint({ halfSize, pxPerBlock }: { halfSize: number; pxPerBlock: number }) {
  const t = useT();
  return (
    <div className="absolute bottom-2 left-2 rounded bg-[#1A222C]/80 px-2 py-1 text-[10px] text-[#8A99AF]">
      {t("sim.hint", halfSize * 2, halfSize * 2, pxPerBlock.toFixed(1))}
    </div>
  );
}
