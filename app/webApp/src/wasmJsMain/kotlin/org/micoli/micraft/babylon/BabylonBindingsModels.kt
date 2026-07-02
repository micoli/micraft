@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.micoli.micraft.babylon

// ── Player model ──────────────────────────────────────────────────────────────

fun jsInitPlayerModel(skin: String): Unit = js("mcInitPlayerModel(skin)")

fun jsIsPlayerBbmodelReady(skin: String): Boolean = js("mcIsPlayerBbmodelReady(skin)")

fun jsCreatePlayerModelNow(scene: JsAny, skin: String): JsAny =
    js("mcCreatePlayerModelNow(scene, skin)")

fun jsSetPlayerTransform(
    model: JsAny,
    x: Double,
    y: Double,
    z: Double,
    yaw: Float,
    pitch: Float,
    isWalking: Boolean
): Unit = js("mcSetPlayerTransform(model, x, y, z, yaw, pitch, isWalking)")

fun jsSetPlayerVisible(model: JsAny, visible: Boolean): Unit =
    js("mcSetPlayerVisible(model, visible)")

fun jsSetPlayerAlpha(model: JsAny, alpha: Double): Unit = js("mcSetPlayerAlpha(model, alpha)")

fun jsDisposePlayerModel(model: JsAny): Unit = js("mcDisposePlayerModel(model)")

// ── NPC models ───────────────────────────────────────────────────────────────

fun jsInitNpcModels(npcTypesJson: String): Unit = js("mcInitNpcModels(npcTypesJson)")

fun jsIsNpcModelsReady(): Boolean = js("mcIsNpcModelsReady()")

fun jsCreateNpcModel(scene: JsAny, npcType: String): JsAny? = js("mcCreateNpcModel(scene, npcType)")

fun jsSetNpcTransform(
    model: JsAny,
    x: Double,
    y: Double,
    z: Double,
    yaw: Float,
    isWalking: Boolean
): Unit = js("mcSetNpcTransform(model, x, y, z, yaw, isWalking)")

fun jsDisposeNpcModel(model: JsAny): Unit = js("mcDisposeNpcModel(model)")

fun jsOpenNpcDialog(json: String): Unit = js("mcOpenNpcDialog(json)")

// ── First-person arm view model ───────────────────────────────────────────────

fun jsCreateFPArms(camera: JsAny, scene: JsAny, skin: String): JsAny? =
    js("mcCreateFPArms(scene, camera, skin)")

fun jsUpdateFPArms(fpArms: JsAny, isWalking: Boolean): Unit =
    js("mcUpdateFPArms(fpArms, isWalking)")

fun jsSetFPArmsVisible(fpArms: JsAny, visible: Boolean): Unit =
    js("mcSetFPArmsVisible(fpArms, visible)")

fun jsDisposeFPArms(fpArms: JsAny): Unit = js("mcDisposeFPArms(fpArms)")

// ── Armor overlay ─────────────────────────────────────────────────────────────

fun jsInitArmorModel(name: String): Unit = js("mcInitArmorModel(name)")

fun jsIsArmorModelReady(name: String): Boolean = js("mcIsArmorModelReady(name)")

fun jsAttachArmor(model: JsAny, armorName: String, scene: JsAny): Unit =
    js("mcAttachArmor(model, armorName, scene)")

fun jsDetachArmor(model: JsAny, armorName: String): Unit = js("mcDetachArmor(model, armorName)")

fun jsDetachAllArmors(model: JsAny): Unit = js("mcDetachAllArmors(model)")
