import { animDisplayName } from "../../lib/animationHelpers";
import type { AnimationEntry } from "../../lib/animationHelpers";
import { BbmodelAnimationViewer } from "../../admin/components/BbmodelAnimationViewer";

export function AnimationDetail({ anim, skin }: { anim: AnimationEntry; skin: string }) {
  const display = animDisplayName(anim.fullName);
  const bbmodel = window.mcState?.playerBbmodels?.[skin] ?? null;
  const row = (label: string, value: string) => (
    <div
      key={label}
      style={{ display: "flex", justifyContent: "space-between", padding: "4px 0", borderBottom: "1px solid #2a2a2a" }}
    >
      <span style={{ color: "#888", fontSize: 12 }}>{label}</span>
      <span style={{ color: "#ddd", fontSize: 12 }}>{value}</span>
    </div>
  );

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12, padding: 8 }}>
      <div style={{ display: "flex", justifyContent: "center" }}>
        <BbmodelAnimationViewer bbmodel={bbmodel} animFullName={anim.fullName} width={160} height={220} />
      </div>
      <div style={{ fontSize: 13, fontWeight: "bold", color: "#eee", textAlign: "center" }}>{display}</div>
      <div>
        {row("Durée", `${anim.length.toFixed(3)} s`)}
        {row("Os animés", String(anim.boneCount))}
      </div>
    </div>
  );
}
