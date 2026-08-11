import { useEffect, useState } from "react";
import { ActiveEffect } from "../UIStateRegistry";
import { BuffBadge } from "./BuffBadge";

interface Props {
  effects: ActiveEffect[];
  layoutStyle?: React.CSSProperties;
}

export function BuffBar({ effects, layoutStyle }: Props) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (effects.length === 0) return;
    const id = setInterval(() => setNow(Date.now()), 500);
    return () => clearInterval(id);
  }, [effects.length]);
  const active = effects.filter((e) => e.expiresAtMs > now);
  if (active.length === 0) return null;

  return (
    <div className="absolute flex gap-1 pointer-events-none" style={layoutStyle}>
      {active.map((e) => (
        <BuffBadge key={e.name} effect={e} />
      ))}
    </div>
  );
}
