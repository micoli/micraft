@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.micoli.micraft.babylon

// ── Player model ──────────────────────────────────────────────────────────────

fun jsInitPlayerModel(skin: String): Unit = js("mc.initPlayerModel(skin)")

fun jsIsPlayerBbmodelReady(skin: String): Boolean = js("mc.isPlayerBbmodelReady(skin)")

fun jsCreatePlayerModelNow(scene: JsAny, skin: String): JsAny =
    js("mc.createPlayerModelNow(scene, skin)")

fun jsSetPlayerTransform(
    model: JsAny,
    x: Double,
    y: Double,
    z: Double,
    yaw: Float,
    pitch: Float,
    isWalking: Boolean
): Unit = js("mc.setPlayerTransform(model, x, y, z, yaw, pitch, isWalking)")

fun jsSetPlayerVisible(model: JsAny, visible: Boolean): Unit =
    js("mc.setPlayerVisible(model, visible)")

fun jsSetPlayerAlpha(model: JsAny, alpha: Double): Unit = js("mc.setPlayerAlpha(model, alpha)")

fun jsDisposePlayerModel(model: JsAny): Unit = js("mc.disposePlayerModel(model)")

// ── NPC models ───────────────────────────────────────────────────────────────

fun jsInitNpcModels(npcTypesJson: String): Unit = js("mc.initNpcModels(npcTypesJson)")

fun jsIsNpcModelsReady(): Boolean = js("mc.isNpcModelsReady()")

fun jsCreateNpcModel(scene: JsAny, npcType: String): JsAny? =
    js("mc.createNpcModel(scene, npcType)")

fun jsSetNpcTransform(
    model: JsAny,
    x: Double,
    y: Double,
    z: Double,
    yaw: Float,
    isWalking: Boolean
): Unit = js("mc.setNpcTransform(model, x, y, z, yaw, isWalking)")

fun jsDisposeNpcModel(model: JsAny): Unit = js("mc.disposeNpcModel(model)")

fun jsHighlightNpcModel(scene: JsAny, model: JsAny, on: Boolean): Unit =
    js(
        """
    (() => {
        if (!window._combatHL) {
            window._combatHL = new BABYLON.HighlightLayer('combatHL', scene, {isStroke:true,blurHorizontalSize:0.3,blurVerticalSize:0.3});
            window._combatHL.innerGlow = false;
            window._combatHL.outerGlow = true;
        }
        var hl = window._combatHL;
        var root = model.root || model;
        var meshes = root.getChildMeshes ? root.getChildMeshes() : [];
        var col = new BABYLON.Color3(1, 0.2, 0.2);
        meshes.forEach(function(m) { if (on) hl.addMesh(m, col); else hl.removeMesh(m); });
    })()
""")

fun jsOpenNpcDialog(json: String): Unit = js("mc.openNpcDialog(json)")

// ── First-person arm view model ───────────────────────────────────────────────

fun jsCreateFPArms(camera: JsAny, scene: JsAny, skin: String): JsAny? =
    js("mc.createFPArms(scene, camera, skin)")

fun jsUpdateFPArms(fpArms: JsAny, isWalking: Boolean): Unit =
    js("mc.updateFPArms(fpArms, isWalking)")

fun jsSetFPArmsVisible(fpArms: JsAny, visible: Boolean): Unit =
    js("mc.setFPArmsVisible(fpArms, visible)")

fun jsDisposeFPArms(fpArms: JsAny): Unit = js("mc.disposeFPArms(fpArms)")

// ── Armor overlay ─────────────────────────────────────────────────────────────

fun jsInitArmorModel(name: String): Unit = js("mc.initArmorModel(name)")

fun jsIsArmorModelReady(name: String): Boolean = js("mc.isArmorModelReady(name)")

fun jsAttachArmor(model: JsAny, armorName: String, scene: JsAny): Unit =
    js("mc.attachArmor(model, armorName, scene)")

fun jsDetachArmor(model: JsAny, armorName: String): Unit = js("mc.detachArmor(model, armorName)")

fun jsDetachAllArmors(model: JsAny): Unit = js("mc.detachAllArmors(model)")
