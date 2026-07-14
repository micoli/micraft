import { useEffect, useState } from "react";
import { Dialog, DialogContent, DialogTitle } from "../../primitives/Dialog";
import { api, UserDto } from "../api";

// ── Design primitives ─────────────────────────────────────────────────────────
function Btn({
  children,
  onClick,
  disabled,
  variant = "primary",
}: {
  children: React.ReactNode;
  onClick?: () => void;
  disabled?: boolean;
  variant?: "primary" | "ghost" | "danger";
}) {
  const s = {
    primary: "bg-[#3C50E0] hover:bg-[#3446c7] text-white",
    ghost: "bg-transparent border border-[#2E3A4E] text-[#8A99AF] hover:bg-[#2E3A4E] hover:text-white",
    danger: "bg-red-600/10 border border-red-600/30 text-red-400 hover:bg-red-600/20",
  }[variant];
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-colors disabled:opacity-50 ${s}`}
    >
      {children}
    </button>
  );
}

function TextInput({
  value,
  onChange,
  type = "text",
  placeholder,
}: {
  value: string;
  onChange: (v: string) => void;
  type?: string;
  placeholder?: string;
}) {
  return (
    <input
      type={type}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
      className="w-full bg-[#0E1726] border border-[#2E3A4E] rounded-lg px-3 py-2 text-sm text-white placeholder-[#4A5568] focus:outline-none focus:border-[#3C50E0] transition-colors"
    />
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <p className="text-xs font-medium text-[#8A99AF] mb-1.5">{label}</p>
      {children}
    </div>
  );
}

// ── User form ─────────────────────────────────────────────────────────────────
function UserForm({
  initial,
  onSave,
  onClose,
  isNew,
}: {
  initial: Partial<UserDto & { password: string }>;
  onSave: (u: UserDto & { password?: string }) => Promise<void>;
  onClose: () => void;
  isNew: boolean;
}) {
  const [email, setEmail] = useState(initial.email ?? "");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState(initial.displayName ?? "");
  const [groups, setGroups] = useState((initial.groups ?? []).join(", "));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const save = async () => {
    setSaving(true);
    setError(null);
    try {
      await onSave({
        email,
        displayName: displayName || email,
        groups: groups
          .split(",")
          .map((g) => g.trim())
          .filter(Boolean),
        password: isNew ? password : undefined,
      });
      onClose();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "Error");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-4">
      {isNew && (
        <Field label="Email">
          <TextInput value={email} onChange={setEmail} placeholder="user@example.com" />
        </Field>
      )}
      {isNew && (
        <Field label="Password">
          <TextInput value={password} onChange={setPassword} type="password" />
        </Field>
      )}
      <Field label="Display Name">
        <TextInput value={displayName} onChange={setDisplayName} placeholder={email} />
      </Field>
      <Field label="Groups (comma-separated)">
        <TextInput value={groups} onChange={setGroups} placeholder="admin, player" />
      </Field>
      {error && <p className="text-red-400 text-xs">{error}</p>}
      <div className="flex gap-2 justify-end pt-1">
        <Btn variant="ghost" onClick={onClose}>
          Cancel
        </Btn>
        <Btn onClick={save} disabled={saving}>
          {saving ? "Saving…" : "Save"}
        </Btn>
      </div>
    </div>
  );
}

// ── Page ──────────────────────────────────────────────────────────────────────
export function UsersPage() {
  const [users, setUsers] = useState<UserDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [addOpen, setAddOpen] = useState(false);
  const [editUser, setEditUser] = useState<UserDto | null>(null);
  const [deleteEmail, setDeleteEmail] = useState<string | null>(null);

  const refresh = async () => {
    setLoading(true);
    setUsers(await api.users.list());
    setLoading(false);
  };

  useEffect(() => {
    refresh();
  }, []);

  const handleAdd = async (u: UserDto & { password?: string }) => {
    const r = await api.users.create({
      email: u.email,
      password: u.password ?? "",
      displayName: u.displayName,
      groups: u.groups,
    });
    if (!r.ok) throw new Error(`Server error: ${r.status}`);
    await refresh();
  };

  const handleEdit = async (u: UserDto) => {
    if (!editUser) return;
    const r = await api.users.update(editUser.email, {
      displayName: u.displayName,
      groups: u.groups,
    });
    if (!r.ok) throw new Error(`Server error: ${r.status}`);
    await refresh();
  };

  const handleDelete = async () => {
    if (!deleteEmail) return;
    await api.users.delete(deleteEmail);
    setDeleteEmail(null);
    await refresh();
  };

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <p className="text-sm text-[#8A99AF]">
          {users.length} user{users.length !== 1 ? "s" : ""}
        </p>
        <Btn onClick={() => setAddOpen(true)}>+ Add User</Btn>
      </div>

      <div className="bg-[#1A222C] rounded-xl border border-[#2E3A4E] overflow-hidden">
        {loading ? (
          <p className="p-6 text-[#8A99AF] text-sm animate-pulse">Loading…</p>
        ) : (
          <table className="w-full">
            <thead>
              <tr className="border-b border-[#2E3A4E]">
                {["Email", "Display Name", "Groups", ""].map((h) => (
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
                  <td colSpan={4} className="px-5 py-8 text-[#8A99AF] text-sm text-center">
                    No users configured
                  </td>
                </tr>
              )}
              {users.map((u) => (
                <tr
                  key={u.email}
                  className="border-b border-[#2E3A4E] last:border-0 hover:bg-[#1F2D3D] transition-colors"
                >
                  <td className="px-5 py-3 text-sm text-[#8A99AF]">{u.email}</td>
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
                  <td className="px-5 py-3">
                    <div className="flex gap-2 justify-end">
                      <Btn variant="ghost" onClick={() => setEditUser(u)}>
                        Edit
                      </Btn>
                      <Btn variant="danger" onClick={() => setDeleteEmail(u.email)}>
                        Delete
                      </Btn>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Dialogs */}
      <Dialog open={addOpen} onOpenChange={setAddOpen}>
        <DialogContent>
          <DialogTitle>Add User</DialogTitle>
          <UserForm initial={{}} isNew onSave={handleAdd} onClose={() => setAddOpen(false)} />
        </DialogContent>
      </Dialog>

      <Dialog open={!!editUser} onOpenChange={(o) => !o && setEditUser(null)}>
        <DialogContent>
          <DialogTitle>Edit User</DialogTitle>
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
          <DialogTitle>Delete User</DialogTitle>
          <p className="text-sm text-[#8A99AF] mb-5">
            Delete <span className="text-white font-medium">{deleteEmail}</span>? This cannot be undone.
          </p>
          <div className="flex gap-2 justify-end">
            <Btn variant="ghost" onClick={() => setDeleteEmail(null)}>
              Cancel
            </Btn>
            <Btn variant="danger" onClick={handleDelete}>
              Delete
            </Btn>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
