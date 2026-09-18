package org.micoli.micraft.game.npc

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.micoli.micraft.game.OllamaConfig
import org.slf4j.LoggerFactory

private val ollamaLog = LoggerFactory.getLogger(OllamaClient::class.java)

data class ChatTurn(val role: String, val content: String)

/**
 * Raw, unvalidated intent parsed from the model's JSON-mode response. [actionType]/[questId]/
 * [itemId] must never be trusted directly — the caller (ChatNpcBehavior) revalidates them against
 * the NPC's own whitelists before acting on them.
 */
data class OllamaChatResult(
    val reply: String,
    val actionType: String,
    val questId: String? = null,
    val itemId: String? = null,
)

/**
 * Pure parsing of an Ollama `/api/chat` HTTP response body into an [OllamaChatResult] — no network,
 * so it's directly unit-testable. Returns null on any missing field or malformed JSON at either
 * nesting level (the outer chat envelope, or the JSON-mode `content` string within it).
 */
internal fun parseOllamaChatResponse(responseText: String, json: Json): OllamaChatResult? =
    runCatching {
            val content =
                json
                    .parseToJsonElement(responseText)
                    .jsonObject["message"]
                    ?.jsonObject
                    ?.get("content")
                    ?.jsonPrimitive
                    ?.content ?: return@runCatching null
            val parsed = json.parseToJsonElement(content).jsonObject
            val reply = parsed["reply"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
            val action = parsed["action"]?.jsonObject
            OllamaChatResult(
                reply = reply,
                actionType = action?.get("type")?.jsonPrimitive?.contentOrNull ?: "none",
                questId = action?.get("questId")?.jsonPrimitive?.contentOrNull,
                itemId = action?.get("itemId")?.jsonPrimitive?.contentOrNull,
            )
        }
        .getOrNull()

/**
 * Thin client for a local Ollama instance's `/api/chat` endpoint, JSON-mode constrained to
 * `{"reply": string, "action": {"type": "none"|"offer_quest"|"give_item", "questId"?, "itemId"?}}`.
 * Follows the same manual-JSON-parsing pattern as [org.micoli.micraft.auth.OAuthProvider] — no
 * ContentNegotiation plugin is installed in this project.
 */
open class OllamaClient(private val config: OllamaConfig) {
    private val http =
        HttpClient(CIO) { install(HttpTimeout) { requestTimeoutMillis = config.requestTimeoutMs } }
    private val json = Json { ignoreUnknownKeys = true }

    private val responseFormat: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("reply") { put("type", "string") }
            putJsonObject("action") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("type") {
                        put("type", "string")
                        putJsonArray("enum") {
                            add("none")
                            add("offer_quest")
                            add("give_item")
                        }
                    }
                    putJsonObject("questId") { put("type", "string") }
                    putJsonObject("itemId") { put("type", "string") }
                }
                putJsonArray("required") { add("type") }
            }
        }
        putJsonArray("required") {
            add("reply")
            add("action")
        }
    }

    /**
     * Returns null on any HTTP error, timeout, or malformed/unparsable response — never guesses.
     */
    open suspend fun chat(
        systemPrompt: String,
        history: List<ChatTurn>,
        userMessage: String,
    ): OllamaChatResult? =
        runCatching {
                val messages = buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                    history.forEach { turn ->
                        add(
                            buildJsonObject {
                                put("role", turn.role)
                                put("content", turn.content)
                            })
                    }
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", userMessage)
                        })
                }
                val requestBody = buildJsonObject {
                    put("model", config.model)
                    put("stream", false)
                    put("messages", messages)
                    put("format", responseFormat)
                }
                val responseText =
                    http
                        .post("${config.baseUrl}/api/chat") {
                            contentType(ContentType.Application.Json)
                            setBody(requestBody.toString())
                        }
                        .bodyAsText()
                parseOllamaChatResponse(responseText, json)
            }
            .onFailure { e -> ollamaLog.warn("Ollama chat call failed: {}", e.message) }
            .getOrNull()
}
