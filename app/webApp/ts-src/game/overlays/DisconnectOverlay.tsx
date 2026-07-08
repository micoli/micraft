import { useState, useEffect } from "react";

export function DisconnectOverlay({ message }: { message: string | null }) {
  const match = message?.match(/(\d+)s/);
  const totalSecs = match ? parseInt(match[1]) : null;

  const [remaining, setRemaining] = useState<number | null>(totalSecs);

  useEffect(() => {
    if (totalSecs === null) {
      setRemaining(null);
      return;
    }
    setRemaining(totalSecs);
    const interval = setInterval(() => {
      setRemaining((r) => (r !== null && r > 0 ? r - 1 : 0));
    }, 1000);
    return () => clearInterval(interval);
  }, [message]);

  if (!message) return null;

  return (
    <div className="fixed inset-0 z-[1000] flex flex-col items-center justify-center bg-black/92 text-white font-mono text-center">
      <div className="flex flex-col items-center gap-4 bg-black/70 border border-white/20 rounded-xl px-10 py-7">
        <svg
          className="w-10 h-10 text-yellow-400"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth={1.5}
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M5.636 5.636a9 9 0 1 0 12.728 12.728M5.636 5.636A9 9 0 0 1 18.364 18.364M5.636 5.636 18.364 18.364"
          />
        </svg>
        <span className="font-bold text-lg">Serveur inaccessible</span>
        {remaining !== null ? (
          <div className="flex flex-col items-center gap-1">
            <span className="text-5xl font-bold tabular-nums text-white/90">{remaining}</span>
            <span className="text-xs text-white/50 uppercase tracking-widest">reconnexion</span>
          </div>
        ) : (
          <span className="text-sm text-white/60">{message}</span>
        )}
        <div className="flex gap-1">
          {[0, 1, 2].map((i) => (
            <span
              key={i}
              className="w-2 h-2 rounded-full bg-white/50 animate-bounce"
              style={{ animationDelay: `${i * 0.15}s` }}
            />
          ))}
        </div>
      </div>
    </div>
  );
}
