import { useEffect, useState } from "react";
import {
  getApiAdminClaims,
  putApiAdminClaimsByIdBounds,
  putApiAdminClaimsByIdTrust,
  deleteApiAdminClaimsById,
} from "../../../generated/api/requests";
import { ClaimDto } from "../../apiTypes";
import { useT } from "../../i18n";
import { Button } from "../../../primitives/Button";
import { ClaimEditDialog } from "./ClaimEditDialog";

export function ClaimsPage() {
  const t = useT();
  const [claims, setClaims] = useState<ClaimDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);

  const load = () => {
    getApiAdminClaims({ throwOnError: true })
      .then((r) => setClaims(r.data))
      .catch((e) => setError(String(e)));
  };

  useEffect(() => {
    load();
  }, []);

  const editing = claims?.find((c) => c.id === editingId) ?? null;

  const saveBounds = async (id: string, yMin: number, yMax: number) => {
    await putApiAdminClaimsByIdBounds({ path: { id }, body: { yMin, yMax }, throwOnError: true });
    load();
  };

  const setTrusted = async (id: string, playerName: string, trusted: boolean) => {
    await putApiAdminClaimsByIdTrust({ path: { id }, body: { playerName, trusted }, throwOnError: true });
    load();
  };

  const deleteClaim = async (id: string) => {
    await deleteApiAdminClaimsById({ path: { id }, throwOnError: true });
    setEditingId(null);
    load();
  };

  if (error) return <div className="p-4 text-red-400">{error}</div>;
  if (!claims) return <div className="p-4 text-[#8A99AF]">{t("common.loading")}</div>;

  return (
    <div className="p-4">
      <h1 className="text-xl font-semibold text-white mb-4">{t("page.claims")}</h1>
      <table className="w-full text-sm text-left text-[#8A99AF]">
        <thead>
          <tr className="border-b border-[#2E3A4E]">
            <th className="py-2 pr-4">Owner</th>
            <th className="py-2 pr-4">Chunks</th>
            <th className="py-2 pr-4">Y range</th>
            <th className="py-2 pr-4">Trusted</th>
            <th className="py-2 pr-4"></th>
          </tr>
        </thead>
        <tbody>
          {claims.map((c) => (
            <tr key={c.id} className="border-b border-[#1C2434]">
              <td className="py-2 pr-4 text-white">{c.ownerName}</td>
              <td className="py-2 pr-4">{c.chunks.length}</td>
              <td className="py-2 pr-4">
                {c.yMin}–{c.yMax}
              </td>
              <td className="py-2 pr-4">
                {(c.trustedPlayerNames ?? []).length > 0 ? c.trustedPlayerNames.join(", ") : "—"}
              </td>
              <td className="py-2 pr-4">
                <Button variant="ghost" size="sm" onClick={() => setEditingId(c.id)}>
                  {t("common.edit")}
                </Button>
              </td>
            </tr>
          ))}
          {claims.length === 0 && (
            <tr>
              <td colSpan={5} className="py-6 text-center">
                No land claims.
              </td>
            </tr>
          )}
        </tbody>
      </table>

      {editing && (
        <ClaimEditDialog
          claim={editing}
          onClose={() => setEditingId(null)}
          onSaveBounds={(yMin, yMax) => saveBounds(editing.id, yMin, yMax)}
          onSetTrusted={(playerName, trusted) => setTrusted(editing.id, playerName, trusted)}
          onDelete={() => deleteClaim(editing.id)}
        />
      )}
    </div>
  );
}
