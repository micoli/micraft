import { useState, useEffect, useCallback } from "react";
import { useNavigate, useParams } from "react-router";
import { useForm } from "@tanstack/react-form";
import { z } from "zod";
import { api, type SceneDto } from "../../api";
import { SceneEditorViewport } from "./SceneEditorViewport";
import { EmptyDetail } from "../../../primitives/EmptyDetail";

const LIST_COLLAPSED_STORAGE_KEY = "adminScenesListCollapsed";

const nameSchema = z.string().trim().min(1, "Name is required.");
const dimensionSchema = z.string().refine((v) => {
  const n = parseInt(v, 10);
  return Number.isFinite(n) && n > 0;
}, "Width/height/depth must be positive integers.");

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
  const [renameSubmitError, setRenameSubmitError] = useState<string | null>(null);
  const [resizing, setResizing] = useState(false);
  const [resizeSubmitError, setResizeSubmitError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [createSubmitError, setCreateSubmitError] = useState<string | null>(null);
  const [listCollapsed, setListCollapsed] = useState(() => localStorage.getItem(LIST_COLLAPSED_STORAGE_KEY) === "1");

  useEffect(() => {
    localStorage.setItem(LIST_COLLAPSED_STORAGE_KEY, listCollapsed ? "1" : "0");
  }, [listCollapsed]);

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

  const renameForm = useForm({
    defaultValues: { name: "" },
    onSubmit: async ({ value }) => {
      if (!selected) return;
      setRenameSubmitError(null);
      try {
        await api.scenes.rename(selected.id, value.name.trim());
        setRenaming(false);
        reload();
      } catch {
        setRenameSubmitError("Failed to rename.");
      }
    },
  });

  const resizeForm = useForm({
    defaultValues: { width: "16", height: "16", depth: "16" },
    onSubmit: async ({ value }) => {
      if (!selected) return;
      setResizeSubmitError(null);
      try {
        await api.scenes.resize(
          selected.id,
          parseInt(value.width, 10),
          parseInt(value.height, 10),
          parseInt(value.depth, 10),
        );
        setResizing(false);
        reload();
      } catch {
        setResizeSubmitError("Failed to resize.");
      }
    },
  });

  const createForm = useForm({
    defaultValues: { name: "", width: "16", height: "16", depth: "16" },
    onSubmit: async ({ value }) => {
      setCreateSubmitError(null);
      try {
        const scene = await api.scenes.create(
          value.name.trim(),
          parseInt(value.width, 10),
          parseInt(value.height, 10),
          parseInt(value.depth, 10),
        );
        setCreating(false);
        createForm.reset();
        reload();
        setSelectedId(scene.id);
      } catch {
        setCreateSubmitError("Failed to create scene.");
      }
    },
  });

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
              <span className="text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF]">Scenes</span>
              <button
                className="text-[11px] text-[#3C50E0] hover:underline"
                onClick={() => {
                  setCreateSubmitError(null);
                  setCreating(true);
                }}
              >
                + New
              </button>
            </>
          )}
          <button
            className="text-[#8A99AF] hover:text-white text-xs shrink-0"
            title={listCollapsed ? "Expand scene list" : "Collapse scene list"}
            onClick={() => setListCollapsed((c) => !c)}
          >
            {listCollapsed ? "»" : "«"}
          </button>
        </div>
        {!listCollapsed && (
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
        )}
      </aside>

      <div className="flex-1 flex flex-col overflow-hidden">
        {!selected && <EmptyDetail message="Select a scene" />}
        {selected && (
          <>
            <div className="shrink-0 flex items-center justify-between gap-3 px-4 py-2 border-b border-[#2E3A4E]">
              {renaming ? (
                <form
                  className="flex items-center gap-2 flex-1"
                  onSubmit={(e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    renameForm.handleSubmit();
                  }}
                >
                  <renameForm.Field name="name" validators={{ onChange: nameSchema }}>
                    {(field) => (
                      <input
                        autoFocus
                        className="bg-[#1A222C] border border-[#2E3A4E] rounded px-2 py-1 text-sm text-white outline-none"
                        value={field.state.value}
                        onChange={(e) => field.handleChange(e.target.value)}
                        onBlur={field.handleBlur}
                      />
                    )}
                  </renameForm.Field>
                  <button type="submit" className="text-xs text-emerald-400 hover:underline">
                    Save
                  </button>
                  <button
                    type="button"
                    className="text-xs text-[#8A99AF] hover:underline"
                    onClick={() => setRenaming(false)}
                  >
                    Cancel
                  </button>
                  {renameSubmitError && <span className="text-xs text-red-400">{renameSubmitError}</span>}
                </form>
              ) : (
                <>
                  <h2 className="text-white font-semibold text-sm">{selected.name}</h2>
                  <div className="flex items-center gap-3">
                    {resizing ? (
                      <form
                        className="flex items-center gap-1"
                        onSubmit={(e) => {
                          e.preventDefault();
                          e.stopPropagation();
                          resizeForm.handleSubmit();
                        }}
                      >
                        <resizeForm.Field name="width" validators={{ onChange: dimensionSchema }}>
                          {(field) => (
                            <input
                              autoFocus
                              type="number"
                              min={1}
                              className="w-14 bg-[#1A222C] border border-[#2E3A4E] rounded px-1 py-0.5 text-[11px] font-mono text-white outline-none"
                              value={field.state.value}
                              onChange={(e) => field.handleChange(e.target.value)}
                              onBlur={field.handleBlur}
                            />
                          )}
                        </resizeForm.Field>
                        <span className="text-[11px] text-[#8A99AF] font-mono">×</span>
                        <resizeForm.Field name="height" validators={{ onChange: dimensionSchema }}>
                          {(field) => (
                            <input
                              type="number"
                              min={1}
                              className="w-14 bg-[#1A222C] border border-[#2E3A4E] rounded px-1 py-0.5 text-[11px] font-mono text-white outline-none"
                              value={field.state.value}
                              onChange={(e) => field.handleChange(e.target.value)}
                              onBlur={field.handleBlur}
                            />
                          )}
                        </resizeForm.Field>
                        <span className="text-[11px] text-[#8A99AF] font-mono">×</span>
                        <resizeForm.Field name="depth" validators={{ onChange: dimensionSchema }}>
                          {(field) => (
                            <input
                              type="number"
                              min={1}
                              className="w-14 bg-[#1A222C] border border-[#2E3A4E] rounded px-1 py-0.5 text-[11px] font-mono text-white outline-none"
                              value={field.state.value}
                              onChange={(e) => field.handleChange(e.target.value)}
                              onBlur={field.handleBlur}
                            />
                          )}
                        </resizeForm.Field>
                        <button type="submit" className="text-xs text-emerald-400 hover:underline">
                          Save
                        </button>
                        <button
                          type="button"
                          className="text-xs text-[#8A99AF] hover:underline"
                          onClick={() => {
                            setResizing(false);
                            setResizeSubmitError(null);
                          }}
                        >
                          Cancel
                        </button>
                        {resizeSubmitError && <span className="text-xs text-red-400">{resizeSubmitError}</span>}
                      </form>
                    ) : (
                      <button
                        className="text-[11px] text-[#8A99AF] font-mono hover:text-white"
                        title="Resize (overlapping region preserved, rest becomes air)"
                        onClick={() => {
                          resizeForm.reset({
                            width: String(selected.width),
                            height: String(selected.height),
                            depth: String(selected.depth),
                          });
                          setResizeSubmitError(null);
                          setResizing(true);
                        }}
                      >
                        {selected.width}×{selected.height}×{selected.depth}
                      </button>
                    )}
                    <button
                      className="text-xs text-[#8A99AF] hover:text-white"
                      onClick={() => {
                        renameForm.reset({ name: selected.name });
                        setRenameSubmitError(null);
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
          <form
            className="bg-[#1C2434] border border-[#2E3A4E] rounded-lg p-4 w-80 flex flex-col gap-3"
            onSubmit={(e) => {
              e.preventDefault();
              e.stopPropagation();
              createForm.handleSubmit();
            }}
          >
            <h3 className="text-white text-sm font-semibold">New scene</h3>
            <createForm.Field name="name" validators={{ onChange: nameSchema }}>
              {(field) => (
                <label className="flex flex-col gap-1 text-xs text-[#8A99AF]">
                  Name
                  <input
                    autoFocus
                    className="bg-[#0E1726] border border-[#2E3A4E] rounded px-2 py-1 text-sm text-white outline-none"
                    value={field.state.value}
                    onChange={(e) => field.handleChange(e.target.value)}
                    onBlur={field.handleBlur}
                  />
                </label>
              )}
            </createForm.Field>
            <createForm.Field name="width" validators={{ onChange: dimensionSchema }}>
              {(field) => (
                <label className="flex flex-col gap-1 text-xs text-[#8A99AF] flex-1">
                  Width
                  <input
                    type="number"
                    min={1}
                    className="bg-[#0E1726] border border-[#2E3A4E] rounded px-2 py-1 text-sm text-white outline-none"
                    value={field.state.value}
                    onChange={(e) => field.handleChange(e.target.value)}
                    onBlur={field.handleBlur}
                  />
                </label>
              )}
            </createForm.Field>
            <createForm.Field name="height" validators={{ onChange: dimensionSchema }}>
              {(field) => (
                <label className="flex flex-col gap-1 text-xs text-[#8A99AF] flex-1">
                  Height
                  <input
                    type="number"
                    min={1}
                    className="bg-[#0E1726] border border-[#2E3A4E] rounded px-2 py-1 text-sm text-white outline-none"
                    value={field.state.value}
                    onChange={(e) => field.handleChange(e.target.value)}
                    onBlur={field.handleBlur}
                  />
                </label>
              )}
            </createForm.Field>
            <createForm.Field name="depth" validators={{ onChange: dimensionSchema }}>
              {(field) => (
                <label className="flex flex-col gap-1 text-xs text-[#8A99AF] flex-1">
                  Depth
                  <input
                    type="number"
                    min={1}
                    className="bg-[#0E1726] border border-[#2E3A4E] rounded px-2 py-1 text-sm text-white outline-none"
                    value={field.state.value}
                    onChange={(e) => field.handleChange(e.target.value)}
                    onBlur={field.handleBlur}
                  />
                </label>
              )}
            </createForm.Field>
            {createSubmitError && <span className="text-xs text-red-400">{createSubmitError}</span>}
            <div className="flex justify-end gap-2 mt-1">
              <button
                type="button"
                className="text-xs text-[#8A99AF] hover:text-white px-3 py-1.5"
                onClick={() => setCreating(false)}
              >
                Cancel
              </button>
              <button
                type="submit"
                className="text-xs bg-[#3C50E0] text-white rounded px-3 py-1.5 hover:bg-[#3C50E0]/80"
              >
                Create
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
