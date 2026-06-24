@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.micoli.micraft.babylon

// ── Meshes ────────────────────────────────────────────────────────────────────

fun jsCreateBox(name: String, size: Double, scene: JsAny): JsAny =
    js("mcCreateBox(name, size, scene)")

fun jsCreateSimpleBox(name: String, size: Double, scene: JsAny): JsAny =
    js("mcCreateSimpleBox(name, size, scene)")

fun jsFreezeMesh(mesh: JsAny): Unit = js("mcFreezeMesh(mesh)")

fun jsOptimizeScene(scene: JsAny): Unit = js("mcOptimizeScene(scene)")

fun jsSetupFog(scene: JsAny, r: Double, g: Double, b: Double): Unit =
    js("mcSetupFog(scene, r, g, b)")

fun jsSetShadersEnabled(scene: JsAny, enabled: Boolean): Unit =
    js("mcSetShadersEnabled(scene, enabled)")

fun jsUpdateSkyTime(scene: JsAny, t: Double): Unit = js("mcUpdateSkyTime(scene, t)")

fun jsSetMeshPosition(mesh: JsAny, x: Double, y: Double, z: Double): Unit =
    js("mesh.position = new BABYLON.Vector3(x,y,z)")

fun jsDisposeMesh(mesh: JsAny): Unit = js("mesh.dispose()")

// ── Materials ─────────────────────────────────────────────────────────────────

fun jsCreateMaterial(name: String, scene: JsAny): JsAny =
    js("new BABYLON.StandardMaterial(name, scene)")

fun jsSetMaterialColor(mat: JsAny, r: Double, g: Double, b: Double): Unit =
    js("mat.diffuseColor = new BABYLON.Color3(r,g,b)")

fun jsSetMeshMaterial(mesh: JsAny, mat: JsAny): Unit = js("mesh.material = mat")

// ── Chunk geometry builder ────────────────────────────────────────────────────

fun jsChunkBegin(cx: Int, cz: Int): Unit = js("mcChunkBegin(cx, cz)")

fun jsChunkFace(wx: Int, wy: Int, wz: Int, faceMat: Int, ao: Int): Unit =
    js("mcChunkFace(wx, wy, wz, faceMat, ao)")

fun jsChunkEnd(scene: JsAny, materials: JsAny): Unit = js("mcChunkEnd(scene, materials)")

fun jsDisposeChunk(key: String): Unit = js("mcDisposeChunk(key)")

// ── Block definitions (bbmodel-driven) ───────────────────────────────────────

fun jsInitBlockDefs(): Unit = js("mcInitBlockDefs()")

fun jsIsBlockDefsReady(): Boolean = js("mcIsBlockDefsReady()")

fun jsCreateBlockMaterials(scene: JsAny): JsAny = js("mcCreateBlockMaterials(scene)")

fun jsSetGrassTint(r: Double, g: Double, b: Double): Unit = js("mcSetGrassTint(r, g, b)")
