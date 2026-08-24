package org.micoli.micraft.game

class NetworkStats {
    var bytesIn = 0
    var bytesOut = 0

    // Accumulated time spent decoding chunk-WS frames (ServerMessageCodec.decode) since the
    // last HUD reset — read by LocalPlayerController into its rolling stats window, same reset
    // cadence as bytesIn/bytesOut.
    var chunkDecodeMsAccum = 0.0
    var chunkDecodeCount = 0
}
