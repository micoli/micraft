export function CodexDetailRow({ label, value }: { label: string; value: string | number | boolean }) {
  return (
    <div
      style={{ display: "flex", justifyContent: "space-between", padding: "4px 0", borderBottom: "1px solid #2a2a2a" }}
    >
      <span style={{ color: "#888", fontSize: 12 }}>{label}</span>
      <span style={{ color: "#ddd", fontSize: 12 }}>{String(value)}</span>
    </div>
  );
}
