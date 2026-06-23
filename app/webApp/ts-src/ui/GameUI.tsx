import { useEffect, useRef, useReducer } from 'react';
import { UiState, UiAction, LogEntry, HudMode, GameLayout } from './types';
import { HUD } from './HUD';
import { Inventory } from './Inventory';
import { ShortcutBar } from './ShortcutBar';
import { Console } from './Console';
import { ServerLog } from './ServerLog';
import { Notifications } from './Notifications';
import { LoginOverlay } from './LoginOverlay';
import { DisconnectOverlay } from './DisconnectOverlay';
import { LayoutEditor } from './LayoutEditor';
import { defaultLayout, resolveActiveLayout, widgetStyle } from './LayoutEngine';

const MC_LOG_MAX = 100;

const HUD_MODES: HudMode[] = ['simple', 'medium', 'complete'];

function loadHudMode(): HudMode {
  try {
    const stored = localStorage.getItem('mc_hud_mode');
    if (stored === 'simple' || stored === 'medium' || stored === 'complete') return stored;
  } catch { /* ignore */ }
  return 'complete';
}

const initial: UiState = {
  hud: null, hudMode: loadHudMode(), notif: null, logs: [], logVisible: false, logKey: 0, inventory: {},
  hotbarVisible: false,
  shortcutBar: Array(10).fill(null),
  selectedSlot: 0,
  consoleOpen: false,
  loginVisible: false, disconnectMsg: null,
  layouts: [defaultLayout()],
  activeLayout: 'default',
  layoutEditorOpen: false,
};

let notifKey = 0;

function reducer(state: UiState, action: UiAction): UiState {
  switch (action.type) {
    case 'hud':         return { ...state, hud: action.data };
    case 'hud_mode_cycle': {
      const next = HUD_MODES[(HUD_MODES.indexOf(state.hudMode) + 1) % HUD_MODES.length];
      try { localStorage.setItem('mc_hud_mode', next); } catch { /* ignore */ }
      return { ...state, hudMode: next };
    }
    case 'notification': return { ...state, notif: { msg: action.msg, key: ++notifKey } };
    case 'log': {
      const now = new Date();
      const time = `${String(now.getHours()).padStart(2,'0')}:${String(now.getMinutes()).padStart(2,'0')}:${String(now.getSeconds()).padStart(2,'0')}`;
      const entry: LogEntry = { time, msg: action.msg };
      const logs = [...state.logs, entry].slice(-MC_LOG_MAX);
      return { ...state, logs, logVisible: true, logKey: state.logKey + 1 };
    }
    case 'log_hide': return { ...state, logVisible: false };
    case 'inventory':    return { ...state, inventory: action.data };
    case 'hotbar_toggle': return { ...state, hotbarVisible: !state.hotbarVisible };
    case 'shortcut_bar_update': return { ...state, shortcutBar: action.data.slots, selectedSlot: action.data.selected };
    case 'slot_select': return { ...state, selectedSlot: action.slot };
    case 'console_show': return { ...state, consoleOpen: true };
    case 'console_hide': return { ...state, consoleOpen: false };
    case 'login_show':   return { ...state, loginVisible: true };
    case 'login_hide':   return { ...state, loginVisible: false };
    case 'disconnect_show': return { ...state, disconnectMsg: action.message };
    case 'disconnect_hide': return { ...state, disconnectMsg: null };
    case 'layouts_sync': return { ...state, layouts: action.layouts, activeLayout: action.activeLayout };
    case 'layout_editor_show': return { ...state, layoutEditorOpen: true };
    case 'layout_editor_hide': return { ...state, layoutEditorOpen: false };
    case 'layout_editor_save': return { ...state, layouts: action.layouts, activeLayout: action.activeLayout, layoutEditorOpen: false };
  }
}

