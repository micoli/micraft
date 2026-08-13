import { useState, useEffect } from "react";
import { getApiQuests } from "../../generated/api/requests";
import { Dialog } from "../../primitives/Dialog";
import { DialogContent } from "../../primitives/DialogContent";
import { DialogTitle } from "../../primitives/DialogTitle";
import { Button } from "../../primitives/Button";
import { QuestProgress, QuestStatus } from "../types";
import { ObjectiveRow } from "./ObjectiveRow";

type QuestType = "KILL" | "FETCH" | "ESCORT" | "EXPLORE" | "BOSS";

type KillObjective = { npcType: string; requiredCount: number };
type RewardItem = { type: string; count: number };
type QuestReward = { xp: number; items: RewardItem[] };

type QuestDef = {
  id: string;
  title: string;
  description: string;
  type: QuestType;
  level: number;
  objectives: KillObjective[];
  itemType: string | null;
  requiredCount: number;
  rewards: QuestReward;
  dependsOn: string[];
  repeatable: boolean;
  cooldownSeconds: number;
};

interface Props {
  open: boolean;
  quests: Record<string, QuestProgress>;
  playerLevel: number;
  onClose: () => void;
  onCommand: (cmd: string) => void;
}

const STATUS_LABEL: Record<QuestStatus, string> = {
  TODO: "Available",
  IN_PROGRESS: "In Progress",
  COMPLETED: "Completed",
  ABANDONED: "Abandoned",
  FAILED: "Failed",
};

const STATUS_COLOR: Record<QuestStatus, string> = {
  TODO: "text-gray-400",
  IN_PROGRESS: "text-yellow-400",
  COMPLETED: "text-green-400",
  ABANDONED: "text-red-400",
  FAILED: "text-red-600",
};

const TYPE_COLOR: Record<QuestType, string> = {
  KILL: "bg-red-800/60",
  BOSS: "bg-purple-800/60",
  FETCH: "bg-blue-800/60",
  ESCORT: "bg-orange-800/60",
  EXPLORE: "bg-teal-800/60",
};

const STATUS_FILTERS: (QuestStatus | "ALL")[] = ["ALL", "IN_PROGRESS", "TODO", "COMPLETED", "ABANDONED", "FAILED"];

function cooldownRemaining(def: QuestDef, progress: QuestProgress): number {
  if (!def.repeatable || !progress.lastCompletedAt) return 0;
  const elapsed = (Date.now() - progress.lastCompletedAt) / 1000;
  return Math.max(0, def.cooldownSeconds - elapsed);
}

