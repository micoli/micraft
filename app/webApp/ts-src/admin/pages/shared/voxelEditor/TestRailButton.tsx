// Floating over the viewport canvas, top-left — the rail circuit test toggle lives here instead
// of the sidebar's mode-button row since, like ViewportCameraHud/HoveredVoxelNameHud, it's tied to
// interacting with the 3D view itself (picking a rail block, watching the cart run) rather than
// being an editing mode alongside Place/Select/Clip Pane.
export function TestRailButton({
  testRailState,
  onToggle,
}: {
  testRailState: "idle" | "picking" | "running";
  onToggle: () => void;
}) {
  return (
    <button
      onClick={onToggle}
      title={testRailState === "picking" ? "Click a rail block to start the test" : undefined}
      className={`absolute top-2 left-2 z-10 flex items-center gap-1.5 px-2 py-1 rounded text-[10px] font-medium transition-colors pointer-events-auto ${
        testRailState === "running"
          ? "bg-orange-600 text-white"
          : testRailState === "picking"
            ? "bg-[#3C50E0] text-white"
            : "bg-black/50 text-[#8A99AF]"
      }`}
    >
      <span className="text-sm leading-none">🛤️</span>
      {testRailState === "running" ? "Stop Test" : testRailState === "picking" ? "Pick Rail…" : "Test Rail"}
    </button>
  );
}
