package org.micoli.micraft.babylon

fun jsLog(msg: String): Unit  = js("console.log('[MiCraft]', msg)")
fun jsWarn(msg: String): Unit  = js("console.warn('[MiCraft]', msg)")
fun jsError(msg: String): Unit = js("console.error('[MiCraft]', msg)")

// ── Engine / Scene ────────────────────────────────────────────────────────────

fun jsCreateEngine(): JsAny = js("mcCreateEngine()")

fun jsCreateScene(engine: JsAny): JsAny = js("new BABYLON.Scene(engine)")

fun jsEngineRunRenderLoop(engine: JsAny, scene: JsAny): Unit =
    js("engine.runRenderLoop(function(){ scene.render(); })")

fun jsSetupResize(engine: JsAny): Unit =
    js("window.addEventListener('resize', function(){ engine.resize(); })")

// ── Lights / Camera ───────────────────────────────────────────────────────────

fun jsCreateHemisphericLight(name: String, scene: JsAny): JsAny =
    js("mcCreateHemisphericLight(name, scene)")

fun jsCreateCamera(name: String, x: Double, y: Double, z: Double, scene: JsAny): JsAny =
    js("new BABYLON.UniversalCamera(name, new BABYLON.Vector3(x,y,z), scene)")

fun jsCameraSetTarget(camera: JsAny, x: Double, y: Double, z: Double): Unit =
    js("camera.setTarget(new BABYLON.Vector3(x,y,z))")

// Canvas retrieved internally — avoids the externref wrapping issue.
fun jsCameraAttachControl(camera: JsAny): Unit =
    js("camera.attachControl(document.getElementById('renderCanvas'), true)")

fun jsCameraSetPosition(camera: JsAny, x: Double, y: Double, z: Double): Unit =
    js("camera.position = new BABYLON.Vector3(x, y, z)")

// ── Meshes ────────────────────────────────────────────────────────────────────

fun jsCreateBox(name: String, size: Double, scene: JsAny): JsAny =
    js("mcCreateBox(name, size, scene)")

fun jsCreateSimpleBox(name: String, size: Double, scene: JsAny): JsAny =
    js("mcCreateSimpleBox(name, size, scene)")

fun jsFreezeMesh(mesh: JsAny): Unit = js("mcFreezeMesh(mesh)")

fun jsOptimizeScene(scene: JsAny): Unit = js("mcOptimizeScene(scene)")

// ── Chunk geometry builder ────────────────────────────────────────────────────

fun jsChunkBegin(cx: Int, cz: Int): Unit = js("mcChunkBegin(cx, cz)")
fun jsChunkFace(wx: Int, wy: Int, wz: Int, faceMat: Int): Unit = js("mcChunkFace(wx, wy, wz, faceMat)")
fun jsChunkEnd(
    scene: JsAny, gm: JsAny, sm: JsAny, dm: JsAny, bm: JsAny,
    sandMat: JsAny, sandstoneMat: JsAny, gravelMat: JsAny, snowMat: JsAny,
): Unit = js("mcChunkEnd(scene, gm, sm, dm, bm, sandMat, sandstoneMat, gravelMat, snowMat)")
fun jsDisposeChunk(key: String): Unit = js("mcDisposeChunk(key)")

fun jsSetMeshPosition(mesh: JsAny, x: Double, y: Double, z: Double): Unit =
    js("mesh.position = new BABYLON.Vector3(x,y,z)")

fun jsDisposeMesh(mesh: JsAny): Unit = js("mesh.dispose()")

// ── Materials ─────────────────────────────────────────────────────────────────

fun jsCreateMaterial(name: String, scene: JsAny): JsAny =
    js("new BABYLON.StandardMaterial(name, scene)")

fun jsSetMaterialColor(mat: JsAny, r: Double, g: Double, b: Double): Unit =
    js("mat.diffuseColor = new BABYLON.Color3(r,g,b)")

fun jsSetMeshMaterial(mesh: JsAny, mat: JsAny): Unit =
    js("mesh.material = mat")

