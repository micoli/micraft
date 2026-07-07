import { useEffect, useRef, useState, KeyboardEvent, MutableRefObject } from "react";

interface ConsoleState {
  history: string[];
  histIdx: number;
  playerName: string;
  tabIdx: number;
  tabMatches: string[];
}

async function computeSuggestions(val: string): Promise<string[]> {
  if (!val.startsWith("/")) return [];
  const knownCommands: string[] = (window.mcState.knownCommands || []).sort();
  const completers: Record<string, (p: string) => string[] | Promise<string[]>> =
    window.mcState.commandCompleters || {};
  const spaceIdx = val.indexOf(" ");
  if (spaceIdx === -1) {
    return knownCommands.filter((cmd) => cmd.startsWith(val));
  }
  const cmd = val.slice(0, spaceIdx);
  const partial = val.slice(spaceIdx + 1);
  return completers[cmd] ? await completers[cmd](partial) : [];
}

interface UseConsoleParams {
  open: boolean;
  onClose: () => void;
  submittedRef: MutableRefObject<string | null>;
  stateRef: MutableRefObject<ConsoleState>;
  initialValueRef: MutableRefObject<string>;
}

export function useConsole({ open, onClose, submittedRef, stateRef, initialValueRef }: UseConsoleParams) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [selIdx, setSelIdx] = useState(-1);
  const completionSeqRef = useRef(0);

  async function updateSuggestions(val: string) {
    const seq = ++completionSeqRef.current;
    const sug = await computeSuggestions(val);
    if (seq === completionSeqRef.current) setSuggestions(sug);
  }

  useEffect(() => {
    if (!open) {
      setSuggestions([]);
      setSelIdx(-1);
      return;
    }
    const el = inputRef.current;
    if (!el) return;
    el.value = initialValueRef.current;
    initialValueRef.current = "";
    submittedRef.current = null;
    stateRef.current.histIdx = -1;
    if (document.pointerLockElement) document.exitPointerLock();
    setTimeout(() => {
      el.focus();
      updateSuggestions(el.value);
      setSelIdx(-1);
    }, 10);
  }, [open]);

  function applyCompletion(val: string, match: string) {
    const el = inputRef.current!;
    const firstSpaceIdx = val.indexOf(" ");
    if (firstSpaceIdx === -1) {
      el.value = suggestions.length === 1 ? match + " " : match;
    } else {
      const lastSpaceIdx = val.lastIndexOf(" ");
      el.value = val.slice(0, lastSpaceIdx + 1) + match;
    }
    updateSuggestions(el.value);
    setSelIdx(-1);
  }

  function handleKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    e.stopPropagation();
    const el = inputRef.current!;
    const c = stateRef.current;
    const h = c.history;

    switch (e.key) {
      case "Enter":
        if (selIdx >= 0 && selIdx < suggestions.length) {
          applyCompletion(el.value, suggestions[selIdx]);
          return;
        }
        if (suggestions.length === 1) {
          const sp = el.value.lastIndexOf(" ");
          const currentToken = sp === -1 ? el.value : el.value.slice(sp + 1);
          if (currentToken !== suggestions[0]) {
            applyCompletion(el.value, suggestions[0]);
            return;
          }
        }
        const text = el.value.trim();
        if (text) {
          submittedRef.current = text;
          if (h.length === 0 || h[h.length - 1] !== text) h.push(text);
          try {
            localStorage.setItem("mc_history_" + c.playerName, JSON.stringify(h.slice(-50)));
          } catch {}
        }
        onClose();
        break;
      case "Escape":
        e.preventDefault();
        onClose();
        break;
      case "ArrowUp":
        e.preventDefault();
        if (suggestions.length > 0) {
          setSelIdx((idx) => (idx <= 0 ? suggestions.length - 1 : idx - 1));
        } else if (h.length > 0) {
          c.histIdx = Math.min(c.histIdx + 1, h.length - 1);
          el.value = h[h.length - 1 - c.histIdx];
          updateSuggestions(el.value);
          setTimeout(() => el.setSelectionRange(el.value.length, el.value.length), 0);
        }
        break;
      case "ArrowDown":
        e.preventDefault();
        if (suggestions.length > 0) {
          setSelIdx((idx) => (idx >= suggestions.length - 1 ? -1 : idx + 1));
        } else {
          c.histIdx = Math.max(c.histIdx - 1, -1);
          el.value = c.histIdx === -1 ? "/" : h[h.length - 1 - c.histIdx];
          updateSuggestions(el.value);
          setTimeout(() => el.setSelectionRange(el.value.length, el.value.length), 0);
        }
        break;
      case "Tab":
        e.preventDefault();
        if (suggestions.length === 0) return;
        const nextIdx = (selIdx + 1) % suggestions.length;
        setSelIdx(nextIdx);
        const val = el.value;
        const firstSpaceIdx = val.indexOf(" ");
        if (firstSpaceIdx === -1) {
          el.value = suggestions[nextIdx];
        } else {
          const lastSpaceIdx = val.lastIndexOf(" ");
          el.value = val.slice(0, lastSpaceIdx + 1) + suggestions[nextIdx];
        }
        break;
      default:
        setSelIdx(-1);
    }
  }

  function handleInput() {
    const el = inputRef.current!;
    updateSuggestions(el.value);
    setSelIdx(-1);
    stateRef.current.tabIdx = -1;
    stateRef.current.tabMatches = [];
  }

  return { inputRef, suggestions, selIdx, handleKeyDown, handleInput, applyCompletion };
}
