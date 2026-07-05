import { MutableRefObject, useEffect, useRef } from "react";
import { cn } from "../primitives/cn";
import { useConsole } from "../hooks/useConsole";

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

export function Console({ open, onClose, submittedRef, stateRef, initialValueRef, layoutStyle }: Props) {
  const listRef = useRef<HTMLDivElement>(null);
  const { inputRef, suggestions, selIdx, handleKeyDown, handleInput, applyCompletion } = useConsole({
    open,
    onClose,
    submittedRef,
    stateRef,
    initialValueRef,
  });

  useEffect(() => {
    if (selIdx < 0 || !listRef.current) return;
    const item = listRef.current.children[selIdx] as HTMLElement | undefined;
    item?.scrollIntoView({ block: "nearest" });
  }, [selIdx]);

  if (!open) return null;

  return (
    <div
      className={cn(
        "bg-black/72 rounded z-[1002] box-border",
        layoutStyle && Object.keys(layoutStyle).length > 0
          ? "relative"
          : "fixed bottom-[60px] left-1/2 -translate-x-1/2 w-[60%]",
      )}
      style={layoutStyle && Object.keys(layoutStyle).length > 0 ? { ...layoutStyle, height: undefined } : undefined}
    >
      {suggestions.length > 0 && (
        <div
          ref={listRef}
          className="absolute bottom-full left-0 right-0 bg-black/85 rounded-t border-b border-white/15 max-h-[40vh] overflow-y-auto"
        >
          {suggestions.map((s, i) => (
            <div
              key={s}
              onMouseDown={(e) => {
                e.preventDefault();
                applyCompletion(inputRef.current!.value, s);
                inputRef.current!.focus();
              }}
              className={cn(
                "px-2 py-0.5 cursor-pointer font-mono text-sm",
                i === selIdx ? "bg-blue-300 text-black" : "text-white/80 hover:bg-white/10",
              )}
            >
              {s}
            </div>
          ))}
        </div>
      )}
      <div className="px-2 py-1">
        <input
          ref={inputRef}
          type="text"
          className="w-full bg-transparent border-none text-white font-mono text-[15px] outline-none"
          onKeyDown={handleKeyDown}
          onInput={handleInput}
        />
      </div>
    </div>
  );
}
