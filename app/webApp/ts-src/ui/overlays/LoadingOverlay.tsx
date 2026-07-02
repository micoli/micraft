export function LoadingOverlay({ progress }: { progress: { loaded: number; total: number } | null }) {
  if (!progress) return null;
  const pct = Math.min(100, Math.round((progress.loaded / Math.max(progress.total, 1)) * 100));
  return (
    <div className="fixed inset-0 z-[1000] flex flex-col items-center justify-center bg-black/80 text-white font-mono pointer-events-all">
      <div className="text-xl font-bold mb-5 tracking-wide">Loading world…</div>
      <div className="w-80 bg-[#222] border-2 border-[#555] rounded-sm overflow-hidden mb-2.5">
        <div
          className="h-[18px] bg-gradient-to-r from-green-600 to-green-400 transition-[width] duration-150 ease-out"
          style={{ width: `${pct}%` }}
        />
      </div>
      <div className="text-sm text-white/60">
        {progress.loaded} / {progress.total} chunks ({pct}%)
      </div>
    </div>
  );
}
