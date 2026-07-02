export function LoadingOverlay({ progress }: { progress: { loaded: number; total: number } | null }) {
  if (!progress) return null;
  const pct = Math.min(100, Math.round((progress.loaded / Math.max(progress.total, 1)) * 100));
  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        background: "rgba(0,0,0,0.80)",
        color: "#fff",
        fontFamily: "monospace",
        zIndex: 1000,
        pointerEvents: "all",
      }}
    >
      <div style={{ fontSize: 20, fontWeight: "bold", marginBottom: 20, letterSpacing: 1 }}>Loading world…</div>
      <div
        style={{
          width: 320,
          background: "#222",
          border: "2px solid #555",
          borderRadius: 3,
          overflow: "hidden",
          marginBottom: 10,
        }}
      >
        <div
          style={{
            width: `${pct}%`,
            height: 18,
            background: "linear-gradient(90deg, #3a3, #5c5)",
            transition: "width 0.15s ease-out",
          }}
        />
      </div>
      <div style={{ fontSize: 13, color: "#aaa" }}>
        {progress.loaded} / {progress.total} chunks ({pct}%)
      </div>
    </div>
  );
}
