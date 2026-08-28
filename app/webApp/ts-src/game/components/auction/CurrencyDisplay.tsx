import { CSSProperties } from "react";
import { copperToParts, CURRENCY_COLORS } from "./currency";

interface Props {
  copper: number;
  style?: CSSProperties;
}

export function CurrencyDisplay({ copper, style }: Props) {
  const { g, s, c } = copperToParts(copper);
  const parts: { value: number; unit: keyof typeof CURRENCY_COLORS }[] = [];
  if (g > 0) parts.push({ value: g, unit: "g" });
  if (s > 0) parts.push({ value: s, unit: "s" });
  if (c > 0 || parts.length === 0) parts.push({ value: c, unit: "c" });

  return (
    <span style={{ display: "inline-flex", gap: 4, ...style }}>
      {parts.map(({ value, unit }) => (
        <span key={unit} style={{ color: CURRENCY_COLORS[unit] }}>
          {value}
          {unit}
        </span>
      ))}
    </span>
  );
}
