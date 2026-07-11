package org.micoli.micraft.game.world.vegetation

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.iterator
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.random.Random
import com.charleskorn.kaml.Yaml
import org.micoli.micraft.game.world.BlockPos
import org.micoli.micraft.game.world.BlockType
import org.micoli.micraft.game.world.WorldState
import org.micoli.micraft.protocol.BlockChange
import org.micoli.micraft.protocol.ServerMessage
import org.slf4j.LoggerFactory

private val vegetationManagerLog = LoggerFactory.getLogger(VegetationManager::class.java)

class VegetationManager(
    private val world: WorldState,
    @Volatile private var config: VegetationConfig,
    private val savePath: Path,
) {
    private val activeBlocks: ConcurrentHashMap<BlockPos, GrowingBlock> = ConcurrentHashMap()
    private var checkTickCounter = 0
    private val random = Random.Default

    fun tryActivate(pos: BlockPos, blockType: BlockType) {
        val matchingChains =
            config.data.chains.filter {
                it.stages.isNotEmpty() && it.stages[0].block == blockType.id
            }
        if (matchingChains.isEmpty()) return
        val chain = matchingChains[random.nextInt(matchingChains.size)]
        activate(pos, chain)
    }

    private fun activate(pos: BlockPos, chain: GrowthChain) {
        if (chain.requiresVegetationHost) {
            val belowY = pos.y - 1
            if (belowY < 0 || !world.getBlockIfLoaded(pos.x, belowY, pos.z).isVegetationHost) {
                vegetationManagerLog.debug(
                    "VegetationManager: rejected {} — block below not a vegetation host", pos)
                return
            }
        }
        val stage = chain.stages[0]
        val ticksRequired = random.nextInt(stage.minTicks, stage.maxTicks + 1)
        activeBlocks[pos] = GrowingBlock(pos, chain.name, 0, 0, ticksRequired)
        vegetationManagerLog.debug(
            "VegetationManager: activated {} on chain '{}' ({} ticks)",
            pos,
            chain.name,
            ticksRequired)
    }

    fun deactivate(pos: BlockPos) {
        activeBlocks.remove(pos)
    }

    suspend fun tick(broadcast: suspend (ServerMessage) -> Unit) {
        if (!config.data.enabled || activeBlocks.isEmpty()) return
        checkTickCounter++
        if (checkTickCounter < config.data.growthCheckIntervalTicks) return
        checkTickCounter = 0

        val changes = mutableListOf<BlockChange>()
        val toAdvance = mutableListOf<BlockPos>()
        val toDeactivate = mutableListOf<BlockPos>()

        for ((pos, growing) in activeBlocks) {
            val chain = config.data.chains.find { it.name == growing.chainName }
            if (chain == null || growing.stageIndex >= chain.stages.size) {
                toDeactivate.add(pos)
                continue
            }
            val expectedBlockId = chain.stages[growing.stageIndex].block
            val current = world.getBlockIfLoaded(pos.x, pos.y, pos.z)
            if (current.id != expectedBlockId) {
                toDeactivate.add(pos)
                continue
            }
            val newAccumulated = growing.ticksAccumulated + config.data.growthCheckIntervalTicks
            if (newAccumulated >= growing.ticksRequired) {
                toAdvance.add(pos)
            } else {
                activeBlocks[pos] = growing.copy(ticksAccumulated = newAccumulated)
            }
        }

        toDeactivate.forEach { activeBlocks.remove(it) }

        for (pos in toAdvance) {
            val growing = activeBlocks[pos] ?: continue
            val chain = config.data.chains.find { it.name == growing.chainName } ?: continue
            val nextStageIndex = growing.stageIndex + 1

            if (nextStageIndex < chain.stages.size) {
                val nextStage = chain.stages[nextStageIndex]
                val nextBlock = BlockType(nextStage.block)
                val change = BlockChange(pos, nextBlock)
                world.applyChange(change)
                changes.add(change)
                val nextTicks = random.nextInt(nextStage.minTicks, nextStage.maxTicks + 1)
                activeBlocks[pos] =
                    GrowingBlock(pos, growing.chainName, nextStageIndex, 0, nextTicks)
                vegetationManagerLog.debug(
                    "VegetationManager: {} → {} (stage {})", pos, nextBlock, nextStageIndex)
            } else {
                activeBlocks.remove(pos)
                changes.addAll(spawnTree(pos, chain))
            }
        }

        if (changes.isNotEmpty()) {
            broadcast(ServerMessage.WorldUpdate(changes))
        }
    }

    private fun spawnTree(pos: BlockPos, chain: GrowthChain): List<BlockChange> {
        val surfaceY = pos.y - 1
        val treeBlocks =
            when (chain.finalTree) {
                "OAK_TREE" -> oakTreeBlocks(pos.x, pos.z, surfaceY)
                "PINE_TREE" -> pineTreeBlocks(pos.x, pos.z, surfaceY, BlockType.PINE_LEAVES)
                "PINE_TREE_SNOW" ->
                    pineTreeBlocks(pos.x, pos.z, surfaceY, BlockType.PINE_LEAVES_SNOW)
                else -> {
                    vegetationManagerLog.warn(
                        "VegetationManager: unknown finalTree '{}' in chain '{}'",
                        chain.finalTree,
                        chain.name)
                    return emptyList()
                }
            }

        val saplingClear = BlockChange(pos, BlockType.AIR)
        world.applyChange(saplingClear)
        val result = mutableListOf(saplingClear)

        for ((treePos, treeType) in treeBlocks) {
            val existing = world.getBlockIfLoaded(treePos.x, treePos.y, treePos.z)
            if (existing == BlockType.AIR || existing.isReplaceable) {
                val change = BlockChange(treePos, treeType)
                world.applyChange(change)
                result.add(change)
            }
        }
        vegetationManagerLog.debug(
            "VegetationManager: spawned {} tree at {} ({} blocks)",
            chain.finalTree,
            pos,
            result.size)
        return result
    }

    fun load() {
        if (!savePath.exists()) return
        runCatching {
                val state = Yaml.default.decodeFromString(VegetationState.serializer(), savePath.readText())
                state.blocks.forEach { activeBlocks[it.pos] = it }
                vegetationManagerLog.info(
                    "VegetationManager: loaded {} growing blocks from {}",
                    state.blocks.size,
                    savePath)
            }
            .onFailure { e ->
                vegetationManagerLog.warn(
                    "VegetationManager: failed to load {}: {}", savePath, e.message)
            }
    }

    fun save() {
        runCatching {
                savePath.parent?.createDirectories()
                val state = VegetationState(activeBlocks.values.toList())
                savePath.writeText(Yaml.default.encodeToString(VegetationState.serializer(), state))
            }
            .onFailure { e ->
                vegetationManagerLog.warn("VegetationManager: failed to save: {}", e.message)
            }
    }

    fun reload(newConfig: VegetationConfig) {
        config = newConfig
    }

    fun activeBlockCount(): Int = activeBlocks.size
}
