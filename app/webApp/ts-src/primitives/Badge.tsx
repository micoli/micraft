export function Badge({ children, color }: { children: React.ReactNode; color: string }) {
  return (
    <span className={`inline-block px-1.5 py-0.5 rounded text-[10px] font-semibold uppercase tracking-wide ${color}`}>
      {children}
    </span>
  );
}
