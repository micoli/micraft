export interface CurrencyParts {
  g: number;
  s: number;
  c: number;
}

export const CURRENCY_COLORS = {
  g: "#ffd700",
  s: "#c0c0c0",
  c: "#b87333",
} as const;

export function copperToParts(copper: number): CurrencyParts {
  const total = Math.max(0, Math.floor(copper));
  return {
    g: Math.floor(total / 100),
    s: Math.floor((total % 100) / 10),
    c: total % 10,
  };
}

export function partsToCopper(parts: CurrencyParts): number {
  return parts.g * 100 + parts.s * 10 + parts.c;
}
