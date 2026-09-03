package org.micoli.micraft.game.world.actionblock

import org.micoli.micraft.game.macro.MacroContext
import org.micoli.micraft.game.macro.MacroExecutor
import org.micoli.micraft.game.world.actionblock.ActionBlockConstants.MAX_REMOTE_DEPTH
import org.micoli.micraft.game.world.actionblock.ActionBlockConstants.MAX_SCRIPT_RUNS_PER_INTERACTION
import org.micoli.micraft.macro.MacroFunctionsJava
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(ActionBlockScriptEngine::class.java)

/**
 * Runs an [ActionBlock]'s JEXL scripts. Implements the `getBlock('name').get/set/remote()` bridge:
 * `get`/`set` read and write the named block's variables through [registry]; `remote()` re-enters
 * this engine to run the target block's `onRemoteEvent` synchronously, guarded against loops and
 * runaway fan-out by a per-invocation call stack + run counter.
 */
class ActionBlockScriptEngine(
    private val registry: ActionBlockRegistry,
    private val macroExecutor: MacroExecutor,
) {
    data class Result(
        val commands: List<String>,
        val notifications: List<String>,
        val error: String? = null,
    )

    private class Invocation {
        val stack = ArrayDeque<String>()
        var runs = 0
        val commands = mutableListOf<String>()
        val notifications = mutableListOf<String>()
        var error: String? = null
    }

    private val current = ThreadLocal<Invocation?>()

    fun run(block: ActionBlock, script: String, base: MacroContext): Result {
        if (script.isBlank()) return Result(emptyList(), emptyList(), null)

        val top = current.get() == null
        val inv = current.get() ?: Invocation().also { current.set(it) }
        try {
            runScript(inv, block, script, base)
            return Result(inv.commands.toList(), inv.notifications.toList(), inv.error)
        } finally {
            if (top) current.remove()
        }
    }

    private fun runScript(
        inv: Invocation,
        block: ActionBlock,
        script: String,
        base: MacroContext,
    ) {
        if (script.isBlank()) return
        if (block.name in inv.stack) {
            inv.error = "remote loop on '${block.name}'"
            log.warn("action-block remote loop: {} stack={}", block.name, inv.stack)
            return
        }
        if (inv.stack.size >= MAX_REMOTE_DEPTH) {
            inv.error = "remote depth limit"
            return
        }
        if (inv.runs >= MAX_SCRIPT_RUNS_PER_INTERACTION) {
            inv.error = "script run limit"
            return
        }
        inv.stack.addLast(block.name)
        inv.runs++
        val ctx =
            base.copy(
                blockName = block.name,
                blockX = block.pos.x,
                blockY = block.pos.y,
                blockZ = block.pos.z,
                blockVariables = block.variables,
            )
        val bridge =
            object : MacroFunctionsJava.BlockBridge {
                override fun get(b: String, v: String): Any? {
                    val raw = registry.byName(b)?.variables?.get(v) ?: return null
                    return raw.toLongOrNull() ?: raw.toDoubleOrNull() ?: raw
                }

                override fun set(b: String, v: String, value: Any?) {
                    registry.setVariable(b, v, value?.toString() ?: "")
                }

                override fun remote(b: String) {
                    val target = registry.byName(b)
                    if (target == null) {
                        inv.error = "unknown block '$b'"
                        return
                    }
                    runScript(inv, target, target.onRemoteEvent, base)
                }
            }
        try {
            macroExecutor.execute(
                script = script,
                context = ctx,
                onSend = { inv.commands.add(it) },
                onAction = {},
                onNotify = { inv.notifications.add(it) },
                blockBridge = bridge,
            )
        } catch (e: Exception) {
            inv.error = e.message ?: e::class.simpleName ?: "error"
            log.warn("action-block script '{}' failed: {}", block.name, e.message)
        } finally {
            inv.stack.removeLast()
        }
    }
}
