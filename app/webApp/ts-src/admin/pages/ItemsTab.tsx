import { useT } from "../i18n";
import { useEffect, useState } from "react";
import { getApiAdminItems } from "../../generated/api/requests";
import { ItemDto } from "../apiTypes";
import { SidebarList } from "./SidebarList";
import { PropRow } from "../PropRow";
import { EmptyDetail } from "../../primitives/EmptyDetail";

type ItemsTabProps = {
  selectedKey: string | null;
  onSelectKey: (key: string | null) => void;
};

export function ItemsTab({ selectedKey, onSelectKey }: ItemsTabProps) {
  const t = useT();
  const [items, setItems] = useState<Record<string, ItemDto>>({});
  const [filter, setFilter] = useState("");

  useEffect(() => {
    getApiAdminItems({ throwOnError: true })
      .then((r) => setItems(r.data))
      .catch(console.error);
  }, []);

  const entries = Object.entries(items)
    .filter(([name]) => name.toLowerCase().includes(filter.toLowerCase()))
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([name, dto]) => ({ name, dto }));

  const selected = selectedKey && items[selectedKey] ? { name: selectedKey, dto: items[selectedKey] } : null;

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
          items={entries}
          selected={selected}
          getKey={(e) => e.name}
          getLabel={(e) => e.name.replace(/_/g, " ")}
          onSelect={(e) => onSelectKey(e.name)}
        />
      </aside>
      <div className="flex-1 overflow-auto p-6">
        {selected ? (
          <div className="max-w-sm">
            <div className="w-12 h-12 rounded mb-4 bg-[#6a5acd] flex items-center justify-center text-2xl">✦</div>
            <h2 className="text-white font-semibold text-base mb-4">{selected.name.replace(/_/g, " ")}</h2>
            <PropRow
              label={t("administration.buildable")}
              value={t(selected.dto.buildable ? "common.yes" : "common.no")}
            />
            {selected.dto.placesBlock && (
              <PropRow label={t("administration.placesBlock")} value={selected.dto.placesBlock} />
            )}
          </div>
        ) : (
          <EmptyDetail message={t("administration.selectItem")} />
        )}
      </div>
    </div>
  );
}
