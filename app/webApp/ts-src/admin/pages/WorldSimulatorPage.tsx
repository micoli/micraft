import { useEffect, useMemo, useState } from "react";
import { ArenaCanvasRenderer } from "../simulator/ArenaCanvasRenderer";
import { ArenaControls } from "../simulator/ArenaControls";
import { ArenaSvgRenderer } from "../simulator/ArenaSvgRenderer";
import { Card } from "../simulator/Card";
import { useT, type Translate, type TranslationKey } from "../i18n";
import {
  LAYER_KEYS,
  LAYER_LABEL_KEYS,
  loadLayers,
  loadRenderer,
  saveLayers,
  saveRenderer,
  useArenaCamera,
  type Layers,
  type RendererKind,
} from "../simulator/arenaView";
import { EventLogPanel } from "../simulator/EventLogPanel";
import { MetricsPanel } from "../simulator/MetricsPanel";
import { SimPlayerPad } from "../simulator/SimPlayerPad";
import { SimulationList } from "../simulator/SimulationList";
import { OverridesEditor, TuningEditor } from "../simulator/RulesEditor";
import { Timeline } from "../simulator/Timeline";
import {
  DEFAULT_POPULATION_CAP,
  frameCapFor,
  npcColor,
  type NpcTuning,
  type SimSpawn,
  type SimulationConfig,
} from "../simulator/types";
import { useSimulation } from "../simulator/useSimulation";

type Tab = "charts" | "log" | "npc" | "rules" | "manager";

// insertion order drives the tab strip; charts first, and it is the tab the page opens on
const TAB_LABEL_KEYS: Record<Tab, TranslationKey> = {
  charts: "sim.tab.charts",
  log: "sim.tab.log",
  npc: "sim.tab.npc",
  rules: "sim.tab.rules",
  manager: "sim.tab.manager",
};

const DEFAULT_ARENA = { halfSize: 100, groundY: 7, wallHeight: 4 };

function baseConfig(tuning: NpcTuning): SimulationConfig {
  return {
    ...DEFAULT_ARENA,
    ticksPerSecond: 200,
    seed: 42,
    zoneLevel: 5,
    maxNpcs: 0,
    populationCap: DEFAULT_POPULATION_CAP,
    maxNpcsPerFrame: frameCapFor(DEFAULT_POPULATION_CAP),
    vegetationDensity: 0.08,
    gameDayDurationSeconds: 60,
    npcTuning: tuning,
    npcDefinitionOverrides: {},
    initialSpawns: [],
    // an identifier the server echoes back, not display copy: it stays put whatever the UI locale is
    players: [{ name: "observer", x: 0, z: 0 }],
    autoSpawnEnabled: true,
  };
}

// ── Small shared bits ─────────────────────────────────────────────────────────

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="mb-1.5 flex items-center gap-2 text-[11px] text-[#8A99AF]">
      <span className="flex-1">{label}</span>
      {children}
    </label>
  );
}

const DISABLED = " disabled:cursor-not-allowed disabled:opacity-40";

const numberInput =
  "w-24 rounded border border-[#2E3A4E] bg-[#0E1726] px-2 py-1 text-right text-[11px] text-white" + DISABLED;

const smallNumberInput =
  "w-16 rounded border border-[#2E3A4E] bg-[#0E1726] px-1.5 py-0.5 text-right text-[11px] text-white" + DISABLED;

// ── Page ──────────────────────────────────────────────────────────────────────

