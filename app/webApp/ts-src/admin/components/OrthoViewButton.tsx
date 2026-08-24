import type { CSSProperties } from "react";

export const buttonStyle: CSSProperties = {
  width: 22,
  height: 22,
  lineHeight: "20px",
  padding: 0,
  fontSize: 14,
  fontFamily: "monospace",
  color: "#ddd",
  background: "rgba(20, 26, 38, 0.75)",
  border: "1px solid #3a3a3a",
  borderRadius: 4,
  cursor: "pointer",
};
export type OrthoView = "T" | "B" | "N" | "S" | "E" | "W";
// Model yaw (radians) that faces each cardinal side toward the camera, 90° apart.
export const ORTHO_YAW: Record<"T" | "B" | "N" | "E" | "S" | "W", number[]> = {
  T: [0, 0.0001],
  B: [0, Math.PI - 0.0001],
  N: [0, Math.PI / 2],
  E: [Math.PI / 2, Math.PI / 2],
  S: [Math.PI, Math.PI / 2],
  W: [-Math.PI / 2, Math.PI / 2],
};

export function OrthoViewButton({
  view,
  setOrthoViewRef,
}: {
  view: "T" | "B" | "N" | "S" | "E" | "W";
  setOrthoViewRef: React.RefObject<((view: OrthoView) => void) | null>;
}) {
  return (
    <button
      key={view}
      type="button"
      onClick={() => setOrthoViewRef.current?.(view)}
      style={buttonStyle}
      aria-label={`View from ${view}`}
    >
      {view}
    </button>
  );
}
