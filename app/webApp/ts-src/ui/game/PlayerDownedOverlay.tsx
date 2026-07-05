export function PlayerDownedOverlay() {
  return (
    <div className="fixed inset-0 z-[1100] flex flex-col items-center justify-center pointer-events-none"
      style={{ background: "rgba(80,0,0,0.55)", backdropFilter: "grayscale(60%) brightness(0.6)" }}
    >
      <span className="text-white font-mono text-2xl tracking-widest uppercase">
        You are downed
      </span>
      <span className="text-white/50 font-mono text-sm mt-2">
        Waiting for stabilization or death…
      </span>
    </div>
  );
}
