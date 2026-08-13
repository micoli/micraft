import { getItemVisual } from "../lib/blockDefs";
import { CssBlockCube, useBlockDefsReady, useBlockPreviews } from "./BlockPreview";
import { ColoredIcon } from "./ColoredIcon";

interface Props {
  itemId: string;
  fallbackBg: string;
  size?: number;
}

export function ItemIcon({ itemId, fallbackBg, size = 26 }: Props) {
  const defsReady = useBlockDefsReady();
  const getPreview = useBlockPreviews();
  const { ordinal, colorHex } = getItemVisual(itemId);

  if (ordinal == null) {
    return (
      <div
        style={{
          width: size,
          height: size,
          background: fallbackBg,
          boxShadow: "inset -3px -3px 0 rgba(0,0,0,0.3),inset 3px 3px 0 rgba(255,255,255,0.15)",
          borderRadius: 2,
        }}
      />
    );
  }

  if (colorHex != null) {
    return <ColoredIcon ordinal={ordinal} colorHex={colorHex} size={size} />;
  }

  const url = getPreview(ordinal);
  if (url)
    return (
      <img src={url} width={size + 8} height={size + 8} style={{ imageRendering: "pixelated", display: "block" }} />
    );
  if (defsReady) return <CssBlockCube ordinal={ordinal} size={size - 4} colorHex={null} />;
  return (
    <div
      style={{
        width: size,
        height: size,
        background: fallbackBg,
        boxShadow: "inset -3px -3px 0 rgba(0,0,0,0.3),inset 3px 3px 0 rgba(255,255,255,0.15)",
        borderRadius: 2,
      }}
    />
  );
}
