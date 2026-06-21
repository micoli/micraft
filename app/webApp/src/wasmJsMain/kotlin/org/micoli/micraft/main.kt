@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package org.micoli.micraft

import androidx.compose.ui.window.ComposeViewport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.micoli.micraft.babylon.*
import org.micoli.micraft.ui.McGameUI
import org.micoli.micraft.ui.McUiState

val uiState = McUiState()

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
    jsSetupMouse()
    jsCreateCrosshair()
    jsLog("camera ready")

    jsCreateHUD()
    jsCreateHotbar()
    jsCreateConsole()
    jsCreateServerLog()

    // Debug mode: ?debug[&bx=8&by=2&bz=8] — keys 1-6 orbit camera around a block face
    if (jsHasUrlParam("debug")) {
        val bx = jsGetUrlParam("bx").toDoubleOrNull() ?: 8.0
        val by = jsGetUrlParam("by").toDoubleOrNull() ?: 2.0
        val bz = jsGetUrlParam("bz").toDoubleOrNull() ?: 8.0
        jsSetupDebugCameraKeys(camera, scene, bx, by, bz)
        jsLog("debug mode: 1-6 keys orbit block ($bx,$by,$bz), Escape unlocks")
    }

    // Compose overlay — rendered on top of BabylonJS canvas, pointer-events none by default
    jsCreateUiOverlay()
    ComposeViewport("mc-ui") { McGameUI(uiState) }

    val client = GameClient(scene, camera)
    val host = jsGetPageHost()
    val port = jsGetPagePort()
    jsLoadBindings(host, port)

    jsShowLoginOverlay()
    jsLog("waiting for login …")

    CoroutineScope(Dispatchers.Default).launch {
        var result = ""
        while (result.isEmpty()) {
            delay(100)
            result = jsConsumeLoginResult()
        }
        val parts = result.split("\t")
        val username = parts[0]
        val playerName = if (parts.size > 1) parts[1] else parts[0]
        jsLog("login: user=$username player=$playerName — connecting to ws://$host:$port/game …")
        client.connect(host, port, username, playerName)
    }

    jsEngineRunRenderLoop(engine, scene)
    jsSetupResize(engine)
    jsLog("render loop started")
}