export function QuestJournal({ open, quests, playerLevel, onClose, onCommand }: Props) {
  const [definitions, setDefinitions] = useState<Record<string, QuestDef>>({});
  const [statusFilter, setStatusFilter] = useState<QuestStatus | "ALL">("ALL");
  const [search, setSearch] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    getApiQuests({ throwOnError: true })
      .then((r) => {
        const map: Record<string, QuestDef> = {};
        for (const q of r.data as unknown as QuestDef[]) map[q.id] = q;
        setDefinitions(map);
      })
      .catch(() => {});
  }, [open]);

  const questIds = Object.keys(definitions);

  const filtered = questIds.filter((id) => {
    const def = definitions[id];
    const progress = quests[id];
    const status: QuestStatus = progress?.status ?? "TODO";
    if (def.level > playerLevel + 2) return false;
    if (statusFilter !== "ALL" && status !== statusFilter) return false;
    if (search && !def.title.toLowerCase().includes(search.toLowerCase())) return false;
    return true;
  });

  filtered.sort((a, b) => definitions[a].level - definitions[b].level);

  const inProgressId = filtered.find((id) => (quests[id]?.status ?? "TODO") === "IN_PROGRESS") ?? null;
  const selected = selectedId && definitions[selectedId] ? selectedId : (inProgressId ?? filtered[0] ?? null);
  const selDef = selected ? definitions[selected] : null;
  const selProgress = selected
    ? (quests[selected] ?? {
        status: "TODO" as QuestStatus,
        progress: {},
        acceptedAt: null,
        completedAt: null,
        lastCompletedAt: null,
      })
    : null;

  const cooldown = selDef && selProgress ? cooldownRemaining(selDef, selProgress) : 0;
  const canAccept =
    selDef &&
    selProgress &&
    (selProgress.status === "TODO" || (selDef.repeatable && selProgress.status === "COMPLETED" && cooldown === 0)) &&
    selDef.dependsOn.every((dep) => quests[dep]?.status === "COMPLETED");
  const canAbandon = selProgress?.status === "IN_PROGRESS";

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        if (!v) onClose();
      }}
    >
      <DialogContent className="w-[min(900px,95vw)] h-[min(600px,90vh)] p-0 flex flex-col overflow-hidden">
        <div className="flex items-center justify-between px-4 py-3 border-b border-white/10">
          <DialogTitle>Quest Journal</DialogTitle>
          <button onClick={onClose} className="text-white/50 hover:text-white text-lg leading-none">
            ✕
          </button>
        </div>
        <div className="flex flex-1 overflow-hidden">
          {/* Left pane */}
          <div className="w-64 flex flex-col border-r border-white/10 shrink-0">
            <div className="p-2 border-b border-white/10 flex flex-col gap-1">
              <input
                type="text"
                placeholder="Search…"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full bg-white/5 border border-white/10 rounded px-2 py-1 text-xs text-white placeholder-white/30 outline-none"
              />
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value as QuestStatus | "ALL")}
                className="w-full bg-white/5 border border-white/10 rounded px-2 py-1 text-xs text-white outline-none"
              >
                {STATUS_FILTERS.map((s) => (
                  <option key={s} value={s} className="bg-gray-900">
                    {s === "ALL" ? "All Quests" : STATUS_LABEL[s]}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex-1 overflow-y-auto">
              {filtered.length === 0 && <p className="text-white/30 text-xs text-center mt-4">No quests</p>}
              {filtered.map((id) => {
                const def = definitions[id];
                const progress = quests[id];
                const status: QuestStatus = progress?.status ?? "TODO";
                return (
                  <button
                    key={id}
                    onClick={() => setSelectedId(id)}
                    className={`w-full text-left px-3 py-2 border-b border-white/5 hover:bg-white/5 transition-colors ${selected === id ? "bg-white/10" : ""}`}
                  >
                    <div className="flex items-start justify-between gap-1">
                      <span className="text-xs text-white/90 font-medium leading-tight">{def.title}</span>
                      <span className="text-[10px] text-white/40 shrink-0">Lv{def.level}</span>
                    </div>
                    <span className={`text-[10px] ${STATUS_COLOR[status]}`}>{STATUS_LABEL[status]}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Right pane */}
          <div className="flex-1 overflow-y-auto p-4">
            {!selDef && <p className="text-white/30 text-sm">Select a quest to view details.</p>}
            {selDef && selProgress && (
              <>
                <div className="flex items-start justify-between mb-3 gap-2">
                  <div>
                    <h2 className="text-base font-semibold text-white">{selDef.title}</h2>
                    <div className="flex items-center gap-2 mt-0.5">
                      <span className={`text-[10px] px-1.5 py-0.5 rounded ${TYPE_COLOR[selDef.type]} text-white/80`}>
                        {selDef.type}
                      </span>
                      <span className="text-[10px] text-white/40">Level {selDef.level}</span>
                      {selDef.repeatable && <span className="text-[10px] text-blue-400">Repeatable</span>}
                    </div>
                  </div>
                  <span className={`text-xs font-medium shrink-0 ${STATUS_COLOR[selProgress.status]}`}>
                    {STATUS_LABEL[selProgress.status]}
                  </span>
                </div>

                <p className="text-sm text-white/70 mb-4">{selDef.description}</p>

                {/* Objectives */}
                {selDef.type === "KILL" || selDef.type === "BOSS" ? (
                  <div className="mb-4">
                    <p className="text-xs text-white/50 uppercase tracking-wide mb-2">Objectives</p>
                    {selDef.objectives.map((obj) => (
                      <ObjectiveRow
                        key={obj.npcType}
                        label={obj.npcType}
                        current={(selProgress.progress ?? {})[obj.npcType] ?? 0}
                        required={obj.requiredCount}
                      />
                    ))}
                  </div>
                ) : selDef.type === "FETCH" && selDef.itemType ? (
                  <div className="mb-4">
                    <p className="text-xs text-white/50 uppercase tracking-wide mb-2">Objectives</p>
                    <ObjectiveRow
                      label={selDef.itemType}
                      current={(selProgress.progress ?? {})[selDef.itemType] ?? 0}
                      required={selDef.requiredCount}
                    />
                  </div>
                ) : null}

                {/* Rewards */}
                {(selDef.rewards.xp > 0 || selDef.rewards.items.length > 0) && (
                  <div className="mb-4">
                    <p className="text-xs text-white/50 uppercase tracking-wide mb-1">Rewards</p>
                    {selDef.rewards.xp > 0 && <p className="text-xs text-yellow-400">{selDef.rewards.xp} XP</p>}
                    {selDef.rewards.items.map((item) => (
                      <p key={item.type} className="text-xs text-white/60">
                        {item.count}× {item.type}
                      </p>
                    ))}
                  </div>
                )}

                {/* Cooldown */}
                {cooldown > 0 && (
                  <p className="text-xs text-orange-400 mb-3">Cooldown: {Math.ceil(cooldown)}s remaining</p>
                )}

                {/* Actions */}
                <div className="flex gap-2 mt-2">
                  {canAccept && (
                    <Button onClick={() => onCommand(`/quest accept ${selected}`)} className="text-xs">
                      Accept
                    </Button>
                  )}
                  {canAbandon && (
                    <Button
                      variant="danger"
                      onClick={() => onCommand(`/quest abandon ${selected}`)}
                      className="text-xs"
                    >
                      Abandon
                    </Button>
                  )}
                </div>
              </>
            )}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
