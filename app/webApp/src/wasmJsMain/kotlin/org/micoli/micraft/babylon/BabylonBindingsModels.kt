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

fun jsInitNpcWalkBones(json: String): Unit = js("mc.initNpcWalkBones(json)")

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

fun jsSetNpcScale(model: JsAny, scale: Float): Unit = js("mc.setNpcScale(model, scale)")

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
        var col = new BABYLON.Color3(0.6, 0.2, 1.0);
        meshes.forEach(function(m) { if (on) hl.addMesh(m, col); else hl.removeMesh(m); });
    })()
""")

fun jsAggroHighlightNpcModel(scene: JsAny, model: JsAny, on: Boolean): Unit =
    js(
        """
    (() => {
        if (!window._aggroHL) {
            window._aggroHL = new BABYLON.HighlightLayer('aggroHL', scene, {isStroke:true,blurHorizontalSize:0.3,blurVerticalSize:0.3});
            window._aggroHL.innerGlow = false;
            window._aggroHL.outerGlow = true;
        }
        var hl = window._aggroHL;
        var root = model.root || model;
        var meshes = root.getChildMeshes ? root.getChildMeshes() : [];
        var col = new BABYLON.Color3(1, 0.2, 0.2);
        meshes.forEach(function(m) { if (on) hl.addMesh(m, col); else hl.removeMesh(m); });
    })()
""")

fun jsSetNpcDead(scene: JsAny, model: JsAny): Unit =
    js(
        """
    (() => {
        if (!window._deadHL) {
            window._deadHL = new BABYLON.HighlightLayer('deadHL', scene, {isStroke:true,blurHorizontalSize:0.5,blurVerticalSize:0.5});
            window._deadHL.innerGlow = false;
            window._deadHL.outerGlow = true;
        }
        var hl = window._deadHL;
        var root = model.root || model;
        root.rotation.z = Math.PI / 2;
        var meshes = root.getChildMeshes ? root.getChildMeshes() : [];
        var col = new BABYLON.Color3(0.45, 0.45, 0.45);
        meshes.forEach(function(m) { hl.addMesh(m, col); });
    })()
""")

fun jsOpenNpcDialog(json: String): Unit = js("mc.openNpcDialog(json)")

// ── Vehicle models ───────────────────────────────────────────────────────────
// Mirrors the NPC model bindings above, minus walk-bone animation/highlighting (a rail vehicle
// never walks, aggros, or dies) — see VehicleManager.kt (game/) for the Kotlin side.

fun jsInitVehicleModels(vehicleTypesJson: String): Unit =
    js("mc.initVehicleModels(vehicleTypesJson)")

fun jsIsVehicleModelsReady(): Boolean = js("mc.isVehicleModelsReady()")

fun jsCreateVehicleModel(scene: JsAny, vehicleType: String): JsAny? =
    js("mc.createVehicleModel(scene, vehicleType)")

fun jsSetVehicleTransform(model: JsAny, x: Double, y: Double, z: Double, yaw: Float): Unit =
    js("mc.setVehicleTransform(model, x, y, z, yaw)")

fun jsDisposeVehicleModel(model: JsAny): Unit = js("mc.disposeVehicleModel(model)")

// ── First person ──────────────────────────────────────────────────────────────

fun jsSetPlayerFirstPerson(model: JsAny, skin: String, enabled: Boolean): Unit =
    js("mc.setPlayerFirstPerson(model, skin, enabled)")

fun jsInitSkinConfig(skin: String): Unit = js("mc.initSkinConfig(skin)")

fun jsIsSkinConfigReady(skin: String): Boolean = js("mc.isSkinConfigReady(skin)")

/** Eye height above the feet, in blocks; 0 when the skin declares no eye anchor. */
fun jsGetSkinEyeHeight(skin: String): Double = js("mc.getSkinEyeHeight(skin)")

// ── Armor overlay ─────────────────────────────────────────────────────────────

fun jsInitArmorModel(name: String): Unit = js("mc.initArmorModel(name)")

fun jsIsArmorModelReady(name: String): Boolean = js("mc.isArmorModelReady(name)")

fun jsAttachArmor(model: JsAny, armorName: String, scene: JsAny): Unit =
    js("mc.attachArmor(model, armorName, scene)")

fun jsDetachArmor(model: JsAny, armorName: String): Unit = js("mc.detachArmor(model, armorName)")

fun jsDetachAllArmors(model: JsAny): Unit = js("mc.detachAllArmors(model)")

fun jsSetRemotePlayerLight(model: JsAny, scene: JsAny, enabled: Boolean): Unit =
    js("mc.setRemotePlayerLight(model, scene, enabled)")
