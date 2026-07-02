interface Props {
  notif: { msg: string; key: number } | null;
}

export function Notifications({ notif }: Props) {
  if (!notif) return null;
  return (
    <div className="fixed bottom-24 left-1/2 -translate-x-1/2 bg-black/72 text-white font-mono text-sm px-3.5 py-1.5 rounded z-[1001] pointer-events-none">
      {notif.msg}
    </div>
  );
}
