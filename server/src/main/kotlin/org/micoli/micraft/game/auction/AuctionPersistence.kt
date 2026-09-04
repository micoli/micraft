package org.micoli.micraft.game.auction

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.builtins.ListSerializer
import org.micoli.micraft.protocol.AuctionListing
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(AuctionPersistence::class.java)

private val yaml =
    Yaml(configuration = YamlConfiguration(strictMode = false, encodeDefaults = true))

class AuctionPersistence(private val worldDir: Path) {
    private val file = worldDir.resolve("auctions.yaml")

    fun loadListings(): List<AuctionListing> {
        if (!file.exists()) return emptyList()
        return try {
            yaml.decodeFromString(ListSerializer(AuctionListing.serializer()), file.readText())
        } catch (e: Exception) {
            log.warn("Failed to load auctions.yaml: {}", e.message)
            emptyList()
        }
    }

    fun saveListings(listings: List<AuctionListing>) {
        try {
            file.writeText(
                yaml.encodeToString(ListSerializer(AuctionListing.serializer()), listings))
        } catch (e: Exception) {
            log.warn("Failed to save auctions.yaml: {}", e.message)
        }
    }

    fun addListing(listing: AuctionListing) {
        saveListings(loadListings() + listing)
    }

    fun updateListing(updated: AuctionListing) {
        saveListings(loadListings().map { if (it.id == updated.id) updated else it })
    }
}
