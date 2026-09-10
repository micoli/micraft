package org.micoli.micraft.config

import java.nio.file.Path

/**
 * A bundled-default file (under `resources/`) paired with its user-writable override (under the
 * data root).
 */
data class OverridablePaths(val resources: Path, val data: Path)

/**
 * Single source of truth for every filesystem location the server reads game data from.
 *
 * `resources/` holds the shipped defaults (read-only, relative to the process CWD). The data root
 * holds the user's world, overrides and generated config; it defaults to `data/` but can be moved
 * with `MICRAFT_DATA_DIR` — mirrors [org.micoli.micraft.di.worldName]'s use of
 * `MICRAFT_WORLD_NAME`.
 */
object ConfigPaths {
    /** Resolves the data root from a raw env value — blank/null falls back to `data/`. */
    internal fun dataRootFrom(envValue: String?): Path =
        Path.of(envValue?.takeIf { it.isNotBlank() } ?: "data")

    val dataRoot: Path = dataRootFrom(System.getenv("MICRAFT_DATA_DIR"))
    val resourcesRoot: Path = Path.of("resources")

    /** `data/config/<rel>` — user-writable config, generated from defaults on first run. */
    fun dataConfig(rel: String): Path = dataRoot.resolve("config").resolve(rel)

    /** `resources/config/<rel>` — bundled config defaults. */
    fun resourcesConfig(rel: String): Path = resourcesRoot.resolve("config").resolve(rel)

    /** `data/resources/<rel>` — user-writable per-entry resource overrides (blocks, armors, …). */
    fun dataResources(rel: String): Path = dataRoot.resolve("resources").resolve(rel)

    /** `resources/<rel>` — bundled resource trees (blocks, weapons, entities, …). */
    fun resourcesDir(rel: String): Path = resourcesRoot.resolve(rel)

    /** `data/world/<rel>`. */
    fun dataWorld(rel: String): Path = dataRoot.resolve("world").resolve(rel)

    /**
     * Pair for a single-file overridable config: `resources/config/<name>` + `data/config/<name>`.
     */
    fun configPair(name: String): OverridablePaths =
        OverridablePaths(resourcesConfig(name), dataConfig(name))

    /**
     * Pair for a directory-based overridable registry: `resources/<sub>` + `data/resources/<sub>`.
     */
    fun dirPair(sub: String): OverridablePaths =
        OverridablePaths(resourcesDir(sub), dataResources(sub))
}
