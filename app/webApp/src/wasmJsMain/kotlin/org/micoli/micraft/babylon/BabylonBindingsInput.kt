@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.micoli.micraft.babylon

// ── Input ─────────────────────────────────────────────────────────────────────

fun jsSetupKeyboard(): Unit = js("mc.setupKeyboard()")

fun jsSetupMouse(): Unit = js("mc.setupMouse()")

fun jsIsBreaking(): Boolean = js("mc.isBreaking()")

fun jsCreateCrosshair(): Unit = js("mc.createCrosshair()")

fun jsIsKeyDown(code: String): Boolean = js("!!window.mcState.keys[code]")

fun jsIsActionDown(action: String): Boolean = js("mc.isActionDown(action)")

fun jsLoadBindings(host: String, port: Int, player: String): Unit =
    js("mc.loadBindings(host, port, player)")

// ── Camera read-back ──────────────────────────────────────────────────────────

fun jsGetCameraPositionX(camera: JsAny): Double = js("mc.getCameraPositionX(camera)")

fun jsGetCameraPositionY(camera: JsAny): Double = js("mc.getCameraPositionY(camera)")

fun jsGetCameraPositionZ(camera: JsAny): Double = js("mc.getCameraPositionZ(camera)")

fun jsGetCameraDir3DX(camera: JsAny): Double = js("mc.getCameraDir3DX(camera)")

fun jsGetCameraDir3DY(camera: JsAny): Double = js("mc.getCameraDir3DY(camera)")

fun jsGetCameraDir3DZ(camera: JsAny): Double = js("mc.getCameraDir3DZ(camera)")

fun jsGetCameraForwardX(camera: JsAny): Double = js("mc.getCameraForwardX(camera)")

fun jsGetCameraForwardY(camera: JsAny): Double = js("camera.getForwardRay(1).direction.y")

fun jsGetCameraForwardZ(camera: JsAny): Double = js("mc.getCameraForwardZ(camera)")

fun jsGetCameraRotationY(camera: JsAny): Double = js("camera.rotation.y")

fun jsGetCameraRotationX(camera: JsAny): Double = js("camera.rotation.x")

fun jsRotateCameraYaw(camera: JsAny, delta: Float): Unit = js("camera.rotation.y += delta")

fun jsDisableCameraKeyboard(camera: JsAny): Unit =
    js(
        """(function(c){
      c.inputs.removeByType('FreeCameraKeyboardMoveInput');
      c.inputs.removeByType('FreeCameraGamepadInput');
      c.inputs.removeByType('FreeCameraMouseInput');
      c.inertia = 0;
      var canvas = document.getElementById('renderCanvas');
      canvas.addEventListener('click', function() {
        if (!document.pointerLockElement) canvas.requestPointerLock();
      });
      document.addEventListener('pointermove', function(e) {
        if (document.pointerLockElement !== canvas) return;
        c.rotation.y += e.movementX * 0.002;
        c.rotation.x += e.movementY * 0.002;
        c.rotation.x = Math.max(-Math.PI / 2, Math.min(Math.PI / 2, c.rotation.x));
      });
    })(camera)""")

// ── Target / break overlays ───────────────────────────────────────────────────

fun jsShowTargetOutline(scene: JsAny, x: Int, y: Int, z: Int, breakable: Boolean): Unit =
    js("mc.showTargetOutline(scene, x, y, z, breakable)")

fun jsHideTargetOutline(): Unit = js("mc.hideTargetOutline()")

fun jsShowBreakOverlay(scene: JsAny, x: Int, y: Int, z: Int, progress: Double): Unit =
    js("mc.showBreakOverlay(scene, x, y, z, progress)")

fun jsHideBreakOverlay(): Unit = js("mc.hideBreakOverlay()")

// ── Event queue ───────────────────────────────────────────────────────────────

fun jsConsumeEvents(): JsAny = js("mc.consumeEvents()")

fun jsEventsLength(arr: JsAny): Int = js("arr.length")

fun jsEventsGet(arr: JsAny, i: Int): String = js("arr[i]")
