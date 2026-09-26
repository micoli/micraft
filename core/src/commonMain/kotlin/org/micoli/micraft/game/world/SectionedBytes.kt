package org.micoli.micraft.game.world

/**
 * A flat byte volume of [size] cells, stored as fixed-size sections that are only allocated once
 * they hold a non-zero byte. Indices are y-major ([Chunk.index]), so a section is a run of whole Y
 * layers: the air above the terrain — most of a 1025-block-high chunk — costs nothing.
 *
 * Mutable through [set] for code that fills a fresh volume. A [Chunk] never mutates its storage
 * once built: [withByte] shares every untouched section with the original, so a block edit copies
 * one section instead of the whole volume.
 */
class SectionedBytes
private constructor(
    val size: Int,
    private val sectionSize: Int,
    private val sections: Array<ByteArray?>
) {
    constructor(
        size: Int = Chunk.TOTAL,
        sectionSize: Int = Chunk.SECTION_SIZE
    ) : this(size, sectionSize, arrayOfNulls((size + sectionSize - 1) / sectionSize))

    private val shift =
        if (sectionSize.countOneBits() == 1) sectionSize.countTrailingZeroBits() else -1

    private fun sectionOf(index: Int) = if (shift >= 0) index ushr shift else index / sectionSize

    private fun offsetOf(index: Int) =
        if (shift >= 0) index and (sectionSize - 1) else index % sectionSize

    operator fun get(index: Int): Byte = sections[sectionOf(index)]?.get(offsetOf(index)) ?: 0

    operator fun set(index: Int, value: Byte) {
        val s = sectionOf(index)
        val section =
            sections[s]
                ?: if (value == ZERO) return else ByteArray(sectionSize).also { sections[s] = it }
        section[offsetOf(index)] = value
    }

    /** A copy with one byte changed, sharing every other section with this one. */
    fun withByte(index: Int, value: Byte): SectionedBytes {
        if (get(index) == value) return this
        val s = sectionOf(index)
        val shared = sections.copyOf()
        shared[s] =
            (sections[s]?.copyOf() ?: ByteArray(sectionSize)).also { it[offsetOf(index)] = value }
        return SectionedBytes(size, sectionSize, shared)
    }

    /** No section allocated: every byte is zero. Cheap enough for hot loops, unlike [isAllZero]. */
    fun isUnallocated(): Boolean = sections.all { it == null }

    fun isAllZero(): Boolean =
        sections.all { section -> section == null || section.all { it == ZERO } }

    /** Highest index holding a non-zero byte, or -1 when every byte is zero. */
    fun highestNonZeroIndex(): Int {
        for (s in sections.indices.reversed()) {
            val section = sections[s] ?: continue
            for (o in section.indices.reversed()) if (section[o] != ZERO) return s * sectionSize + o
        }
        return -1
    }

    /** The first [length] bytes as one flat y-major array. */
    fun toByteArray(length: Int = size): ByteArray {
        val out = ByteArray(length)
        for (s in sections.indices) {
            val section = sections[s] ?: continue
            val start = s * sectionSize
            if (start >= length) break
            section.copyInto(out, start, 0, minOf(sectionSize, length - start))
        }
        return out
    }

    /** Sections actually allocated, in bytes: what the volume costs in memory. */
    fun allocatedBytes(): Int = sections.count { it != null } * sectionSize

    fun contentEquals(other: SectionedBytes): Boolean {
        if (size != other.size) return false
        for (i in 0 until size) if (get(i) != other[i]) return false
        return true
    }

    fun contentHashCode(): Int {
        var h = size
        for (i in 0 until size) {
            val b = get(i)
            if (b != ZERO) h = 31 * h + i * 257 + b
        }
        return h
    }

    companion object {
        private const val ZERO: Byte = 0

        /**
         * Builds a volume from a flat y-major array; [bytes] may be shorter than [size] (a wire
         * buffer that stops at the chunk's top Y). All-zero sections stay unallocated.
         */
        fun of(
            bytes: ByteArray,
            size: Int = Chunk.TOTAL,
            sectionSize: Int = Chunk.SECTION_SIZE
        ): SectionedBytes {
            val volume = SectionedBytes(size, sectionSize)
            var start = 0
            while (start < bytes.size && start < size) {
                val end = minOf(start + sectionSize, bytes.size, size)
                if ((start until end).any { bytes[it] != ZERO }) {
                    volume.sections[start / sectionSize] =
                        ByteArray(sectionSize).also { bytes.copyInto(it, 0, start, end) }
                }
                start += sectionSize
            }
            return volume
        }
    }
}
