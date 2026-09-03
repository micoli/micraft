package org.micoli.micraft.game.macro

import java.util.ArrayList
import java.util.HashMap
import org.apache.commons.jexl3.JexlBuilder
import org.apache.commons.jexl3.MapContext
import org.apache.commons.jexl3.introspection.JexlSandbox
import org.micoli.micraft.macro.MacroFunctionsJava

class MacroExecutor {
    private val sandbox =
        JexlSandbox(false).apply {
            allow(MacroFunctionsJava::class.java.name)
                .execute("send")
                .execute("action")
                .execute("notify")
                .execute("getBlock")
            allow(MacroFunctionsJava.BlockHandle::class.java.name)
                .execute("get")
                .execute("set")
                .execute("remote")
            // Allow basic collection access for macro context variables
            allow(HashMap::class.java.name)
            allow(ArrayList::class.java.name)
        }

    private val jexl =
        JexlBuilder()
            .namespaces(mapOf("mc" to MacroFunctionsJava::class.java))
            .loader(MacroFunctionsJava::class.java.classLoader)
            .sandbox(sandbox)
            .silent(false)
            .strict(true)
            .create()

    fun execute(
        script: String,
        context: MacroContext = MacroContext(),
        onSend: (String) -> Unit,
        onAction: (String) -> Unit,
        onNotify: (String) -> Unit = {},
        blockBridge: MacroFunctionsJava.BlockBridge? = null,
    ) {
        val preprocessed = preprocess(script)
        val snapshot = MacroFunctionsJava.snapshot()
        MacroFunctionsJava.setSendCallback { cmd -> onSend(cmd) }
        MacroFunctionsJava.setActionCallback { act -> onAction(act) }
        MacroFunctionsJava.setNotifyCallback { msg -> onNotify(msg) }
        if (blockBridge != null) MacroFunctionsJava.setBlockBridge(blockBridge)
        val posMap = HashMap<String, Any>()
        posMap["x"] = context.posX
        posMap["y"] = context.posY
        posMap["z"] = context.posZ
        val playerMap = HashMap<String, Any>()
        playerMap["name"] = context.playerName
        playerMap["id"] = context.playerId
        playerMap["hp"] = context.currentHp
        playerMap["mana"] = context.currentMana
        val selfMap = HashMap<String, Any>()
        selfMap["name"] = context.blockName
        selfMap["x"] = context.blockX
        selfMap["y"] = context.blockY
        selfMap["z"] = context.blockZ
        selfMap["vars"] = HashMap(context.blockVariables)
        val jexlContext =
            MapContext().apply {
                set("position", posMap)
                set("biome", context.biome)
                set("yaw", context.yaw)
                set("pitch", context.pitch)
                set("currentHp", context.currentHp)
                set("currentMana", context.currentMana)
                set("effects", ArrayList(context.effects))
                set("player", playerMap)
                set("self", selfMap)
            }
        try {
            jexl.createScript(preprocessed).execute(jexlContext)
        } finally {
            MacroFunctionsJava.restore(snapshot)
        }
    }

    private fun preprocess(script: String): String =
        script
            .replace(Regex("""(?<![:\w])send\s*\("""), "mc:send(")
            .replace(Regex("""(?<![:\w])action\s*\("""), "mc:action(")
            .replace(Regex("""(?<![:\w])notify\s*\("""), "mc:notify(")
            .replace(Regex("""(?<![:\w])getBlock\s*\("""), "mc:getBlock(")
}
