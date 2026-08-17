import { CssBlockCube, useColoredBlockPreview } from "../../shared/BlockPreview";

export function SlotBlockIcon({
  ordinal,
  colorHex,
  defsReady,
  getPreview,
  fallbackBg,
  size = 40,
}: {
  ordinal: number;
  colorHex: string | null;
  defsReady: boolean;
  getPreview: (o: number) => string | null;
  fallbackBg: string;
  size?: number;
}) {
  const coloredUrl = useColoredBlockPreview(colorHex != null ? ordinal : null, colorHex);
  if (colorHex != null) {
    return coloredUrl ? (
      <img src={coloredUrl} width={size} height={size} style={{ imageRendering: "pixelated", display: "block" }} />
    ) : (
      <CssBlockCube ordinal={ordinal} size={26} colorHex={colorHex} />
    );
  }
  const cachedUrl = getPreview(ordinal);
  if (cachedUrl) {
    return <img src={cachedUrl} width={size} height={size} style={{ imageRendering: "pixelated", display: "block" }} />;
  }
  if (defsReady) return <CssBlockCube ordinal={ordinal} size={26} colorHex={null} />;
  return (
    <div
      className="w-[26px] h-[26px] rounded-sm"
      style={{
        background: fallbackBg,
        boxShadow: "inset -3px -3px 0 rgba(0,0,0,0.3),inset 3px 3px 0 rgba(255,255,255,0.15)",
      }}
    />
  );
}
