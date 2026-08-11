import { CssBlockCube, useColoredBlockPreview } from "./BlockPreview";

export function ColoredIcon({ ordinal, colorHex, size }: { ordinal: number; colorHex: string; size: number }) {
  const url = useColoredBlockPreview(ordinal, colorHex);
  if (url)
    return (
      <img src={url} width={size + 8} height={size + 8} style={{ imageRendering: "pixelated", display: "block" }} />
    );
  return <CssBlockCube ordinal={ordinal} size={size - 4} colorHex={colorHex} />;
}
