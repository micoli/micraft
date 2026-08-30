@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.micoli.micraft

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.micoli.micraft.babylon.*
import org.micoli.micraft.game.GameClient
import org.micoli.micraft.protocol.MessageEncoding
import org.micoli.micraft.ui.McUiState
import org.micoli.micraft.ui.WebUiBridge

val uiState = McUiState()

fun main() {
    jsLog("main() started")

    if (!jsHasRenderCanvas()) {
        jsLog("main(): no #renderCanvas — admin chunk-preview mode, skipping full game bootstrap")
        // The real game flow reads server config to pick PROTOBUF vs JSON message encoding
        // before ever decoding a ServerMessage (see below) — admin preview needs the same
        // handshake, otherwise ServerMessageCodec.decode assumes the compiled-in PROTOBUF
        // default even when the server is actually configured for JSON.
        CoroutineScope(Dispatchers.Default).launch {
            val host = jsGetPageHost()
            val port = jsGetPagePort()
            runCatching {
                    val config =
                        HttpClient(Js).get("http://$host:$port/api/auth/config").bodyAsText()
                    Json.parseToJsonElement(config)
                        .jsonObject["messageEncoder"]
                        ?.jsonPrimitive
                        ?.content
                }
                .getOrNull()
                ?.let { MessageEncoding.current = MessageEncoding.fromConfigValue(it) }
        }
        return
    }

    val engine = jsCreateEngine()
    jsLog("BabylonJS engine created")

    val scene = jsCreateScene(engine)
    jsLog("scene created")

    jsCreateHemisphericLight("light", scene)
    jsCreateSunLight(scene)

    val camera = jsCreateCamera("camera", 8.0, 10.0, -10.0, scene)
    jsCameraSetTarget(camera, 8.0, 8.0, 8.0)
    jsCameraAttachControl(camera)
    jsDisableCameraKeyboard(camera)
    jsSetupKeyboard()
    jsSetupMouse()
    jsCreateCrosshair()
    jsLog("camera ready")

    jsCreateHUD()
    jsCreateMinimap()
    jsCreateHotbar()
    jsCreateConsole()
    jsCreateServerLog()

    val scope = CoroutineScope(Dispatchers.Default)
    WebUiBridge(uiState, scope).start()

    val client = GameClient(scene, camera, uiState)
    val host = jsGetPageHost()
    val port = jsGetPagePort()

    jsShowLoginOverlay()
    jsLog("waiting for login …")

    scope.launch {
        var result = ""
        while (result.isEmpty()) {
            delay(100)
            result = jsConsumeLoginResult()
        }
        val parts = result.split("\t")
        val username = parts[0]
        val playerName = if (parts.size > 1) parts[1] else parts[0]
        val lang = if (parts.size > 2) parts[2] else "en"
        val token = if (parts.size > 3) parts[3] else ""
        runCatching {
                val config = HttpClient(Js).get("http://$host:$port/api/auth/config").bodyAsText()
                Json.parseToJsonElement(config).jsonObject["messageEncoder"]?.jsonPrimitive?.content
            }
            .getOrNull()
            ?.let { MessageEncoding.current = MessageEncoding.fromConfigValue(it) }
        jsLoadBindings(host, port, playerName)
        jsFetchI18n(lang)
        jsLog(
            "login: user=$username player=$playerName lang=$lang — connecting to ws://$host:$port/game …")
        jsHideLoginOverlay()
        jsEngineRunRenderLoop(engine, scene)
        jsSetupResize(engine)
        jsLog("render loop started")
        client.connect(host, port, username, playerName, lang, token)
    }
}
