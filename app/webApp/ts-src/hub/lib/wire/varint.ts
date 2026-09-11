/**
 * Minimal varint helpers for the hand-rolled protobuf codec (see `../hubCodec.ts`). Plain varint,
 * no zigzag — every field this codec touches (ids, counts, prices, timestamps, coordinates that
 * stay within `WorldConstants.Y`) is non-negative, matching kotlinx.serialization.protobuf's
 * default `int32`/`int64` (varint, not `SINT32`/`SINT64`) for un-annotated `Int`/`Long` fields.
 */

export function encodeVarint(value: bigint): number[] {
  if (value < 0n) throw new Error("encodeVarint: negative values are not supported");
  const bytes: number[] = [];
  let v = value;
  do {
    let b = Number(v & 0x7fn);
    v >>= 7n;
    if (v !== 0n) b |= 0x80;
    bytes.push(b);
  } while (v !== 0n);
  return bytes;
}

export function decodeVarint(bytes: Uint8Array, offset: number): { value: bigint; next: number } {
  let result = 0n;
  let shift = 0n;
  let pos = offset;
  for (;;) {
    const b = bytes[pos++];
    if (b === undefined) throw new Error("decodeVarint: truncated buffer");
    result |= BigInt(b & 0x7f) << shift;
    if ((b & 0x80) === 0) break;
    shift += 7n;
  }
  return { value: result, next: pos };
}
