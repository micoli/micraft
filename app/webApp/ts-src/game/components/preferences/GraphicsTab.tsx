import { UsePreferences } from "../../hooks/usePreferences";
import { NumberInput } from "../../../primitives/NumberInput";

export function GraphicsTab({
  pref,
  fullMeshedChunks,
  impostorMeshedChunks,
}: {
  pref: UsePreferences;
  fullMeshedChunks: number;
  impostorMeshedChunks: number;
}) {
  const effForward = pref.localOverrideForwardViewRadius ?? 7;
  const effImpostorRadius = pref.localOverrideImpostorRadiusChunks ?? 5;

  return (
    <>
      {[
        {
          state: pref.localShaders,
          setter: pref.setLocalShaders,
          label: "Shaders (ambient occlusion, directional shading, fog)",
          title: "Toggles all shader-based lighting effects. Disable for a flat, cheaper render.",
        },
        {
          state: pref.localDynamicFogEnabled,
          setter: pref.setLocalDynamicFogEnabled,
          label: "Dynamic fog (sky color blended into fog, may affect performance)",
          title: "Blends the current sky color into distance fog instead of a fixed color.",
        },
        {
          state: pref.localAnimatedFavicon,
          setter: pref.setLocalAnimatedFavicon,
          label: "Animated favicon (rotating block icon in browser tab)",
          title: "Shows a rotating block icon in the browser tab while playing.",
        },
        {
          state: pref.localAutoTarget,
          setter: pref.setLocalAutoTarget,
          label: "Auto-target nearest aggro mob (when no target selected)",
          title: "Automatically selects the nearest hostile mob as your target when you have none.",
        },
      ].map(({ state, setter, label, title }) => (
        <div key={label} className="flex items-center gap-2 py-1.5 border-b border-[#2a2a2a]" title={title}>
          <input type="checkbox" checked={state} onChange={(e) => setter(e.target.checked)} />
          <span>{label}</span>
        </div>
      ))}
      <div
        className="flex flex-col gap-1 py-2 border-b border-[#2a2a2a]"
        title="Horizontal camera field of view. Wider values show more of the scene but distort the edges."
      >
        <div className="flex justify-between text-sm">
          <span>Field of View</span>
          <span className="text-[#aaa]">{pref.localFov}°</span>
        </div>
        <input
          type="range"
          min={60}
          max={120}
          step={1}
          value={pref.localFov}
          onChange={(e) => pref.setLocalFov(Number(e.target.value))}
          className="w-full accent-[#888]"
        />
        <div className="flex justify-between text-xs text-[#666]">
          <span>60° (narrow)</span>
          <span>120° (fisheye)</span>
        </div>
      </div>
      <div
        className="flex flex-col gap-1 py-2 border-b border-[#2a2a2a]"
        title="Minimum sun-angle change before shadows are recomputed. Higher values recompute less often (cheaper, less smooth)."
      >
        <div className="flex justify-between text-sm">
          <span>Shadow update threshold</span>
          <span className="text-[#aaa]">{pref.localShadowAngleDeg}°</span>
        </div>
        <input
          type="range"
          min={1}
          max={10}
          step={1}
          value={pref.localShadowAngleDeg}
          onChange={(e) => pref.setLocalShadowAngleDeg(Number(e.target.value))}
          className="w-full accent-[#888]"
        />
        <div className="flex justify-between text-xs text-[#666]">
          <span>1° (smooth)</span>
          <span>10° (perf)</span>
        </div>
      </div>

      <div className="pt-2 text-[11px] text-[#888] uppercase tracking-wide">Rendering overrides</div>
      {[
        {
          value: pref.localOverrideForwardViewRadius,
          setter: pref.setLocalOverrideForwardViewRadius,
          label: "Forward view radius",
          title:
            "How many chunks (in the direction you're facing) are streamed from the server and kept loaded. Caps every other radius below.",
          fallback: 7,
          min: 1,
          max: 16,
        },
        {
          value: pref.localOverrideImpostorRadiusChunks,
          setter: pref.setLocalOverrideImpostorRadiusChunks,
          label: "Impostor radius (chunks)",
          title:
            "Chunks beyond this radius are rendered as flat impostors instead of full geometry. Can't exceed forward view radius.",
          fallback: 5,
          min: 0,
          max: effForward,
        },
        {
          value: pref.localOverrideImpostorFovBonusChunks,
          setter: pref.setLocalOverrideImpostorFovBonusChunks,
          label: "Impostor FOV bonus (chunks)",
          title:
            "Extra radius granted to chunks in front of you (within the ~60° view cone), keeping full geometry farther out ahead while chunks to the side/behind still switch to impostor at the base radius.",
          fallback: 2,
          min: 0,
          max: Math.max(0, effForward - effImpostorRadius),
        },
      ].map(({ value, setter, label, title, fallback, min, max }) => (
        <div key={label} className="flex items-center gap-2 py-1.5 border-b border-[#2a2a2a]" title={title}>
          <input
            type="checkbox"
            checked={value !== null}
            onChange={(e) => setter(e.target.checked ? fallback : null)}
          />
          <span className="flex-1">{label}</span>
          <NumberInput
            min={min}
            max={max}
            disabled={value === null || (label !== "Forward view radius" && pref.localOverrideUseImpostor === false)}
            value={Math.min(value ?? fallback, max)}
            onChange={(e) => setter(Number(e.target.value))}
            className="w-16 bg-[#2a2a2a] border border-[#555] rounded-sm text-[#eee] px-1.5 py-0.5 text-xs font-mono outline-none disabled:opacity-40"
          />
        </div>
      ))}
      <div
        className="flex items-center gap-2 py-1.5 border-b border-[#2a2a2a]"
        title="Renders far chunks as cheap flat impostors instead of full geometry. Disabling forces full detail everywhere within forward view radius."
      >
        <input
          type="checkbox"
          checked={pref.localOverrideUseImpostor !== null}
          onChange={(e) => pref.setLocalOverrideUseImpostor(e.target.checked ? true : null)}
        />
        <span className="flex-1">Far-chunk impostors</span>
        <input
          type="checkbox"
          disabled={pref.localOverrideUseImpostor === null}
          checked={pref.localOverrideUseImpostor ?? true}
          onChange={(e) => pref.setLocalOverrideUseImpostor(e.target.checked)}
          className="disabled:opacity-40"
        />
      </div>
      <div
        className="flex justify-between py-1.5 text-xs text-[#888]"
        title="Live count of currently loaded chunks rendered at full detail vs. as flat impostors."
      >
        <span>Full-mesh chunks: {fullMeshedChunks}</span>
        <span>Impostor chunks: {impostorMeshedChunks}</span>
      </div>
    </>
  );
}
