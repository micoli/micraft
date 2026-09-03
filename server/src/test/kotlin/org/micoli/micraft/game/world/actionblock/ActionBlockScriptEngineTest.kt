package org.micoli.micraft.game.world.actionblock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.micoli.micraft.game.macro.MacroContext
import org.micoli.micraft.game.macro.MacroExecutor
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockType

class ActionBlockScriptEngineTest {
    private val solid: (BlockPos) -> BlockType = { BlockType.STONE }

    private fun engine(r: ActionBlockRegistry) = ActionBlockScriptEngine(r, MacroExecutor())

    @Test
    fun getSet_readsAndWritesNamedBlockVariables() {
        val r = ActionBlockRegistry(null)
        val a = r.create(BlockPos(0, 64, 0), "Alice", solid)!!
        r.upsert(a.pos, a.name, "getBlock('${a.name}').set('v', 1)", "", "", emptyMap())
        val block = r.byName(a.name)!!
        engine(r).run(block, block.onActivate, MacroContext())
        assertEquals("1", r.byName(a.name)?.variables?.get("v"))
    }

    @Test
    fun remote_runsTargetOnRemoteEvent() {
        val r = ActionBlockRegistry(null)
        val target = r.create(BlockPos(0, 64, 0), "Alice", solid)!!
        r.upsert(target.pos, "target", "", "", "getBlock('target').set('hit', 'yes')", emptyMap())
        val trigger = r.create(BlockPos(1, 64, 0), "Alice", solid)!!
        r.upsert(trigger.pos, "trigger", "getBlock('target').remote()", "", "", emptyMap())

        engine(r).run(r.byName("trigger")!!, r.byName("trigger")!!.onActivate, MacroContext())
        assertEquals("yes", r.byName("target")?.variables?.get("hit"))
    }

    @Test
    fun remote_selfLoop_isGuarded() {
        val r = ActionBlockRegistry(null)
        val a = r.create(BlockPos(0, 64, 0), "Alice", solid)!!
        r.upsert(
            a.pos, "loop", "getBlock('loop').remote()", "", "getBlock('loop').remote()", emptyMap())
        val result =
            engine(r).run(r.byName("loop")!!, r.byName("loop")!!.onActivate, MacroContext())
        assertNotNull(result.error)
        assertTrue(result.error.contains("loop"))
    }

    @Test
    fun notify_collectsNotifications() {
        val r = ActionBlockRegistry(null)
        val a = r.create(BlockPos(0, 64, 0), "Alice", solid)!!
        r.upsert(a.pos, a.name, "notify('hello')", "", "", emptyMap())
        val result =
            engine(r).run(r.byName(a.name)!!, r.byName(a.name)!!.onActivate, MacroContext())
        assertEquals(listOf("hello"), result.notifications)
        assertEquals(null, result.error)
    }

    @Test
    fun send_collectsCommands() {
        val r = ActionBlockRegistry(null)
        val a = r.create(BlockPos(0, 64, 0), "Alice", solid)!!
        r.upsert(a.pos, a.name, "send('/say hi')", "", "", emptyMap())
        val result =
            engine(r).run(r.byName(a.name)!!, r.byName(a.name)!!.onActivate, MacroContext())
        assertEquals(listOf("/say hi"), result.commands)
    }
}
