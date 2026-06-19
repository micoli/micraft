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

    val client = GameClient(scene, camera)
    val host = jsGetPageHost()
    val port = jsGetPagePort()
    jsLog("connecting to ws://$host:$port/game …")
    client.connect(host, port)

    jsEngineRunRenderLoop(engine, scene)
    jsSetupResize(engine)
    jsLog("render loop started")
}
