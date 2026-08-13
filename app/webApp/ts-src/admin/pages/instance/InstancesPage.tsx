import { useState, useEffect, useCallback } from "react";
import { useNavigate, useParams } from "react-router";
import { api, type InstanceZoneDto } from "../../api";
import { InstanceEditorViewport } from "./InstanceEditorViewport";
import { InstanceChunkPicker } from "./InstanceChunkPicker";
import { CopyTeleportCommand } from "./CopyTeleportCommand";
import { EmptyDetail } from "../../../primitives/EmptyDetail";

const LIST_COLLAPSED_STORAGE_KEY = "adminInstancesListCollapsed";

function chunksFingerprint(chunks: InstanceZoneDto["chunks"]): string {
  return chunks
    .map((c) => `${c.cx},${c.cz}`)
    .sort()
    .join("|");
}

export function InstancesPage() {
  const { id: routeId } = useParams<{ id?: string }>();
  const navigate = useNavigate();
  const [instances, setInstances] = useState<InstanceZoneDto[]>([]);
  const [selectedId, setSelectedIdState] = useState<string | null>(routeId ?? null);
  const setSelectedId = useCallback(
    (id: string | null) => {
      setSelectedIdState(id);
      navigate(id ? `/admin/instances/${id}` : "/admin/instances");
    },
    [navigate],
  );
  const [renaming, setRenaming] = useState(false);
  const [renameValue, setRenameValue] = useState("");
  const [editingBounds, setEditingBounds] = useState(false);
  const [yMinValue, setYMinValue] = useState("0");
  const [yMaxValue, setYMaxValue] = useState("0");
  const [boundsError, setBoundsError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [editingChunks, setEditingChunks] = useState(false);
  const [listCollapsed, setListCollapsed] = useState(() => localStorage.getItem(LIST_COLLAPSED_STORAGE_KEY) === "1");

  useEffect(() => {
    localStorage.setItem(LIST_COLLAPSED_STORAGE_KEY, listCollapsed ? "1" : "0");
  }, [listCollapsed]);

  const reload = useCallback(() => {
    api.instances
      .list()
      .then((list) => setInstances(list.sort((a, b) => a.name.localeCompare(b.name))))
      .catch(console.error);
  }, []);

  useEffect(() => {
    reload();
  }, [reload]);

  useEffect(() => {
    setSelectedIdState(routeId ?? null);
  }, [routeId]);

  const selected = instances.find((i) => i.id === selectedId) ?? null;

  const submitRename = () => {
    if (!selected || !renameValue.trim()) return;
    api.instances
      .rename(selected.id, renameValue.trim())
      .then(() => {
        setRenaming(false);
        reload();
      })
      .catch(console.error);
  };

  const submitBounds = () => {
    if (!selected) return;
    setBoundsError(null);
    const yMin = parseInt(yMinValue, 10);
    const yMax = parseInt(yMaxValue, 10);
    if (!Number.isFinite(yMin) || !Number.isFinite(yMax) || yMin >= yMax) {
      setBoundsError("yMin must be less than yMax.");
      return;
    }
    api.instances
      .updateBounds(selected.id, yMin, yMax)
      .then(() => {
        setEditingBounds(false);
        reload();
      })
      .catch(() => setBoundsError("Failed to update bounds."));
  };

  const toggleEnabled = () => {
    if (!selected) return;
    api.instances.setEnabled(selected.id, !selected.enabled).then(reload).catch(console.error);
  };

  const deleteSelected = () => {
    if (!selected) return;
    if (!confirm(`Delete instance zone "${selected.name}"?`)) return;
    api.instances
      .delete(selected.id)
      .then(() => {
        setSelectedId(null);
        reload();
      })
      .catch(console.error);
  };

  return (
    <div className="flex h-full overflow-hidden -m-6">
      <aside
        className={`${listCollapsed ? "w-8" : "w-56"} shrink-0 flex flex-col border-r border-[#2E3A4E] overflow-hidden transition-[width]`}
        // The viewport canvas resizes via flexbox as this aside's width transitions, but Babylon
        // only recomputes its render size on a window "resize" event — fire one once the CSS
        // width transition settles (same fix as VoxelEditorSidebar's drag handle).
        onTransitionEnd={() => window.dispatchEvent(new Event("resize"))}
      >
        <div className="px-3 py-2 border-b border-[#2E3A4E] flex items-center justify-between">
          {!listCollapsed && (
            <>
              <span className="text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF]">Instances</span>
              <button className="text-[11px] text-[#3C50E0] hover:underline" onClick={() => setCreating(true)}>
                + New
              </button>
            </>
          )}
          <button
            className="text-[#8A99AF] hover:text-white text-xs shrink-0"
            title={listCollapsed ? "Expand instance list" : "Collapse instance list"}
            onClick={() => setListCollapsed((c) => !c)}
          >
            {listCollapsed ? "»" : "«"}
          </button>
        </div>
        {!listCollapsed && (
          <div className="flex-1 overflow-y-auto py-2">
            {instances.map((zone) => {
              const isSelected = zone.id === selectedId;
              return (
                <button
                  key={zone.id}
                  onClick={() => {
                    setSelectedId(zone.id);
                    setRenaming(false);
                    setEditingBounds(false);
                    setEditingChunks(false);
                  }}
                  className={`w-full text-left px-3 py-2 text-sm truncate transition-colors flex items-center gap-1.5 ${
                    isSelected ? "bg-[#3C50E0]/20 text-white" : "text-[#8A99AF] hover:text-white hover:bg-[#2E3A4E]"
                  }`}
                >
                  <span
                    className={`h-1.5 w-1.5 shrink-0 rounded-full ${zone.enabled ? "bg-emerald-400" : "bg-[#8A99AF]"}`}
                    title={zone.enabled ? "Enabled" : "Disabled"}
                  />
                  <span className={`truncate ${zone.enabled ? "" : "opacity-50"}`}>{zone.name}</span>
                </button>
              );
            })}
            {instances.length === 0 && (
              <div className="px-3 py-2 text-xs text-[#8A99AF]">
                No instance zones yet. Click &quot;+ New&quot; to create one.
              </div>
            )}
          </div>
        )}
      </aside>

      <div className="flex-1 flex flex-col overflow-hidden">
        {!selected && <EmptyDetail message="Select an instance zone" />}
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
                    <span className="text-[11px] text-[#8A99AF] font-mono">
                      {selected.chunks.length} chunk{selected.chunks.length !== 1 ? "s" : ""}
                    </span>
                    {editingBounds ? (
                      <div className="flex items-center gap-1">
                        <span className="text-[11px] text-[#8A99AF] font-mono">y[</span>
                        <input
                          autoFocus
                          type="number"
                          className="w-16 bg-[#1A222C] border border-[#2E3A4E] rounded px-1 py-0.5 text-[11px] font-mono text-white outline-none"
                          value={yMinValue}
                          onChange={(e) => setYMinValue(e.target.value)}
                          onKeyDown={(e) => e.key === "Enter" && submitBounds()}
                        />
                        <span className="text-[11px] text-[#8A99AF] font-mono">,</span>
                        <input
                          type="number"
                          className="w-16 bg-[#1A222C] border border-[#2E3A4E] rounded px-1 py-0.5 text-[11px] font-mono text-white outline-none"
                          value={yMaxValue}
                          onChange={(e) => setYMaxValue(e.target.value)}
                          onKeyDown={(e) => e.key === "Enter" && submitBounds()}
                        />
                        <span className="text-[11px] text-[#8A99AF] font-mono">]</span>
                        <button className="text-xs text-emerald-400 hover:underline" onClick={submitBounds}>
                          Save
                        </button>
                        <button
                          className="text-xs text-[#8A99AF] hover:underline"
                          onClick={() => {
                            setEditingBounds(false);
                            setBoundsError(null);
                          }}
                        >
                          Cancel
                        </button>
                        {boundsError && <span className="text-xs text-red-400">{boundsError}</span>}
                      </div>
                    ) : (
                      <button
                        className="text-[11px] text-[#8A99AF] font-mono hover:text-white"
                        title="Edit yMin/yMax"
                        onClick={() => {
                          setYMinValue(String(selected.yMin));
                          setYMaxValue(String(selected.yMax));
                          setBoundsError(null);
                          setEditingBounds(true);
                        }}
                      >
                        y[{selected.yMin},{selected.yMax}]
                      </button>
                    )}
                    <CopyTeleportCommand zone={selected} />
                    <button
                      className={`text-xs hover:underline ${selected.enabled ? "text-emerald-400" : "text-[#8A99AF]"}`}
                      title={selected.enabled ? "Disable this instance zone" : "Enable this instance zone"}
                      onClick={toggleEnabled}
                    >
                      {selected.enabled ? "Enabled" : "Disabled"}
                    </button>
                    <button className="text-xs text-[#8A99AF] hover:text-white" onClick={() => setEditingChunks(true)}>
                      Edit chunks
                    </button>
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
            <InstanceEditorViewport
              key={`${selected.id}:${selected.yMin}:${selected.yMax}:${chunksFingerprint(selected.chunks)}`}
              zone={selected}
            />
          </>
        )}
      </div>

      {creating && (
        <InstanceChunkPicker
          onCancel={() => setCreating(false)}
          onSaved={(zone) => {
            setCreating(false);
            reload();
            setSelectedId(zone.id);
          }}
        />
      )}

      {editingChunks && selected && (
        <InstanceChunkPicker
          editZone={selected}
          onCancel={() => setEditingChunks(false)}
          onSaved={() => {
            setEditingChunks(false);
            reload();
          }}
        />
      )}
    </div>
  );
}
