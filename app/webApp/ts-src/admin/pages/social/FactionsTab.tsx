import { useCallback, useEffect, useState } from "react";
import {
  getApiAdminSocialFactions,
  getApiAdminSocialFactionsByIdMembers,
  getApiAdminSocialPlayers,
  postApiAdminSocialFactions,
  postApiAdminSocialFactionsByIdMembers,
  deleteApiAdminSocialFactionsByIdMembersByPlayerId,
  putApiAdminSocialFactionsSettings,
  deleteApiAdminSocialFactionsById,
} from "../../../generated/api/requests";
import type {
  OrgMicoliMicraftSocialFactionDefinition as FactionDef,
  OrgMicoliMicraftHttpFactionSettingsRequest as FactionSettings,
  OrgMicoliMicraftHttpSocialMemberDto as MemberDto,
} from "../../../generated/api/requests/types.gen";
import { EmptyDetail } from "../../../primitives/EmptyDetail";
import { errorText } from "./err";
import { NameCombo } from "./NameCombo";

const EMPTY: FactionDef = { id: "", name: "", color: "#888888", description: "", spawnX: null, spawnZ: null };

export function FactionsTab({
  selectedId,
  onSelect,
}: {
  selectedId: string | null;
  onSelect: (id: string | null) => void;
}) {
  const [settings, setSettings] = useState<FactionSettings | null>(null);
  const [list, setList] = useState<FactionDef[]>([]);
  const [draft, setDraft] = useState<FactionDef | null>(null);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [members, setMembers] = useState<MemberDto[]>([]);
  const [players, setPlayers] = useState<string[]>([]);

  const reload = useCallback(() => {
    getApiAdminSocialFactions({ throwOnError: true })
      .then((r) => {
        setSettings(r.data.settings);
        setList(r.data.list);
      })
      .catch(console.error);
  }, []);

  useEffect(() => reload(), [reload]);

  useEffect(() => {
    getApiAdminSocialPlayers({ throwOnError: true })
      .then((r) => setPlayers(r.data))
      .catch(() => setPlayers([]));
  }, []);

  const selected = list.find((f) => f.id === selectedId) ?? null;

  const reloadMembers = useCallback((id: string) => {
    getApiAdminSocialFactionsByIdMembers({ path: { id }, throwOnError: true })
      .then((r) => setMembers(r.data))
      .catch(() => setMembers([]));
  }, []);

  useEffect(() => {
    const match = list.find((f) => f.id === selectedId) ?? null;
    setDraft(match ? { ...match } : null);
    setCreating(false);
    setMembers([]);
    if (selectedId) reloadMembers(selectedId);
  }, [selectedId, list, reloadMembers]);

  const addMember = async (playerName: string) => {
    if (!selectedId) return;
    await postApiAdminSocialFactionsByIdMembers({
      path: { id: selectedId },
      body: { playerName },
      throwOnError: true,
    });
    reloadMembers(selectedId);
    reload();
  };

  const removeMember = async (playerId: string) => {
    if (!selectedId) return;
    await deleteApiAdminSocialFactionsByIdMembersByPlayerId({
      path: { id: selectedId, playerId },
      throwOnError: true,
    }).catch((e) => alert(errorText(e)));
    reloadMembers(selectedId);
    reload();
  };

  const saveSettings = async (next: FactionSettings) => {
    setSettings(next);
    await putApiAdminSocialFactionsSettings({ body: next, throwOnError: true }).catch(console.error);
  };

  const save = async (def: FactionDef) => {
    setError(null);
    if (!def.id.trim()) {
      setError("id required");
      return;
    }
    try {
      await postApiAdminSocialFactions({ body: def, throwOnError: true });
      setCreating(false);
      reload();
      onSelect(def.id);
    } catch (e) {
      setError(errorText(e));
    }
  };

  const remove = async (id: string) => {
    if (!confirm(`Delete faction "${id}"?`)) return;
    await deleteApiAdminSocialFactionsById({ path: { id }, throwOnError: true }).catch(console.error);
    onSelect(null);
    reload();
  };

  const editor = (value: FactionDef, onChange: (d: FactionDef) => void, isNew: boolean) => (
    <div className="flex flex-col gap-2 bg-[#0E1726] border border-[#2E3A4E] rounded p-3 max-w-md">
      <label className="flex flex-col gap-1 text-xs text-[#8A99AF]">
        id
        <input
          value={value.id}
          disabled={!isNew}
          onChange={(e) => onChange({ ...value, id: e.target.value })}
          className="bg-[#1A222C] border border-[#2E3A4E] rounded px-2 py-1 text-sm text-white outline-none disabled:opacity-50"
        />
      </label>
      <label className="flex flex-col gap-1 text-xs text-[#8A99AF]">
        name
        <input
          value={value.name}
          onChange={(e) => onChange({ ...value, name: e.target.value })}
          className="bg-[#1A222C] border border-[#2E3A4E] rounded px-2 py-1 text-sm text-white outline-none"
        />
      </label>
      <label className="flex flex-col gap-1 text-xs text-[#8A99AF]">
        color
        <input
          type="color"
          value={value.color}
          onChange={(e) => onChange({ ...value, color: e.target.value })}
          className="h-8 w-16 bg-[#1A222C] border border-[#2E3A4E] rounded"
        />
      </label>
      <label className="flex flex-col gap-1 text-xs text-[#8A99AF]">
        description
        <textarea
          value={value.description}
          onChange={(e) => onChange({ ...value, description: e.target.value })}
          className="bg-[#1A222C] border border-[#2E3A4E] rounded px-2 py-1 text-sm text-white outline-none"
        />
      </label>
      <div className="flex gap-2">
        {(["spawnX", "spawnZ"] as const).map((k) => (
          <label key={k} className="flex flex-col gap-1 text-xs text-[#8A99AF] flex-1">
            {k}
            <input
              value={value[k] ?? ""}
              onChange={(e) => onChange({ ...value, [k]: e.target.value === "" ? null : Number(e.target.value) })}
              className="bg-[#1A222C] border border-[#2E3A4E] rounded px-2 py-1 text-sm text-white outline-none"
            />
          </label>
        ))}
      </div>
      {error && <span className="text-xs text-red-400">{error}</span>}
      <button
        className="text-xs bg-[#3C50E0] text-white rounded px-3 py-1.5 hover:bg-[#3C50E0]/80 self-start"
        onClick={() => save(value)}
      >
        Save
      </button>
    </div>
  );

  return (
    <div className="flex h-full overflow-hidden">
      <aside className="w-64 shrink-0 flex flex-col border-r border-[#2E3A4E] overflow-hidden">
        <div className="px-3 py-2 border-b border-[#2E3A4E] flex items-center justify-between">
          <span className="text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF]">Factions</span>
          <button
            className="text-[11px] text-[#3C50E0] hover:underline"
            onClick={() => {
              onSelect(null);
              setCreating(true);
              setDraft({ ...EMPTY });
            }}
          >
            + New
          </button>
        </div>
        <div className="flex-1 overflow-y-auto py-2">
          {list.map((f) => (
            <button
              key={f.id}
              onClick={() => onSelect(f.id)}
              className={`w-full text-left px-3 py-2 text-sm truncate flex items-center gap-2 ${
                f.id === selectedId
                  ? "bg-[#3C50E0]/20 text-white"
                  : "text-[#8A99AF] hover:text-white hover:bg-[#2E3A4E]"
              }`}
            >
              <span className="w-3 h-3 rounded-sm shrink-0" style={{ background: f.color }} />
              <span className="truncate">{f.name || f.id}</span>
            </button>
          ))}
          {list.length === 0 && <div className="px-3 py-2 text-xs text-[#8A99AF]">No factions.</div>}
        </div>
      </aside>

      <div className="flex-1 flex flex-col overflow-y-auto p-4 gap-6">
        {settings && (
          <div className="flex flex-col gap-2">
            <p className="text-xs font-semibold uppercase tracking-widest text-[#8A99AF]">Global settings</p>
            <div className="flex flex-wrap gap-4 text-xs text-white">
              <label className="flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={settings.enabled}
                  onChange={(e) => saveSettings({ ...settings, enabled: e.target.checked })}
                />
                enabled
              </label>
              <label className="flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={settings.friendlyFire}
                  onChange={(e) => saveSettings({ ...settings, friendlyFire: e.target.checked })}
                />
                friendlyFire
              </label>
              <label className="flex items-center gap-2">
                change cooldown (s)
                <input
                  value={settings.changeCooldownSeconds}
                  onChange={(e) => saveSettings({ ...settings, changeCooldownSeconds: Number(e.target.value) || 0 })}
                  className="w-20 bg-[#0E1726] border border-[#2E3A4E] rounded px-2 py-1 text-white outline-none"
                />
              </label>
              <label className="flex items-center gap-2">
                spawn ring radius
                <input
                  value={settings.spawnRingRadius}
                  onChange={(e) => saveSettings({ ...settings, spawnRingRadius: Number(e.target.value) || 0 })}
                  className="w-20 bg-[#0E1726] border border-[#2E3A4E] rounded px-2 py-1 text-white outline-none"
                />
              </label>
            </div>
          </div>
        )}

        {creating && draft && editor(draft, setDraft, true)}
        {!creating && selected && draft && (
          <div className="flex flex-col gap-2">
            <div className="flex items-center justify-between max-w-md">
              <h2 className="text-white font-semibold text-sm">{selected.name || selected.id}</h2>
              <button className="text-xs text-red-400 hover:text-red-300" onClick={() => remove(selected.id)}>
                Delete
              </button>
            </div>
            {editor(draft, setDraft, false)}

            <div className="max-w-md">
              <p className="text-xs font-medium text-[#8A99AF] mb-2">Members ({members.length})</p>
              <ul className="flex flex-col gap-1 mb-2">
                {members.map((m) => (
                  <li
                    key={m.playerId}
                    className="flex items-center justify-between bg-[#0E1726] border border-[#2E3A4E] rounded px-2 py-1 text-xs text-white"
                  >
                    <span>
                      {m.playerName}
                      {!m.online && <span className="text-[#8A99AF]"> (offline)</span>}
                    </span>
                    <button className="text-red-400 hover:text-red-300" onClick={() => removeMember(m.playerId)}>
                      Remove
                    </button>
                  </li>
                ))}
                {members.length === 0 && <li className="text-xs text-[#8A99AF]">No members.</li>}
              </ul>
              <NameCombo names={players} placeholder="Player name" buttonLabel="Add" onAdd={addMember} />
            </div>
          </div>
        )}
        {!creating && !selected && <EmptyDetail message="Select a faction" />}
      </div>
    </div>
  );
}
