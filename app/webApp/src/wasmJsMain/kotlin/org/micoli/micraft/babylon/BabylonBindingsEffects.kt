@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.micoli.micraft.babylon

fun jsAoEEffect(scene: JsAny, x: Float, y: Float, z: Float, radius: Float): Unit =
    js("mc.aoeEffect(scene, x, y, z, radius)")
