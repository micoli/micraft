import { useState } from "react";
import { GroupDto } from "../../apiTypes";
import { useT } from "../../i18n";
import { Field } from "../user/Field";
import { TextInput } from "../user/TextInput";
import { Button } from "../../../primitives/Button";
import { PermissionsEditor } from "./PermissionsEditor";

export function GroupForm({
  initial,
  isNew,
  onSave,
  onClose,
}: {
  initial: Partial<GroupDto>;
  isNew: boolean;
  onSave: (group: { name: string; permissions: string[] }) => Promise<void>;
  onClose: () => void;
}) {
  const t = useT();
  const [name, setName] = useState(initial.name ?? "");
  const [permissions, setPermissions] = useState<string[]>(initial.permissions ?? []);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setError(null);
    setSaving(true);
    try {
      await onSave({ name: name.trim(), permissions });
      onClose();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : t("common.error"));
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={submit} className="space-y-4">
      {isNew && (
        <Field label={t("rbac.name")}>
          <TextInput value={name} onChange={setName} placeholder={t("rbac.namePlaceholder")} />
        </Field>
      )}
      <Field label={t("rbac.permissions")}>
        <PermissionsEditor value={permissions} onChange={setPermissions} />
      </Field>
      {error && <p className="text-red-400 text-xs">{error}</p>}
      <div className="flex gap-2 justify-end pt-1">
        <Button variant="ghost" onClick={onClose} type="button">
          {t("common.cancel")}
        </Button>
        <Button type="submit" disabled={saving || !name.trim()}>
          {saving ? t("common.saving") : t("common.save")}
        </Button>
      </div>
    </form>
  );
}
