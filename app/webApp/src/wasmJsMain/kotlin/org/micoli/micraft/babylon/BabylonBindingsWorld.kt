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
fun jsChunkFaceAppend(wx: Int, wy: Int, wz: Int, faceMat: Int, ao: Int): Unit =
    js(
        "{const i=window.__mcFI;window.__mcFB[i]=wx;window.__mcFB[i+1]=wy;window.__mcFB[i+2]=wz;window.__mcFB[i+3]=faceMat;window.__mcFB[i+4]=ao;window.__mcFI=i+5}")

// Process a budget slice of __mcFB into FaceGroups; returns faces processed.
fun jsChunkProcessFaces(cursor: Int, maxFaces: Int): Int =
    js("mc.chunkProcessFaces(cursor, maxFaces)")

// Total faces written to __mcFB by jsChunkFaceAppend calls for the current chunk.
fun jsGetFaceCount(): Int = js("(window.__mcFI / 5) | 0")

fun jsChunkEnd(scene: JsAny, materials: JsAny): Unit = js("mc.chunkEnd(scene, materials)")

fun jsDisposeChunk(key: String): Unit = js("mc.disposeChunk(key)")

// ── Block definitions (bbmodel-driven)

fun jsInitBlockDefs(): Unit = js("mc.initBlockDefs()")

fun jsIsBlockDefsReady(): Boolean = js("mc.isBlockDefsReady()")

fun jsCreateBlockMaterials(scene: JsAny): JsAny = js("mc.createBlockMaterials(scene)")

fun jsSetGrassTint(r: Double, g: Double, b: Double): Unit = js("mc.setGrassTint(r, g, b)")
// wasm-trigger 1784213500
