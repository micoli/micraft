export function DisconnectOverlay({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <div className="fixed inset-0 z-[1000] flex flex-col items-center justify-center bg-black/[0.72] text-white font-mono font-bold text-[22px] leading-loose text-center">
      ⚠️ DISCONNECTED
      <span className="text-[15px] font-normal">{message}</span>
    </div>
  );
}
