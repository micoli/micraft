import { useState, useEffect, useCallback } from "react";
import { useNavigate, useParams } from "react-router";
import { api, type SceneDto } from "../../api";
import { SceneEditorViewport } from "./SceneEditorViewport";
import { EmptyDetail } from "../../../primitives/EmptyDetail";

// Mirrors InstancesPage.tsx's master/detail layout, but a Scene is a bounded, self-contained
// X/Y/Z raw block buffer (see SceneMesher.kt) — not tied to the live world/chunk grid — so
// creation is a trivial name+width/height/depth form instead of InstanceChunkPicker's
// raster/pan/zoom chunk-selection flow, and there's no yMin/yMax/enabled/chunks bookkeeping.
export function ScenesPage() {
  const { id: routeId } = useParams<{ id?: string }>();
  const navigate = useNavigate();
  const [scenes, setScenes] = useState<SceneDto[]>([]);
  const [selectedId, setSelectedIdState] = useState<string | null>(routeId ?? null);
  const setSelectedId = useCallback(
    (id: string | null) => {
      setSelectedIdState(id);
      navigate(id ? `/admin/scenes/${id}` : "/admin/scenes");
    },
    [navigate],
  );
  const [renaming, setRenaming] = useState(false);
  const [renameValue, setRenameValue] = useState("");
  const [resizing, setResizing] = useState(false);
  const [widthValue, setWidthValue] = useState("16");
  const [heightValue, setHeightValue] = useState("16");
  const [depthValue, setDepthValue] = useState("16");
  const [resizeError, setResizeError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [createName, setCreateName] = useState("");
  const [createWidth, setCreateWidth] = useState("16");
  const [createHeight, setCreateHeight] = useState("16");
  const [createDepth, setCreateDepth] = useState("16");
  const [createError, setCreateError] = useState<string | null>(null);

  const reload = useCallback(() => {
    api.scenes
      .list()
      .then((list) => setScenes(list.sort((a, b) => a.name.localeCompare(b.name))))
      .catch(console.error);
  }, []);

  useEffect(() => {
    reload();
  }, [reload]);

  useEffect(() => {
    setSelectedIdState(routeId ?? null);
  }, [routeId]);

  const selected = scenes.find((s) => s.id === selectedId) ?? null;

  const submitRename = () => {
    if (!selected || !renameValue.trim()) return;
    api.scenes
      .rename(selected.id, renameValue.trim())
      .then(() => {
        setRenaming(false);
        reload();
      })
      .catch(console.error);
  };

  const submitResize = () => {
    if (!selected) return;
    setResizeError(null);
    const width = parseInt(widthValue, 10);
    const height = parseInt(heightValue, 10);
    const depth = parseInt(depthValue, 10);
    if (![width, height, depth].every((v) => Number.isFinite(v) && v > 0)) {
      setResizeError("Width/height/depth must be positive integers.");
      return;
    }
    api.scenes
      .resize(selected.id, width, height, depth)
      .then(() => {
        setResizing(false);
        reload();
      })
      .catch(() => setResizeError("Failed to resize."));
  };

  const deleteSelected = () => {
    if (!selected) return;
    if (!confirm(`Delete scene "${selected.name}"?`)) return;
    api.scenes
      .delete(selected.id)
      .then(() => {
        setSelectedId(null);
        reload();
      })
      .catch(console.error);
  };

  const submitCreate = () => {
    setCreateError(null);
    const name = createName.trim();
    const width = parseInt(createWidth, 10);
    const height = parseInt(createHeight, 10);
    const depth = parseInt(createDepth, 10);
    if (!name) {
      setCreateError("Name is required.");
      return;
    }
    if (![width, height, depth].every((v) => Number.isFinite(v) && v > 0)) {
      setCreateError("Width/height/depth must be positive integers.");
      return;
    }
    api.scenes
      .create(name, width, height, depth)
      .then((scene) => {
        setCreating(false);
        setCreateName("");
        reload();
        setSelectedId(scene.id);
      })
      .catch(() => setCreateError("Failed to create scene."));
  };

  return (
    <div className="flex h-full overflow-hidden -m-6">
      <aside className="w-[14.2857%] shrink-0 flex flex-col border-r border-[#2E3A4E] overflow-hidden">
        <div className="px-3 py-2 border-b border-[#2E3A4E] flex items-center justify-between">
          <span className="text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF]">Scenes</span>
          <button
            className="text-[11px] text-[#3C50E0] hover:underline"
            onClick={() => {
              setCreateError(null);
              setCreating(true);
            }}
          >
            + New
          </button>
        </div>
        <div className="flex-1 overflow-y-auto py-2">
          {scenes.map((scene) => {
            const isSelected = scene.id === selectedId;
            return (
              <button
                key={scene.id}
                onClick={() => {
                  setSelectedId(scene.id);
                  setRenaming(false);
                  setResizing(false);
                }}
                className={`w-full text-left px-3 py-2 text-sm truncate transition-colors flex flex-col gap-0.5 ${
                  isSelected ? "bg-[#3C50E0]/20 text-white" : "text-[#8A99AF] hover:text-white hover:bg-[#2E3A4E]"
                }`}
              >
                <span className="truncate">{scene.name}</span>
                <span className="text-[10px] font-mono text-[#8A99AF]">
                  {scene.width}×{scene.height}×{scene.depth}
                </span>
              </button>
            );
          })}
          {scenes.length === 0 && (
            <div className="px-3 py-2 text-xs text-[#8A99AF]">
              No scenes yet. Click &quot;+ New&quot; to create one.
            </div>
          )}
        </div>
      </aside>

      <div className="w-[85.7143%] flex flex-col overflow-hidden">
        {!selected && <EmptyDetail message="Select a scene" />}
        {selected && (
          <>
            <div className="shrink-0 flex items-center justify-between gap-3 px-4 py-2 border-b border-[#2E3A4E]">
              {renaming ? (
                <div className="flex items-center gap-2 flex-1">
                  <input
                    autoFocus
                    className="bg-[#1A222C] border border-[#2E3A4E] rounded px-2 py-1 text-sm text-white outline-none"
                    value={renameValue}
                    onChange={(e) => setRenameValue(e.target.value)}
                    onKeyDown={(e) => e.key === "Enter" && submitRename()}
                  />
                  <button className="text-xs text-emerald-400 hover:underline" onClick={submitRename}>
                    Save
                  </button>
                  <button className="text-xs text-[#8A99AF] hover:underline" onClick={() => setRenaming(false)}>
                    Cancel
                  </button>
                </div>
              ) : (
                <>
                  <h2 className="text-white font-semibold text-sm">{selected.name}</h2>
                  <div className="flex items-center gap-3">
                    {resizing ? (
                      <div className="flex items-center gap-1">
                        <input
                          autoFocus
                          type="number"
                          min={1}
                          className="w-14 bg-[#1A222C] border border-[#2E3A4E] rounded px-1 py-0.5 text-[11px] font-mono text-white outline-none"
                          value={widthValue}
                          onChange={(e) => setWidthValue(e.target.value)}
                          onKeyDown={(e) => e.key === "Enter" && submitResize()}
                        />
                        <span className="text-[11px] text-[#8A99AF] font-mono">×</span>
                        <input
                          type="number"
                          min={1}
                          className="w-14 bg-[#1A222C] border border-[#2E3A4E] rounded px-1 py-0.5 text-[11px] font-mono text-white outline-none"
                          value={heightValue}
                          onChange={(e) => setHeightValue(e.target.value)}
                          onKeyDown={(e) => e.key === "Enter" && submitResize()}
                        />
                        <span className="text-[11px] text-[#8A99AF] font-mono">×</span>
                        <input
                          type="number"
                          min={1}
                          className="w-14 bg-[#1A222C] border border-[#2E3A4E] rounded px-1 py-0.5 text-[11px] font-mono text-white outline-none"
                          value={depthValue}
                          onChange={(e) => setDepthValue(e.target.value)}
                          onKeyDown={(e) => e.key === "Enter" && submitResize()}
                        />
                        <button className="text-xs text-emerald-400 hover:underline" onClick={submitResize}>
                          Save
                        </button>
                        <button
                          className="text-xs text-[#8A99AF] hover:underline"
                          onClick={() => {
                            setResizing(false);
                            setResizeError(null);
                          }}
                        >
                          Cancel
                        </button>
                        {resizeError && <span className="text-xs text-red-400">{resizeError}</span>}
                      </div>
                    ) : (
                      <button
                        className="text-[11px] text-[#8A99AF] font-mono hover:text-white"
                        title="Resize (overlapping region preserved, rest becomes air)"
                        onClick={() => {
                          setWidthValue(String(selected.width));
                          setHeightValue(String(selected.height));
                          setDepthValue(String(selected.depth));
                          setResizeError(null);
                          setResizing(true);
                        }}
                      >
                        {selected.width}×{selected.height}×{selected.depth}
                      </button>
                    )}
                    <button
                      className="text-xs text-[#8A99AF] hover:text-white"
                      onClick={() => {
                        setRenameValue(selected.name);
                        setRenaming(true);
                      }}
                    >
                      Rename
                    </button>
                    <button className="text-xs text-red-400 hover:text-red-300" onClick={deleteSelected}>
                      Delete
                    </button>
                    <span className="text-[11px] text-[#8A99AF] font-mono">U:undo, Y:redo</span>
                  </div>
                </>
              )}
            </div>
            <SceneEditorViewport
              key={`${selected.id}:${selected.width}:${selected.height}:${selected.depth}`}
              scene={selected}
            />
          </>
        )}
      </div>

      {creating && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50">
          <div className="bg-[#1C2434] border border-[#2E3A4E] rounded-lg p-4 w-80 flex flex-col gap-3">
            <h3 className="text-white text-sm font-semibold">New scene</h3>
            <label className="flex flex-col gap-1 text-xs text-[#8A99AF]">
              Name
              <input
                autoFocus
                className="bg-[#0E1726] border border-[#2E3A4E] rounded px-2 py-1 text-sm text-white outline-none"
                value={createName}
                onChange={(e) => setCreateName(e.target.value)}
              />
            </label>
            <label className="flex flex-col gap-1 text-xs text-[#8A99AF] flex-1">
              Width
              <input
                type="number"
                min={1}
                className="bg-[#0E1726] border border-[#2E3A4E] rounded px-2 py-1 text-sm text-white outline-none"
                value={createWidth}
                onChange={(e) => setCreateWidth(e.target.value)}
              />
            </label>
            <label className="flex flex-col gap-1 text-xs text-[#8A99AF] flex-1">
              Height
              <input
                type="number"
                min={1}
                className="bg-[#0E1726] border border-[#2E3A4E] rounded px-2 py-1 text-sm text-white outline-none"
                value={createHeight}
                onChange={(e) => setCreateHeight(e.target.value)}
              />
            </label>
            <label className="flex flex-col gap-1 text-xs text-[#8A99AF] flex-1">
              Depth
              <input
                type="number"
                min={1}
                className="bg-[#0E1726] border border-[#2E3A4E] rounded px-2 py-1 text-sm text-white outline-none"
                value={createDepth}
                onChange={(e) => setCreateDepth(e.target.value)}
              />
            </label>
            {createError && <span className="text-xs text-red-400">{createError}</span>}
            <div className="flex justify-end gap-2 mt-1">
              <button
                className="text-xs text-[#8A99AF] hover:text-white px-3 py-1.5"
                onClick={() => setCreating(false)}
              >
                Cancel
              </button>
              <button
                className="text-xs bg-[#3C50E0] text-white rounded px-3 py-1.5 hover:bg-[#3C50E0]/80"
                onClick={submitCreate}
              >
                Create
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
