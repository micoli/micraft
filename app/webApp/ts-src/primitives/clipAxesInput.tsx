import { ClipAxis, ClipPlaneState } from "../admin/pages/shared/voxelEditor/clipAxis";

interface ClipAxesInputProps {
  axis: ClipAxis;
  clipPlanes: Record<ClipAxis, ClipPlaneState>;
  clipBounds: {
    x: readonly [number, number];
    y: readonly [number, number];
    z: readonly [number, number];
  };
  setClipPlanes: (
    value:
      | ((prevState: Record<ClipAxis, ClipPlaneState>) => Record<ClipAxis, ClipPlaneState>)
      | Record<ClipAxis, ClipPlaneState>,
  ) => void;
}

export function ClipAxesInput({ axis, clipPlanes, clipBounds, setClipPlanes }: ClipAxesInputProps) {
  const cp = clipPlanes[axis];
  const [min, max] = clipBounds[axis];
  return (
    <div key={axis} className="flex items-center gap-1 min-w-0 text-xs text-[#8A99AF]">
      <input
        type="checkbox"
        checked={cp.enabled}
        onChange={(e) => setClipPlanes((s) => ({ ...s, [axis]: { ...s[axis], enabled: e.target.checked } }))}
      />
      <span className="w-3 shrink-0 text-white font-medium uppercase">{axis}</span>
      <input
        type="range"
        min={min}
        max={max}
        step={0.25}
        value={cp.pos}
        disabled={!cp.enabled}
        onChange={(e) => setClipPlanes((s) => ({ ...s, [axis]: { ...s[axis], pos: Number(e.target.value) } }))}
        className="w-0 flex-1 min-w-0 accent-[#3C50E0]"
      />
      <input
        type="checkbox"
        checked={cp.flipped}
        disabled={!cp.enabled}
        title="Flip"
        onChange={(e) => setClipPlanes((s) => ({ ...s, [axis]: { ...s[axis], flipped: e.target.checked } }))}
      />
    </div>
  );
}
