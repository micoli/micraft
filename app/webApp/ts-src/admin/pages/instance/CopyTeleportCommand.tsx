import type { InstanceZoneDto } from "../../apiTypes";
import { useState } from "react";

const CHUNK_SIZE = 16;

function teleportCommandFor(zone: InstanceZoneDto): string {
  const sumX = zone.chunks.reduce((sum, { cx }) => sum + cx * CHUNK_SIZE + CHUNK_SIZE / 2, 0);
  const sumZ = zone.chunks.reduce((sum, { cz }) => sum + cz * CHUNK_SIZE + CHUNK_SIZE / 2, 0);
  const x = Math.round(sumX / zone.chunks.length);
  const z = Math.round(sumZ / zone.chunks.length);
  const y = Math.round((zone.yMin + zone.yMax) / 2);
  return `/teleport ${x} ${y} ${z}`;
}

export function CopyTeleportCommand({ zone }: { zone: InstanceZoneDto }) {
  const [copied, setCopied] = useState(false);
  const command = teleportCommandFor(zone);

  return (
    <button
      className="flex items-center gap-1.5 rounded border border-[#2E3A4E] bg-[#1A222C] px-2 py-1 text-[11px] font-mono text-[#8A99AF] hover:text-white hover:border-[#3C50E0] transition-colors"
      title="Copy /teleport command to the instance's centroid"
      onClick={() => {
        navigator.clipboard
          .writeText(command)
          .then(() => {
            setCopied(true);
            setTimeout(() => setCopied(false), 1500);
          })
          .catch(console.error);
      }}
    >
      {command}
      <span className={copied ? "text-emerald-400" : "text-[#8A99AF]"}>{copied ? "Copied!" : "Copy"}</span>
    </button>
  );
}
