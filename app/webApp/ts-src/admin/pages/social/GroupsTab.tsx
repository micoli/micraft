import { useCallback, useEffect, useState } from "react";
import {
  getApiAdminSocialGroups,
  getApiAdminSocialPlayers,
  postApiAdminSocialGroups,
  postApiAdminSocialGroupsByIdMembers,
  deleteApiAdminSocialGroupsById,
  deleteApiAdminSocialGroupsByIdMembersByPlayerId,
} from "../../../generated/api/requests";
import type { OrgMicoliMicraftSocialGroupInfo as GroupInfo } from "../../../generated/api/requests/types.gen";
import { EmptyDetail } from "../../../primitives/EmptyDetail";
import { errorText } from "./err";
import { NameCombo } from "./NameCombo";

export function GroupsTab({
  selectedId,
  onSelect,
}: {
  selectedId: string | null;
  onSelect: (id: string | null) => void;
}) {
  const [groups, setGroups] = useState<GroupInfo[]>([]);
  const [nameOptions, setNameOptions] = useState<string[]>([]);
  const [createError, setCreateError] = useState<string | null>(null);

  const reload = useCallback(() => {
    getApiAdminSocialGroups({ throwOnError: true })
      .then((r) => setGroups(r.data))
      .catch(console.error);
  }, []);

  useEffect(() => {
    reload();
    getApiAdminSocialPlayers({ throwOnError: true })
      .then((r) => setNameOptions(r.data))
      .catch(() => setNameOptions([]));
  }, [reload]);

  const selected = groups.find((g) => g.id === selectedId) ?? null;

  const create = async (playerName: string) => {
    setCreateError(null);
    try {
      const { data } = await postApiAdminSocialGroups({ body: { playerName }, throwOnError: true });
      reload();
      onSelect(data.id);
    } catch (e) {
      setCreateError(errorText(e));
    }
  };

  const disband = async (id: string) => {
    if (!confirm("Disband this group?")) return;
    await deleteApiAdminSocialGroupsById({ path: { id }, throwOnError: true }).catch(console.error);
    onSelect(null);
    reload();
  };

  const addMember = async (playerName: string) => {
    if (!selected) return;
    const { data } = await postApiAdminSocialGroupsByIdMembers({
      path: { id: selected.id },
      body: { playerName },
      throwOnError: true,
    });
    setGroups((gs) => gs.map((g) => (g.id === data.id ? data : g)));
  };

  const removeMember = async (playerId: string) => {
    if (!selected) return;
    await deleteApiAdminSocialGroupsByIdMembersByPlayerId({
      path: { id: selected.id, playerId },
      throwOnError: true,
    });
    reload();
  };

  return (
    <div className="flex h-full overflow-hidden">
      <aside className="w-64 shrink-0 flex flex-col border-r border-[#2E3A4E] overflow-hidden">
        <div className="px-3 py-2 border-b border-[#2E3A4E]">
          <span className="text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF]">Groups</span>
        </div>
        <div className="px-3 py-2 border-b border-[#2E3A4E]">
          <p className="text-[10px] text-[#8A99AF] mb-1">New group — leader (online)</p>
          <NameCombo names={nameOptions} placeholder="Player name" buttonLabel="Create" onAdd={create} />
          {createError && <p className="text-xs text-red-400 mt-1">{createError}</p>}
        </div>
        <div className="flex-1 overflow-y-auto py-2">
          {groups.map((g) => (
            <button
              key={g.id}
              onClick={() => onSelect(g.id)}
              className={`w-full text-left px-3 py-2 text-sm truncate flex flex-col gap-0.5 ${
                g.id === selectedId
                  ? "bg-[#3C50E0]/20 text-white"
                  : "text-[#8A99AF] hover:text-white hover:bg-[#2E3A4E]"
              }`}
            >
              <span className="truncate">{g.leaderName}&apos;s group</span>
              <span className="text-[10px] font-mono text-[#8A99AF]">{g.members.length} members</span>
            </button>
          ))}
          {groups.length === 0 && <div className="px-3 py-2 text-xs text-[#8A99AF]">No active groups.</div>}
        </div>
      </aside>

      <div className="flex-1 flex flex-col overflow-hidden">
        {!selected && <EmptyDetail message="Select a group" />}
        {selected && (
          <div className="flex flex-col gap-4 p-4 overflow-y-auto">
            <div className="flex items-center justify-between">
              <h2 className="text-white font-semibold text-sm">{selected.leaderName}&apos;s group</h2>
              <button className="text-xs text-red-400 hover:text-red-300" onClick={() => disband(selected.id)}>
                Disband
              </button>
            </div>
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
                      {m.playerId === selected.leaderId && <span className="text-[#8A99AF]"> — leader</span>}
                      {!m.online && <span className="text-[#8A99AF]"> (offline)</span>}
                    </span>
                    {m.playerId !== selected.leaderId && (
                      <button className="text-red-400 hover:text-red-300" onClick={() => removeMember(m.playerId)}>
                        Remove
                      </button>
                    )}
                  </li>
                ))}
              </ul>
              <NameCombo names={nameOptions} placeholder="Player name (online)" buttonLabel="Add" onAdd={addMember} />
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
