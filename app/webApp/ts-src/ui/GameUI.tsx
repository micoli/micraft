import { useEffect, useRef, useReducer } from 'react';
import { UiState, UiAction, LogEntry } from './types';
import { HUD } from './HUD';
import { Hotbar } from './Hotbar';
import { Console } from './Console';
import { ServerLog } from './ServerLog';
import { Notifications } from './Notifications';
import { LoginOverlay } from './LoginOverlay';
import { DisconnectOverlay } from './DisconnectOverlay';

const MC_LOG_MAX = 10;

const initial: UiState = {
  hud: null, notif: null, logs: [], inventory: {},
  hotbarVisible: false, consoleOpen: false,
  loginVisible: false, disconnectMsg: null,
};

let notifKey = 0;

function reducer(state: UiState, action: UiAction): UiState {
  switch (action.type) {
    case 'hud':         return { ...state, hud: action.data };
    case 'notification': return { ...state, notif: { msg: action.msg, key: ++notifKey } };
    case 'log': {
      const now = new Date();
      const time = `${String(now.getHours()).padStart(2,'0')}:${String(now.getMinutes()).padStart(2,'0')}:${String(now.getSeconds()).padStart(2,'0')}`;
      const entry: LogEntry = { time, msg: action.msg };
      const logs = [...state.logs, entry].slice(-MC_LOG_MAX);
      return { ...state, logs };
    }
    case 'inventory':    return { ...state, inventory: action.data };
    case 'hotbar_toggle': return { ...state, hotbarVisible: !state.hotbarVisible };
    case 'console_show': return { ...state, consoleOpen: true };
    case 'console_hide': return { ...state, consoleOpen: false };
    case 'login_show':   return { ...state, loginVisible: true };
    case 'login_hide':   return { ...state, loginVisible: false };
    case 'disconnect_show': return { ...state, disconnectMsg: action.message };
    case 'disconnect_hide': return { ...state, disconnectMsg: null };
  }
}

export function GameUI() {
  const [state, dispatch] = useReducer(reducer, initial);

  // Refs for synchronous reads by Kotlin
  const consoleOpenRef = useRef(false);
  const consoleStateRef = useRef({ history: [] as string[], histIdx: -1, playerName: '', tabIdx: -1, tabMatches: [] as string[] });
  const consoleSubmittedRef = useRef<string | null>(null);
  const loginResultRef = useRef('');
  const notifTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Keep consoleOpenRef in sync
  useEffect(() => { consoleOpenRef.current = state.consoleOpen; }, [state.consoleOpen]);

  // Auto-dismiss notifications
  useEffect(() => {
    if (!state.notif) return;
    if (notifTimerRef.current) clearTimeout(notifTimerRef.current);
    notifTimerRef.current = setTimeout(() => dispatch({ type: 'notification', msg: '' }), 3000);
    return () => { if (notifTimerRef.current) clearTimeout(notifTimerRef.current); };
  }, [state.notif?.key]);

  useEffect(() => {
    // Wire Kotlin-callable window functions to React dispatch
    (window as any).mcUpdateHUD = (x: number, y: number, z: number, yaw: number, pitch: number, stance: string, speed: number, fps: number, kbIn: number, kbOut: number, biome: string, targetBlock: string) =>
      dispatch({ type: 'hud', data: { x, y, z, yaw, pitch, stance, speed, fps, kbIn, kbOut, biome, targetBlock } });

    (window as any).mcShowNotification = (msg: string) => dispatch({ type: 'notification', msg });
    (window as any).mcAddServerLog     = (msg: string) => dispatch({ type: 'log', msg });
    (window as any).mcUpdateHotbar     = (json: string) => dispatch({ type: 'inventory', data: JSON.parse(json) });
    (window as any).mcToggleHotbar     = () => dispatch({ type: 'hotbar_toggle' });

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
        dispatch({ type: 'console_show' });
      } else if (ke.key === 'Enter' && !consoleOpenRef.current) {
        ke.preventDefault();
        dispatch({ type: 'console_show' });
      }
    }
    document.addEventListener('keydown', onGlobalKeydown);
    return () => document.removeEventListener('keydown', onGlobalKeydown);
  }, []);

  return (
    <>
      <HUD data={state.hud} />
      <Hotbar inventory={state.inventory} visible={state.hotbarVisible} />
      <ServerLog logs={state.logs} />
      <Notifications notif={state.notif?.msg ? state.notif : null} />
      <Console
        open={state.consoleOpen}
        onClose={() => dispatch({ type: 'console_hide' })}
        submittedRef={consoleSubmittedRef}
        stateRef={consoleStateRef}
      />
      <div id="mc-login-root" data-visible={String(state.loginVisible)}>
        <LoginOverlay visible={state.loginVisible} loginResultRef={loginResultRef} onHide={() => dispatch({ type: 'login_hide' })} />
      </div>
      <DisconnectOverlay message={state.disconnectMsg} />
    </>
  );
}
