// Editor-only overlay: one clickable marker per switch/junction rail block, shown while a rail
// test is active (see VoxelEditorSidebar's "Test Rail" button). Each marker floats above its
// block and labels the currently active branch out of the total branch count; clicking one asks
// the caller to cycle it (persisted server-side — see AdminController.applyInstanceSwitchToggle).
const MARKER_SIZE = 0.4;

export interface RailJunction {
  wx: number;
  wy: number;
  wz: number;
  branchCount: number;
  currentBranch: number;
}

export interface RailSwitchMarkers {
  // Replaces the full marker set (called on rail-test start and after every reload).
  update(junctions: RailJunction[]): void;
  // Returns the junction a pick hit, if any of our marker meshes was picked.
  hitTest(pickedMesh: InstanceType<typeof BABYLON.Mesh> | null | undefined): RailJunction | null;
  clear(): void;
  dispose(): void;
}

export function createRailSwitchMarkers(
  B: typeof BABYLON,
  scene: InstanceType<typeof BABYLON.Scene>,
): RailSwitchMarkers {
  let meshes: InstanceType<typeof BABYLON.Mesh>[] = [];

  function makeLabelMaterial(current: number, total: number) {
    const tex = new B.DynamicTexture("railSwitchLabelTex", { width: 128, height: 128 }, scene, false);
    const ctx = tex.getContext() as CanvasRenderingContext2D;
    ctx.fillStyle = "#3C50E0";
    ctx.fillRect(0, 0, 128, 128);
    ctx.fillStyle = "#ffffff";
    ctx.font = "bold 48px sans-serif";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(`${current + 1}/${total}`, 64, 64);
    tex.update();
    const mat = new B.StandardMaterial("railSwitchLabelMat", scene);
    mat.diffuseTexture = tex;
    mat.emissiveColor = B.Color3.White();
    mat.specularColor = B.Color3.Black();
    mat.disableLighting = true;
    return mat;
  }

  function clear() {
    for (const mesh of meshes) mesh.dispose();
    meshes = [];
  }

  return {
    update(junctions) {
      clear();
      meshes = junctions.map((junction) => {
        const mesh = B.MeshBuilder.CreateBox("railSwitchMarker", { size: MARKER_SIZE }, scene);
        mesh.position.set(junction.wx + 0.5, junction.wy + 1 + MARKER_SIZE / 2, junction.wz + 0.5);
        mesh.material = makeLabelMaterial(junction.currentBranch, junction.branchCount);
        mesh.billboardMode = B.Mesh.BILLBOARDMODE_ALL;
        mesh.isPickable = true;
        mesh.metadata = { railJunction: junction };
        return mesh;
      });
    },
    hitTest(pickedMesh) {
      const junction = pickedMesh?.metadata?.railJunction as RailJunction | undefined;
      return junction ?? null;
    },
    clear,
    dispose: clear,
  };
}