export function GameUI() {
  const [state, dispatch] = useReducer(reducer, initial);

  // Refs for synchronous reads by Kotlin
  const logTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const consoleOpenRef = useRef(false);
  const pendingSlotUpdateRef = useRef<string>('');
  const consoleStateRef = useRef({ history: [] as string[], histIdx: -1, playerName: '', tabIdx: -1, tabMatches: [] as string[] });
  const consoleSubmittedRef = useRef<string | null>(null);
  const consoleInitialValueRef = useRef('');
  const loginResultRef = useRef('');
  const notifTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pendingLayoutUpdateRef = useRef<string>('');

  // Keep consoleOpenRef in sync
  useEffect(() => { consoleOpenRef.current = state.consoleOpen; }, [state.consoleOpen]);

  // Auto-hide server log after 5s of no new messages
  useEffect(() => {
    if (!state.logVisible) return;
    if (logTimerRef.current) clearTimeout(logTimerRef.current);
    logTimerRef.current = setTimeout(() => dispatch({ type: 'log_hide' }), 5000);
    return () => { if (logTimerRef.current) clearTimeout(logTimerRef.current); };
  }, [state.logKey]);

  // Auto-dismiss notifications
  useEffect(() => {
    if (!state.notif) return;
    if (notifTimerRef.current) clearTimeout(notifTimerRef.current);
    notifTimerRef.current = setTimeout(() => dispatch({ type: 'notification', msg: '' }), 3000);
    return () => { if (notifTimerRef.current) clearTimeout(notifTimerRef.current); };
  }, [state.notif?.key]);

  // Update /layout autocomplete completer whenever layouts change
  useEffect(() => {
    (window as any).__mcCommandCompleters = (window as any).__mcCommandCompleters ?? {};
    (window as any).__mcCommandCompleters['/layout'] = (partial: string) =>
      state.layouts.map((l: GameLayout) => l.name).filter((n: string) => n.startsWith(partial));
  }, [state.layouts]);

  useEffect(() => {
    // Wire Kotlin-callable window functions to React dispatch
    (window as any).mcUpdateHUD = (x: number, y: number, z: number, yaw: number, pitch: number, stance: string, speed: number, fps: number, kbIn: number, kbOut: number, biome: string, targetBlock: string, gameTime: string) =>
      dispatch({ type: 'hud', data: { x, y, z, yaw, pitch, stance, speed, fps, kbIn, kbOut, biome, targetBlock, gameTime } });

    (window as any).mcShowNotification = (msg: string) => dispatch({ type: 'notification', msg });
    (window as any).mcAddServerLog     = (msg: string) => dispatch({ type: 'log', msg });
    (window as any).mcUpdateHotbar     = (json: string) => dispatch({ type: 'inventory', data: JSON.parse(json) });
    (window as any).mcToggleHotbar     = () => dispatch({ type: 'hotbar_toggle' });
    (window as any).mcUpdateShortcutBar = (json: string) => dispatch({ type: 'shortcut_bar_update', data: JSON.parse(json) });
    (window as any).mcSetSelectedSlot   = (slot: number) => dispatch({ type: 'slot_select', slot });
    (window as any).mcConsumeSlotUpdate = () => {
      const v = pendingSlotUpdateRef.current;
      pendingSlotUpdateRef.current = '';
      return v;
    };

    (window as any).mcShowLoginOverlay     = () => dispatch({ type: 'login_show' });
    (window as any).mcHideLoginOverlay     = () => dispatch({ type: 'login_hide' });
    (window as any).mcShowDisconnectedOverlay = (msg: string) => dispatch({ type: 'disconnect_show', message: msg });
    (window as any).mcHideDisconnectedOverlay = () => dispatch({ type: 'disconnect_hide' });

    (window as any).mcShowConsole = () => dispatch({ type: 'console_show' });
    (window as any).mcHideConsole = () => dispatch({ type: 'console_hide' });
    (window as any).mcIsConsoleOpen = () => consoleOpenRef.current;

    (window as any).mcConsumeConsoleInput = () => {
      const v = consoleSubmittedRef.current || '';
      consoleSubmittedRef.current = null;
      return v;
    };

    (window as any).mcConsumeLoginResult = () => {
      const v = loginResultRef.current;
      loginResultRef.current = '';
      return v;
    };

    (window as any).mcConsoleSetPlayer = (name: string) => {
      consoleStateRef.current.playerName = name;
      try {
        const stored = localStorage.getItem('mc_history_' + name);
        consoleStateRef.current.history = stored ? JSON.parse(stored) : [];
      } catch { consoleStateRef.current.history = []; }
    };

    (window as any).mcCycleHudMode = () => dispatch({ type: 'hud_mode_cycle' });

    (window as any).mcSyncLayouts = (json: string) => {
      const data: { layouts: GameLayout[]; activeLayout: string } = JSON.parse(json);
      dispatch({ type: 'layouts_sync', layouts: data.layouts, activeLayout: data.activeLayout });
    };

    (window as any).mcShowLayoutEditor = () => dispatch({ type: 'layout_editor_show' });
    (window as any).mcHideLayoutEditor = () => dispatch({ type: 'layout_editor_hide' });

    (window as any).mcConsumeLayoutUpdate = () => {
      const v = pendingLayoutUpdateRef.current;
      pendingLayoutUpdateRef.current = '';
      return v;
    };

    // no-ops: React handles creation
    (window as any).mcCreateHUD       = () => {};
    (window as any).mcCreateHotbar    = () => {};
    (window as any).mcCreateConsole   = () => {};
    (window as any).mcCreateServerLog = () => {};

    // Global keydown: open console via '/' or Enter (when not already open and no modal)
    function onGlobalKeydown(e: Event) {
      const ke = e as globalThis.KeyboardEvent;
      const tag = (document.activeElement as HTMLElement)?.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA') return;
      const loginEl = document.getElementById('mc-login-root');
      if (loginEl && (loginEl as HTMLElement).dataset.visible === 'true') return;
      if (ke.key === '/' && !consoleOpenRef.current) {
        ke.preventDefault();
        consoleInitialValueRef.current = '/';
        dispatch({ type: 'console_show' });
      } else if (ke.key === 'Enter' && !consoleOpenRef.current) {
        ke.preventDefault();
        consoleInitialValueRef.current = '';
        dispatch({ type: 'console_show' });
      }
    }
    document.addEventListener('keydown', onGlobalKeydown);
    return () => document.removeEventListener('keydown', onGlobalKeydown);
  }, []);

  const activeLayout = resolveActiveLayout(state.layouts, state.activeLayout);

  const handleLayoutSave = (layouts: GameLayout[], newActiveLayout: string) => {
    dispatch({ type: 'layout_editor_save', layouts, activeLayout: newActiveLayout });
    pendingLayoutUpdateRef.current = JSON.stringify({ layouts, activeLayout: newActiveLayout });
  };

  const minimapStyle: React.CSSProperties = {
    ...widgetStyle(activeLayout, 'MINIMAP'),
    zIndex: 999,
    pointerEvents: 'none',
    border: '2px solid rgba(255,255,255,0.25)',
    boxShadow: '0 2px 8px rgba(0,0,0,0.5)',
    borderRadius: 6,
    overflow: 'hidden',
  };

  return (
    <>
      {/* Minimap host: React manages position; Kotlin appends the canvas here */}
      <div id="mc-minimap-host" style={minimapStyle} />

      <HUD data={state.hud} mode={state.hudMode} layoutStyle={widgetStyle(activeLayout, 'HUD')} />
      <ShortcutBar
        inventory={state.inventory}
        slots={state.shortcutBar}
        selectedSlot={state.selectedSlot}
        onSlotDrop={(slot, itemType) => {
          pendingSlotUpdateRef.current = JSON.stringify({ slot, itemType: itemType ?? null });
        }}
        layoutStyle={widgetStyle(activeLayout, 'SHORTCUT_BAR')}
      />
      <Inventory inventory={state.inventory} visible={state.hotbarVisible} />
      <ServerLog logs={state.logs} visible={state.logVisible || state.consoleOpen} layoutStyle={widgetStyle(activeLayout, 'CHAT_HISTORY')} />
      <Notifications notif={state.notif?.msg ? state.notif : null} />
      <Console
        open={state.consoleOpen}
        onClose={() => dispatch({ type: 'console_hide' })}
        submittedRef={consoleSubmittedRef}
        stateRef={consoleStateRef}
        initialValueRef={consoleInitialValueRef}
        layoutStyle={widgetStyle(activeLayout, 'INPUT_BOX')}
      />
      <div id="mc-login-root" data-visible={String(state.loginVisible)}>
        <LoginOverlay visible={state.loginVisible} loginResultRef={loginResultRef} onHide={() => dispatch({ type: 'login_hide' })} />
      </div>
      <DisconnectOverlay message={state.disconnectMsg} />
      <LayoutEditor
        open={state.layoutEditorOpen}
        layouts={state.layouts}
        activeLayout={state.activeLayout}
        onSave={handleLayoutSave}
        onClose={() => dispatch({ type: 'layout_editor_hide' })}
      />
    </>
  );
}
