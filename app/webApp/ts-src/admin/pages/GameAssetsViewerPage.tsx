import { useState, useEffect } from "react";
import { getApiGameAssets } from "../../generated/api/requests";
import { ModelViewer } from "../components/ModelViewer";
import { useT, type TranslationKey } from "../i18n";

interface AssetEntry {
  pack: string;
  name: string;
  path: string;
  format: string;
}

type Tree = Record<string, Record<string, AssetEntry[]>>;

function buildTree(assets: AssetEntry[]): Tree {
  const tree: Tree = {};
  for (const a of assets) {
    const parts = a.path.split("/");
    const subfolder = parts.length > 3 ? parts.slice(1, -1).join("/") : a.pack;
    if (!tree[a.pack]) tree[a.pack] = {};
    if (!tree[a.pack][subfolder]) tree[a.pack][subfolder] = [];
    tree[a.pack][subfolder].push(a);
  }
  return tree;
}

const FORMAT_BADGE: Record<string, string> = {
  glb: "bg-emerald-500/20 text-emerald-400",
  gltf: "bg-blue-500/20 text-blue-400",
  fbx: "bg-orange-500/20 text-orange-400",
  bbmodel: "bg-purple-500/20 text-purple-400",
};

export function GameAssetsViewerPage() {
  const t = useT();
  const [assets, setAssets] = useState<AssetEntry[]>([]);
  const [errorKey, setErrorKey] = useState<TranslationKey | null>(null);
  const [selected, setSelected] = useState<AssetEntry | null>(null);
  const [expandedPacks, setExpandedPacks] = useState<Set<string>>(new Set());
  const [expandedFolders, setExpandedFolders] = useState<Set<string>>(new Set());

  useEffect(() => {
    getApiGameAssets({ throwOnError: true })
      .then((r) => r.data as unknown as AssetEntry[])
      .then((data) => {
        setAssets(data);
        if (data.length > 0) {
          const packs = [...new Set(data.map((a) => a.pack))];
          setExpandedPacks(new Set(packs.slice(0, 1)));
        }
      })
      .catch(() => setErrorKey("assets.failedToLoad"));
  }, []);

  const tree = buildTree(assets);

  const togglePack = (pack: string) => {
    setExpandedPacks((prev) => {
      const next = new Set(prev);
      if (next.has(pack)) next.delete(pack);
      else next.add(pack);
      return next;
    });
  };

  const toggleFolder = (key: string) => {
    setExpandedFolders((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  };

  return (
    <div className="flex h-full overflow-hidden -m-6">
      {/* SidebarComponent */}
      <aside className="w-72 shrink-0 flex flex-col border-r border-[#2E3A4E] overflow-hidden">
        <div className="px-4 py-3 border-b border-[#2E3A4E] text-xs font-semibold uppercase tracking-widest text-[#8A99AF]">
          {t("assets.count", assets.length)}
        </div>
        <div className="flex-1 overflow-y-auto py-2">
          {errorKey && <div className="px-4 py-2 text-red-400 text-xs">{t(errorKey)}</div>}
          {Object.entries(tree).map(([pack, folders]) => (
            <div key={pack}>
              <button
                onClick={() => togglePack(pack)}
                className="w-full flex items-center gap-2 px-3 py-1.5 text-left text-[11px] font-semibold text-[#8A99AF] hover:text-white hover:bg-[#2E3A4E] uppercase tracking-wide"
              >
                <span className="text-[9px]">{expandedPacks.has(pack) ? "▼" : "▶"}</span>
                <span className="truncate">{pack.replace(/_/g, " ")}</span>
              </button>
              {expandedPacks.has(pack) &&
                Object.entries(folders).map(([folder, items]) => {
                  const label = folder.split("/").pop() ?? folder;
                  const key = `${pack}/${folder}`;
                  return (
                    <div key={folder}>
                      <button
                        onClick={() => toggleFolder(key)}
                        className="w-full flex items-center gap-2 pl-6 pr-3 py-1 text-left text-[11px] text-[#8A99AF] hover:text-white hover:bg-[#2E3A4E]"
                      >
                        <span className="text-[9px]">{expandedFolders.has(key) ? "▼" : "▶"}</span>
                        <span className="truncate">{label}</span>
                        <span className="ml-auto text-[9px] opacity-50">{items.length}</span>
                      </button>
                      {expandedFolders.has(key) &&
                        items.map((a) => (
                          <button
                            key={a.path}
                            onClick={() => setSelected(a)}
                            className={`w-full flex items-center gap-2 pl-10 pr-3 py-1 text-left text-xs transition-colors ${
                              selected?.path === a.path
                                ? "bg-[#3C50E0]/20 text-white"
                                : "text-[#8A99AF] hover:text-white hover:bg-[#2E3A4E]"
                            }`}
                          >
                            <span className="truncate">{a.name}</span>
                            <span
                              className={`ml-auto shrink-0 px-1.5 py-0.5 rounded text-[9px] font-mono ${FORMAT_BADGE[a.format] ?? "bg-white/10 text-white/50"}`}
                            >
                              {a.format}
                            </span>
                          </button>
                        ))}
                    </div>
                  );
                })}
            </div>
          ))}
        </div>
      </aside>

      {/* Viewer */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {selected && (
          <div className="shrink-0 px-4 py-2 border-b border-[#2E3A4E] flex items-center gap-3 text-xs text-[#8A99AF]">
            <span className="text-white font-medium">{selected.name}</span>
            <span
              className={`px-1.5 py-0.5 rounded font-mono ${FORMAT_BADGE[selected.format] ?? "bg-white/10 text-white/50"}`}
            >
              {selected.format}
            </span>
            <span className="ml-auto opacity-50 font-mono truncate">{selected.path}</span>
          </div>
        )}
        <ModelViewer
          format={selected?.format}
          url={
            selected
              ? "/api/game-assets/file/" +
                selected.path
                  .replace(/^game-assets\//, "")
                  .split("/")
                  .map(encodeURIComponent)
                  .join("/")
              : null
          }
        />
      </div>
    </div>
  );
}
