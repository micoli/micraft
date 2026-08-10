import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useT } from "../i18n";
import { mergeBuckets, replaceBuckets } from "./metrics/metrics";
import {
  EVENT_HISTORY,
  type SimMetricBucket,
  type SimMetrics,
  type NpcTuning,
  type SimArena,
  type SimEvent,
  type SimMessage,
  type SimNpc,
  type SimNpcDetail,
  type SimPlayer,
  type SimStats,
  type SimViewport,
  type SimulationConfig,
  type SimulationDefaults,
  type SimulationInfo,
} from "./types";

/** How often the event log is refreshed, whatever the arena's tick rate. */
const EVENT_PUBLISH_INTERVAL_MS = 500;

/**
 * Max rate at which WS frame data is flushed into React state. The arena sends frames far faster
 * than the eye can track; throttling the snapshot publish cuts React re-renders (and the cyclic
 * fiber garbage the cycle collector has to clean up) without changing what the user sees.
 */
const SNAPSHOT_INTERVAL_MS = 50; // 20 fps

/** Matches SimMetrics.DEFAULT_BUCKET_GAME_DAYS; only used before the first payload arrives. */
const DEFAULT_BUCKET_GAME_DAYS = 0.25;

const EMPTY_STATS: SimStats = {
  tick: 0,
  gameDay: 0,
  configuredTps: 0,
  realTps: 0,
  npcCount: 0,
  paused: true,
  foodBlocks: 0,
  regrowingCells: 0,
  populationCap: 0,
};

interface Snapshot {
  running: boolean;
  truncated: boolean;
  /** Grazing food as flat [x, z, isFlower] triples. */
  food: number[];
  arena: SimArena | null;
  config: SimulationConfig | null;
  npcs: SimNpc[];
  players: SimPlayer[];
  stats: SimStats;
}

const EMPTY_SNAPSHOT: Snapshot = {
  running: false,
  truncated: false,
  food: [],
  arena: null,
  config: null,
  npcs: [],
  players: [],
  stats: EMPTY_STATS,
};

export interface SimulationState {
  connected: boolean;
  running: boolean;
  arena: SimArena | null;
  config: SimulationConfig | null;
  npcs: SimNpc[];
  players: SimPlayer[];
  stats: SimStats;
  events: SimEvent[];
  /** Bucketed history behind the charts, oldest first. */
  metrics: SimMetricBucket[];
  /** Width of one metric bucket, in game days. */
  bucketGameDays: number;
  /** Grazing food as flat [x, z, isFlower] triples; only re-sent when it changes. */
  food: number[];
  /** The server left NPCs out of the last frame to keep it small. */
  truncated: boolean;
  /** Arenas currently running on the server, whoever started them. */
  simulations: SimulationInfo[];
  /** Arena this page is watching, if any. */
  simulationId: string | null;
  detail: SimNpcDetail | null;
  defaults: SimulationDefaults | null;
  error: string | null;
  init: (config: SimulationConfig, name?: string) => void;
  /** Close an arena for everyone; defaults to the attached one. */
  stop: (simulationId?: string) => void;
  /** Rebuild an arena from its own configEditor; defaults to the attached one. */
  restart: (simulationId?: string) => void;
  setSpeed: (ticksPerSecond: number) => void;
  step: (count: number) => void;
  inspect: (npcId: string) => void;
  clearDetail: () => void;
  spawn: (type: string, x: number, z: number, count: number, level?: number | null) => void;
  applyTuning: (tuning: NpcTuning) => void;
  applyOverrides: (overrides: Record<string, unknown>) => void;
  reloadEntityDefs: () => void;
  playerInput: (name: string, dx: number, dz: number, yaw: number, jump: boolean) => void;
  /** Tell the server which world rectangle is on screen, so it only sends what is visible. */
  setViewport: (viewport: SimViewport | null) => void;
  /** Watch an arena someone else started. */
  attach: (simulationId: string) => void;
  /** Stop watching without stopping the arena. */
  detach: () => void;
  refreshSimulations: () => void;
}

function wsUrl(): string {
  const proto = window.location.protocol === "https:" ? "wss:" : "ws:";
  const token = sessionStorage.getItem("micraft-auth-token");
  const query = token ? `?token=${encodeURIComponent(token)}` : "";
  return `${proto}//${window.location.host}/api/admin/ws/simulation${query}`;
}

/**
 * Owns the simulation websocket. Frames arrive at a fixed rate whatever the simulation speed is;
 * they land in refs and a requestAnimationFrame tick turns them into a single re-render, the same
 * approach the world map uses.
 */
