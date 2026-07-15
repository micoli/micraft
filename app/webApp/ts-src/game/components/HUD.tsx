import { HudData } from "../types";
import { cn } from "../../primitives/cn";

export function HUD({ data, layoutStyle }: { data: HudData | null; layoutStyle?: React.CSSProperties }) {
  if (!data) return null;
  const { stance, biome, targetBlock } = data;

  const simple = [`Stance: ${stance}`, `Biome: ${biome ? biome : "?"}`, `Block: ${targetBlock ? targetBlock : "?"}`];
  return (
    <div
      className={cn(
        "bg-black/55 text-white font-mono text-[13px] leading-relaxed px-3 py-2 rounded-md pointer-events-none z-[999] whitespace-pre",
        !layoutStyle && "fixed top-3 right-3",
      )}
      style={layoutStyle}
    >
      {simple.join("\n")}
    </div>
  );
}
