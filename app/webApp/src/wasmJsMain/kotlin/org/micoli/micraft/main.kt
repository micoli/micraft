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
    jsLog("camera ready")

    val client = GameClient(scene)
    jsLog("connecting to ws://localhost:8080/ws …")
    client.connect("localhost", 8080)

    jsEngineRunRenderLoop(engine, scene)
    jsSetupResize(engine)
    jsLog("render loop started")
}
