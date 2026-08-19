// Locomotive glyph (svgrepo.com) rendered inline, recolored via currentColor to follow the
// button's text color — svgr-less setups can't import raw .svg files as components here.
const TRAIN_ICON_PATHS = [
  `M442.784,66.747c-10.807-18.5-30.44-35.316-60.643-47.303C351.889,7.44,310.941,0.016,255.993,0
  c-73.228,0.048-121.696,13.167-152.875,32.925c-15.559,9.88-26.712,21.504-33.917,33.822
  c-7.205,12.302-10.382,25.201-10.367,37.243v259.272c0,41.656,33.759,75.446,75.462,75.462h22.196L104.313,512h41.704
  l12.521-20.286h194.909L365.968,512h41.719l-52.18-73.276h22.197c41.703-0.016,75.446-33.806,75.462-75.462V103.99
  C453.166,91.948,449.988,79.049,442.784,66.747z M170.62,472.145l12.789-20.702h145.182l12.79,20.702H170.62z M429.003,363.262
  c-0.015,14.19-5.726,26.948-15.023,36.268c-9.313,9.305-22.07,15.023-36.276,15.031H134.297
  c-14.205-0.008-26.963-5.726-36.276-15.031c-9.313-9.32-15.024-22.078-15.024-36.268V103.99c0-7.857,2.014-16.383,7.064-25.043
  c7.582-12.939,22.26-26.507,48.719-37.039c26.412-10.516,64.451-17.753,117.213-17.736c70.396-0.04,114.474,12.892,139.928,29.142
  c12.758,8.124,20.938,16.982,26.004,25.634c5.065,8.66,7.064,17.186,7.079,25.043V363.262z`,
  `M188.915,101.521H323.07c8.919,0,16.155-7.221,16.155-16.156c0-8.911-7.236-16.14-16.155-16.14H188.915
  c-8.919,0-16.14,7.229-16.14,16.14C172.775,94.3,179.996,101.521,188.915,101.521z`,
];

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
      <svg viewBox="0 0 512 512" className="w-3.5 h-3.5 shrink-0 fill-current">
        {TRAIN_ICON_PATHS.map((d) => (
          <path key={d} d={d} />
        ))}
        <circle cx="160.017" cy="351.323" r="20.136" />
        <circle cx="351.983" cy="351.323" r="20.136" />
        <rect x="132.016" y="153.669" width="107.49" height="98.319" />
        <rect x="267.696" y="153.669" width="112.288" height="98.319" />
      </svg>
      {testRailState === "running" ? "Stop Test" : testRailState === "picking" ? "Pick Rail…" : "Test Rail"}
    </button>
  );
}
