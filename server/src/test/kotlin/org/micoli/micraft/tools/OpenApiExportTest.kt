package org.micoli.micraft.tools

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import org.micoli.micraft.module

class OpenApiExportTest {

    @Test
    fun `openapi yaml and README API Routes section are up to date`() = testApplication {
        application { module() }
        val spec = client.get("/api.yaml").bodyAsText()
        val routesTable = buildRoutesTable(spec)
        val checkOnly = System.getProperty("openapi.check") == "true"

        val specFile = File("server/openapi/openapi.yaml")
        val readme = File("README.md")
        val readmeContent = readme.readText()
        val begin = "<!-- BEGIN_API_ROUTES -->"
        val end = "<!-- END_API_ROUTES -->"
        val beginIdx = readmeContent.indexOf(begin)
        val endIdx = readmeContent.indexOf(end)
        check(beginIdx >= 0 && endIdx >= 0) {
            "ERROR: sentinel markers $begin / $end not found in README.md"
        }
        val section = listOf(begin, "", routesTable, "", end).joinToString("\n")
        val newReadmeContent =
            readmeContent.substring(0, beginIdx) +
                section +
                readmeContent.substring(endIdx + end.length)

        if (checkOnly) {
            assertEquals(
                specFile.readText(),
                spec,
                "server/openapi/openapi.yaml is out of date — run: make dc CMD=\"./gradlew :server:exportOpenApi\"")
            assertEquals(
                readmeContent,
                newReadmeContent,
                "README.md API Routes section is out of date — run: make dc CMD=\"./gradlew :server:exportOpenApi\"")
        } else {
            specFile.parentFile.mkdirs()
            specFile.writeText(spec)
            readme.writeText(newReadmeContent)
        }
    }
}

private fun buildRoutesTable(specYaml: String): String {
    val mapper = ObjectMapper(YAMLFactory())
    @Suppress("UNCHECKED_CAST")
    val root = mapper.readValue(specYaml, Map::class.java) as Map<String, Any?>
    @Suppress("UNCHECKED_CAST") val paths = (root["paths"] as? Map<String, Any?>).orEmpty()

    data class RouteRow(val method: String, val path: String, val description: String)

    val rows =
        paths.entries
            .flatMap { (path, operations) ->
                @Suppress("UNCHECKED_CAST")
                (operations as Map<String, Any?>).entries.mapNotNull { (method, operation) ->
                    if (method.lowercase() !in setOf("get", "post", "put", "delete", "patch"))
                        return@mapNotNull null
                    @Suppress("UNCHECKED_CAST") val op = operation as Map<String, Any?>
                    val description =
                        (op["summary"] as? String) ?: (op["description"] as? String) ?: ""
                    RouteRow(method.uppercase(), path, description.replace("\n", " "))
                }
            }
            .sortedWith(compareBy({ it.path }, { it.method }))

    val header = listOf("| Method | Path | Description |", "|--------|------|-------------|")
    val body =
        rows.map { "| ${it.method} | `${it.path}` | ${it.description.replace("|", "\\|")} |" }
    return (header + body).joinToString("\n")
}
