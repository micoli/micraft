import { useT } from "../i18n";
import { useEffect, useState } from "react";
import { api, BlockInfoDto } from "../api";
import { SidebarList } from "./SidebarList";
import { PropRow } from "../PropRow";
import { EmptyDetail } from "./EmptyDetail";

export function BlocksTab() {
  const t = useT();
  const [blocks, setBlocks] = useState<BlockInfoDto[]>([]);
  const [selected, setSelected] = useState<BlockInfoDto | null>(null);
  const [filter, setFilter] = useState("");

  useEffect(() => {
    api.blocks
      .list()
      .then((b) => setBlocks(b.filter((x) => x.name !== "AIR")))
      .catch(console.error);
  }, []);

  const filtered = blocks
    .filter((b) => b.name.toLowerCase().includes(filter.toLowerCase()))
    .sort((a, b) => a.name.localeCompare(b.name));

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
          getKey={(b) => b.name}
          getLabel={(b) => b.name.replace(/_/g, " ")}
          onSelect={setSelected}
        />
      </aside>
      <div className="flex-1 overflow-auto p-6">
        {selected ? (
          <div className="max-w-sm">
            <div className="w-12 h-12 rounded mb-4" style={{ background: `rgb(${selected.minimapColor.join(",")})` }} />
            <h2 className="text-white font-semibold text-base mb-4">{selected.name.replace(/_/g, " ")}</h2>
            <PropRow label={t("administration.hardness")} value={selected.hardness === -1 ? "∞" : selected.hardness} />
            <PropRow label={t("administration.solid")} value={t(selected.solid ? "common.yes" : "common.no")} />
            <PropRow
              label={t("administration.transparent")}
              value={t(selected.transparent ? "common.yes" : "common.no")}
            />
            <PropRow label={t("administration.liquid")} value={t(selected.liquid ? "common.yes" : "common.no")} />
            {selected.modelElement && <PropRow label={t("administration.model")} value={selected.modelElement} />}
          </div>
        ) : (
          <EmptyDetail message={t("administration.selectBlock")} />
        )}
      </div>
    </div>
  );
}
