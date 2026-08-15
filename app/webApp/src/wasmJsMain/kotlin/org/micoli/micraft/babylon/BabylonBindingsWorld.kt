@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.micoli.micraft.babylon

// ── Meshes

fun jsCreateBox(name: String, size: Double, scene: JsAny): JsAny =
    js("mc.createBox(name, size, scene)")

fun jsCreateSimpleBox(name: String, size: Double, scene: JsAny): JsAny =
    js("mc.createSimpleBox(name, size, scene)")

fun jsFreezeMesh(mesh: JsAny): Unit = js("mc.freezeMesh(mesh)")

fun jsOptimizeScene(scene: JsAny): Unit = js("mc.optimizeScene(scene)")

fun jsSetupFog(scene: JsAny, r: Double, g: Double, b: Double): Unit =
    js("mc.setupFog(scene, r, g, b)")

fun jsSetShadersEnabled(scene: JsAny, enabled: Boolean): Unit =
    js("mc.setShadersEnabled(scene, enabled)")

fun jsUpdateSkyTime(scene: JsAny, t: Double): Unit = js("mc.updateSkyTime(scene, t)")

fun jsSetCaveFactor(factor: Double): Unit =
    js("{ if(window.mcState) window.mcState.caveFactor = factor }")

fun jsSetPlayerLight(scene: JsAny, x: Double, y: Double, z: Double, intensity: Double): Unit =
    js("mc.setPlayerLight(scene, x, y, z, intensity)")

fun jsSetMeshPosition(mesh: JsAny, x: Double, y: Double, z: Double): Unit =
    js("mesh.position = new BABYLON.Vector3(x,y,z)")

fun jsDisposeMesh(mesh: JsAny): Unit = js("mesh.dispose()")

// ── Materials

fun jsCreateMaterial(name: String, scene: JsAny): JsAny =
    js("new BABYLON.StandardMaterial(name, scene)")

fun jsSetMaterialColor(mat: JsAny, r: Double, g: Double, b: Double): Unit =
    js("mat.diffuseColor = new BABYLON.Color3(r,g,b)")

fun jsSetMeshMaterial(mesh: JsAny, mat: JsAny): Unit = js("mesh.material = mat")

// ── Chunk geometry builder

fun jsChunkBegin(cx: Int, cz: Int): Unit = js("mc.chunkBegin(cx, cz)")

// Batch approach: write face data directly into a pre-allocated JS Int32Array.
// Eliminates JS function-call dispatch and dict lookup per face; work deferred to
// the tight loop in chunkEnd (which the JS engine can JIT more aggressively).
// Stride is 7 ints/face — 6th slot is runLenX, 7th is runLenZ (greedy-merge run lengths from
// renderRow along the block's local X and/or Z axis; 1 = unmerged along that axis). Both slots
// are always written by every append variant below so chunkProcessFaces can read a fixed stride
// regardless of which call wrote it. Only top/bottom faces ever have both > 1 at once (full 2D
// rectangle merge, see ChunkManager.emitGreedyRects) — east/west only ever stretch runLenZ,
// south/north only ever stretch runLenX (see stretchVertsAxis/stretchUVAxis in chunkBuilder.ts).
fun jsChunkFaceAppend(wx: Int, wy: Int, wz: Int, faceMat: Int, ao: Int): Unit =
    js(
        "{const i=window.__mcFI;window.__mcFB[i]=wx;window.__mcFB[i+1]=wy;window.__mcFB[i+2]=wz;window.__mcFB[i+3]=faceMat;window.__mcFB[i+4]=ao;window.__mcFB[i+5]=1;window.__mcFB[i+6]=1;window.__mcFI=i+7}")

// The ao int is a bitfield: bits 0-15 = per-vertex AO levels, bits 16-17 = yOffset,
// bits 18-23 = plain color index (0 = textured), bits 24-25 = xOffset, bits 26-27 = zOffset.
//
// Like jsChunkFaceAppend but packs yOffset (0..2) into bits 16-17 of ao so chunkProcessFaces
// can shift geometry by yOffset/3 within the cell.
fun jsChunkFaceAppendYOffset(wx: Int, wy: Int, wz: Int, yOffset: Int, faceMat: Int, ao: Int): Unit =
    js(
        "{const i=window.__mcFI;window.__mcFB[i]=wx;window.__mcFB[i+1]=wy;window.__mcFB[i+2]=wz;window.__mcFB[i+3]=faceMat;window.__mcFB[i+4]=(ao|(yOffset<<16));window.__mcFB[i+5]=1;window.__mcFB[i+6]=1;window.__mcFI=i+7}")

