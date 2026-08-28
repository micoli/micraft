import { CSSProperties, useState } from "react";
import { CurrencyDigitInput } from "./CurrencyDigitInput";
import { CURRENCY_COLORS, copperToParts, partsToCopper } from "./currency";

interface Props {
  initialCopper?: number;
  onChange: (copper: number | null) => void;
  style?: CSSProperties;
}

const UNIT_COPPER = { g: 100, s: 10, c: 1 } as const;

function clampField(raw: string, max?: number): string {
  if (raw.trim() === "") return "";
  const n = Math.max(0, Math.floor(Number(raw)) || 0);
  return String(max !== undefined ? Math.min(n, max) : n);
}

export function CurrencyInput({ initialCopper, onChange, style }: Props) {
  const initial = initialCopper ? copperToParts(initialCopper) : null;
  const [g, setG] = useState(initial && initial.g > 0 ? String(initial.g) : "");
  const [s, setS] = useState(initial && initial.s > 0 ? String(initial.s) : "");
  const [c, setC] = useState(initial && initial.c > 0 ? String(initial.c) : "");

  const emit = (nextG: string, nextS: string, nextC: string) => {
    if (nextG.trim() === "" && nextS.trim() === "" && nextC.trim() === "") {
      onChange(null);
      return;
    }
    onChange(
      partsToCopper({
        g: Number(nextG) || 0,
        s: Number(nextS) || 0,
        c: Number(nextC) || 0,
      }),
    );
  };

  const step = (unit: keyof typeof UNIT_COPPER, delta: number) => {
    const total = partsToCopper({ g: Number(g) || 0, s: Number(s) || 0, c: Number(c) || 0 });
    const parts = copperToParts(Math.max(0, total + delta * UNIT_COPPER[unit]));
    setG(String(parts.g));
    setS(String(parts.s));
    setC(String(parts.c));
    emit(String(parts.g), String(parts.s), String(parts.c));
  };

  return (
    <div style={{ display: "flex", gap: 4, alignItems: "center", ...style }}>
      <CurrencyDigitInput
        value={g}
        color={CURRENCY_COLORS.g}
        onChange={(next) => {
          const clamped = clampField(next);
          setG(clamped);
          emit(clamped, s, c);
        }}
        onStep={(delta) => step("g", delta)}
      />
      <span style={{ color: CURRENCY_COLORS.g, fontSize: 12 }}>g</span>
      <CurrencyDigitInput
        value={s}
        color={CURRENCY_COLORS.s}
        width={50}
        onChange={(next) => {
          const clamped = clampField(next, 9);
          setS(clamped);
          emit(g, clamped, c);
        }}
        onStep={(delta) => step("s", delta)}
      />
      <span style={{ color: CURRENCY_COLORS.s, fontSize: 12 }}>s</span>
      <CurrencyDigitInput
        value={c}
        color={CURRENCY_COLORS.c}
        width={50}
        onChange={(next) => {
          const clamped = clampField(next, 9);
          setC(clamped);
          emit(g, s, clamped);
        }}
        onStep={(delta) => step("c", delta)}
      />
      <span style={{ color: CURRENCY_COLORS.c, fontSize: 12 }}>c</span>
    </div>
  );
}
