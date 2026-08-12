import { WorldStatsDto } from "../../api";
import { useI18n } from "../../i18n";
import { Icon } from "../../../primitives/Icon";
import { ICONS } from "../../../primitives/icons";
import { Badge } from "../../../primitives/Badge";

export function WorldCard({ world, onRefresh: _onRefresh }: { world: WorldStatsDto; onRefresh: () => void }) {
  const { locale, t } = useI18n();
  const date = new Date(world.createdAt).toLocaleDateString(locale, {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
  return (
    <div
      className={`bg-[#1A222C] rounded-xl border p-5 transition-colors ${
        world.isActive ? "border-[#3C50E0]" : "border-[#2E3A4E]"
      }`}
    >
      <div className="flex items-start justify-between gap-3 mb-4">
        <div className="flex items-center gap-2.5 min-w-0">
          <div
            className={`w-9 h-9 rounded-lg flex items-center justify-center shrink-0 ${
              world.isActive ? "bg-[#3C50E0]/20 text-[#3C50E0]" : "bg-[#2E3A4E] text-[#8A99AF]"
            }`}
          >
            <Icon d={ICONS.world} size={18} />
          </div>
          <div className="min-w-0">
            <p className="text-white font-semibold text-[15px] truncate">{world.name}</p>
            <p className="text-[11px] text-[#8A99AF]">{t("worlds.created", date)}</p>
          </div>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          {world.isActive && (
            <Badge color="bg-[#3C50E0]/20 text-[#3C50E0]">
              <Icon d={ICONS.active} size={10} />
              <span className="ml-1">{t("worlds.active")}</span>
            </Badge>
          )}
          <Badge color="bg-[#2E3A4E] text-[#8A99AF]">{world.generator}</Badge>
        </div>
      </div>
      <div className="grid grid-cols-3 gap-3 p-3 bg-[#0E1726] rounded-lg">
        <div className="text-center">
          <p className="text-[10px] uppercase tracking-widest text-[#8A99AF] mb-1">{t("worlds.seed")}</p>
          <p className="text-white font-mono text-sm font-semibold tabular-nums">{world.seed}</p>
        </div>
        <div className="text-center border-x border-[#2E3A4E]">
          <p className="text-[10px] uppercase tracking-widest text-[#8A99AF] mb-1">{t("worlds.chunks")}</p>
          <p className="text-white font-semibold text-sm tabular-nums">{world.chunkCount.toLocaleString()}</p>
        </div>
        <div className="text-center">
          <p className="text-[10px] uppercase tracking-widest text-[#8A99AF] mb-1">{t("worlds.players")}</p>
          <p className="text-white font-semibold text-sm tabular-nums">{world.playerCount}</p>
        </div>
      </div>
    </div>
  );
}
