export function registerCombatTargetHighlight(): Pick<McBindings, "highlightNpcModel"> {
  let highlightLayer: InstanceType<typeof BABYLON.HighlightLayer> | null = null;

  return {
    highlightNpcModel: (scene: unknown, model: unknown, on: boolean): void => {
      if (!highlightLayer) {
        highlightLayer = new BABYLON.HighlightLayer("combatHL", scene as InstanceType<(typeof BABYLON)["Scene"]>);
        highlightLayer.innerGlow = false;
        highlightLayer.outerGlow = true;
      }
      const root = model as InstanceType<typeof BABYLON.TransformNode>;
      const meshes = root.getChildMeshes?.() ?? [];
      if (meshes.length === 0 && typeof (model as { isPickable?: boolean }).isPickable !== "undefined") {
        meshes.push(model as InstanceType<typeof BABYLON.AbstractMesh>);
      }
      const color = new BABYLON.Color3(1, 0.2, 0.2);
      meshes.forEach((mesh) => {
        if (on) {
          highlightLayer!.addMesh(mesh as unknown as InstanceType<typeof BABYLON.Mesh>, color);
        } else {
          highlightLayer!.removeMesh(mesh as unknown as InstanceType<typeof BABYLON.Mesh>);
        }
      });
    },
  };
}
