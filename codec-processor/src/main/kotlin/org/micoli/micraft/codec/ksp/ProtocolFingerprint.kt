package org.micoli.micraft.codec.ksp

import java.security.MessageDigest

/**
 * Hash of the wire protocol: one line per message (side, `@ProtoId`, fields) and per project type
 * it carries (fields, enum entries, sealed subclasses). Any change that can break decoding across a
 * client and a server built from different sources changes it; the line order does not.
 */
object ProtocolFingerprint {
    fun of(lines: Collection<String>): String =
        MessageDigest.getInstance("SHA-256")
            .digest(lines.sorted().joinToString("\n").toByteArray())
            .take(8)
            .joinToString("") { "%02x".format(it) }
}
