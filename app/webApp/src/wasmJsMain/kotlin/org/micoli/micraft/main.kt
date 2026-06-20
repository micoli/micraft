package org.micoli.micraft

import org.micoli.micraft.babylon.*

fun main() {
    jsLog("main() started")

    val engine = jsCreateEngine()
    jsLog("BabylonJS engine created")

    val scene = jsCreateScene(engine)
    jsLog("scene created")

    jsCreateHemisphericLight("light", scene)

    val camera = jsCreateCamera("camera", 8.0, 10.0, -10.0, scene)
    jsCameraSetTarget(camera, 8.0, 8.0, 8.0)
    jsCameraAttachControl(camera)
    jsDisableCameraKeyboard(camera)
    jsSetupKeyboard()
    jsLog("camera ready")

    jsCreateHUD()

    // Debug mode: ?debug[&bx=8&by=2&bz=8] — keys 1-6 orbit camera around a block face
    if (jsHasUrlParam("debug")) {
        val bx = jsGetUrlParam("bx").toDoubleOrNull() ?: 8.0
        val by = jsGetUrlParam("by").toDoubleOrNull() ?: 2.0
        val bz = jsGetUrlParam("bz").toDoubleOrNull() ?: 8.0
        jsSetupDebugCameraKeys(camera, scene, bx, by, bz)
        jsLog("debug mode: 1-6 keys orbit block ($bx,$by,$bz), Escape unlocks")
    }

    val client = GameClient(scene, camera)
    val host = jsGetPageHost()
    val port = jsGetPagePort()
    jsLog("connecting to ws://$host:$port/game …")
    client.connect(host, port)

    jsEngineRunRenderLoop(engine, scene)
    jsSetupResize(engine)
    jsLog("render loop started")
}
