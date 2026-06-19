package org.micoli.micraft

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform