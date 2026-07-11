export function PlayerDownedOverlay() {
  return (
    <>
      {/* backdrop-filter + dark tint, pulsing */}
      <div className="downed-filter fixed inset-0 z-[1099] pointer-events-none" />
      {/* red vignette at edges, pulsing */}
      <div className="downed-vignette fixed inset-0 z-[1100] pointer-events-none" />
      {/* text */}
      <div className="fixed inset-0 z-[1101] flex flex-col items-center justify-center pointer-events-none">
        <span className="text-white font-mono text-2xl tracking-widest uppercase drop-shadow-lg">You are downed</span>
        <span className="text-white/50 font-mono text-sm mt-2">Waiting for stabilization or death…</span>
      </div>
    </>
  );
}
