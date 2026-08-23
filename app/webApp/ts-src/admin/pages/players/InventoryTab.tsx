import { PlayerFile } from "../../apiTypes";
import { useT } from "../../i18n";

export function InventoryTab({ file }: { file: PlayerFile }) {
  const t = useT();
  const entries = Object.entries(file.state.inventory).sort(([a], [b]) => a.localeCompare(b));

  return (
    <div className="p-5">
      {entries.length === 0 ? (
        <p className="text-xs text-[#4A5568]">{t("players.inventoryEmpty")}</p>
      ) : (
        <table className="w-full text-xs">
          <thead>
            <tr className="text-left text-[#8A99AF] border-b border-[#2E3A4E]">
              <th className="py-1.5 font-medium">{t("players.itemColumn")}</th>
              <th className="py-1.5 font-medium text-right">{t("players.countColumn")}</th>
            </tr>
          </thead>
          <tbody>
            {entries.map(([item, count]) => (
              <tr key={item} className="border-b border-[#2E3A4E] last:border-0">
                <td className="py-1.5 text-white font-mono">{item}</td>
                <td className="py-1.5 text-white text-right tabular-nums">{count}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
