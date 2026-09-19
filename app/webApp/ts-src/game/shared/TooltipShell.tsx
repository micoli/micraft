export function TooltipShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 z-[9999] bg-black/90 border border-white/30 rounded-md px-3 py-2 text-white font-mono text-xs whitespace-nowrap pointer-events-none flex flex-col items-center gap-0.5 min-w-[130px]">
      {children}
    </div>
  );
}