export function WorldSimulatorPage() {
  const t = useT();
  const sim = useSimulation();
  const [tab, setTab] = useState<Tab>("charts");
  const [layers, setLayers] = useState<Layers>(() => loadLayers());
  const [renderer, setRenderer] = useState<RendererKind>(() => loadRenderer());
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const [halfSize, setHalfSize] = useState(DEFAULT_ARENA.halfSize);
  const [seed, setSeed] = useState(42);
  const [dayDuration, setDayDuration] = useState(60);
  const [zoneLevel, setZoneLevel] = useState(5);
  const [autoSpawn, setAutoSpawn] = useState(true);
  const [populationCap, setPopulationCap] = useState(DEFAULT_POPULATION_CAP);
  const [vegetationDensity, setVegetationDensity] = useState(0.08);
  const [simulationName, setSimulationName] = useState("");
  const [spawns, setSpawns] = useState<SimSpawn[]>([]);
  const [tuning, setTuning] = useState<NpcTuning | null>(null);
  const [spawnType, setSpawnType] = useState("");
  const [spawnCount, setSpawnCount] = useState(1);
  const [liveSpawnType, setLiveSpawnType] = useState("");
  const [liveSpawnCount, setLiveSpawnCount] = useState(1);

  const npcTypes = sim.defaults?.npcTypes ?? [];
  // The server holds its config as an immutable value: these only ever apply to a new arena, so
  // leaving them editable while one is attached just invites edits that go nowhere.
  const locked = sim.simulationId !== null;
  const [arenaFolded, setArenaFolded] = useState(false);
  const [spawnsFolded, setSpawnsFolded] = useState(false);

  // Folded once locked: a column of greyed-out inputs is noise, and the room goes to the panels that
  // still do something. Both stay toggles, so the settings behind them remain readable.
  useEffect(() => {
    setArenaFolded(locked);
    setSpawnsFolded(locked);
  }, [locked]);

  useEffect(() => {
    if (sim.defaults && tuning == null) setTuning(sim.defaults.tuning);
    if (sim.defaults && sim.defaults.npcTypes.length > 0) {
      const first = sim.defaults.npcTypes[0];
      if (spawnType === "") setSpawnType(first);
      if (liveSpawnType === "") setLiveSpawnType(first);
    }
  }, [sim.defaults, tuning, spawnType, liveSpawnType]);

  // Shown in the folded header, so folding the panel does not hide what the arena will start with.
  const spawnSummary =
    spawns.length === 0
      ? t("sim.page.spawnSummaryNone")
      : t(
          "sim.page.spawnSummary",
          spawns.length,
          spawns.reduce((sum, spawn) => sum + spawn.count, 0),
        );

  useEffect(() => saveLayers(layers), [layers]);

  useEffect(() => saveRenderer(renderer), [renderer]);

  useEffect(() => {
    if (sim.detail) setSelectedId(sim.detail.npc.id);
  }, [sim.detail]);

  const arena = sim.arena ?? null;
  const view = useArenaCamera(arena?.halfSize ?? DEFAULT_ARENA.halfSize);
  const bounds = view.visibleBounds;

  // Sending a few thousand NPCs 20 times a second is what makes the page unusable, so the server
  // only sends what is on screen.
  const { running: simRunning, setViewport } = sim;
  useEffect(() => {
    if (!simRunning) return;
    setViewport(bounds);
  }, [simRunning, setViewport, bounds]);
  const byType = useMemo(() => {
    const counts = new Map<string, number>();
    sim.npcs.forEach((n) => counts.set(n.type, (counts.get(n.type) ?? 0) + 1));
    return [...counts.entries()].sort((a, b) => b[1] - a[1]);
  }, [sim.npcs]);

  const start = () => {
    if (!tuning) return;
    sim.init(
      {
        ...baseConfig(tuning),
        halfSize,
        seed,
        zoneLevel,
        gameDayDurationSeconds: dayDuration,
        npcTuning: { ...tuning, gameDayDurationSeconds: dayDuration },
        initialSpawns: spawns,
        autoSpawnEnabled: autoSpawn,
        populationCap,
        maxNpcsPerFrame: frameCapFor(populationCap),
        vegetationDensity,
      },
      simulationName,
    );
  };

  return (
    <div className="-m-6 flex h-[calc(100vh-4rem)] flex-col">
      {/* toolbar */}
      <div className="flex shrink-0 items-center gap-2 border-b border-[#2E3A4E] bg-[#1A222C] px-4 py-2">
        <span className={"inline-block h-2 w-2 rounded-full " + (sim.connected ? "bg-emerald-400" : "bg-red-400")} />
        <span className="text-[11px] text-[#8A99AF]">
          {t(sim.connected ? "sim.page.connected" : "sim.page.disconnected")}
        </span>
        <button
          type="button"
          onClick={start}
          disabled={!sim.connected || !tuning}
          className="ml-3 rounded bg-[#3C50E0] px-3 py-1 text-[11px] font-medium text-white hover:bg-[#3C50E0]/80 disabled:opacity-40"
        >
          {t("sim.page.start")}
        </button>
        <button
          type="button"
          onClick={sim.restart}
          disabled={!sim.running}
          className="rounded bg-[#2E3A4E] px-3 py-1 text-[11px] text-[#C7D2FE] hover:bg-[#3C50E0]/60 disabled:opacity-40"
        >
          {t("sim.page.quickRestart")}
        </button>
        <button
          type="button"
          onClick={sim.detach}
          disabled={!sim.simulationId}
          title={t("sim.page.detachTitle")}
          className="rounded bg-[#2E3A4E] px-3 py-1 text-[11px] text-[#C7D2FE] hover:bg-[#3C50E0]/60 disabled:opacity-40"
        >
          {t("sim.page.detach")}
        </button>
        <button
          type="button"
          onClick={sim.stop}
          disabled={!sim.simulationId}
          title={t("sim.page.closeTitle")}
          className="rounded bg-[#2E3A4E] px-3 py-1 text-[11px] text-[#C7D2FE] hover:bg-red-500/60 disabled:opacity-40"
        >
          {t("sim.page.close")}
        </button>
        {sim.error && <span className="ml-3 text-[11px] text-red-300">{sim.error}</span>}
        {sim.truncated && (
          <span className="ml-3 rounded bg-[#FACC15]/15 px-2 py-0.5 text-[11px] text-[#FACC15]">
            {t("sim.page.truncated")}
          </span>
        )}
        <span className="ml-auto text-[11px] text-[#8A99AF]">
          {t("sim.page.shownNpcs", sim.npcs.length, sim.stats.npcCount)}
          {sim.stats.populationCap > 0 ? t("sim.page.populationCap", sim.stats.populationCap) : ""} ·{" "}
          {t("sim.page.playersAndFood", sim.players.length, sim.stats.foodBlocks)}
          {sim.stats.regrowingCells > 0 ? t("sim.page.regrowing", sim.stats.regrowingCells) : ""}
        </span>
      </div>

      <div className="flex min-h-0 flex-1">
        {/* left: setup */}
        <div className="w-72 shrink-0 space-y-3 overflow-auto border-r border-[#2E3A4E] p-3">
          <SimulationList
            simulations={sim.simulations}
            attachedId={sim.simulationId}
            onAttach={sim.attach}
            onRefresh={sim.refreshSimulations}
          />

          <Card
            title={t("sim.page.arena")}
            collapsed={arenaFolded}
            onCollapsed={setArenaFolded}
            summary={t("sim.page.arenaSummary", halfSize * 2, halfSize * 2, seed, dayDuration)}
          >
            <Row label={t("sim.page.nameOptional")}>
              <input
                type="text"
                value={simulationName}
                onChange={(e) => setSimulationName(e.target.value)}
                placeholder={t("sim.page.auto")}
                disabled={locked}
                className={
                  "w-32 rounded border border-[#2E3A4E] bg-[#0E1726] px-2 py-1 text-[11px] text-white" + DISABLED
                }
              />
            </Row>
            <Row label={t("sim.page.halfSize")}>
              <input
                type="number"
                step={10}
                min={10}
                value={halfSize}
                onChange={(e) => setHalfSize(Number(e.target.value))}
                disabled={locked}
                className={numberInput}
              />
            </Row>
            <Row label={t("sim.page.seed")}>
              <input
                type="number"
                value={seed}
                onChange={(e) => setSeed(Number(e.target.value))}
                disabled={locked}
                className={numberInput}
              />
            </Row>
            <Row label={t("sim.page.dayDuration")}>
              <input
                type="number"
                step={5}
                min={1}
                value={dayDuration}
                onChange={(e) => setDayDuration(Number(e.target.value))}
                disabled={locked}
                className={numberInput}
              />
            </Row>
            <Row label={t("sim.page.zoneLevel")}>
              <input
                type="number"
                min={1}
                value={zoneLevel}
                onChange={(e) => setZoneLevel(Number(e.target.value))}
                disabled={locked}
                className={numberInput}
              />
            </Row>
            <Row label={t("sim.page.populationCapLabel")}>
              <input
                type="number"
                step={100}
                min={0}
                value={populationCap}
                onChange={(e) => setPopulationCap(Number(e.target.value))}
                disabled={locked}
                className={numberInput}
              />
            </Row>
            <Row label={t("sim.page.vegetationDensity")}>
              <input
                type="number"
                step={0.01}
                min={0}
                max={1}
                value={vegetationDensity}
                onChange={(e) => setVegetationDensity(Number(e.target.value))}
                disabled={locked}
                className={numberInput}
              />
            </Row>
            <Row label={t("sim.page.autoSpawner")}>
              <input
                type="checkbox"
                checked={autoSpawn}
                onChange={(e) => setAutoSpawn(e.target.checked)}
                disabled={locked}
                className={"accent-[#3C50E0]" + DISABLED}
              />
            </Row>
            <p className="text-[10px] text-[#4A5568]">
              {t("sim.page.capHint")} {t(locked ? "sim.page.lockedHint" : "sim.page.unlockedHint")}
            </p>
          </Card>

          <Card
            title={t("sim.page.initialPopulation")}
            collapsed={spawnsFolded}
            onCollapsed={setSpawnsFolded}
            summary={spawnSummary}
          >
            {spawns.length === 0 && <p className="mb-2 text-[11px] text-[#4A5568]">{t("sim.page.noSpawns")}</p>}
            {spawns.map((spawn, index) => (
              <div key={`${spawn.type}-${index}`} className="mb-1.5 flex items-center gap-1.5">
                <span className="flex-1 text-[11px] text-white">{spawn.type}</span>
                <input
                  type="number"
                  min={1}
                  value={spawn.count}
                  onChange={(e) =>
                    setSpawns((s) => s.map((v, i) => (i === index ? { ...v, count: Number(e.target.value) } : v)))
                  }
                  disabled={locked}
                  className={smallNumberInput}
                />
                <button
                  type="button"
                  disabled={locked}
                  onClick={() => setSpawns((s) => s.filter((_, i) => i !== index))}
                  className={"rounded bg-[#2E3A4E] px-1.5 text-[11px] text-[#8A99AF] hover:text-red-300" + DISABLED}
                >
                  ×
                </button>
              </div>
            ))}
            <div className="mt-2 flex items-center gap-1.5">
              <select
                value={spawnType}
                onChange={(e) => setSpawnType(e.target.value)}
                disabled={locked}
                className={
                  "flex-1 rounded border border-[#2E3A4E] bg-[#0E1726] px-1.5 py-1 text-[11px] text-white" + DISABLED
                }
              >
                {npcTypes.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
              <input
                type="number"
                min={1}
                value={spawnCount}
                onChange={(e) => setSpawnCount(Number(e.target.value))}
                disabled={locked}
                className={smallNumberInput}
              />
              <button
                type="button"
                disabled={locked || !spawnType}
                onClick={() => setSpawns((s) => [...s, { type: spawnType, count: spawnCount, level: null }])}
                className={"rounded bg-[#2E3A4E] px-2 py-1 text-[11px] text-[#C7D2FE] hover:bg-[#3C50E0]/60" + DISABLED}
              >
                +
              </button>
            </div>
          </Card>

          {/* Its own card, not folded with the initial population: this acts on a running arena, and
              hiding it whenever one is attached would bury the only control that still does anything. */}
          <Card title={t("sim.page.instantSpawn")}>
            <div className="flex items-center gap-1.5">
              <select
                value={liveSpawnType}
                onChange={(e) => setLiveSpawnType(e.target.value)}
                className="flex-1 rounded border border-[#2E3A4E] bg-[#0E1726] px-1.5 py-1 text-[11px] text-white"
              >
                {npcTypes.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
              <input
                type="number"
                min={1}
                value={liveSpawnCount}
                onChange={(e) => setLiveSpawnCount(Number(e.target.value))}
                className="w-16 rounded border border-[#2E3A4E] bg-[#0E1726] px-1.5 py-0.5 text-right text-[11px] text-white"
              />
            </div>
            <button
              type="button"
              disabled={!sim.running || !liveSpawnType}
              onClick={() => sim.spawn(liveSpawnType, 0, 0, liveSpawnCount, null)}
              className="mt-2 w-full rounded bg-[#2E3A4E] px-2 py-1 text-[11px] text-[#C7D2FE] hover:bg-[#3C50E0]/60 disabled:opacity-40"
            >
              {t("sim.page.spawnNow")}
            </button>
          </Card>

          <Card title={t("sim.page.layers")}>
            {LAYER_KEYS.map((key) => (
              <Row key={key} label={t(LAYER_LABEL_KEYS[key])}>
                <input
                  type="checkbox"
                  checked={layers[key]}
                  onChange={(e) => setLayers((s) => ({ ...s, [key]: e.target.checked }))}
                  className="accent-[#3C50E0]"
                />
              </Row>
            ))}
          </Card>
        </div>

        {/* center: arena + timeline */}
        <div className="flex min-w-0 flex-1 flex-col">
          <div className="relative min-h-0 flex-1">
            {arena && (
              <ArenaControls renderer={renderer} onRenderer={setRenderer} onZoom={view.zoomBy} onFitAll={view.fitAll} />
            )}
            {arena ? (
              (() => {
                const Renderer = renderer === "canvas" ? ArenaCanvasRenderer : ArenaSvgRenderer;
                return (
                  <Renderer
                    arena={arena}
                    food={sim.food}
                    npcs={sim.npcs}
                    players={sim.players}
                    layers={layers}
                    selectedId={selectedId}
                    view={view}
                    onSelect={(id) => {
                      setSelectedId(id);
                      setTab("npc");
                      sim.inspect(id);
                    }}
                  />
                );
              })()
            ) : (
              <div className="flex h-full items-center justify-center text-[12px] text-[#4A5568]">
                {t("sim.page.noSimulation")}
              </div>
            )}
          </div>
          {/* timeline and the player pad share one row: both are things you use while it runs */}
          <div className="flex shrink-0 items-start gap-3 border-t border-[#2E3A4E] p-3">
            <div className="min-w-0 flex-1">
              <Timeline stats={sim.stats} disabled={!sim.running} onSpeed={sim.setSpeed} onStep={sim.step} />
            </div>
            <SimPlayerPad players={sim.players} onInput={sim.playerInput} />
          </div>
        </div>

        {/* right: tabs */}
        <div className="flex w-[26rem] shrink-0 flex-col border-l border-[#2E3A4E]">
          <div className="flex shrink-0 gap-1 border-b border-[#2E3A4E] bg-[#1A222C] px-2 py-1.5">
            {(Object.keys(TAB_LABEL_KEYS) as Tab[]).map((key) => (
              <button
                key={key}
                type="button"
                onClick={() => setTab(key)}
                className={
                  "rounded px-2.5 py-1 text-[11px] font-medium transition-colors " +
                  (tab === key ? "bg-[#3C50E0] text-white" : "text-[#8A99AF] hover:bg-[#2E3A4E] hover:text-white")
                }
              >
                {t(TAB_LABEL_KEYS[key])}
              </button>
            ))}
          </div>

          <div className="min-h-0 flex-1 overflow-hidden p-3">
            {tab === "charts" && <MetricsPanel buckets={sim.metrics} bucketGameDays={sim.bucketGameDays} />}

            {tab === "log" && (
              <EventLogPanel
                events={sim.events}
                onSelect={(id) => {
                  setSelectedId(id);
                  sim.inspect(id);
                  setTab("npc");
                }}
              />
            )}

            {tab === "npc" && <NpcDetailPanel sim={sim} />}

            {tab === "rules" && tuning && (
              <TuningEditor
                value={tuning}
                base={sim.defaults?.tuning ?? null}
                onChange={setTuning}
                onApply={() => sim.applyTuning(tuning)}
              />
            )}

            {tab === "manager" && (
              <div className="flex h-full flex-col gap-3">
                <div>
                  <p className="mb-1.5 text-[10px] font-semibold uppercase tracking-widest text-[#8A99AF]">
                    {t("sim.page.population")}
                  </p>
                  {byType.length === 0 && <p className="text-[11px] text-[#4A5568]">{t("sim.page.populationEmpty")}</p>}
                  {byType.map(([type, count]) => (
                    <div key={type} className="flex items-center gap-2 py-0.5 text-[11px]">
                      <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ background: npcColor(type) }} />
                      <span className="flex-1 text-white">{type}</span>
                      <span className="text-[#8A99AF]">{count}</span>
                    </div>
                  ))}
                </div>
                <div className="min-h-0 flex-1">
                  <OverridesEditor npcTypes={npcTypes} onApply={sim.applyOverrides} />
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

// ── NPC detail ────────────────────────────────────────────────────────────────

function NpcDetailPanel({ sim }: { sim: ReturnType<typeof useSimulation> }) {
  const t: Translate = useT();
  const detail = sim.detail;
  if (!detail) {
    return <p className="text-[11px] text-[#4A5568]">{t("sim.detail.hint")}</p>;
  }
  const npc = detail.npc;
  const rows: [string, string][] = [
    [t("sim.detail.type"), npc.type],
    [t("sim.detail.level"), t("sim.detail.levelValue", npc.level, detail.xp)],
    [t("sim.detail.hitPoints"), `${npc.currentHp}/${npc.maxHp}`],
    [t("sim.detail.mana"), `${detail.currentMana}/${detail.maxMana}`],
    [t("sim.detail.behavior"), detail.behaviorKey],
    [t("sim.detail.aggro"), t("sim.detail.aggroValue", detail.aggroMode, detail.aggroRange)],
    [t("sim.detail.aggroTarget"), npc.aggroTargetId ?? "—"],
    [t("sim.detail.class"), detail.characterClass],
    [t("sim.detail.speedRadius"), `${detail.wanderSpeed} / ${detail.wanderRadius}`],
    [t("sim.detail.size"), `${detail.width} × ${detail.height}`],
    [t("sim.detail.wanderPhase"), detail.wanderPhase],
    [t("sim.detail.position"), `${npc.x.toFixed(2)}, ${npc.y.toFixed(2)}, ${npc.z.toFixed(2)}`],
    [t("sim.detail.spawnPoint"), `${detail.spawnX.toFixed(1)}, ${detail.spawnZ.toFixed(1)}`],
    [t("sim.detail.attacks"), detail.attacks.join(", ") || "—"],
    [t("sim.detail.spells"), detail.spells.join(", ") || "—"],
    [t("sim.detail.activeEffects"), detail.activeEffects.join(", ") || "—"],
    [t("sim.detail.diet"), detail.diet ?? "—"],
    [t("sim.detail.gender"), npc.gender ?? "—"],
    [t("sim.detail.ageDays"), npc.ageGameDays != null ? npc.ageGameDays.toFixed(2) : "—"],
    [t("sim.detail.hunger"), npc.hunger != null ? `${Math.round(npc.hunger * 100)}%` : "—"],
    [
      t("sim.detail.gestation"),
      npc.gestationRemainingDays != null ? t("sim.detail.gestationValue", npc.gestationRemainingDays.toFixed(2)) : "—",
    ],
    [t("sim.detail.preyTarget"), detail.preyTargetId ?? "—"],
    [t("sim.detail.mateTarget"), detail.mateTargetId ?? "—"],
    [t("sim.detail.parents"), detail.parentIds.length ? detail.parentIds.map((p) => p.slice(0, 8)).join(", ") : "—"],
  ];

  return (
    <div className="flex h-full flex-col">
      <div className="mb-2 flex items-center gap-2">
        <span className="inline-block h-3 w-3 rounded-full" style={{ background: npcColor(npc.type) }} />
        <span className="flex-1 truncate text-[12px] font-semibold text-white">{npc.name}</span>
        <button
          type="button"
          onClick={() => sim.inspect(npc.id)}
          className="rounded bg-[#2E3A4E] px-2 py-0.5 text-[10px] text-[#C7D2FE] hover:bg-[#3C50E0]/60"
        >
          {t("common.refresh")}
        </button>
      </div>
      <div className="flex-1 overflow-auto rounded border border-[#2E3A4E] bg-[#0E1726] p-2">
        {rows.map(([label, value]) => (
          <div key={label} className="flex gap-2 border-b border-[#1A222C] py-1 text-[11px] last:border-0">
            <span className="w-40 shrink-0 text-[#8A99AF]">{label}</span>
            <span className="break-all text-white">{value}</span>
          </div>
        ))}
        {detail.baseStats && (
          <div className="mt-2 text-[11px]">
            <p className="mb-1 text-[#8A99AF]">{t("sim.detail.baseStats")}</p>
            <div className="flex flex-wrap gap-2">
              {Object.entries(detail.baseStats).map(([stat, value]) => (
                <span key={stat} className="rounded bg-[#1A222C] px-1.5 py-0.5 text-white">
                  {stat} {value}
                </span>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