fun jsCreateTextureMaterial(name: String, url: String, scene: JsAny): JsAny =
    js("mcCreateTextureMaterial(name, url, scene)")

fun jsCreateGrassMaterial(scene: JsAny): JsAny =
    js("mcCreateGrassMaterial(scene)")

// ── Input ─────────────────────────────────────────────────────────────────────

fun jsSetupKeyboard(): Unit = js("mcSetupKeyboard()")
fun jsSetupMouse(): Unit = js("mcSetupMouse()")
fun jsIsBreaking(): Boolean = js("mcIsBreaking()")
fun jsCreateCrosshair(): Unit = js("mcCreateCrosshair()")
fun jsGetCameraPositionX(camera: JsAny): Double = js("mcGetCameraPositionX(camera)")
fun jsGetCameraPositionY(camera: JsAny): Double = js("mcGetCameraPositionY(camera)")
fun jsGetCameraPositionZ(camera: JsAny): Double = js("mcGetCameraPositionZ(camera)")
fun jsGetCameraDir3DX(camera: JsAny): Double = js("mcGetCameraDir3DX(camera)")
fun jsGetCameraDir3DY(camera: JsAny): Double = js("mcGetCameraDir3DY(camera)")
fun jsGetCameraDir3DZ(camera: JsAny): Double = js("mcGetCameraDir3DZ(camera)")
fun jsShowTargetOutline(scene: JsAny, x: Int, y: Int, z: Int, breakable: Boolean): Unit = js("mcShowTargetOutline(scene, x, y, z, breakable)")
fun jsHideTargetOutline(): Unit = js("mcHideTargetOutline()")
fun jsShowBreakOverlay(scene: JsAny, x: Int, y: Int, z: Int, progress: Double): Unit = js("mcShowBreakOverlay(scene, x, y, z, progress)")
fun jsHideBreakOverlay(): Unit = js("mcHideBreakOverlay()")

fun jsIsKeyDown(code: String): Boolean = js("!!(window.__mc && window.__mc.keys[code])")

fun jsDisableCameraKeyboard(camera: JsAny): Unit =
    js("(function(c){c.inputs.removeByType('FreeCameraKeyboardMoveInput');c.inputs.removeByType('FreeCameraGamepadInput');c.inertia=0;})(camera)")

fun jsGetCameraForwardX(camera: JsAny): Double = js("mcGetCameraForwardX(camera)")

fun jsGetCameraForwardZ(camera: JsAny): Double = js("mcGetCameraForwardZ(camera)")

fun jsGetCameraRotationY(camera: JsAny): Double = js("camera.rotation.y")
fun jsGetCameraRotationX(camera: JsAny): Double = js("camera.rotation.x")
fun jsRotateCameraYaw(camera: JsAny, delta: Float): Unit = js("camera.rotation.y += delta")

fun jsGetCameraForwardY(camera: JsAny): Double = js("camera.getForwardRay(1).direction.y")

fun jsConsumeFlyToggle(): Boolean = js("mcConsumeFlyToggle()")

fun jsGetPageHost(): String = js("window.location.hostname")
fun jsGetPagePort(): Int    = js("parseInt(window.location.port) || (window.location.protocol === 'https:' ? 443 : 80)")
fun jsNow(): Double         = js("Date.now()")

// ── Disconnect overlay ────────────────────────────────────────────────────────

fun jsShowDisconnectedOverlay(message: String): Unit = js("mcShowDisconnectedOverlay(message)")

fun jsHideDisconnectedOverlay(): Unit = js("mcHideDisconnectedOverlay()")

// ── Login overlay ─────────────────────────────────────────────────────────────

fun jsShowLoginOverlay(): Unit = js("mcShowLoginOverlay()")

fun jsConsumeLoginResult(): String = js("mcConsumeLoginResult()")

// ── Autocomplete ──────────────────────────────────────────────────────────────

fun jsSetConnectedPlayers(namesJson: String): Unit = js("mcSetConnectedPlayers(namesJson)")

// ── Debug camera ─────────────────────────────────────────────────────────────

fun jsHasUrlParam(name: String): Boolean =
    js("(new URLSearchParams(window.location.search).has(name))")

