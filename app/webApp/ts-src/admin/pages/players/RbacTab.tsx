import { useState } from "react";
import { PlayerFile } from "../../apiTypes";
import { useT } from "../../i18n";
import { getAdminToken } from "../../auth/adminTokenStorage";
import { GroupsMultiSelect } from "../rbac/GroupsMultiSelect";
import { SaveButton } from "../../../primitives/SaveButton";

/**
 * In-game RBAC groups on the character itself (`PlayerState.groups`), independent of the
 * account-level groups on the Users page — those only gate this admin panel. Not yet in the
 * generated API client (`/api/admin/players/{name}/groups` postdates the last `make gen-api`),
 * hence the manual fetch — same pattern as LoggersPage's `{name}` tail-parameter workaround.
 */
export function RbacTab({ name, file }: { name: string; file: PlayerFile }) {
  const t = useT();
  // `groups` postdates the last `make gen-api` too — same reason as the manual fetch above.
  const initialGroups = (file.state as { groups?: string[] }).groups ?? [];
  const [groups, setGroups] = useState<string[]>(initialGroups);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const save = async () => {
    setSaving(true);
    setError(null);
    try {
      const r = await fetch(`/api/admin/players/${encodeURIComponent(name)}/groups`, {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${getAdminToken()}`,
        },
        body: JSON.stringify(groups),
      });
      if (!r.ok) throw new Error();
      setSaved(true);
      setTimeout(() => setSaved(false), 1500);
    } catch {
      setError(t("common.saveFailed"));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="p-5 space-y-4">
      <p className="text-xs text-[#8A99AF]">{t("players.rbacHint")}</p>
      <GroupsMultiSelect value={groups} onChange={setGroups} />
      {error && <p className="text-xs text-red-400">{error}</p>}
      <SaveButton saving={saving} saved={saved} onClick={save} />
    </div>
  );
}
