package org.micoli.micraft.game.session

import java.util.concurrent.atomic.AtomicLong

class NetworkStats {
    val bytesIn = AtomicLong(0)
    val bytesOut = AtomicLong(0)
}
