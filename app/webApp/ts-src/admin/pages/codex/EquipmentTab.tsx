import { useEffect, useRef, useState } from "react";
import { useT } from "../../i18n";
import { getApiTools, getApiWeapons } from "../../../generated/api/requests";
import type {
  OrgMicoliMicraftGameEquipmentToolDefinition,
  OrgMicoliMicraftGameEquipmentWeaponDefinition,
} from "../../../generated/api/requests";
import { SidebarList } from "../SidebarList";
import { PropRow } from "../../PropRow";
import { EmptyDetail } from "../../../primitives/EmptyDetail";
import { BbmodelAnimationViewer } from "../../components/BbmodelAnimationViewer";

type EquipmentTabProps = {
  selectedKey: string | null;
  onSelectKey: (key: string | null) => void;
};

type EquipmentKind = "weapon" | "tool";

type EquipmentEntry =
  | { name: string; kind: "weapon"; def: OrgMicoliMicraftGameEquipmentWeaponDefinition }
  | { name: string; kind: "tool"; def: OrgMicoliMicraftGameEquipmentToolDefinition };

function bbmodelUrl(kind: EquipmentKind, name: string) {
  return `/api/models/${kind === "weapon" ? "weapons" : "tools"}/${name}/${name}.bbmodel`;
}

export function EquipmentTab({ selectedKey, onSelectKey }: EquipmentTabProps) {
  const t = useT();
  const [entries, setEntries] = useState<EquipmentEntry[]>([]);
  const [filter, setFilter] = useState("");
  const [bbmodel, setBbmodel] = useState<BbModel | null>(null);

  useEffect(() => {
    Promise.all([getApiWeapons({ throwOnError: true }), getApiTools({ throwOnError: true })])
      .then(([weapons, tools]) => {
        const weaponEntries: EquipmentEntry[] = Object.entries(weapons.data).map(([name, def]) => ({
          name,
          kind: "weapon" as const,
          def,
        }));
        const toolEntries: EquipmentEntry[] = Object.entries(tools.data).map(([name, def]) => ({
          name,
          kind: "tool" as const,
          def,
        }));
        setEntries([...weaponEntries, ...toolEntries]);
      })
      .catch(console.error);
  }, []);

  const filtered = entries
    .filter((e) => e.name.toLowerCase().includes(filter.toLowerCase()))
    .sort((a, b) => a.name.localeCompare(b.name));

  const selected = selectedKey ? (entries.find((e) => e.name === selectedKey) ?? null) : null;

  const hasAutoSelected = useRef(false);
  useEffect(() => {
    if (hasAutoSelected.current || selectedKey || filtered.length === 0) return;
    hasAutoSelected.current = true;
    onSelectKey(filtered[0].name);
  }, [filtered, selectedKey, onSelectKey]);

  useEffect(() => {
    setBbmodel(null);
    if (!selected) return;
    fetch(bbmodelUrl(selected.kind, selected.name))
      .then((r) => r.json() as Promise<BbModel>)
      .then(setBbmodel)
      .catch(console.error);
  }, [selected]);

  return (
    <div className="flex h-full overflow-hidden">
      <aside className="w-56 shrink-0 flex flex-col border-r border-[#2E3A4E] overflow-hidden">
        <div className="px-3 py-2 border-b border-[#2E3A4E]">
          <input
            className="w-full bg-[#1A222C] border border-[#2E3A4E] rounded px-2 py-1 text-xs text-white placeholder-[#8A99AF] outline-none"
            placeholder={t("administration.filter")}
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          />
        </div>
        <SidebarList
          items={filtered}
          selected={selected}
          getKey={(e) => e.name}
          getLabel={(e) =>
            `${e.name.replace(/_/g, " ")} (${t(`administration.equipmentKind${e.kind === "weapon" ? "Weapon" : "Tool"}`)})`
          }
          onSelect={(e) => onSelectKey(e.name)}
        />
      </aside>
      <div className="flex-1 overflow-auto p-6">
        {selected ? (
          <div className="flex gap-8">
            <div className="flex flex-col items-center gap-2">
              <BbmodelAnimationViewer bbmodel={bbmodel} animFullName="" standaloneItem width={240} height={240} />
            </div>
            <div className="max-w-sm flex-1">
              <h2 className="text-white font-semibold text-base mb-4">{selected.name.replace(/_/g, " ")}</h2>
              <PropRow
                label={t("administration.equipmentKindLabel")}
                value={t(
                  selected.kind === "weapon"
                    ? "administration.equipmentKindWeapon"
                    : "administration.equipmentKindTool",
                )}
              />
              <PropRow label={t("administration.equipmentCategory")} value={selected.def.category} />
              {selected.kind === "tool" && (
                <PropRow label={t("administration.equipmentBreakSpeed")} value={selected.def.breakSpeedMultiplier} />
              )}
              {selected.kind === "weapon" &&
                (() => {
                  const bonus = selected.def.statBonus ?? {};
                  return (
                    <>
                      <PropRow label="STR" value={bonus.str ?? 0} />
                      <PropRow label="DEX" value={bonus.dex ?? 0} />
                      <PropRow label="INT" value={bonus.intel ?? 0} />
                      <PropRow label="WIS" value={bonus.wis ?? 0} />
                      <PropRow label="CON" value={bonus.con ?? 0} />
                      <PropRow label="CHA" value={bonus.cha ?? 0} />
                      <PropRow label="AC" value={bonus.acBonus ?? 0} />
                    </>
                  );
                })()}
            </div>
          </div>
        ) : (
          <EmptyDetail message={t("administration.selectItem")} />
        )}
      </div>
    </div>
  );
}
