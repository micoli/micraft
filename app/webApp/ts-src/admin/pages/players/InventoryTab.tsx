import { useEffect, useState } from "react";
import { PlayerFile } from "../../apiTypes";
import { useT } from "../../i18n";
import { getApiAdminItems, getApiAdminBlocks } from "../../../generated/api/requests";
import { GiveItemForm } from "./GiveItemForm";

export function InventoryTab({
  file,
  onGive,
}: {
  file: PlayerFile;
  onGive: (name: string, count: number) => Promise<void>;
}) {
  const t = useT();
  const entries = Object.entries(file.state.inventory).sort(([a], [b]) => a.localeCompare(b));

  const [giveNames, setGiveNames] = useState<string[]>([]);

  useEffect(() => {
    Promise.all([
      getApiAdminItems({ throwOnError: true }).then((r) => Object.keys(r.data)),
      getApiAdminBlocks({ throwOnError: true }).then((r) => r.data.map((b) => b.name)),
    ]).then(([items, blocks]) => setGiveNames([...items, ...blocks]));
  }, []);

  return (
    <div className="p-5 space-y-5">
      <GiveItemForm
        names={giveNames}
        placeholder={t("players.giveInventoryNamePlaceholder")}
        datalistId="inventory-give-names"
        onGive={onGive}
      />

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
