// Floating over the viewport canvas, top-right — shows the block name under the cursor while
// hovering a voxel in the 3D view (any mode), independent of ViewportCameraHud's top-center
// camera-modifier hints.
export function HoveredVoxelNameHud({ name }: { name: string | null }) {
  if (!name) return null;
  return (
    <div className="absolute top-2 right-2 z-10 pointer-events-none rounded border border-[#2E3A4E] bg-[#0B1220]/90 px-2 py-1 text-[11px] font-semibold text-white">
      {name.replace(/_/g, " ")}
    </div>
  );
}
