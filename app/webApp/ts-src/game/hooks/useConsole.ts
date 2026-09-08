import { useCallback, useEffect, useRef, useState, KeyboardEvent, MutableRefObject } from "react";
import { matchesEvent } from "../lib/input/keyboard";
import { Suggestion, suggestionLabel, suggestionValue } from "../types";

/** Replaces an applied completion's label (a whole space-delimited argument, which may itself
 * contain spaces) with its wire value. */
function swapCompletedArgs(text: string, applied: Map<string, string>): string {
  let out = text;
  for (const [label, value] of applied) {
    if (label === value) continue;
    out = out.split(" " + label).join(" " + value);
  }
  return out;
}

interface ConsoleState {
  history: string[];
  histIdx: number;
  playerName: string;
  tabIdx: number;
  tabMatches: string[];
}

async function computeSuggestions(val: string): Promise<Suggestion[]> {
  if (!val.startsWith("/")) return [];
  const knownCommands: string[] = (window.mcState.knownCommands || []).sort();
  const completers: Record<string, (p: string) => Suggestion[] | Promise<Suggestion[]>> =
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
  focusRef: MutableRefObject<boolean>;
}

export function useConsole({ open, onClose, submittedRef, stateRef, initialValueRef, focusRef }: UseConsoleParams) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [suggestions, setSuggestions] = useState<Suggestion[]>([]);
  const [selIdx, setSelIdx] = useState(-1);
  const completionSeqRef = useRef(0);
  const appliedCompletionsRef = useRef<Map<string, string>>(new Map());
  // The last completion inserted, so re-triggering (Enter / repeated Tab) replaces from the same
  // anchor instead of the last space — a label may contain spaces (e.g. "Morlin Swanhelm").
  const lastApplyRef = useRef<{ prefix: string; text: string } | null>(null);

  function insertPrefix(val: string): string {
    const la = lastApplyRef.current;
    if (la && val === la.prefix + la.text) return la.prefix;
    const firstSpaceIdx = val.indexOf(" ");
    if (firstSpaceIdx === -1) return "";
    return val.slice(0, val.lastIndexOf(" ") + 1);
  }

  const updateSuggestions = useCallback(async (val: string) => {
    const seq = ++completionSeqRef.current;
    const sug = await computeSuggestions(val);
    if (seq === completionSeqRef.current) setSuggestions(sug);
  }, []);

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
    appliedCompletionsRef.current.clear();
    lastApplyRef.current = null;
    stateRef.current.histIdx = -1;
    const shouldFocus = focusRef.current;
    if (shouldFocus && document.pointerLockElement) document.exitPointerLock();
    setTimeout(() => {
      if (shouldFocus) el.focus();
      if (el.value.includes(" ")) updateSuggestions(el.value);
      setSelIdx(-1);
    }, 10);
  }, [open, focusRef, initialValueRef, stateRef, submittedRef, updateSuggestions]);

  function applyCompletion(val: string, match: Suggestion) {
    const el = inputRef.current!;
    const label = suggestionLabel(match);
    if (typeof match !== "string") appliedCompletionsRef.current.set(label, suggestionValue(match));
    const prefix = insertPrefix(val);
    const inserted = prefix === "" && suggestions.length === 1 ? label + " " : label;
    el.value = prefix + inserted;
    lastApplyRef.current = { prefix, text: inserted };
    updateSuggestions(el.value);
    setSelIdx(-1);
  }

  function handleKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    e.stopPropagation();
    const el = inputRef.current!;
    const c = stateRef.current;
    const h = c.history;

    switch (e.key) {
      case "Enter": {
        if (selIdx >= 0 && selIdx < suggestions.length) {
          applyCompletion(el.value, suggestions[selIdx]);
          return;
        }
        const la = lastApplyRef.current;
        const alreadyApplied = la != null && el.value === la.prefix + la.text;
        if (!alreadyApplied && suggestions.length === 1) {
          const sp = el.value.lastIndexOf(" ");
          const currentToken = sp === -1 ? el.value : el.value.slice(sp + 1);
          if (currentToken !== suggestionLabel(suggestions[0])) {
            applyCompletion(el.value, suggestions[0]);
            return;
          }
        }
        const text = el.value.trim();
        if (text) {
          if (text === "/disconnect" && window.mcState) {
            window.mcState.intentionalDisconnect = true;
          }
          submittedRef.current = swapCompletedArgs(text, appliedCompletionsRef.current);
          if (h.length === 0 || h[h.length - 1] !== text) h.push(text);
          try {
            localStorage.setItem("mc_history_" + c.playerName, JSON.stringify(h.slice(-50)));
          } catch {
            /* empty */
          }
        }
        onClose();
        break;
      }
      case "Escape":
        e.preventDefault();
        onClose();
        break;
      case "ArrowUp":
        e.preventDefault();
        if (suggestions.length > 0 && c.histIdx === -1) {
          setSelIdx((idx) => (idx <= 0 ? suggestions.length - 1 : idx - 1));
        } else if (h.length > 0) {
          c.histIdx = Math.min(c.histIdx + 1, h.length - 1);
          el.value = h[h.length - 1 - c.histIdx];
          setSuggestions([]);
          setSelIdx(-1);
          setTimeout(() => el.setSelectionRange(el.value.length, el.value.length), 0);
        }
        break;
      case "ArrowDown":
        e.preventDefault();
        if (suggestions.length > 0 && c.histIdx === -1) {
          setSelIdx((idx) => (idx >= suggestions.length - 1 ? -1 : idx + 1));
        } else if (c.histIdx >= 0) {
          c.histIdx = Math.max(c.histIdx - 1, -1);
          el.value = c.histIdx === -1 ? "/" : h[h.length - 1 - c.histIdx];
          setSuggestions([]);
          setSelIdx(-1);
          setTimeout(() => el.setSelectionRange(el.value.length, el.value.length), 0);
        }
        break;
      case "Tab": {
        e.preventDefault();
        if (suggestions.length === 0) return;
        const nextIdx = (selIdx + 1) % suggestions.length;
        setSelIdx(nextIdx);
        const match = suggestions[nextIdx];
        const label = suggestionLabel(match);
        const prefix = insertPrefix(el.value);
        el.value = prefix + label;
        lastApplyRef.current = { prefix, text: label };
        if (typeof match !== "string") {
          appliedCompletionsRef.current.set(label, suggestionValue(match));
        }
        break;
      }
      default: {
        const toggleKeys = window.mcState?.bindings?.console_toggle;
        if (toggleKeys?.some((k) => matchesEvent(k, e.nativeEvent)) && el.value === "") {
          e.preventDefault();
          onClose();
          return;
        }
        setSelIdx(-1);
      }
    }
  }

  function handleInput() {
    const el = inputRef.current!;
    stateRef.current.histIdx = -1;
    lastApplyRef.current = null;
    updateSuggestions(el.value);
    setSelIdx(-1);
    stateRef.current.tabIdx = -1;
    stateRef.current.tabMatches = [];
  }

  return { inputRef, suggestions, selIdx, handleKeyDown, handleInput, applyCompletion };
}
