import { decodeVarint } from "./varint";

interface RawField {
  wireType: number;
  varint?: bigint;
  bytes?: Uint8Array;
}

/** Every occurrence of every field number, in wire order — repeated fields need every entry. */
export type FieldTable = Map<number, RawField[]>;

export function readFields(bytes: Uint8Array): FieldTable {
  const fields: FieldTable = new Map();
  let pos = 0;
  while (pos < bytes.length) {
    const tagRes = decodeVarint(bytes, pos);
    pos = tagRes.next;
    const tag = Number(tagRes.value);
    const fieldNumber = tag >>> 3;
    const wireType = tag & 0x7;
    let entry: RawField;
    if (wireType === 0) {
      const r = decodeVarint(bytes, pos);
      pos = r.next;
      entry = { wireType, varint: r.value };
    } else if (wireType === 2) {
      const lenRes = decodeVarint(bytes, pos);
      pos = lenRes.next;
      const len = Number(lenRes.value);
      entry = { wireType, bytes: bytes.slice(pos, pos + len) };
      pos += len;
    } else {
      throw new Error(`hub wire codec: unsupported wire type ${wireType} (field ${fieldNumber})`);
    }
    const list = fields.get(fieldNumber);
    if (list) list.push(entry);
    else fields.set(fieldNumber, [entry]);
  }
  return fields;
}

function firstBytes(fields: FieldTable, fieldNumber: number): Uint8Array | undefined {
  return fields.get(fieldNumber)?.[0]?.bytes;
}

function firstVarint(fields: FieldTable, fieldNumber: number): bigint | undefined {
  return fields.get(fieldNumber)?.[0]?.varint;
}

export function readString(fields: FieldTable, fieldNumber: number, fallback = ""): string {
  const raw = firstBytes(fields, fieldNumber);
  return raw ? new TextDecoder().decode(raw) : fallback;
}

export function readOptionalString(fields: FieldTable, fieldNumber: number): string | null {
  const raw = firstBytes(fields, fieldNumber);
  return raw ? new TextDecoder().decode(raw) : null;
}

export function readInt(fields: FieldTable, fieldNumber: number, fallback = 0): number {
  const v = firstVarint(fields, fieldNumber);
  return v === undefined ? fallback : Number(v);
}

export function readOptionalLong(fields: FieldTable, fieldNumber: number): number | null {
  const v = firstVarint(fields, fieldNumber);
  return v === undefined ? null : Number(v);
}

export function readLong(fields: FieldTable, fieldNumber: number, fallback = 0): number {
  return readOptionalLong(fields, fieldNumber) ?? fallback;
}

export function readBool(fields: FieldTable, fieldNumber: number, fallback = false): boolean {
  const v = firstVarint(fields, fieldNumber);
  return v === undefined ? fallback : v !== 0n;
}

export function readMessages(fields: FieldTable, fieldNumber: number): Uint8Array[] {
  return (fields.get(fieldNumber) ?? []).map((f) => f.bytes).filter((b): b is Uint8Array => !!b);
}

export function readRepeatedStrings(fields: FieldTable, fieldNumber: number): string[] {
  return readMessages(fields, fieldNumber).map((b) => new TextDecoder().decode(b));
}

export function readStringToInt32Map(fields: FieldTable, fieldNumber: number): Record<string, number> {
  const result: Record<string, number> = {};
  for (const entryBytes of readMessages(fields, fieldNumber)) {
    const entry = readFields(entryBytes);
    result[readString(entry, 1)] = readInt(entry, 2);
  }
  return result;
}
