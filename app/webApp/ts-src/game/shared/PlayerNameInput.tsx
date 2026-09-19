import { useState } from "react";
import { Input } from "../../primitives/Input";

interface Props {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
}

/** Text input with an online-player-name dropdown, filtered from `window.mcState.connectedPlayers`
 * — the same client-side list the console's `/kick`/`/teleport`/`/talk` completers already use
 * (`game/lib/utils.ts` `playerSuggestions`), so no server round-trip is needed here. */
export function PlayerNameInput({ value, onChange, placeholder }: Props) {
  const [focused, setFocused] = useState(false);
  const trimmed = value.trim().toLowerCase();
  const suggestions = trimmed
    ? (window.mcState.connectedPlayers || []).filter((p) => p.name.toLowerCase().startsWith(trimmed)).slice(0, 8)
    : [];

  return (
    <div style={{ position: "relative", flex: 1 }}>
      <Input
        value={value}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
        onFocus={() => setFocused(true)}
        // Delayed so a click on a suggestion (which also blurs the input) still registers.
        onBlur={() => setTimeout(() => setFocused(false), 150)}
      />
      {focused && suggestions.length > 0 && (
        <ul
          style={{
            position: "absolute",
            top: "100%",
            left: 0,
            right: 0,
            zIndex: 10,
            margin: "2px 0 0",
            padding: 4,
            listStyle: "none",
            background: "#111",
            border: "1px solid #555",
            borderRadius: 4,
            maxHeight: 160,
            overflowY: "auto",
          }}
        >
          {suggestions.map((p) => (
            <li key={p.id}>
              <button
                type="button"
                onClick={() => onChange(p.name)}
                style={{
                  display: "block",
                  width: "100%",
                  textAlign: "left",
                  padding: "4px 6px",
                  background: "transparent",
                  border: "none",
                  color: "#eee",
                  fontFamily: "monospace",
                  fontSize: 13,
                  cursor: "pointer",
                }}
              >
                {p.name}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
