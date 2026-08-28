import { useState, useMemo, useCallback } from "react";
import { Dialog } from "../../primitives/Dialog";
import { DialogContent } from "../../primitives/DialogContent";
import { DialogTitle } from "../../primitives/DialogTitle";
import { Button } from "../../primitives/Button";
import { NumberInput } from "../../primitives/NumberInput";
import { RecipeDefinition } from "../types";

interface Props {
  open: boolean;
  onClose: () => void;
  recipes: Record<string, RecipeDefinition>;
  knownRecipes: string[];
  inventory: Record<string, number>;
  itemMeta: Record<string, { label: string; bg: string }>;
  onCommand: (cmd: string) => void;
}

function maxCraftable(recipe: RecipeDefinition, inventory: Record<string, number>): number {
  if (recipe.ingredients.length === 0) return 0;
  return Math.floor(Math.min(...recipe.ingredients.map((ing) => Math.floor((inventory[ing.type] ?? 0) / ing.count))));
}

export function Craft({ open, onClose, recipes, knownRecipes, inventory, itemMeta, onCommand }: Props) {
  const [filterKnown, setFilterKnown] = useState(true);
  const [search, setSearch] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [count, setCount] = useState(1);

  const recipeList = useMemo(() => {
    const q = search.trim().toLowerCase();
    return Object.entries(recipes).filter(([id, recipe]) => {
      if (filterKnown && !knownRecipes.includes(id)) return false;
      if (!q) return true;
      return id.toLowerCase().includes(q) || recipe.giveId.toLowerCase().includes(q);
    });
  }, [recipes, knownRecipes, filterKnown, search]);

  const selected = selectedId ? recipes[selectedId] : null;

  const maxCount = useMemo(() => {
    if (!selected) return 0;
    return maxCraftable(selected, inventory);
  }, [selected, inventory]);

  const safeCount = Math.min(Math.max(1, count), Math.max(1, maxCount));
  const canCraft = !!selectedId && knownRecipes.includes(selectedId) && maxCount >= 1;

  const handleSelect = useCallback((id: string) => {
    setSelectedId(id);
    setCount(1);
  }, []);

  const handleCraft = useCallback(() => {
    if (!selectedId || !canCraft) return;
    onCommand(`/docraft ${selectedId} ${safeCount}`);
  }, [selectedId, safeCount, canCraft, onCommand]);

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        if (!v) onClose();
      }}
    >
      <DialogContent className="w-[760px] max-w-[95vw] max-h-[80vh] flex flex-col gap-4">
        <DialogTitle>Crafting</DialogTitle>

        <div className="flex gap-4 flex-1 min-h-0 overflow-hidden">
          {/* Left — recipe list */}
          <div className="w-48 flex flex-col gap-2 shrink-0">
            <input
              type="text"
              placeholder="Search…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              onMouseDown={(e) => e.stopPropagation()}
              className="w-full bg-white/10 border border-white/20 rounded px-2 py-1 text-white text-xs placeholder:text-white/30 focus:outline-none focus:border-white/50"
            />
            <label className="flex items-center gap-2 text-xs text-white/70 cursor-pointer select-none">
              <input
                type="checkbox"
                checked={filterKnown}
                onChange={(e) => setFilterKnown(e.target.checked)}
                className="accent-white"
              />
              Known only
            </label>
            <div className="flex-1 overflow-y-auto flex flex-col gap-1 min-h-0">
              {recipeList.length === 0 ? (
                <div className="text-white/40 text-xs text-center py-4">No recipes</div>
              ) : (
                recipeList.map(([id, recipe]) => {
                  const known = knownRecipes.includes(id);
                  return (
                    <button
                      key={id}
                      onClick={() => handleSelect(id)}
                      className={[
                        "text-left px-2 py-1.5 rounded text-xs transition-colors",
                        selectedId === id
                          ? "bg-white/20 border border-white/40 text-white"
                          : "hover:bg-white/10 border border-transparent text-white/70",
                        !known && "opacity-50",
                      ].join(" ")}
                    >
                      <div className="font-mono font-semibold leading-tight">{id.toLowerCase().replace(/_/g, " ")}</div>
                      <div className="text-white/50 text-[10px]">
                        {recipe.giveAmount}× {recipe.giveId.toLowerCase()} ({recipe.giveType})
                      </div>
                    </button>
                  );
                })
              )}
            </div>
          </div>

          {/* Center — ingredients */}
          <div className="flex-1 flex flex-col gap-3 min-w-0">
            {selected ? (
              <>
                <div className="flex items-center gap-2">
                  <span className="text-xs text-white/60">Count:</span>
                  <NumberInput
                    min={1}
                    max={Math.max(1, maxCount)}
                    value={safeCount}
                    onChange={(e) => setCount(Math.max(1, parseInt(e.target.value) || 1))}
                    className="w-16 bg-white/10 border border-white/20 rounded px-2 py-0.5 text-white text-sm text-center focus:outline-none focus:border-white/50"
                    onMouseDown={(e) => e.stopPropagation()}
                  />
                  <span className="text-xs text-white/40">/ {maxCount} max</span>
                </div>
                <div className="flex flex-wrap gap-2">
                  {selected.ingredients.map((ing) => {
                    const have = inventory[ing.type] ?? 0;
                    const need = ing.count * safeCount;
                    const ok = have >= need;
                    const meta = itemMeta[ing.type];
                    return (
                      <div
                        key={ing.type}
                        className={[
                          "w-[80px] h-[80px] rounded border-2 flex flex-col items-center justify-center gap-1 p-1",
                          ok ? "border-green-500/60 bg-green-900/20" : "border-red-500/60 bg-red-900/20",
                        ].join(" ")}
                        title={`${ing.type}: need ${need}, have ${have}`}
                      >
                        {meta && (
                          <div
                            className="w-6 h-6 rounded-sm"
                            style={{
                              background: meta.bg,
                              boxShadow: "inset -2px -2px 0 rgba(0,0,0,0.3),inset 2px 2px 0 rgba(255,255,255,0.15)",
                            }}
                          />
                        )}
                        <div className="text-[9px] font-mono text-white/80 text-center leading-tight">
                          {(meta?.label ?? ing.type.slice(0, 4)).toLowerCase()}
                        </div>
                        <div
                          className={["text-[10px] font-bold font-mono", ok ? "text-green-300" : "text-red-400"].join(
                            " ",
                          )}
                        >
                          {need}/{have}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </>
            ) : (
              <div className="text-white/30 text-sm text-center py-8">Select a recipe</div>
            )}
          </div>

          {/* Right — output */}
          {selected && (
            <div className="w-28 shrink-0 flex flex-col items-center gap-2 border-l border-white/10 pl-4">
              <div className="text-xs text-white/50 uppercase tracking-wide">Output</div>
              <div className="w-[72px] h-[72px] rounded border-2 border-white/30 bg-white/5 flex flex-col items-center justify-center gap-1 p-1">
                {(() => {
                  const meta = itemMeta[selected.giveId];
                  return meta ? (
                    <div
                      className="w-8 h-8 rounded-sm"
                      style={{
                        background: meta.bg,
                        boxShadow: "inset -2px -2px 0 rgba(0,0,0,0.3),inset 2px 2px 0 rgba(255,255,255,0.15)",
                      }}
                    />
                  ) : null;
                })()}
                <div className="text-[9px] font-mono text-white/70 text-center">{selected.giveId.toLowerCase()}</div>
              </div>
              <div className="text-white font-bold text-sm">{selected.giveAmount * safeCount}×</div>
              <div className="text-[10px] text-white/40 font-mono">{selected.giveType}</div>
            </div>
          )}
        </div>

        {/* Bottom bar */}
        <div className="flex gap-2 justify-end border-t border-white/10 pt-3">
          <Button variant="primary" disabled={!canCraft} onClick={handleCraft}>
            Craft {safeCount > 1 ? `${safeCount}×` : ""}
          </Button>
          <Button variant="secondary" onClick={onClose}>
            Close
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
