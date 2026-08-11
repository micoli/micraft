import { plainColorHex } from "../blocks/blockDefs";
import { Block3DPreview } from "../shared/Block3DPreview";
import type { BlockEntry, ItemEntry } from "./CodexModal";

export function ItemDetail({ item, blocks, defsReady }: { item: ItemEntry; blocks: BlockEntry[]; defsReady: boolean }) {
  const linkedBlock = item.placesBlock ? blocks.find((b) => b.name === item.placesBlock) : null;
  const colorHex = plainColorHex(item.plainColor);

  const row = (label: string, value: string) => (
    <div
      key={label}
      style={{ display: "flex", justifyContent: "space-between", padding: "4px 0", borderBottom: "1px solid #2a2a2a" }}
    >
      <span style={{ color: "#888", fontSize: 12 }}>{label}</span>
      <span style={{ color: "#ddd", fontSize: 12 }}>{value}</span>
    </div>
  );

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12, padding: 8 }}>
      <div style={{ display: "flex", justifyContent: "center" }}>
        {defsReady && linkedBlock ? (
          <Block3DPreview ordinal={linkedBlock.ordinal} colorHex={colorHex} />
        ) : (
          <div
            style={{
              width: 160,
              height: 160,
              background: "#1a1a1a",
              borderRadius: 6,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              fontSize: 60,
            }}
          >
            ✦
          </div>
        )}
      </div>
      <div style={{ fontSize: 15, fontWeight: "bold", color: "#eee", textAlign: "center" }}>
        {item.name.replace(/_/g, " ")}
      </div>
      <div>
        {row("Posable", item.buildable ? "oui" : "non")}
        {row("Place le bloc", item.placesBlock ? item.placesBlock.replace(/_/g, " ") : "—")}
        {item.plainColor ? row("Couleur", `${item.plainColor} (#${colorHex ?? "??????"})`) : null}
      </div>
    </div>
  );
}
