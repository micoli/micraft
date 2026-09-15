import { useEffect, useState } from "react";
import { useT } from "../../i18n";
import { TextInput } from "../user/TextInput";
import { Button } from "../../../primitives/Button";
import { getApiAdminPermissions } from "../../../generated/api/requests";

export function PermissionsEditor({ value, onChange }: { value: string[]; onChange: (permissions: string[]) => void }) {
  const t = useT();
  const [known, setKnown] = useState<string[]>([]);
  const [customDraft, setCustomDraft] = useState("");
  const [filter, setFilter] = useState("");

  useEffect(() => {
    void getApiAdminPermissions().then(({ data }) => setKnown(data ?? []));
  }, []);

  const toggle = (perm: string) => onChange(value.includes(perm) ? value.filter((p) => p !== perm) : [...value, perm]);

  const addCustom = () => {
    const perm = customDraft.trim();
    if (!perm || value.includes(perm)) return;
    onChange([...value, perm]);
    setCustomDraft("");
  };

  // Already-assigned permissions not in the discovered list (custom, or from code the discovery
  // pass doesn't cover) still get a checkbox — unchecking them removes them, same as any other.
  const allPermissions = [...known, ...value.filter((p) => !known.includes(p))].sort();
  const filteredPermissions = allPermissions.filter((p) => p.toLowerCase().includes(filter.toLowerCase()));

  return (
    <div className="space-y-3">
      <TextInput value={filter} onChange={setFilter} placeholder={t("rbac.filterPermissions")} />
      <div className="max-h-[55vh] overflow-y-auto space-y-1 rounded-lg border border-[#2E3A4E] p-2">
        {filteredPermissions.length === 0 && (
          <p className="text-xs text-[#4A5568] px-1 py-1">
            {allPermissions.length === 0 ? t("rbac.noPermissions") : t("rbac.noPermissionsMatch")}
          </p>
        )}
        {filteredPermissions.map((perm) => (
          <label
            key={perm}
            className="flex items-center gap-2 px-1 py-1 rounded hover:bg-[#1F2D3D] cursor-pointer text-sm text-white"
          >
            <input
              type="checkbox"
              checked={value.includes(perm)}
              onChange={() => toggle(perm)}
              className="accent-[#3C50E0]"
            />
            {perm}
          </label>
        ))}
      </div>
      <div className="flex gap-2">
        <TextInput
          value={customDraft}
          onChange={setCustomDraft}
          placeholder={t("rbac.permissionPlaceholder")}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              addCustom();
            }
          }}
        />
        <Button type="button" variant="ghost" onClick={addCustom}>
          {t("rbac.addPermission")}
        </Button>
      </div>
    </div>
  );
}
