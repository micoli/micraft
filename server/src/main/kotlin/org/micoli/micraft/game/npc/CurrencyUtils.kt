package org.micoli.micraft.game.npc

object CurrencyUtils {
    fun deductCopper(wallet: Long, cost: Int): Long {
        require(wallet >= cost) { "insufficient_funds" }
        return wallet - cost
    }

    fun addCopper(wallet: Long, amount: Int): Long = wallet + amount

    fun addCopper(wallet: Long, amount: Long): Long = wallet + amount
}
