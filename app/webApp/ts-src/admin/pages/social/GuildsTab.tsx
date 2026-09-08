import { useCallback, useEffect, useState } from "react";
import {
  getApiAdminSocialPlayers,
  getApiAdminSocialGuilds,
  postApiAdminSocialGuilds,
  putApiAdminSocialGuildsById,
  deleteApiAdminSocialGuildsById,
  postApiAdminSocialGuildsByIdMembers,
  deleteApiAdminSocialGuildsByIdMembersByPlayerId,
  putApiAdminSocialGuildsByIdMembersByPlayerId,
} from "../../../generated/api/requests";
import type { OrgMicoliMicraftSocialGuildInfoDto as GuildDto } from "../../../generated/api/requests/types.gen";
import { EmptyDetail } from "../../../primitives/EmptyDetail";
import { errorText } from "./err";
import { NameCombo } from "./NameCombo";
import { NameField } from "./NameField";

export function GuildsTab({
  selectedId,
  onSelect,
}: {
  selectedId: string | null;
  onSelect: (id: string | null) => void;
}) {
  const [guilds, setGuilds] = useState<GuildDto[]>([]);
  const [players, setPlayers] = useState<string[]>([]);
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({ name: "", tag: "", ownerName: "" });
  const [formError, setFormError] = useState<string | null>(null);
  const [edit, setEdit] = useState<{ name: string; tag: string; motd: string } | null>(null);

  const reload = useCallback(() => {
    getApiAdminSocialGuilds({ throwOnError: true })
      .then((r) => setGuilds(r.data.sort((a, b) => a.name.localeCompare(b.name))))
      .catch(console.error);
  }, []);

  useEffect(() => {
    reload();
    getApiAdminSocialPlayers({ throwOnError: true })
      .then((r) => setPlayers(r.data))
      .catch(() => setPlayers([]));
  }, [reload]);

  const selected = guilds.find((g) => g.id === selectedId) ?? null;

  const patch = (dto: GuildDto) => setGuilds((gs) => gs.map((g) => (g.id === dto.id ? dto : g)));

  const create = async () => {
    setFormError(null);
    try {
      const { data } = await postApiAdminSocialGuilds({ body: form, throwOnError: true });
      setCreating(false);
      setForm({ name: "", tag: "", ownerName: "" });
      reload();
      onSelect(data.id);
    } catch (e) {
      setFormError(errorText(e));
    }
  };

  const saveEdit = async () => {
    if (!selected || !edit) return;
    try {
      const { data } = await putApiAdminSocialGuildsById({
        path: { id: selected.id },
        body: edit,
        throwOnError: true,
      });
      patch(data);
      setEdit(null);
    } catch (e) {
      alert(errorText(e));
    }
  };

  const remove = async (id: string) => {
    if (!confirm("Disband this guild?")) return;
    await deleteApiAdminSocialGuildsById({ path: { id }, throwOnError: true }).catch(console.error);
    onSelect(null);
    reload();
  };

  const addMember = async (playerName: string) => {
    if (!selected) return;
    const { data } = await postApiAdminSocialGuildsByIdMembers({
      path: { id: selected.id },
      body: { playerName },
      throwOnError: true,
    });
    patch(data);
  };

  const setRank = async (playerId: string, rank: string) => {
    if (!selected) return;
    const { data } = await putApiAdminSocialGuildsByIdMembersByPlayerId({
      path: { id: selected.id, playerId },
      body: { rank },
      throwOnError: true,
    }).catch((e) => {
      alert(errorText(e));
      throw e;
    });
    patch(data);
  };

  const removeMember = async (playerId: string) => {
    if (!selected) return;
    try {
      const { data } = await deleteApiAdminSocialGuildsByIdMembersByPlayerId({
        path: { id: selected.id, playerId },
        throwOnError: true,
      });
      patch(data);
    } catch (e) {
      alert(errorText(e));
    }
  };

  return (
    <div className="flex h-full overflow-hidden">
      <aside className="w-64 shrink-0 flex flex-col border-r border-[#2E3A4E] overflow-hidden">
        <div className="px-3 py-2 border-b border-[#2E3A4E] flex items-center justify-between">
          <span className="text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF]">Guilds</span>
          <button className="text-[11px] text-[#3C50E0] hover:underline" onClick={() => setCreating(true)}>
            + New
          </button>
        </div>
        <div className="flex-1 overflow-y-auto py-2">
          {guilds.map((g) => (
            <button
              key={g.id}
              onClick={() => {
                onSelect(g.id);
                setEdit(null);
              }}
              className={`w-full text-left px-3 py-2 text-sm truncate flex flex-col gap-0.5 ${
                g.id === selectedId
                  ? "bg-[#3C50E0]/20 text-white"
                  : "text-[#8A99AF] hover:text-white hover:bg-[#2E3A4E]"
              }`}
            >
              <span className="truncate">
                [{g.tag}] {g.name}
              </span>
              <span className="text-[10px] font-mono text-[#8A99AF]">{g.members.length} members</span>
            </button>
          ))}
          {guilds.length === 0 && <div className="px-3 py-2 text-xs text-[#8A99AF]">No guilds.</div>}
        </div>
      </aside>

      <div className="flex-1 flex flex-col overflow-hidden">
        {!selected && <EmptyDetail message="Select a guild" />}
        {selected && (
          <div className="flex flex-col gap-4 p-4 overflow-y-auto">
            <div className="flex items-center justify-between">
              <h2 className="text-white font-semibold text-sm">
                [{selected.tag}] {selected.name}
              </h2>
              <div className="flex gap-3">
                <button
                  className="text-xs text-[#8A99AF] hover:text-white"
                  onClick={() => setEdit({ name: selected.name, tag: selected.tag, motd: selected.motd })}
                >
                  Edit
                </button>
                <button className="text-xs text-red-400 hover:text-red-300" onClick={() => remove(selected.id)}>
                  Disband
                </button>
              </div>
            </div>

            {edit ? (
              <div className="flex flex-col gap-2 bg-[#0E1726] border border-[#2E3A4E] rounded p-3">
                {(["name", "tag", "motd"] as const).map((k) => (
                  <label key={k} className="flex flex-col gap-1 text-xs text-[#8A99AF]">
                    {k}
                    <input
                      value={edit[k]}
                      onChange={(e) => setEdit({ ...edit, [k]: e.target.value })}
                      className="bg-[#1A222C] border border-[#2E3A4E] rounded px-2 py-1 text-sm text-white outline-none"
                    />
                  </label>
                ))}
                <div className="flex gap-2">
                  <button className="text-xs text-emerald-400 hover:underline" onClick={saveEdit}>
                    Save
                  </button>
                  <button className="text-xs text-[#8A99AF] hover:underline" onClick={() => setEdit(null)}>
                    Cancel
                  </button>
                </div>
              </div>
            ) : (
              selected.motd && <p className="text-xs text-[#8A99AF] italic">{selected.motd}</p>
            )}

            <div>
              <p className="text-xs font-medium text-[#8A99AF] mb-2">Members ({selected.members.length})</p>
              <ul className="flex flex-col gap-1 mb-2">
                {selected.members.map((m) => (
                  <li
                    key={m.playerId}
                    className="flex items-center justify-between bg-[#0E1726] border border-[#2E3A4E] rounded px-2 py-1 text-xs text-white"
                  >
                    <span>
                      {m.playerName}
                      {m.playerId === selected.ownerId && <span className="text-[#8A99AF]"> — owner</span>}
                      {!m.online && <span className="text-[#8A99AF]"> (offline)</span>}
                    </span>
                    <span className="flex items-center gap-2">
                      <select
                        value={m.rank}
                        disabled={m.playerId === selected.ownerId}
                        onChange={(e) => setRank(m.playerId, e.target.value)}
                        className="bg-[#1A222C] border border-[#2E3A4E] rounded px-1 py-0.5 text-white disabled:opacity-50"
                      >
                        {selected.ranks.map((r) => (
                          <option key={r.name} value={r.name}>
                            {r.name}
                          </option>
                        ))}
                      </select>
                      {m.playerId !== selected.ownerId && (
                        <button className="text-red-400 hover:text-red-300" onClick={() => removeMember(m.playerId)}>
                          Remove
                        </button>
                      )}
                    </span>
                  </li>
                ))}
              </ul>
              <NameCombo names={players} placeholder="Player name" buttonLabel="Add" onAdd={addMember} />
            </div>
          </div>
        )}
      </div>

      {creating && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50">
          <div className="bg-[#1C2434] border border-[#2E3A4E] rounded-lg p-4 w-80 flex flex-col gap-3">
            <h3 className="text-white text-sm font-semibold">New guild</h3>
            {(["name", "tag"] as const).map((k) => (
              <label key={k} className="flex flex-col gap-1 text-xs text-[#8A99AF]">
                {k}
                <input
                  value={form[k]}
                  onChange={(e) => setForm({ ...form, [k]: e.target.value })}
                  className="bg-[#0E1726] border border-[#2E3A4E] rounded px-2 py-1 text-sm text-white outline-none"
                />
              </label>
            ))}
            <NameField
              label="owner"
              value={form.ownerName}
              names={players}
              onChange={(v) => setForm({ ...form, ownerName: v })}
            />
            {formError && <span className="text-xs text-red-400">{formError}</span>}
            <div className="flex justify-end gap-2 mt-1">
              <button
                className="text-xs text-[#8A99AF] hover:text-white px-3 py-1.5"
                onClick={() => setCreating(false)}
              >
                Cancel
              </button>
              <button
                className="text-xs bg-[#3C50E0] text-white rounded px-3 py-1.5 hover:bg-[#3C50E0]/80"
                onClick={create}
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
