import { useEffect, useState } from "react";
import { Link } from "react-router";
import { Dialog } from "../../../primitives/Dialog";
import { getApiAdminGroups, deleteApiAdminGroupsByName } from "../../../generated/api/requests";
import { GroupDto } from "../../apiTypes";
import { useT } from "../../i18n";
import { Button } from "../../../primitives/Button";
import { DialogContent } from "../../../primitives/DialogContent";
import { DialogTitle } from "../../../primitives/DialogTitle";

export function RbacPage() {
  const t = useT();
  const [groups, setGroups] = useState<GroupDto[]>([]);
  const [defaultGroups, setDefaultGroups] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [unavailable, setUnavailable] = useState(false);
  const [deleteName, setDeleteName] = useState<string | null>(null);

  const refresh = async () => {
    setLoading(true);
    try {
      const { data, response } = await getApiAdminGroups();
      if (response?.status === 503) {
        setUnavailable(true);
        return;
      }
      setGroups(data?.groups ?? []);
      setDefaultGroups(data?.defaultGroups ?? []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void refresh();
  }, []);

  const handleDelete = async () => {
    if (!deleteName) return;
    await deleteApiAdminGroupsByName({ path: { name: deleteName } });
    setDeleteName(null);
    await refresh();
  };

  if (unavailable) {
    return (
      <div className="rounded-xl border border-[#2E3A4E] bg-[#1A222C] p-6 text-sm text-[#8A99AF]">
        {t("rbac.requiresLocal")} <code className="text-white">auth.provider: local</code> {t("rbac.requiresLocalOr")}{" "}
        <code className="text-white">oauth</code>.
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <p className="text-sm text-[#8A99AF]">
          {t(groups.length === 1 ? "rbac.countOne" : "rbac.countMany", groups.length)}
        </p>
        <Link to="/admin/rbac/new">
          <Button>{t("rbac.add")}</Button>
        </Link>
      </div>

      <div className="bg-[#1A222C] rounded-xl border border-[#2E3A4E] overflow-hidden">
        {loading ? (
          <p className="p-6 text-[#8A99AF] text-sm animate-pulse">{t("common.loading")}</p>
        ) : (
          <table className="w-full">
            <thead>
              <tr className="border-b border-[#2E3A4E]">
                {[t("rbac.name"), t("rbac.permissions"), ""].map((h) => (
                  <th
                    key={h}
                    className="px-5 py-3 text-left text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF]"
                  >
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {groups.map((g) => (
                <tr key={g.name} className="border-b border-[#2E3A4E] last:border-0 hover:bg-[#1F2D3D]">
                  <td className="px-5 py-3 text-sm text-white font-medium">
                    <div className="flex items-center gap-2">
                      {g.name}
                      {defaultGroups.includes(g.name) && (
                        <span className="text-[10px] font-medium px-2 py-0.5 rounded-full border bg-green-950/40 border-green-700/40 text-green-400">
                          {t("rbac.default")}
                        </span>
                      )}
                      {!g.editable && (
                        <span className="text-[10px] font-medium px-2 py-0.5 rounded-full border bg-amber-950/40 border-amber-700/40 text-amber-400">
                          {t("rbac.builtin")}
                        </span>
                      )}
                    </div>
                  </td>
                  <td className="px-5 py-3">
                    <div className="flex flex-wrap gap-1">
                      {g.permissions.map((perm) => (
                        <span
                          key={perm}
                          className="bg-[#3C50E0]/20 text-[#818CF8] text-[10px] font-medium px-2 py-0.5 rounded-full border border-[#3C50E0]/30"
                        >
                          {perm}
                        </span>
                      ))}
                    </div>
                  </td>
                  <td className="px-5 py-3">
                    <div className="flex gap-2 justify-end">
                      {g.editable && (
                        <>
                          <Link to={`/admin/rbac/${encodeURIComponent(g.name)}`}>
                            <Button variant="ghost">{t("common.edit")}</Button>
                          </Link>
                          <Button variant="danger" onClick={() => setDeleteName(g.name)}>
                            {t("common.delete")}
                          </Button>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <Dialog open={!!deleteName} onOpenChange={(o) => !o && setDeleteName(null)}>
        <DialogContent>
          <DialogTitle>{t("rbac.deleteTitle")}</DialogTitle>
          <p className="text-sm text-[#8A99AF] mb-5">
            {t("rbac.deleteConfirmBefore")} <span className="text-white font-medium">{deleteName}</span>
            {t("rbac.deleteConfirmAfter")}
          </p>
          <div className="flex gap-2 justify-end">
            <Button variant="ghost" onClick={() => setDeleteName(null)}>
              {t("common.cancel")}
            </Button>
            <Button variant="danger" onClick={handleDelete}>
              {t("common.delete")}
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
