package org.micoli.micraft.game.world

/**
 * The on-disk chunk format predates the y-major [Chunk.index]: block/state files hold one flat
 * x-major array and entity files store x-major master indices. Kept as is so existing worlds load
 * unchanged; converted at the persistence boundary only.
 */
object LegacyChunkLayout {
    fun index(x: Int, y: Int, z: Int) = (x * Chunk.SIZE_Y * Chunk.SIZE_Z) + (y * Chunk.SIZE_Z) + z

    private fun xyz(idx: Int): Triple<Int, Int, Int> {
        val yz = Chunk.SIZE_Y * Chunk.SIZE_Z
        return Triple(idx / yz, (idx % yz) / Chunk.SIZE_Z, idx % Chunk.SIZE_Z)
    }

    fun toStorage(flat: ByteArray): SectionedBytes {
        val yMajor = ByteArray(Chunk.TOTAL)
        for (x in 0 until Chunk.SIZE_X) for (y in 0 until Chunk.SIZE_Y) for (z in
            0 until Chunk.SIZE_Z) {
            yMajor[Chunk.index(x, y, z)] = flat[index(x, y, z)]
        }
        return SectionedBytes.of(yMajor)
    }

    fun fromStorage(storage: SectionedBytes): ByteArray {
        val flat = ByteArray(Chunk.TOTAL)
        for (x in 0 until Chunk.SIZE_X) for (y in 0 until Chunk.SIZE_Y) for (z in
            0 until Chunk.SIZE_Z) {
            flat[index(x, y, z)] = storage[Chunk.index(x, y, z)]
        }
        return flat
    }

    fun toStorageIndex(legacyIdx: Int): Int =
        xyz(legacyIdx).let { (x, y, z) -> Chunk.index(x, y, z) }

    fun fromStorageIndex(idx: Int): Int = Chunk.indexToXYZ(idx).let { (x, y, z) -> index(x, y, z) }
}
