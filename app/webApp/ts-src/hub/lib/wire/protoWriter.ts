import { encodeVarint } from "./varint";

const WIRE_VARINT = 0;
const WIRE_LEN = 2;

/**
 * Builds one protobuf message's wire bytes, field by field, in the exact declaration order of the
 * matching Kotlin `@Serializable` class — kotlinx.serialization.protobuf numbers fields 1, 2, 3…
 * by declaration order when no `@ProtoNumber` is present (true of every message in the hub's
 * subset). See `hubCodec.ts` for the per-message field tables this backs.
 */
export class ProtoWriter {
  private bytes: number[] = [];

  private tag(fieldNumber: number, wireType: number) {
    this.bytes.push(...encodeVarint(BigInt((fieldNumber << 3) | wireType)));
  }

  varint(fieldNumber: number, value: number | bigint | boolean) {
    const v = typeof value === "boolean" ? (value ? 1n : 0n) : BigInt(value);
    this.tag(fieldNumber, WIRE_VARINT);
    this.bytes.push(...encodeVarint(v));
  }

  optionalVarint(fieldNumber: number, value: number | bigint | boolean | null | undefined) {
    if (value === null || value === undefined) return;
    this.varint(fieldNumber, value);
  }

  private lenPrefixed(fieldNumber: number, data: Uint8Array) {
    this.tag(fieldNumber, WIRE_LEN);
    this.bytes.push(...encodeVarint(BigInt(data.length)));
    for (const b of data) this.bytes.push(b);
  }

  string(fieldNumber: number, value: string) {
    this.lenPrefixed(fieldNumber, new TextEncoder().encode(value));
  }

  optionalString(fieldNumber: number, value: string | null | undefined) {
    if (value === null || value === undefined) return;
    this.string(fieldNumber, value);
  }

  /** Embeds an already-encoded sub-message (or a proto3 map entry — same LEN wire shape). */
  message(fieldNumber: number, data: Uint8Array) {
    this.lenPrefixed(fieldNumber, data);
  }

  repeatedMessages(fieldNumber: number, items: Uint8Array[]) {
    for (const item of items) this.message(fieldNumber, item);
  }

  repeatedStrings(fieldNumber: number, items: string[]) {
    for (const item of items) this.string(fieldNumber, item);
  }

  /** proto3 map entry wire shape: an embedded `{key: 1, value: 2}` message per entry. */
  stringToInt32Map(fieldNumber: number, entries: Record<string, number>) {
    for (const [key, value] of Object.entries(entries)) {
      const entry = new ProtoWriter();
      entry.string(1, key);
      entry.varint(2, value);
      this.message(fieldNumber, entry.finish());
    }
  }

  finish(): Uint8Array {
    return new Uint8Array(this.bytes);
  }
}
