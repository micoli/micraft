import { useEffect, useRef, useState, KeyboardEvent, MutableRefObject } from 'react';

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
  initialValueRef: MutableRefObject<string>;
  layoutStyle?: React.CSSProperties;
}

function computeSuggestions(val: string): string[] {
  if (!val.startsWith('/')) return [];
  const knownCommands: string[] = (window as any).__mcKnownCommands || [];
  const completers: Record<string, (p: string) => string[]> = (window as any).__mcCommandCompleters || {};
  const spaceIdx = val.indexOf(' ');
  if (spaceIdx === -1) {
    return knownCommands.filter(cmd => cmd.startsWith(val));
  }
  const cmd = val.slice(0, spaceIdx);
  const partial = val.slice(spaceIdx + 1);
  return completers[cmd] ? completers[cmd](partial) : [];
}

export function Console({ open, onClose, submittedRef, stateRef, initialValueRef, layoutStyle }: Props) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [selIdx, setSelIdx] = useState(-1);

  useEffect(() => {
    if (open) {
      const el = inputRef.current;
      if (!el) return;
      el.value = initialValueRef.current;
      initialValueRef.current = '';
      submittedRef.current = null;
      stateRef.current.histIdx = -1;
      if (document.pointerLockElement) document.exitPointerLock();
      setTimeout(() => {
        el.focus();
        setSuggestions(computeSuggestions(el.value));
        setSelIdx(-1);
      }, 10);
    } else {
      setSuggestions([]);
      setSelIdx(-1);
    }
  }, [open]);

  function applyCompletion(val: string, match: string) {
    const el = inputRef.current!;
    const spaceIdx = val.indexOf(' ');
    if (spaceIdx === -1) {
      el.value = suggestions.length === 1 ? match + ' ' : match;
    } else {
      el.value = val.slice(0, spaceIdx + 1) + match;
    }
    const newSuggestions = computeSuggestions(el.value);
    setSuggestions(newSuggestions);
    setSelIdx(-1);
  }

  function handleKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    e.stopPropagation();
    const el = inputRef.current!;
    const c = stateRef.current;
    const h = c.history;

    if (e.key === 'Enter') {
      if (selIdx >= 0 && selIdx < suggestions.length) {
        applyCompletion(el.value, suggestions[selIdx]);
        return;
      }
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
      if (suggestions.length > 0) {
        setSelIdx(idx => idx <= 0 ? suggestions.length - 1 : idx - 1);
      } else if (h.length > 0) {
        c.histIdx = Math.min(c.histIdx + 1, h.length - 1);
        el.value = h[h.length - 1 - c.histIdx];
        setSuggestions(computeSuggestions(el.value));
        setTimeout(() => el.setSelectionRange(el.value.length, el.value.length), 0);
      }
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (suggestions.length > 0) {
        setSelIdx(idx => idx >= suggestions.length - 1 ? -1 : idx + 1);
      } else {
        c.histIdx = Math.max(c.histIdx - 1, -1);
        el.value = c.histIdx === -1 ? '/' : h[h.length - 1 - c.histIdx];
        setSuggestions(computeSuggestions(el.value));
        setTimeout(() => el.setSelectionRange(el.value.length, el.value.length), 0);
      }
    } else if (e.key === 'Tab') {
      e.preventDefault();
      if (suggestions.length === 0) return;
      const nextIdx = (selIdx + 1) % suggestions.length;
      setSelIdx(nextIdx);
      const val = el.value;
      const spaceIdx = val.indexOf(' ');
      if (spaceIdx === -1) {
        el.value = suggestions[nextIdx];
      } else {
        el.value = val.slice(0, spaceIdx + 1) + suggestions[nextIdx];
      }
    } else {
      setSelIdx(-1);
    }
  }

  function handleInput() {
    const el = inputRef.current!;
    setSuggestions(computeSuggestions(el.value));
    setSelIdx(-1);
    stateRef.current.tabIdx = -1;
    stateRef.current.tabMatches = [];
  }

  if (!open) return null;

  const containerStyle: React.CSSProperties = layoutStyle && Object.keys(layoutStyle).length > 0
    ? { position: 'relative', background: 'rgba(0,0,0,0.72)', borderRadius: 4, zIndex: 1002, boxSizing: 'border-box', ...layoutStyle, height: undefined }
    : { position: 'fixed', bottom: 60, left: '50%', transform: 'translateX(-50%)', width: '60%', background: 'rgba(0,0,0,0.72)', borderRadius: 4, zIndex: 1002, boxSizing: 'border-box' };

  return (
    <div style={containerStyle}>
      {suggestions.length > 0 && (
        <div style={{
          position: 'absolute', bottom: '100%', left: 0, right: 0,
          background: 'rgba(0,0,0,0.85)', borderRadius: '4px 4px 0 0',
          borderBottom: '1px solid rgba(255,255,255,0.15)',
          maxHeight: '40vh', overflowY: 'auto',
        }}>
          {suggestions.map((s, i) => (
            <div
              key={s}
              onMouseDown={e => { e.preventDefault(); applyCompletion(inputRef.current!.value, s); inputRef.current!.focus(); }}
              style={{
                padding: '2px 8px',
                cursor: 'pointer',
                color: i === selIdx ? '#000' : '#ccc',
                background: i === selIdx ? '#7eb9ff' : 'transparent',
                font: '14px monospace',
              }}
            >
              {s}
            </div>
          ))}
        </div>
      )}
      <div style={{ padding: '4px 8px' }}>
        <input
          ref={inputRef}
          type="text"
          style={{ width: '100%', background: 'transparent', border: 'none', color: '#fff', font: '15px monospace', outline: 'none' }}
          onKeyDown={handleKeyDown}
          onInput={handleInput}
        />
      </div>
    </div>
  );
}
