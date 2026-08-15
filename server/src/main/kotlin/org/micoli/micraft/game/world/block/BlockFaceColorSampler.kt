package org.micoli.micraft.game.world.block

import java.awt.image.BufferedImage
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val bbmodelJson = Json { ignoreUnknownKeys = true }

@Serializable private data class BbTexture(val path: String = "")

@Serializable private data class BbFace(val texture: Int = -1)

@Serializable private data class BbElement(val faces: Map<String, BbFace> = emptyMap())

@Serializable
private data class BbModel(
    val elements: List<BbElement> = emptyList(),
    val textures: List<BbTexture> = emptyList(),
)

/**
 * Precomputes and caches the mean RGB color of each block's top-face and side-face textures, for
 * the far-chunk impostor mesh (buildChunkImpostorMesh in chunkBuilder.ts) — a flat stand-in that
 * reads block appearance from an averaged texture color instead of full geometry. Colors are cached
 * by PNG file path so repeated /reload calls don't re-decode textures that haven't changed.
 */
object BlockFaceColorSampler {
    private val colorCache = ConcurrentHashMap<String, List<Int>>()

    /**
     * Returns (topColor, sideColor); falls back to [fallback] wherever the bbmodel/texture can't be
     * resolved.
     */
    fun sample(
        resourcesBlocksPath: Path,
        modelElement: String,
        fallback: List<Int>,
    ): Pair<List<Int>, List<Int>> {
        if (modelElement.isBlank()) return fallback to fallback
        val blockDir = resourcesBlocksPath.resolve(modelElement)
        val bbPath = blockDir.resolve("$modelElement.bbmodel")
        if (!bbPath.exists()) return fallback to fallback
        val model =
            runCatching { bbmodelJson.decodeFromString(BbModel.serializer(), bbPath.readText()) }
                .getOrNull() ?: return fallback to fallback
        val element = model.elements.firstOrNull() ?: return fallback to fallback
        val upTex = element.faces["up"]?.texture
        val sideTex =
            element.faces["north"]?.texture
                ?: element.faces["south"]?.texture
                ?: element.faces["east"]?.texture
                ?: element.faces["west"]?.texture

        val topColor = upTex?.let { meanColor(blockDir, model, it) } ?: fallback
        val sideColor = sideTex?.let { meanColor(blockDir, model, it) } ?: topColor
        return topColor to sideColor
    }

    private fun meanColor(blockDir: Path, model: BbModel, texIdx: Int): List<Int>? {
        val texPath =
            model.textures.getOrNull(texIdx)?.path?.takeIf { it.isNotBlank() } ?: return null
        val pngPath = blockDir.resolve(texPath.substringAfterLast('/'))
        val cacheKey = pngPath.toString()
        colorCache[cacheKey]?.let {
            return it
        }
        if (!pngPath.exists()) return null
        val img = runCatching { ImageIO.read(pngPath.toFile()) }.getOrNull() ?: return null
        val color = averagePixels(img)
        colorCache[cacheKey] = color
        return color
    }

    private fun averagePixels(img: BufferedImage): List<Int> {
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0L
        for (y in 0 until img.height) {
            for (x in 0 until img.width) {
                val argb = img.getRGB(x, y)
                if ((argb ushr 24) and 0xff == 0) continue // fully transparent pixel — skip
                r += (argb ushr 16) and 0xff
                g += (argb ushr 8) and 0xff
                b += argb and 0xff
                count++
            }
        }
        if (count == 0L) return listOf(128, 128, 128)
        return listOf((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    }
}
