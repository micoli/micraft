package org.micoli.micraft.game.npc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Credits: Fantasy name generation logic based on
// https://github.com/FyefoxxM/fantasy-name-generator
// by FyefoxxM — see also https://jdookeran.medium.com/day-7-fantasy-name-generator-c2b4458b13f7
object FantasyNameGenerator {
    private val data: Map<String, Map<String, List<String>>> by lazy { loadData() }

    private fun loadData(): Map<String, Map<String, List<String>>> {
        val json =
            FantasyNameGenerator::class
                .java
                .classLoader
                .getResourceAsStream("name_data.json")
                ?.bufferedReader()
                ?.readText() ?: error("name_data.json not found in resources")
        val root = Json.parseToJsonElement(json).jsonObject
        return root.mapValues { (_, raceEl) ->
            raceEl.jsonObject.mapValues { (_, listEl) ->
                (listEl as JsonArray).map { it.jsonPrimitive.content }
            }
        }
    }

    private fun race(npcType: String, isAnimal: Boolean): String =
        when {
            isAnimal -> "animal"
            "orc" in npcType || "goblin" in npcType || "troll" in npcType -> "orc"
            "elf" in npcType || "elven" in npcType -> "elf"
            "dwarf" in npcType || "dwarven" in npcType -> "dwarf"
            else -> "human"
        }

    fun generate(npcType: String, isAnimal: Boolean = false): String {
        val r = race(npcType, isAnimal)
        val d = data[r] ?: data["human"]!!
        return when (r) {
            "orc" -> {
                val first = d["first_start"]!!.random() + d["first_end"]!!.random()
                val title = d["titles"]!!.random()
                "${first.replaceFirstChar { it.uppercase() }} $title"
            }
            // A pet gets one plain name, not a "First Last" fantasy name.
            "animal" -> {
                val first = d["first_start"]!!.random() + d["first_end"]!!.random()
                first.replaceFirstChar { it.uppercase() }
            }
            else -> {
                val first = d["first_start"]!!.random() + d["first_end"]!!.random()
                val last = d["surname_prefix"]!!.random() + d["surname_suffix"]!!.random()
                "${first.replaceFirstChar { it.uppercase() }} ${last.replaceFirstChar { it.uppercase() }}"
            }
        }
    }
}
