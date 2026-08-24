import { useLayoutEffect, useRef, useState } from "react";
import { postApiAdminRestart, postApiAdminReload } from "../../../generated/api/requests";
import { useGetApiAdminStatus } from "../../../generated/api/queries";
import { useT, type TranslationKey } from "../../i18n";
import { GameTimeSetter } from "./GameTimeSetter";
import { pad2 } from "./utils";
import { StatCard } from "./StatCard";
import { HeapBar } from "./HeapBar";
import { TickChart } from "./TickChart";
import { Card } from "./Card";
import { Row } from "./Row";
import { Svg } from "./Svg";
import { ICONS } from "../../../primitives/icons";

// ── Helpers ───────────────────────────────────────────────────────────────────
function kb(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1_048_576) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1_048_576).toFixed(1)} MB`;
}

export function ticksToTime(ticks: number, ticksPerDay: number): { h: number; m: number } {
  const day = ticks % ticksPerDay;
  const h = Math.floor((day * 24) / ticksPerDay);
  const m = Math.floor(((day * 24 * 60) / ticksPerDay) % 60);
  return { h, m };
}

// ── Page ─────────────────────────────────────────────────────────────────────
export function StatusPage() {
  const t = useT();
  const [restarting, setRestarting] = useState(false);
  const [reloading, setReloading] = useState(false);
  const [reloadMsg, setReloadMsg] = useState<string | null>(null);
  const { data: snap, isError, isFetching, refetch } = useGetApiAdminStatus({}, undefined, { refetchInterval: 5000 });
  const errorKey: TranslationKey | null = isError ? "status.unreachable" : null;
  const indicatorsRef = useRef<HTMLDivElement>(null);
  const [chartHeight, setChartHeight] = useState(90);

  useLayoutEffect(() => {
    const el = indicatorsRef.current;
    if (!el) return;
    const observer = new ResizeObserver(([entry]) => setChartHeight(Math.max(90, entry.contentRect.height)));
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  const restart = async () => {
    if (!confirm(t("status.confirmRestart"))) return;
    setRestarting(true);
    try {
      await postApiAdminRestart();
    } catch {
      /* empty */
    }
    setTimeout(() => setRestarting(false), 4000);
  };

  const reload = async () => {
    setReloading(true);
    setReloadMsg(null);
    try {
      const { data, error } = await postApiAdminReload();
      if (error || !data) throw new Error();
      setReloadMsg(t("status.reloadDone", data.result));
    } catch {
      setReloadMsg(t("status.reloadFailed"));
    }
    setReloading(false);
  };

  if (errorKey) return <p className="text-red-400 text-sm">{t(errorKey)}</p>;
  if (!snap) return <p className="text-[#8A99AF] text-sm animate-pulse">{t("common.loading")}</p>;

  const topNpcs = Object.entries(snap.npcByType)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 6);
  const tpd = snap.ticksPerDay || 72000;
  const { h, m } = ticksToTime(snap.gameTicks, tpd);

  return (
    <div className="space-y-6">
      {/* Top stat row */}
      <div className="grid grid-cols-2 xl:grid-cols-4 gap-4">
        <StatCard
          label={t("status.connectedPlayers")}
          value={snap.connectedPlayers}
          sub={snap.playerNames.join(", ") || t("status.noPlayers")}
          icon={ICONS.users}
          color="bg-blue-500/20 text-blue-400"
        />
        <StatCard
          label={t("status.npcsAlive")}
          value={snap.npcTotal}
          sub={t("status.estimated", kb(snap.npcEstBytes))}
          icon={ICONS.npc}
          color="bg-violet-500/20 text-violet-400"
        />
        <StatCard
          label={t("status.loadedChunks")}
          value={snap.loadedChunks}
          icon={ICONS.chunk}
          color="bg-emerald-500/20 text-emerald-400"
        />
        <StatCard
          label={t("status.gameTime")}
          value={`${pad2(h)}:${pad2(m)}`}
          sub={t("status.tickValue", snap.gameTicks.toLocaleString())}
          icon={ICONS.tick}
          color="bg-amber-500/20 text-amber-400"
        />
      </div>

      {/* Middle row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* NPC breakdown */}
        <Card title={t("status.npcsByType")}>
          {topNpcs.length === 0 ? (
            <p className="text-[#8A99AF] text-sm">{t("status.noneCapitalised")}</p>
          ) : (
            topNpcs.map(([type, count]) => <Row key={type} label={type} value={count} />)
          )}
        </Card>

        {/* World */}
        <Card title={t("status.world")}>
          <Row label={t("status.groundItems")} value={snap.worldItems} />
          <Row label={t("status.activeLiquids")} value={snap.activeLiquids} />
          <Row label={t("status.pendingLiquidTicks")} value={snap.pendingLiquidTicks} />
          <Row label={t("status.growingVegetation")} value={snap.activeVegetation} />
          <div className="pt-3">
            <p className="text-[10px] uppercase tracking-widest font-semibold text-[#8A99AF] mb-1">
              {t("status.setGameTime")}
            </p>
            <GameTimeSetter snap={snap} t={t} />
          </div>
        </Card>

        {/* Network */}
        <Card title={t("status.network")}>
          <Row label={t("status.received")} value={kb(snap.networkBytesIn)} />
          <Row label={t("status.sent")} value={kb(snap.networkBytesOut)} />
        </Card>

        {/* Processor */}
        <Card title={t("status.processor")}>
          <HeapBar used={snap.heapUsedMb} max={snap.heapMaxMb} label={t("status.heap")} />
          <div className="mt-3 space-y-0.5">
            <Row label={t("status.nonHeap")} value={`${snap.nonHeapUsedMb} MB`} />
            <Row label={t("status.processors")} value={snap.processors} />
            <Row label={t("status.processCpu")} value={`${snap.processCpuLoadPct.toFixed(1)}%`} />
            <Row label={t("status.systemCpu")} value={`${snap.systemCpuLoadPct.toFixed(1)}%`} />
            <Row label={t("status.threads")} value={`${snap.threadCount} (peak ${snap.peakThreadCount})`} />
          </div>
        </Card>
      </div>

      {/* CPU breakdown row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <Card title={t("status.tickBreakdown")} className="lg:col-span-2">
          <div className="flex flex-col lg:flex-row gap-4 items-stretch">
            <div className="lg:w-56 shrink-0" ref={indicatorsRef}>
              <Row
                label={t("status.tickDuration")}
                value={t("status.tickDurationValue", snap.avgTickDurationMs.toFixed(2), snap.tickBudgetMs.toString())}
                accent={snap.avgTickDurationMs > snap.tickBudgetMs}
              />
              {snap.tickProfile.length === 0 ? (
                <p className="text-[#8A99AF] text-sm mt-2">{t("status.noneCapitalised")}</p>
              ) : (
                <div className="mt-2 space-y-0.5">
                  {snap.tickProfile.map((p) => (
                    <Row key={p.name} label={p.name} value={`${p.avgMs.toFixed(2)} ms`} />
                  ))}
                </div>
              )}
            </div>
            <div className="flex-1 min-w-0">
              <TickChart phases={snap.tickProfile} budgetMs={snap.tickBudgetMs} chartHeight={chartHeight} />
            </div>
          </div>
        </Card>

        <Card title={t("status.gc")}>
          {snap.gcStats.length === 0 ? (
            <p className="text-[#8A99AF] text-sm">{t("status.noneCapitalised")}</p>
          ) : (
            snap.gcStats.map((g) => (
              <Row
                key={g.name}
                label={g.name}
                value={t("status.gcCountTime", g.collectionCount.toString(), g.collectionTimeMs.toString())}
              />
            ))
          )}
        </Card>
      </div>

      {/* Footer row */}
      <div className="flex items-center justify-between text-[11px] text-[#8A99AF]">
        <span>
          {t("status.autoRefresh")}
          {reloadMsg && <span className="ml-3">{reloadMsg}</span>}
        </span>
        <div className="flex items-center gap-2">
          <button
            onClick={() => refetch()}
            disabled={isFetching}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-[#2E3A4E] hover:bg-[#2E3A4E] text-[#8A99AF] hover:text-white transition-colors disabled:opacity-50"
          >
            <Svg d={ICONS.restart} size={13} className={isFetching ? "animate-spin" : undefined} />
            {t("status.refresh")}
          </button>
          <button
            onClick={reload}
            disabled={reloading}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-[#2E3A4E] hover:bg-[#2E3A4E] text-[#8A99AF] hover:text-white transition-colors disabled:opacity-50"
          >
            <Svg d={ICONS.restart} size={13} />
            {reloading ? t("status.reloading") : t("status.reloadConfig")}
          </button>
          <button
            onClick={restart}
            disabled={restarting}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-[#2E3A4E] hover:bg-[#2E3A4E] text-[#8A99AF] hover:text-white transition-colors disabled:opacity-50"
          >
            <Svg d={ICONS.restart} size={13} />
            {restarting ? t("status.restarting") : t("status.restartServer")}
          </button>
        </div>
      </div>
    </div>
  );
}
