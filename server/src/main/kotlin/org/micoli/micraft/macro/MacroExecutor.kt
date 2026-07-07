package org.micoli.micraft.macro

import org.apache.commons.jexl3.JexlBuilder
import org.apache.commons.jexl3.MapContext
import org.apache.commons.jexl3.introspection.JexlSandbox

class MacroExecutor {
    private val sandbox =
        JexlSandbox(false).apply {
            allow(MacroFunctionsJava::class.java.name).execute("send").execute("action")
        }

    private val jexl =
        JexlBuilder()
            .namespaces(mapOf("mc" to MacroFunctionsJava::class.java))
            .loader(MacroFunctionsJava::class.java.classLoader)
            .sandbox(sandbox)
            .silent(false)
            .strict(true)
            .create()

    fun execute(script: String, onSend: (String) -> Unit, onAction: (String) -> Unit) {
        val preprocessed = preprocess(script)
        MacroFunctionsJava.setSendCallback { cmd -> onSend(cmd) }
        MacroFunctionsJava.setActionCallback { act -> onAction(act) }
        try {
            jexl.createScript(preprocessed).execute(MapContext())
        } finally {
            MacroFunctionsJava.clearCallbacks()
        }
    }

    private fun preprocess(script: String): String =
        script
            .replace(Regex("""(?<![:\w])send\s*\("""), "mc:send(")
            .replace(Regex("""(?<![:\w])action\s*\("""), "mc:action(")
}
