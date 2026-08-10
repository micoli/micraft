import { useT } from "../../i18n";

export function SaveBtn({ saving, saved, onClick }: { saving: boolean; saved: boolean; onClick: () => void }) {
  const t = useT();
  return (
    <button
      onClick={onClick}
      disabled={saving}
      className="px-4 py-1.5 rounded-lg text-sm font-medium bg-[#3C50E0] hover:bg-[#3446c7] text-white transition-colors disabled:opacity-50"
    >
      {saving ? t("common.saving") : saved ? t("common.saved") : t("common.save")}
    </button>
  );
}
