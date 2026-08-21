import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { Dialog } from "../../../primitives/Dialog";
import {
  getApiAuthConfig,
  getApiAdminUsers,
  getApiPlayersByEmailByEmail,
  postApiAdminUsers,
  putApiAdminUsersByEmail,
  deleteApiAdminUsersByEmail,
} from "../../../generated/api/requests";
import { UserDto } from "../../apiTypes";
import { useT } from "../../i18n";
import { Button } from "../../../primitives/Button";
import { UserForm } from "./UserForm";
import { DialogContent } from "../../../primitives/DialogContent";
import { DialogTitle } from "../../../primitives/DialogTitle";

export function UsersPage() {
  const t = useT();
  const [searchParams] = useSearchParams();
  const highlightEmail = searchParams.get("u");
  const highlightRef = useRef<HTMLTableRowElement>(null);
  const [users, setUsers] = useState<UserDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [unavailable, setUnavailable] = useState(false);
  const [authProvider, setAuthProvider] = useState<string>("local");
  const [playersByEmail, setPlayersByEmail] = useState<Record<string, string[]>>({});
  const [addOpen, setAddOpen] = useState(false);
  const [editUser, setEditUser] = useState<UserDto | null>(null);
  const [deleteEmail, setDeleteEmail] = useState<string | null>(null);

  useEffect(() => {
    if (highlightRef.current) highlightRef.current.scrollIntoView({ behavior: "smooth", block: "center" });
  }, [loading]);

  const refresh = async () => {
    setLoading(true);
    try {
      const [configR, usersR] = await Promise.all([getApiAuthConfig(), getApiAdminUsers()]);
      setAuthProvider(configR.data?.provider ?? "local");
      if (usersR.response?.status === 503) {
        setUnavailable(true);
        return;
      }
      const loadedUsers = usersR.data ?? [];
      setUsers(loadedUsers);
      const entries = await Promise.all(
        loadedUsers.map(async (u) => {
          const r = await getApiPlayersByEmailByEmail({ path: { email: u.email } });
          const players = r.data ?? [];
          return [u.email, players.map((p) => p.name)] as const;
        }),
      );
      setPlayersByEmail(Object.fromEntries(entries));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void refresh();
  }, []);

  const handleAdd = async (u: UserDto & { password?: string }) => {
    const { response } = await postApiAdminUsers({
      body: { email: u.email, password: u.password ?? "", displayName: u.displayName, groups: u.groups },
    });
    if (!response?.ok) throw new Error(t("common.serverError", response?.status ?? 0));
    await refresh();
  };

  const handleEdit = async (u: UserDto) => {
    if (!editUser) return;
    const { response } = await putApiAdminUsersByEmail({
      path: { email: editUser.email },
      body: { displayName: u.displayName, groups: u.groups },
    });
    if (!response?.ok) throw new Error(t("common.serverError", response?.status ?? 0));
    await refresh();
  };

  const handleDelete = async () => {
    if (!deleteEmail) return;
    await deleteApiAdminUsersByEmail({ path: { email: deleteEmail } });
    setDeleteEmail(null);
    await refresh();
  };

  if (unavailable) {
    return (
      <div className="rounded-xl border border-[#2E3A4E] bg-[#1A222C] p-6 text-sm text-[#8A99AF]">
        {t("users.requiresLocalBefore")} <code className="text-white">auth.provider: local</code>{" "}
        {t("users.requiresLocalBetween")} <code className="text-white">data/config/server.yaml</code>.
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <p className="text-sm text-[#8A99AF]">
          {t(users.length === 1 ? "users.countOne" : "users.countMany", users.length)}
        </p>
        <Button onClick={() => setAddOpen(true)}>{t("users.add")}</Button>
      </div>

      <div className="bg-[#1A222C] rounded-xl border border-[#2E3A4E] overflow-hidden">
        {loading ? (
          <p className="p-6 text-[#8A99AF] text-sm animate-pulse">{t("common.loading")}</p>
        ) : (
          <table className="w-full">
            <thead>
              <tr className="border-b border-[#2E3A4E]">
                {(authProvider === "none"
                  ? [t("users.email"), t("users.players"), ""]
                  : [t("users.email"), t("users.displayName"), t("users.groups"), t("users.players"), ""]
                ).map((h) => (
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
              {users.length === 0 && (
                <tr>
                  <td
                    colSpan={authProvider === "none" ? 3 : 5}
                    className="px-5 py-8 text-[#8A99AF] text-sm text-center"
                  >
                    {t("users.none")}
                  </td>
                </tr>
              )}
              {users.map((u) => {
                const isHighlighted = highlightEmail && u.email.toLowerCase() === highlightEmail.toLowerCase();
                return (
                  <tr
                    key={u.email}
                    ref={isHighlighted ? highlightRef : undefined}
                    className={`border-b border-[#2E3A4E] last:border-0 transition-colors ${isHighlighted ? "bg-[#3C50E0]/10 ring-1 ring-inset ring-[#3C50E0]/40" : "hover:bg-[#1F2D3D]"}`}
                  >
                    <td className="px-5 py-3 text-sm text-[#8A99AF]">{u.email}</td>
                    {authProvider !== "none" && (
                      <>
                        <td className="px-5 py-3 text-sm text-white font-medium">{u.displayName}</td>
                        <td className="px-5 py-3">
                          <div className="flex flex-wrap gap-1">
                            {u.groups.map((g) => (
                              <span
                                key={g}
                                className="bg-[#3C50E0]/20 text-[#818CF8] text-[10px] font-medium px-2 py-0.5 rounded-full border border-[#3C50E0]/30"
                              >
                                {g}
                              </span>
                            ))}
                          </div>
                        </td>
                      </>
                    )}
                    <td className="px-5 py-3">
                      <div className="flex flex-wrap gap-1">
                        {(playersByEmail[u.email] ?? []).map((p) => (
                          <Link
                            key={p}
                            to={`/admin/players/${encodeURIComponent(p)}`}
                            className="text-[10px] font-medium px-2 py-0.5 rounded-full border bg-green-950/40 border-green-700/40 text-green-400 hover:text-white hover:border-green-500 transition-colors"
                          >
                            {p}
                          </Link>
                        ))}
                      </div>
                    </td>
                    <td className="px-5 py-3">
                      <div className="flex gap-2 justify-end">
                        {authProvider !== "none" && (
                          <Button variant="ghost" onClick={() => setEditUser(u)}>
                            {t("common.edit")}
                          </Button>
                        )}
                        <Button variant="danger" onClick={() => setDeleteEmail(u.email)}>
                          {t("common.delete")}
                        </Button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>

      {/* Dialogs */}
      <Dialog open={addOpen} onOpenChange={setAddOpen}>
        <DialogContent>
          <DialogTitle>{t("users.addTitle")}</DialogTitle>
          <UserForm
            initial={{}}
            isNew
            noauth={authProvider === "none"}
            onSave={handleAdd}
            onClose={() => setAddOpen(false)}
          />
        </DialogContent>
      </Dialog>

      <Dialog open={!!editUser} onOpenChange={(o) => !o && setEditUser(null)}>
        <DialogContent>
          <DialogTitle>{t("users.editTitle")}</DialogTitle>
          {editUser && (
            <UserForm
              initial={editUser}
              isNew={false}
              onSave={handleEdit as (u: UserDto & { password?: string }) => Promise<void>}
              onClose={() => setEditUser(null)}
            />
          )}
        </DialogContent>
      </Dialog>

      <Dialog open={!!deleteEmail} onOpenChange={(o) => !o && setDeleteEmail(null)}>
        <DialogContent>
          <DialogTitle>{t("users.deleteTitle")}</DialogTitle>
          <p className="text-sm text-[#8A99AF] mb-5">
            {t("users.deleteConfirmBefore")} <span className="text-white font-medium">{deleteEmail}</span>
            {t("users.deleteConfirmAfter")}
          </p>
          <div className="flex gap-2 justify-end">
            <Button variant="ghost" onClick={() => setDeleteEmail(null)}>
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
