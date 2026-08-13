import { useGetApiAdminWorlds } from "../../../generated/api/queries";
import { useT, type TranslationKey } from "../../i18n";
import { CreateWorldForm } from "./CreateWorldForm";
import { WorldCard } from "./WorldCard";

export function WorldsPage() {
  const t = useT();
  const { data: worlds, isError, refetch } = useGetApiAdminWorlds();
  const errorKey: TranslationKey | null = isError ? "worlds.failedToLoad" : null;
  const load = () => void refetch();

  if (errorKey) return <p className="text-red-400 text-sm">{t(errorKey)}</p>;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-[#8A99AF] text-xs">
            {worlds ? t(worlds.length === 1 ? "worlds.countOne" : "worlds.countMany", worlds.length) : "…"}
          </p>
          <p className="text-[11px] text-[#4A5568] mt-0.5">
            {t("worlds.switchHintBefore")} <code className="font-mono">MICRAFT_WORLD_NAME</code>{" "}
            {t("worlds.switchHintAfter")}
          </p>
        </div>
        <CreateWorldForm onCreated={load} />
      </div>

      {worlds === undefined ? (
        <p className="text-[#8A99AF] text-sm animate-pulse">{t("common.loading")}</p>
      ) : worlds.length === 0 ? (
        <p className="text-[#8A99AF] text-sm">{t("worlds.none")}</p>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-4">
          {worlds.map((w) => (
            <WorldCard key={w.name} world={w} onRefresh={load} />
          ))}
        </div>
      )}
    </div>
  );
}
