import { cn } from "./cn";

export interface BarSegment {
  value: number;
  className?: string;
  color?: string;
}

interface SegmentedBarProps {
  segments: BarSegment[];
  className?: string;
  style?: React.CSSProperties;
}

export function SegmentedBar({ segments, className, style }: SegmentedBarProps) {
  const total = segments.reduce((sum, s) => sum + Math.max(0, s.value), 0) || 1;
  return (
    <div className={cn("flex overflow-hidden", className)} style={style}>
      {segments.map((s, k) => (
        <div
          key={k}
          className={cn("transition-[width] duration-150 ease-out", s.className)}
          style={{ width: `${(Math.max(0, s.value) / total) * 100}%`, background: s.color }}
        />
      ))}
    </div>
  );
}