// Packs yOffset, xOffset and zOffset into ao bitfield alongside AO+color bits.
// xOffset packed into bits 24-25, zOffset into bits 26-27.
fun jsChunkFaceAppendXZOffset(
    wx: Int,
    wy: Int,
    wz: Int,
    yOffset: Int,
    xOffset: Int,
    zOffset: Int,
    faceMat: Int,
    ao: Int
): Unit =
    js(
        "{const i=window.__mcFI;const packed=(ao|(yOffset<<16)|(xOffset<<24)|(zOffset<<26));window.__mcFB[i]=wx;window.__mcFB[i+1]=wy;window.__mcFB[i+2]=wz;window.__mcFB[i+3]=faceMat;window.__mcFB[i+4]=packed;window.__mcFB[i+5]=1;window.__mcFB[i+6]=1;window.__mcFI=i+7}")

// Like jsChunkFaceAppend but for a run of runLenX blocks merged along local X (south/north faces).
fun jsChunkFaceAppendRunX(wx: Int, wy: Int, wz: Int, faceMat: Int, ao: Int, runLenX: Int): Unit =
    js(
        "{const i=window.__mcFI;window.__mcFB[i]=wx;window.__mcFB[i+1]=wy;window.__mcFB[i+2]=wz;window.__mcFB[i+3]=faceMat;window.__mcFB[i+4]=ao;window.__mcFB[i+5]=runLenX;window.__mcFB[i+6]=1;window.__mcFI=i+7}")

// Like jsChunkFaceAppend but for a run of runLenZ blocks merged along local Z (east/west faces).
fun jsChunkFaceAppendRunZ(wx: Int, wy: Int, wz: Int, faceMat: Int, ao: Int, runLenZ: Int): Unit =
    js(
        "{const i=window.__mcFI;window.__mcFB[i]=wx;window.__mcFB[i+1]=wy;window.__mcFB[i+2]=wz;window.__mcFB[i+3]=faceMat;window.__mcFB[i+4]=ao;window.__mcFB[i+5]=1;window.__mcFB[i+6]=runLenZ;window.__mcFI=i+7}")

// Full 2D rectangle merge (top/bottom faces only) — runLenX × runLenZ blocks merged into one
// quad by ChunkManager.emitGreedyRects.
fun jsChunkFaceAppendRun2D(
    wx: Int,
    wy: Int,
    wz: Int,
    faceMat: Int,
    ao: Int,
    runLenX: Int,
    runLenZ: Int
): Unit =
    js(
        "{const i=window.__mcFI;window.__mcFB[i]=wx;window.__mcFB[i+1]=wy;window.__mcFB[i+2]=wz;window.__mcFB[i+3]=faceMat;window.__mcFB[i+4]=ao;window.__mcFB[i+5]=runLenX;window.__mcFB[i+6]=runLenZ;window.__mcFI=i+7}")

// Process a budget slice of __mcFB into FaceGroups; returns faces processed.
fun jsChunkProcessFaces(cursor: Int, maxFaces: Int): Int =
    js("mc.chunkProcessFaces(cursor, maxFaces)")

// Total faces written to __mcFB by jsChunkFaceAppend calls for the current chunk.
fun jsGetFaceCount(): Int = js("(window.__mcFI / 7) | 0")

fun jsChunkEnd(scene: JsAny, materials: JsAny): Unit = js("mc.chunkEnd(scene, materials)")

fun jsDisposeChunk(key: String): Unit = js("mc.disposeChunk(key)")

// Cheap flat-colored stand-in mesh for a chunk far from the viewer — see
// ChunkManager.IMPOSTOR_RADIUS_CHUNKS and chunkBuilder.ts's buildChunkImpostorMesh.
fun jsBuildChunkImpostor(scene: JsAny, cx: Int, cz: Int): Unit =
    js("mc.buildChunkImpostor(scene, cx, cz)")

// Server-configured skirt depth (world.impostorSkirtDepth in server.yaml) pushed via
// RegistrySync — see WorldConstants.IMPOSTOR_SKIRT_DEPTH and buildChunkImpostorMesh.
fun jsSetImpostorSkirtDepth(depth: Int): Unit = js("mc.setImpostorSkirtDepth(depth)")

// scene.activeCamera is the FPS camera in normal play (tracks the player) and the free orbit
// camera while /mode creative is active (see creativeMode.ts) — reading its position, rather
// than the player's server-authoritative position, is what makes the impostor radius follow the
// camera you're actually looking through instead of leaving it pinned to a possibly-distant
// player position while flying around in creative mode.
fun jsGetActiveCameraChunkX(scene: JsAny): Int =
    js("(function(){var c=scene.activeCamera;return c?Math.floor(c.position.x/16):0;})()")

fun jsGetActiveCameraChunkZ(scene: JsAny): Int =
    js("(function(){var c=scene.activeCamera;return c?Math.floor(c.position.z/16):0;})()")

// ── Block definitions (bbmodel-driven)

fun jsInitBlockDefs(): Unit = js("mc.initBlockDefs()")

fun jsIsBlockDefsReady(): Boolean = js("mc.isBlockDefsReady()")

fun jsCreateBlockMaterials(scene: JsAny): JsAny = js("mc.createBlockMaterials(scene)")

fun jsSetGrassTint(r: Double, g: Double, b: Double): Unit = js("mc.setGrassTint(r, g, b)")
