type Rotation = { x: number; y: number; z: number };

interface Props {
  label: string;
  value: Rotation;
  overridden: boolean;
  onChange: (axis: "x" | "y" | "z", value: number) => void;
  onReset: () => void;
}

const AXES = ["x", "y", "z"] as const;

export function RotateSliders({ label, value, overridden, onChange, onReset }: Props) {
  return (
    <div className="mt-2">
      <div className="flex items-center justify-between mb-1">
        <span className="text-[10px] text-[#8A99AF] uppercase tracking-widest">{label} rotate</span>
        {overridden && (
          <button type="button" onClick={onReset} className="text-[10px] text-[#3C50E0] hover:underline">
            reset
          </button>
        )}
      </div>
      {AXES.map((axis) => {
        // kotlinx.serialization omits default (0) fields from the JSON payload, so an axis can
        // arrive as `undefined` here rather than `NaN` — guard both.
        const raw = value[axis];
        const axisValue = typeof raw === "number" && !Number.isNaN(raw) ? raw : 0;
        return (
          <div key={axis} className="flex items-center gap-2 mb-1">
            <span className="text-[10px] text-[#8A99AF] w-3 uppercase">{axis}</span>
            <input
              type="range"
              min={-270}
              max={270}
              step={1}
              value={axisValue}
              onChange={(e) => onChange(axis, Number(e.target.value))}
              className="flex-1"
            />
            <span className="text-[10px] text-[#8A99AF] w-9 text-right tabular-nums">{Math.round(axisValue)}°</span>
          </div>
        );
      })}
    </div>
  );
}
