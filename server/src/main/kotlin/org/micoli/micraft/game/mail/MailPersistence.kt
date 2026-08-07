package org.micoli.micraft.game.mail

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.builtins.ListSerializer
import org.micoli.micraft.protocol.MailMessage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("MailPersistence")

private val yaml =
    Yaml(configuration = YamlConfiguration(strictMode = false, encodeDefaults = true))

internal fun String.sanitize() = replace(Regex("[^a-zA-Z0-9_-]"), "_").take(64)

class MailPersistence(private val playersDir: Path) {
    private fun mailboxFile(name: String): Path =
        playersDir.resolve("${name.sanitize()}_mailbox.yaml")

    fun loadMails(name: String): List<MailMessage> {
        val file = mailboxFile(name)
        if (!file.exists()) return emptyList()
        return try {
            yaml.decodeFromString(ListSerializer(MailMessage.serializer()), file.readText())
        } catch (e: Exception) {
            log.warn("Failed to load mailbox for {}: {}", name, e.message)
            emptyList()
        }
    }

    fun saveMails(name: String, mails: List<MailMessage>) {
        val file = mailboxFile(name)
        try {
            file.writeText(yaml.encodeToString(ListSerializer(MailMessage.serializer()), mails))
        } catch (e: Exception) {
            log.warn("Failed to save mailbox for {}: {}", name, e.message)
        }
    }

    fun addMail(name: String, mail: MailMessage) {
        val existing = loadMails(name)
        saveMails(name, existing + mail)
    }

    fun updateMail(name: String, updated: MailMessage) {
        val mails = loadMails(name).map { if (it.id == updated.id) updated else it }
        saveMails(name, mails)
    }

    fun deleteMail(name: String, mailId: String) {
        val mails = loadMails(name).filter { it.id != mailId }
        saveMails(name, mails)
    }

    fun playerExists(name: String): Boolean =
        playersDir.resolve("${name.sanitize()}.yaml").exists() ||
            playersDir.resolve("${name.sanitize()}.json").exists()

    fun knownPlayerNames(): List<String> {
        val dir = playersDir.toFile()
        return dir.listFiles { f ->
                (f.extension == "yaml" || f.extension == "json") &&
                    !f.name.endsWith("_mailbox.yaml") &&
                    !f.name.endsWith("-keybindings.json") &&
                    !f.name.endsWith("-keybindings.yaml") &&
                    !f.name.endsWith("-custom-commands.yaml") &&
                    !f.name.endsWith("-macros.yaml")
            }
            ?.map { it.nameWithoutExtension }
            ?.sorted() ?: emptyList()
    }
}
