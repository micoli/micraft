import { useT } from "../i18n";
import { useEffect, useState } from "react";
import { getApiAdminBlocks } from "../../generated/api/requests";
import { BlockInfoDto } from "../apiTypes";
import { SidebarList } from "./SidebarList";
import { PropRow } from "../PropRow";
import { EmptyDetail } from "../../primitives/EmptyDetail";

type BlocksTabProps = {
  selectedKey: string | null;
  onSelectKey: (key: string | null) => void;
};

export function BlocksTab({ selectedKey, onSelectKey }: BlocksTabProps) {
  const t = useT();
  const [blocks, setBlocks] = useState<BlockInfoDto[]>([]);
  const [filter, setFilter] = useState("");

  useEffect(() => {
    getApiAdminBlocks({ throwOnError: true })
      .then((r) => setBlocks(r.data.filter((x) => x.name !== "AIR")))
      .catch(console.error);
  }, []);

  const filtered = blocks
    .filter((b) => b.name.toLowerCase().includes(filter.toLowerCase()))
    .sort((a, b) => a.name.localeCompare(b.name));

  const selected = blocks.find((b) => b.name === selectedKey) ?? null;

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
          onSelect={(b) => onSelectKey(b.name)}
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
