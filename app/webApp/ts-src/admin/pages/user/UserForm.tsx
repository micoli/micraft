import { UserDto } from "../../api";
import { useState } from "react";
import { useT } from "../../i18n";
import { Field } from "./Field";
import { TextInput } from "./TextInput";
import { Btn } from "./Btn";

export function UserForm({
  initial,
  onSave,
  onClose,
  isNew,
  noauth = false,
}: {
  initial: Partial<UserDto & { password: string }>;
  onSave: (u: UserDto & { password?: string }) => Promise<void>;
  onClose: () => void;
  isNew: boolean;
  noauth?: boolean;
}) {
  const [email, setEmail] = useState(initial.email ?? "");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState(initial.displayName ?? "");
  const [groups, setGroups] = useState((initial.groups ?? []).join(", "));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const t = useT();

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
      setError(e instanceof Error ? e.message : t("common.error"));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-4">
      {isNew && (
        <Field label={t("users.email")}>
          <TextInput value={email} onChange={setEmail} placeholder={t("users.emailPlaceholder")} />
        </Field>
      )}
      {isNew && !noauth && (
        <Field label={t("users.password")}>
          <TextInput value={password} onChange={setPassword} type="password" />
        </Field>
      )}
      {!noauth && (
        <Field label={t("users.displayName")}>
          <TextInput value={displayName} onChange={setDisplayName} placeholder={email} />
        </Field>
      )}
      {!noauth && (
        <Field label={t("users.groupsField")}>
          <TextInput value={groups} onChange={setGroups} placeholder={t("users.groupsPlaceholder")} />
        </Field>
      )}
      {error && <p className="text-red-400 text-xs">{error}</p>}
      <div className="flex gap-2 justify-end pt-1">
        <Btn variant="ghost" onClick={onClose}>
          {t("common.cancel")}
        </Btn>
        <Btn onClick={save} disabled={saving}>
          {saving ? t("common.saving") : t("common.save")}
        </Btn>
      </div>
    </div>
  );
}
