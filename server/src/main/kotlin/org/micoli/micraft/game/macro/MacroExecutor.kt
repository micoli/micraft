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
            allow(MacroFunctionsJava::class.java.name).execute("send").execute("action")
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
    ) {
        val preprocessed = preprocess(script)
        MacroFunctionsJava.setSendCallback { cmd -> onSend(cmd) }
        MacroFunctionsJava.setActionCallback { act -> onAction(act) }
        val posMap = HashMap<String, Any>()
        posMap["x"] = context.posX
        posMap["y"] = context.posY
        posMap["z"] = context.posZ
        val jexlContext =
            MapContext().apply {
                set("position", posMap)
                set("biome", context.biome)
                set("yaw", context.yaw)
                set("pitch", context.pitch)
                set("currentHp", context.currentHp)
                set("currentMana", context.currentMana)
                set("effects", ArrayList(context.effects))
            }
        try {
            jexl.createScript(preprocessed).execute(jexlContext)
        } finally {
            MacroFunctionsJava.clearCallbacks()
        }
    }

    private fun preprocess(script: String): String =
        script
            .replace(Regex("""(?<![:\w])send\s*\("""), "mc:send(")
            .replace(Regex("""(?<![:\w])action\s*\("""), "mc:action(")
}
