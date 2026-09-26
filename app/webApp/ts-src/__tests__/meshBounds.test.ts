import * as Babylon from "@babylonjs/core";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { setBoundsFromPositions } from "../game/lib/meshBounds";

describe("setBoundsFromPositions", () => {
  let engine: Babylon.NullEngine;
  let scene: Babylon.Scene;

  beforeAll(() => {
    (globalThis as { BABYLON?: typeof Babylon }).BABYLON = Babylon;
    engine = new Babylon.NullEngine();
    scene = new Babylon.Scene(engine);
  });

  afterAll(() => engine.dispose());

  const meshWith = (positions: Float32Array) => {
    const mesh = new Babylon.Mesh("chunk", scene);
    const vd = new Babylon.VertexData();
    vd.positions = positions;
    vd.indices = [0, 1, 2];
    vd.applyToMesh(mesh, false);
    return mesh;
  };

  it("matches refreshBoundingInfo without keeping a Vector3 per vertex", () => {
    const positions = new Float32Array([3, -2, 7, -1, 5, 0, 4, 1, -6]);
    const reference = meshWith(positions).refreshBoundingInfo();
    const mesh = meshWith(positions);

    setBoundsFromPositions(mesh, positions);

    const box = mesh.getBoundingInfo().boundingBox;
    expect(box.minimum.asArray()).toEqual(reference.getBoundingInfo().boundingBox.minimum.asArray());
    expect(box.maximum.asArray()).toEqual(reference.getBoundingInfo().boundingBox.maximum.asArray());
    expect(mesh._internalAbstractMeshDataInfo._positions).toBeNull();
  });

  it("still lets a ray pick the mesh", () => {
    const positions = new Float32Array([0, 0, 0, 1, 0, 0, 0, 1, 0]);
    const mesh = meshWith(positions);
    setBoundsFromPositions(mesh, positions);

    const hit = mesh.intersects(new Babylon.Ray(new Babylon.Vector3(0.2, 0.2, -5), new Babylon.Vector3(0, 0, 1)));

    expect(hit.hit).toBe(true);
  });
});
