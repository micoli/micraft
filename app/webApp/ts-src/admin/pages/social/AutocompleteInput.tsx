import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";

/**
 * Visible autocomplete: a text input with a filtered dropdown of `options`.
 * Unlike a bare `<datalist>` the suggestion list is always rendered on focus,
 * navigable with the arrow keys, and selectable by click or Enter.
 *
 * The dropdown is `position: fixed`, anchored to the input's bounding box, so it
 * is not clipped by an ancestor's `overflow: hidden` (admin panels use it a lot).
 */
export function AutocompleteInput({
  value,
  onChange,
  options,
  placeholder,
  disabled,
  onEnterEmpty,
  autoFocus,
}: {
  value: string;
  onChange: (v: string) => void;
  options: string[];
  placeholder?: string;
  disabled?: boolean;
  /** Called on Enter when the dropdown is closed / nothing highlighted. */
  onEnterEmpty?: () => void;
  autoFocus?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [highlight, setHighlight] = useState(0);
  const [rect, setRect] = useState<DOMRect | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const matches = useMemo(() => {
    const q = value.trim().toLowerCase();
    const list = q ? options.filter((o) => o.toLowerCase().includes(q)) : options;
    return list.slice(0, 50);
  }, [value, options]);

  useLayoutEffect(() => {
    if (open) setRect(inputRef.current?.getBoundingClientRect() ?? null);
  }, [open, value, matches.length]);

  useEffect(() => {
    if (!open) return;
    const close = () => setOpen(false);
    const onDoc = (e: MouseEvent) => {
      if (!inputRef.current?.parentElement?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onDoc);
    window.addEventListener("resize", close);
    window.addEventListener("scroll", close, true);
    return () => {
      document.removeEventListener("mousedown", onDoc);
      window.removeEventListener("resize", close);
      window.removeEventListener("scroll", close, true);
    };
  }, [open]);

  const pick = (name: string) => {
    onChange(name);
    setOpen(false);
  };

  return (
    <div className="relative flex-1">
      <input
        ref={inputRef}
        value={value}
        placeholder={placeholder}
        disabled={disabled}
        autoFocus={autoFocus}
        onChange={(e) => {
          onChange(e.target.value);
          setOpen(true);
          setHighlight(0);
        }}
        onFocus={() => setOpen(true)}
        onKeyDown={(e) => {
          if (e.key === "ArrowDown") {
            e.preventDefault();
            setOpen(true);
            setHighlight((h) => Math.min(h + 1, matches.length - 1));
          } else if (e.key === "ArrowUp") {
            e.preventDefault();
            setHighlight((h) => Math.max(h - 1, 0));
          } else if (e.key === "Enter") {
            if (open && matches[highlight]) {
              e.preventDefault();
              pick(matches[highlight]);
            } else {
              onEnterEmpty?.();
            }
          } else if (e.key === "Escape") {
            setOpen(false);
          }
        }}
        className="w-full bg-[#0E1726] border border-[#2E3A4E] rounded px-2 py-1 text-xs text-white focus:outline-none focus:border-[#3C50E0]"
      />
      {open && rect && matches.length > 0 && (
        <ul
          className="fixed z-[1000] max-h-48 overflow-y-auto bg-[#1C2434] border border-[#2E3A4E] rounded shadow-lg"
          style={{ top: rect.bottom + 2, left: rect.left, width: rect.width }}
        >
          {matches.map((name, i) => (
            <li key={name}>
              <button
                type="button"
                onMouseDown={(e) => {
                  e.preventDefault();
                  pick(name);
                }}
                onMouseEnter={() => setHighlight(i)}
                className={`w-full text-left px-2 py-1 text-xs ${
                  i === highlight ? "bg-[#3C50E0]/30 text-white" : "text-[#8A99AF] hover:text-white"
                }`}
              >
                {name}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
