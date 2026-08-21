import { useEffect, useState } from "react";
import { GLBModelViewer } from "./GLBModelViewer";
import { BlendSceneTree, collectObjectNames, type BlendSceneNode } from "./BlendSceneTree";

interface Props {
  path: string;
}

function toRelPath(path: string) {
  return path.replace(/^game-assets\//, "");
}

function toApiUrl(relPath: string) {
  return "/api/game-assets/file/" + relPath.split("/").map(encodeURIComponent).join("/");
}

function toBlendApiUrl(route: string, relPath: string) {
  return `/api/game-assets/${route}/` + relPath.split("/").map(encodeURIComponent).join("/");
}

export function BlendModelViewer({ path }: Props) {
  const [sceneTree, setSceneTree] = useState<BlendSceneNode | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [objPath, setObjPath] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [bbmodelUrl, setBbmodelUrl] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);
  const [clearingCache, setClearingCache] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const [autoApply, setAutoApply] = useState(false);

  useEffect(() => {
    setSceneTree(null);
    setSelected(new Set());
    setObjPath(null);
    setError(null);
    setBbmodelUrl(null);
    setExportError(null);
    const relPath = toRelPath(path);

    let cancelled = false;
    fetch(toBlendApiUrl("blend-scene", relPath))
      .then(async (r) => {
        if (!r.ok) throw new Error((await r.text()) || `HTTP ${r.status}`);
        return r.json() as Promise<BlendSceneNode>;
      })
      .then((tree) => {
        if (cancelled) return;
        setSceneTree(tree);
        setSelected(new Set(collectObjectNames(tree)));
      })
      .catch((e) => {
        if (!cancelled) setError(e instanceof Error ? e.message : String(e));
      });

    fetch(toBlendApiUrl("blend-preview", relPath))
      .then(async (r) => {
        if (!r.ok) throw new Error((await r.text()) || `HTTP ${r.status}`);
        return r.json() as Promise<{ path: string }>;
      })
      .then((data) => {
        if (!cancelled) setObjPath(data.path);
      })
      .catch((e) => {
        if (!cancelled) setError(e instanceof Error ? e.message : String(e));
      });

    return () => {
      cancelled = true;
    };
  }, [path, reloadKey]);

  function clearCache() {
    setClearingCache(true);
    fetch(toBlendApiUrl("blend-cache", toRelPath(path)), { method: "DELETE" })
      .then(async (r) => {
        if (!r.ok && r.status !== 204) throw new Error((await r.text()) || `HTTP ${r.status}`);
        setReloadKey((k) => k + 1);
      })
      .catch((e) => setError(e instanceof Error ? e.message : String(e)))
      .finally(() => setClearingCache(false));
  }

  function toggleObjects(objectNames: string[], checked: boolean) {
    setSelected((prev) => {
      const next = new Set(prev);
      objectNames.forEach((name) => (checked ? next.add(name) : next.delete(name)));
      if (autoApply) refreshPreview(next);
      return next;
    });
  }

  function refreshPreview(objectNames: Set<string> = selected) {
    setRefreshing(true);
    setBbmodelUrl(null);
    setExportError(null);
    const relPath = toRelPath(path);
    const url = toBlendApiUrl("blend-preview", relPath) + "?objects=" + encodeURIComponent([...objectNames].join(","));
    fetch(url)
      .then(async (r) => {
        if (!r.ok) throw new Error((await r.text()) || `HTTP ${r.status}`);
        return r.json() as Promise<{ path: string }>;
      })
      .then((data) => setObjPath(data.path))
      .catch((e) => setError(e instanceof Error ? e.message : String(e)))
      .finally(() => setRefreshing(false));
  }

  function exportToBbmodel() {
    if (!objPath) return;
    setExporting(true);
    setExportError(null);
    const exportUrl = "/api/game-assets/bbmodel-export/" + objPath.split("/").map(encodeURIComponent).join("/");
    fetch(exportUrl)
      .then(async (r) => {
        if (!r.ok) throw new Error((await r.text()) || `HTTP ${r.status}`);
        return r.json() as Promise<{ path: string }>;
      })
      .then((data) => setBbmodelUrl(toApiUrl(data.path)))
      .catch((e) => setExportError(e instanceof Error ? e.message : String(e)))
      .finally(() => setExporting(false));
  }

  if (error) {
    return (
      <div className="flex-1 flex items-center justify-center text-red-400 text-xs px-4 text-center whitespace-pre-wrap">
        {error}
      </div>
    );
  }

  if (!objPath) {
    return (
      <div className="flex-1 flex items-center justify-center text-[#8A99AF] text-sm">
        Converting .blend via Blender…
      </div>
    );
  }

  return (
    <div className="flex-1 flex overflow-hidden">
      <div className="flex-1 flex flex-col min-w-0">
        <div className="flex-1 min-h-0">
          <GLBModelViewer url={toApiUrl(objPath)} />
        </div>
        <div className="shrink-0 border-t border-[#2E3A4E] px-4 py-2 flex items-center gap-2">
          <button
            onClick={exportToBbmodel}
            disabled={exporting}
            className="px-3 py-1 rounded text-xs font-mono border border-[#2E3A4E] text-[#8A99AF] hover:border-[#3C50E0] hover:text-white disabled:opacity-50"
          >
            {exporting ? "Exporting…" : "Export en .bbmodel"}
          </button>
          {bbmodelUrl && (
            <a href={bbmodelUrl} download className="text-xs font-mono text-[#3C50E0] hover:underline">
              Télécharger le .bbmodel
            </a>
          )}
          {exportError && <span className="text-xs text-red-400">{exportError}</span>}
          <button
            onClick={clearCache}
            disabled={clearingCache}
            className="ml-auto px-3 py-1 rounded text-xs font-mono border border-[#2E3A4E] text-[#8A99AF] hover:border-red-400 hover:text-red-400 disabled:opacity-50"
          >
            {clearingCache ? "Vidage…" : "Vider le cache"}
          </button>
        </div>
      </div>
      {sceneTree && (
        <div className="w-56 shrink-0 border-l border-[#2E3A4E] flex flex-col overflow-hidden">
          <div className="shrink-0 px-3 py-2 border-b border-[#2E3A4E] flex items-center justify-between">
            <span className="text-xs text-[#8A99AF]">Scène</span>
            <div className="flex items-center gap-1.5">
              <button
                onClick={() => refreshPreview()}
                disabled={refreshing || selected.size === 0}
                className="px-2 py-0.5 rounded text-[11px] font-mono border border-[#2E3A4E] text-[#8A99AF] hover:border-[#3C50E0] hover:text-white disabled:opacity-50"
              >
                {refreshing ? "…" : "Appliquer"}
              </button>
              <label className="flex items-center gap-1 text-[11px] text-[#8A99AF] cursor-pointer">
                <input
                  type="checkbox"
                  checked={autoApply}
                  onChange={(e) => setAutoApply(e.target.checked)}
                  className="accent-[#3C50E0]"
                />
                Auto
              </label>
            </div>
          </div>
          <div className="flex-1 overflow-y-auto px-3 py-2">
            <BlendSceneTree node={sceneTree} selected={selected} onToggle={toggleObjects} />
          </div>
        </div>
      )}
    </div>
  );
}
