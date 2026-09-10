import { useGameContext } from "../GameContext";

interface Props {
  layoutStyle?: React.CSSProperties;
}

// Angles are screen-space bearings when the player faces yaw 0 (i.e. +Z / "south").
// Verified in-game: forward(yaw) = (sin yaw, cos yaw), so +Z is at screen angle 0 and -Z at 180.
const CARDINALS: { label: string; angle: number }[] = [
  { label: "N", angle: 180 },
  { label: "E", angle: 90 },
  { label: "S", angle: 0 },
  { label: "W", angle: 270 },
];

export function Compass({ layoutStyle }: Props) {
  const { state } = useGameContext();
  const target = state.compassTarget;
  const hud = state.hud;

  if (!target || !hud) return null;

  const dx = target.x - hud.x;
  const dz = target.z - hud.z;
  const dy = target.y - hud.y;
  const flatDist = Math.hypot(dx, dz);
  const realDist = Math.hypot(dx, dy, dz);

  // Movement/yaw convention (verified in-game): forward(yaw) = (sin yaw, cos yaw); strafe-right = (cos yaw, -sin yaw).
  // Screen "up" = forward, so the target's screen bearing (clockwise from up) is atan2(dx, dz) - yaw.
  const norm = (deg: number) => ((deg % 360) + 540) % 360 - 180;
  const needleAngle = norm((Math.atan2(dx, dz) * 180) / Math.PI - hud.yaw);
  const roseAngle = norm(-hud.yaw);

  const vertical = Math.abs(dy) > 2 ? (dy > 0 ? "▲" : "▼") : "";

  return (
    <div
      className="flex flex-col items-center justify-center gap-1 bg-black/55 rounded-md p-2 pointer-events-none z-[998]"
      style={{ ...layoutStyle, userSelect: "none", aspectRatio: "1 / 1" }}
    >
      <svg viewBox="0 0 100 100" className="w-full flex-1 min-h-0">
        <circle cx="50" cy="50" r="46" fill="rgba(0,0,0,0.35)" stroke="rgba(255,255,255,0.25)" strokeWidth="2" />
        {/* fixed heading marker: the top of the dial is the direction the player is looking */}
        <polygon points="50,2 45,12 55,12" fill="#5dade2" />
        <g transform={`rotate(${roseAngle} 50 50)`}>
          {CARDINALS.map((c) => (
            <g key={c.label} transform={`rotate(${c.angle} 50 50)`}>
              <line x1="50" y1="6" x2="50" y2="14" stroke="rgba(255,255,255,0.4)" strokeWidth="2" />
              <text
                x="50"
                y="24"
                textAnchor="middle"
                fontSize="11"
                fill={c.label === "N" ? "#e74c3c" : "rgba(255,255,255,0.7)"}
                fontFamily="monospace"
              >
                {c.label}
              </text>
            </g>
          ))}
        </g>
        {/* red needle points toward the compass target */}
        <g transform={`rotate(${needleAngle} 50 50)`}>
          <polygon points="50,16 43,52 57,52" fill="#e74c3c" />
          <polygon points="50,84 44,50 56,50" fill="rgba(255,255,255,0.35)" />
        </g>
        <circle cx="50" cy="50" r="3" fill="rgba(255,255,255,0.85)" />
      </svg>
      <span className="text-[11px] text-white font-mono leading-none">
        {vertical}
        {Math.round(flatDist)} m
      </span>
      <span className="text-[9px] text-white/60 font-mono leading-none">↗ {Math.round(realDist)} m</span>
      {target.label && (
        <span className="text-[9px] text-white/60 font-mono leading-none truncate max-w-full">{target.label}</span>
      )}
    </div>
  );
}
