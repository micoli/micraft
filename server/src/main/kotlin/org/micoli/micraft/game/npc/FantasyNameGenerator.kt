package org.micoli.micraft.game.npc

import kotlin.random.Random
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Credits: Fantasy name generation logic based on https://github.com/FyefoxxM/fantasy-name-generator
// by FyefoxxM — see also https://jdookeran.medium.com/day-7-fantasy-name-generator-c2b4458b13f7
object FantasyNameGenerator {
    private val data: Map<String, Map<String, List<String>>> by lazy { loadData() }

    private fun loadData(): Map<String, Map<String, List<String>>> {
        val json = FantasyNameGenerator::class.java.classLoader
            .getResourceAsStream("name_data.json")
            ?.bufferedReader()
            ?.readText()
            ?: error("name_data.json not found in resources")
        val root = Json.parseToJsonElement(json).jsonObject
        return root.mapValues { (_, raceEl) ->
            raceEl.jsonObject.mapValues { (_, listEl) ->
                (listEl as JsonArray).map { it.jsonPrimitive.content }
            }
        }
    }

    private fun race(npcType: String): String = when {
        "orc" in npcType || "goblin" in npcType || "troll" in npcType -> "orc"
        "elf" in npcType || "elven" in npcType -> "elf"
        "dwarf" in npcType || "dwarven" in npcType -> "dwarf"
        else -> "human"
    }

    fun generate(npcType: String): String {
        val r = race(npcType)
        val d = data[r] ?: data["human"]!!
        return when (r) {
            "orc" -> {
                val first = d["first_start"]!!.random() + d["first_end"]!!.random()
                val title = d["titles"]!!.random()
                "${first.replaceFirstChar { it.uppercase() }} $title"
            }
            else -> {
                val first = d["first_start"]!!.random() + d["first_end"]!!.random()
                val last = d["surname_prefix"]!!.random() + d["surname_suffix"]!!.random()
                "${first.replaceFirstChar { it.uppercase() }} ${last.replaceFirstChar { it.uppercase() }}"
            }
        }
    }
}