fun jsGetUrlParam(name: String): String = js("mcGetUrlParam(name)")

/**
 * Binds keys 1-6 to camera positions facing each face of the block at (bx,by,bz).
 * Uses onBeforeRenderObservable to override client-side prediction camera updates.
 * Escape releases the lock and restores free camera.
 * Face mapping: 1=+Z, 2=-Z, 3=+X, 4=-X, 5=+Y, 6=-Y  (BabylonJS CreateBox order)
 */
fun jsSetupDebugCameraKeys(camera: JsAny, scene: JsAny, bx: Double, by: Double, bz: Double): Unit =
    js("mcSetupDebugCameraKeys(camera, scene, bx, by, bz)")

// ── Console ───────────────────────────────────────────────────────────────────

fun jsCreateConsole(): Unit    = js("mcCreateConsole()")
fun jsCreateServerLog(): Unit  = js("mcCreateServerLog()")
fun jsAddServerLog(message: String): Unit = js("mcAddServerLog(message)")
fun jsConsoleSetPlayer(name: String): Unit = js("mcConsoleSetPlayer(name)")
fun jsIsConsoleOpen(): Boolean = js("mcIsConsoleOpen()")
fun jsConsumeConsoleInput(): String = js("mcConsumeConsoleInput()")
fun jsShowNotification(message: String): Unit = js("mcShowNotification(message)")

// ── Hotbar ────────────────────────────────────────────────────────────────────

fun jsCreateHotbar(): Unit = js("mcCreateHotbar()")
fun jsUpdateHotbar(inventoryJson: String): Unit = js("mcUpdateHotbar(inventoryJson)")
fun jsConsumeInventoryToggle(): Boolean = js("mcConsumeInventoryToggle()")
fun jsToggleHotbar(): Unit = js("mcToggleHotbar()")

// ── HUD ───────────────────────────────────────────────────────────────────────

fun jsCreateHUD(): Unit = js("mcCreateHUD()")

fun jsUpdateHUD(x: Double, y: Double, z: Double, yaw: Double, pitch: Double, stance: String, speed: Double, fps: Int, kbIn: Double, kbOut: Double, biome: String): Unit =
    js("mcUpdateHUD(x, y, z, yaw, pitch, stance, speed, fps, kbIn, kbOut, biome)")

// ── Player model ──────────────────────────────────────────────────────────────

fun jsInitPlayerModel(): Unit = js("mcInitPlayerModel()")
fun jsIsPlayerBbmodelReady(): Boolean = js("mcIsPlayerBbmodelReady()")
fun jsCreatePlayerModelNow(scene: JsAny): JsAny = js("mcCreatePlayerModelNow(scene)")
fun jsSetPlayerTransform(model: JsAny, x: Double, y: Double, z: Double, yaw: Float, pitch: Float, isWalking: Boolean): Unit =
    js("mcSetPlayerTransform(model, x, y, z, yaw, pitch, isWalking)")
fun jsSetPlayerVisible(model: JsAny, visible: Boolean): Unit = js("mcSetPlayerVisible(model, visible)")
fun jsSetPlayerAlpha(model: JsAny, alpha: Double): Unit = js("mcSetPlayerAlpha(model, alpha)")
fun jsDisposePlayerModel(model: JsAny): Unit = js("mcDisposePlayerModel(model)")
fun jsConsumeViewToggle(): Boolean = js("mcConsumeViewToggle()")

// ── First-person arm view model ───────────────────────────────────────────────

fun jsCreateFPArms(camera: JsAny, scene: JsAny): JsAny? = js("mcCreateFPArms(scene, camera)")
fun jsUpdateFPArms(fpArms: JsAny, isWalking: Boolean): Unit = js("mcUpdateFPArms(fpArms, isWalking)")
fun jsSetFPArmsVisible(fpArms: JsAny, visible: Boolean): Unit = js("mcSetFPArmsVisible(fpArms, visible)")
fun jsDisposeFPArms(fpArms: JsAny): Unit = js("mcDisposeFPArms(fpArms)")
