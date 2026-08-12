import { type BlockInfoDto } from "../../api";
import { Block3DPreview } from "../../../game/shared/Block3DPreview";

export function BlockHoverTooltip({
  block,
  ordinal,
  above,
  previewsReady,
}: {
  block: BlockInfoDto;
  ordinal: number | null;
  above: boolean;
  previewsReady: boolean;
}) {
  return (
    <div
      className={`pointer-events-none absolute z-30 left-1/2 -translate-x-1/2 flex flex-col items-center gap-0.5 whitespace-nowrap rounded border border-[#2E3A4E] bg-[#0B1220] px-2 py-1 text-[10px] shadow-lg ${
        above ? "bottom-full mb-1" : "top-full mt-1"
      }`}
    >
      {ordinal !== null && previewsReady && <Block3DPreview ordinal={ordinal} size={72} />}
      <span className="font-semibold text-white">{block.name.replace(/_/g, " ")}</span>
      <span className="text-[#8A99AF]">
        {block.hardness < 0 ? "Unbreakable" : `Hardness ${block.hardness}`} · {block.solid ? "Solid" : "Non-solid"}
        {block.transparent ? " · Transparent" : ""}
        {block.liquid ? " · Liquid" : ""}
      </span>
    </div>
  );
}