export function useSimulation(): SimulationState {
  const socketRef = useRef<WebSocket | null>(null);
  const npcsRef = useRef<SimNpc[]>([]);
  const playersRef = useRef<SimPlayer[]>([]);
  const statsRef = useRef<SimStats>(EMPTY_STATS);
  const eventsRef = useRef<SimEvent[]>([]);
  const arenaRef = useRef<SimArena | null>(null);
  const configRef = useRef<SimulationConfig | null>(null);
  const runningRef = useRef(false);
  const truncatedRef = useRef(false);
  const foodRef = useRef<number[]>([]);
  const snapshotDirtyRef = useRef(false);
  const viewportRef = useRef<string>("");
  /** Bumped on every batch of incoming events; the log publisher compares it to what it showed. */
  const eventRevisionRef = useRef(0);

  // Frames land in the refs above; a fixed-interval timer publishes them into state. Reading a ref
  // during render is both a lint error and a correctness trap; the published snapshot is what
  // components consume.
  const [snapshot, setSnapshot] = useState<Snapshot>(EMPTY_SNAPSHOT);
  const [connected, setConnected] = useState(false);
  const [detail, setDetail] = useState<SimNpcDetail | null>(null);
  const [defaults, setDefaults] = useState<SimulationDefaults | null>(null);
  const [error, setError] = useState<string | null>(null);
  /**
   * The log is published on its own 500 ms beat, not with the frames. A fast arena emits events far
   * quicker than anyone can read them, and re-rendering 300 rows at frame rate is wasted work.
   */
  const [events, setEvents] = useState<SimEvent[]>([]);
  /**
   * Published as plain state rather than through the rAF batch: the server pushes buckets about once
   * a second, which is already the cadence the charts want.
   */
  const [metrics, setMetrics] = useState<SimMetrics>({
    bucketGameDays: DEFAULT_BUCKET_GAME_DAYS,
    buckets: [],
  });
  const [simulations, setSimulations] = useState<SimulationInfo[]>([]);
  const [simulationId, setSimulationId] = useState<string | null>(null);

  // Held in a ref rather than listed as an effect dependency: the socket effect must not tear down
  // and reconnect the arena just because the operator switched the UI language.
  const t = useT();
  const tRef = useRef(t);
  useEffect(() => {
    tRef.current = t;
  }, [t]);

  const publish = useCallback(() => {
    snapshotDirtyRef.current = true;
  }, []);

  // Flush snapshot state on a fixed cadence rather than per WS frame. Frames can arrive dozens of
  // times a second; publishing each one into React state churns the fiber tree at the same rate and
  // fills the cycle collector with short-lived cyclic objects (parent→child→return pointers), which
  // is what causes the UI to freeze. 20 fps is plenty for a simulation view.
  useEffect(() => {
    const timer = setInterval(() => {
      if (!snapshotDirtyRef.current) return;
      snapshotDirtyRef.current = false;
      setSnapshot({
        running: runningRef.current,
        truncated: truncatedRef.current,
        food: foodRef.current,
        arena: arenaRef.current,
        config: configRef.current,
        npcs: npcsRef.current,
        players: playersRef.current,
        stats: statsRef.current,
      });
    }, SNAPSHOT_INTERVAL_MS);
    return () => clearInterval(timer);
  }, []);

  const pushEvents = useCallback((incoming: SimEvent[]) => {
    if (incoming.length === 0) return;
    const merged = eventsRef.current.concat(incoming);
    eventsRef.current = merged.length > EVENT_HISTORY ? merged.slice(merged.length - EVENT_HISTORY) : merged;
    eventRevisionRef.current++;
  }, []);

  // publish the log on its own cadence
  useEffect(() => {
    let shownRevision = -1;
    const timer = setInterval(() => {
      if (eventRevisionRef.current === shownRevision) return;
      shownRevision = eventRevisionRef.current;
      setEvents(eventsRef.current);
    }, EVENT_PUBLISH_INTERVAL_MS);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    const headers: Record<string, string> = {};
    const token = sessionStorage.getItem("micraft-auth-token");
    if (token) headers.Authorization = `Bearer ${token}`;
    fetch("/api/admin/simulation/defaults", { headers })
      .then((r) => (r.ok ? r.json() : null))
      .then((d) => d && setDefaults(d as SimulationDefaults))
      .catch(() => setError(tRef.current("sim.error.defaults")));
  }, []);

  useEffect(() => {
    const socket = new WebSocket(wsUrl());
    socketRef.current = socket;
    socket.onopen = () => setConnected(true);
    socket.onclose = () => {
      setConnected(false);
      runningRef.current = false;
      publish();
    };
    socket.onerror = () => setError(tRef.current("sim.error.connectionLost"));
    socket.onmessage = (event) => {
      let message: SimMessage;
      try {
        message = JSON.parse(event.data as string) as SimMessage;
      } catch {
        return;
      }
      switch (message.t) {
        case "snapshot":
          setSimulationId(message.simulationId);
          arenaRef.current = message.arena;
          configRef.current = message.config;
          npcsRef.current = message.npcs;
          playersRef.current = message.players;
          statsRef.current = message.stats;
          eventsRef.current = message.events.slice(-EVENT_HISTORY);
          eventRevisionRef.current++;
          setEvents(eventsRef.current);
          truncatedRef.current = message.truncated ?? false;
          foodRef.current = message.food ?? [];
          // a snapshot carries the server's whole series: replace, never merge, or a restart would
          // leave the previous arena's history in the charts
          setMetrics({
            bucketGameDays: message.metrics?.bucketGameDays ?? DEFAULT_BUCKET_GAME_DAYS,
            buckets: replaceBuckets(message.metrics),
          });
          runningRef.current = true;
          setError(null);
          publish();
          break;
        case "frame":
          npcsRef.current = message.npcs;
          playersRef.current = message.players;
          statsRef.current = message.stats;
          truncatedRef.current = message.truncated ?? false;
          // absent means unchanged since the last frame
          if (message.food) foodRef.current = message.food;
          if (message.metrics) {
            const incoming = message.metrics;
            setMetrics((current) => ({
              bucketGameDays: incoming.bucketGameDays,
              buckets: mergeBuckets(current.buckets, incoming.buckets),
            }));
          }
          pushEvents(message.events);
          publish();
          break;
        case "simulations":
          setSimulations(message.simulations);
          setSimulationId(message.attachedId ?? null);
          break;
        case "npcDetail":
          setDetail(message.detail);
          break;
        case "stopped":
          setSimulationId(null);
          runningRef.current = false;
          npcsRef.current = [];
          playersRef.current = [];
          foodRef.current = [];
          eventsRef.current = [];
          setEvents([]);
          setMetrics({ bucketGameDays: DEFAULT_BUCKET_GAME_DAYS, buckets: [] });
          statsRef.current = EMPTY_STATS;
          publish();
          break;
        case "error":
          setError(message.message);
          break;
      }
    };
    return () => {
      socket.close();
      socketRef.current = null;
    };
  }, [pushEvents, publish]);

  const setViewport = useCallback((viewport: SimViewport | null) => {
    const key = viewport
      ? `${Math.round(viewport.minX)},${Math.round(viewport.minZ)},${Math.round(viewport.maxX)},${Math.round(viewport.maxZ)}`
      : "";
    if (key === viewportRef.current) return;
    viewportRef.current = key;
    const socket = socketRef.current;
    if (socket?.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ t: "viewport", viewport }));
    }
  }, []);

  const send = useCallback((payload: unknown) => {
    const socket = socketRef.current;
    if (!socket || socket.readyState !== WebSocket.OPEN) {
      setError(tRef.current("sim.error.notConnected"));
      return;
    }
    socket.send(JSON.stringify(payload));
  }, []);

  /**
   * Every action in one memoised bag. They all close over nothing but the stable [send], so the
   * whole group keeps one identity for the lifetime of the page. Frames arrive dozens of times a
   * second; rebuilding these arrows on each of them would hand every child new props and make
   * memoising anything downstream pointless.
   */
  const actions = useMemo(
    () => ({
      init: (config: SimulationConfig, name?: string) => send({ t: "init", config, name: name ?? "" }),
      // no id = the arena this socket watches, so the panels that only know about "the current one"
      // keep working
      stop: (simulationId?: string) => send({ t: "stop", simulationId: simulationId ?? null }),
      restart: (simulationId?: string) => send({ t: "restart", simulationId: simulationId ?? null }),
      setSpeed: (ticksPerSecond: number) => send({ t: "speed", ticksPerSecond }),
      step: (count: number) => send({ t: "step", count }),
      inspect: (npcId: string) => send({ t: "inspect", npcId }),
      clearDetail: () => setDetail(null),
      spawn: (type: string, x: number, z: number, count: number, level?: number | null) =>
        send({ t: "spawn", type, x, z, count, level: level ?? null }),
      applyTuning: (tuning: NpcTuning) => send({ t: "tuning", tuning }),
      applyOverrides: (overrides: Record<string, unknown>) => send({ t: "defs", overrides }),
      reloadEntityDefs: () => send({ t: "reloadEntityDefs" }),
      playerInput: (name: string, dx: number, dz: number, yaw: number, jump: boolean) =>
        send({ t: "playerInput", name, dx, dz, yaw, jump }),
      attach: (id: string) => send({ t: "attach", simulationId: id }),
      detach: () => send({ t: "detach" }),
      refreshSimulations: () => send({ t: "list" }),
    }),
    [send],
  );

  return useMemo(
    () => ({
      connected,
      running: snapshot.running,
      arena: snapshot.arena,
      config: snapshot.config,
      npcs: snapshot.npcs,
      players: snapshot.players,
      stats: snapshot.stats,
      events,
      metrics: metrics.buckets,
      bucketGameDays: metrics.bucketGameDays,
      food: snapshot.food,
      truncated: snapshot.truncated,
      simulations,
      simulationId,
      detail,
      defaults,
      error,
      setViewport,
      ...actions,
    }),
    [connected, snapshot, events, metrics, simulations, simulationId, detail, defaults, error, setViewport, actions],
  );
}
