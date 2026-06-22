import { HudData } from './types';

const style: React.CSSProperties = {
  position: 'fixed', top: 12, right: 12,
  background: 'rgba(0,0,0,0.55)', color: '#fff',
  font: '13px/1.6 monospace', padding: '8px 12px',
  borderRadius: 6, pointerEvents: 'none', zIndex: 999,
  whiteSpace: 'pre',
};

export function HUD({ data }: { data: HudData | null }) {
  if (!data) return null;
  const { x, y, z, yaw, pitch, stance, speed, fps, kbIn, kbOut, biome, targetBlock } = data;
  const lines = [
    `FPS   ${fps}`,
    `Pos:  ${x.toFixed(1)},${y.toFixed(1)},${z.toFixed(1)}`,
    `Yaw   ${yaw.toFixed(1)}°`,
    `Pitch ${pitch.toFixed(1)}°`,
    stance,
    `Speed ×${speed.toFixed(1)}`,
    `↓ ${kbIn.toFixed(1)} KB/s  ↑ ${kbOut.toFixed(1)} KB/s`,
    ...(biome ? [`Biome ${biome}`] : []),
    ...(targetBlock ? [`Block ${targetBlock}`] : []),
  ];
  return <div style={style}>{lines.join('\n')}</div>;
}
