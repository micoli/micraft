export function LoadingOverlay({ progress }: { progress: { meshed: number; downloaded: number; total: number } | null }) {
  if (!progress) return null;
  const { meshed, downloaded, total } = progress;
  const safeTotal = Math.max(total, 1);
  const meshedPct = Math.min(100, (meshed / safeTotal) * 100);
  const downloadedPct = Math.min(100 - meshedPct, (downloaded / safeTotal) * 100);
  const todoPct = Math.max(0, 100 - meshedPct - downloadedPct);
  const overallPct = Math.round(meshedPct + downloadedPct);
  return (
    <div className="fixed inset-0 z-[1000] flex flex-col items-center justify-center bg-black/80 text-white font-mono pointer-events-all">
      <div className="text-xl font-bold mb-5 tracking-wide">Loading world…</div>
      <div className="w-80 bg-[#222] border-2 border-[#555] rounded-sm overflow-hidden mb-2.5 flex h-[18px]">
        <div
          className="h-full bg-gradient-to-r from-green-700 to-green-500 transition-[width] duration-150 ease-out"
          style={{ width: `${meshedPct}%` }}
        />
        <div
          className="h-full bg-gradient-to-r from-orange-600 to-orange-400 transition-[width] duration-150 ease-out"
          style={{ width: `${downloadedPct}%` }}
        />
        <div
          className="h-full bg-gradient-to-r from-red-900 to-red-700 transition-[width] duration-150 ease-out"
          style={{ width: `${todoPct}%` }}
        />
      </div>
      <div className="text-sm text-white/60">
        {meshed} meshed · {downloaded} queued · {total - meshed - downloaded} pending ({overallPct}%)
      </div>
    </div>
  );
}
