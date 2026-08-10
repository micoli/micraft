import { BOX_H } from "./metrics";

/** Vertical guide marking the hovered slice; the same mark on all three charts. */
export function HoverGuide({ x, width }: { x: number; width: number }) {
  return <rect x={x} y={0} width={Math.max(width, 0.8)} height={BOX_H} fill="#C7D2FE" opacity={0.12} />;
}
