import { useEffect, useRef, KeyboardEvent, MutableRefObject } from 'react';

interface ConsoleState {
  history: string[];
  histIdx: number;
  playerName: string;
  tabIdx: number;
  tabMatches: string[];
}

interface Props {
  open: boolean;
  onClose: () => void;
  submittedRef: MutableRefObject<string | null>;
  stateRef: MutableRefObject<ConsoleState>;
}

export function Console({ open, onClose, submittedRef, stateRef }: Props) {
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (open) {
      const el = inputRef.current;
      if (!el) return;
      el.value = '/';
      submittedRef.current = null;
      stateRef.current.histIdx = -1;
      if (document.pointerLockElement) document.exitPointerLock();
      setTimeout(() => el.focus(), 10);
    }
  }, [open]);

  function handleKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    e.stopPropagation();
    const el = inputRef.current!;
    const c = stateRef.current;
    const h = c.history;

    if (e.key === 'Enter') {
      const text = el.value.trim();
      if (text) {
        submittedRef.current = text;
        if (h.length === 0 || h[h.length - 1] !== text) h.push(text);
        try { localStorage.setItem('mc_history_' + c.playerName, JSON.stringify(h.slice(-50))); } catch {}
      }
      onClose();
    } else if (e.key === 'Escape') {
      onClose();
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      if (h.length > 0) {
        c.histIdx = Math.min(c.histIdx + 1, h.length - 1);
        el.value = h[h.length - 1 - c.histIdx];
        setTimeout(() => el.setSelectionRange(el.value.length, el.value.length), 0);
      }
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      c.histIdx = Math.max(c.histIdx - 1, -1);
      el.value = c.histIdx === -1 ? '/' : h[h.length - 1 - c.histIdx];
      setTimeout(() => el.setSelectionRange(el.value.length, el.value.length), 0);
    } else if (e.key === 'Tab') {
      e.preventDefault();
      const val = el.value;
      if (!val.startsWith('/')) return;
      const knownCommands: string[] = (window as any).__mcKnownCommands || [];
      const completers: Record<string, (p: string) => string[]> = (window as any).__mcCommandCompleters || {};
      const spaceIdx = val.indexOf(' ');
      let matches: string[];
      if (spaceIdx === -1) {
        matches = knownCommands.filter(cmd => cmd.startsWith(val));
        if (matches.length === 0) return;
        if (matches.length === 1) {
          el.value = matches[0] + ' ';
          c.tabIdx = -1; c.tabMatches = [];
        } else {
          if (c.tabMatches.join('|') !== matches.join('|')) { c.tabIdx = -1; c.tabMatches = matches; }
          c.tabIdx = (c.tabIdx + 1) % c.tabMatches.length;
          el.value = c.tabMatches[c.tabIdx];
        }
      } else {
        const cmd = val.slice(0, spaceIdx);
        const partial = val.slice(spaceIdx + 1);
        matches = completers[cmd] ? completers[cmd](partial) : [];
        if (matches.length === 0) return;
        if (matches.length === 1) {
          el.value = cmd + ' ' + matches[0];
          c.tabIdx = -1; c.tabMatches = [];
        } else {
          if (c.tabMatches.join('|') !== matches.join('|')) { c.tabIdx = -1; c.tabMatches = matches; }
          c.tabIdx = (c.tabIdx + 1) % c.tabMatches.length;
          el.value = cmd + ' ' + c.tabMatches[c.tabIdx];
        }
      }
    } else {
      c.tabIdx = -1; c.tabMatches = [];
    }
  }

  if (!open) return null;

  return (
    <div style={{
      position: 'fixed', bottom: 60, left: '50%', transform: 'translateX(-50%)',
      width: '60%', background: 'rgba(0,0,0,0.72)', borderRadius: 4, padding: '4px 8px',
      zIndex: 1002, boxSizing: 'border-box',
    }}>
      <input
        ref={inputRef}
        type="text"
        style={{ width: '100%', background: 'transparent', border: 'none', color: '#fff', font: '15px monospace', outline: 'none' }}
        onKeyDown={handleKeyDown}
      />
    </div>
  );
}
