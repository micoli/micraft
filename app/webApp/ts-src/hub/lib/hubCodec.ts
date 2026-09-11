import { ProtoWriter } from "./wire/protoWriter";
import {
  readBool,
  readFields,
  readInt,
  readLong,
  readMessages,
  readOptionalLong,
  readOptionalString,
  readRepeatedStrings,
  readString,
  readStringToInt32Map,
  type FieldTable,
} from "./wire/protoReader";
import {
  AUCTION_DURATIONS,
  AUCTION_STATUSES,
  type AuctionBidDto,
  type AuctionDuration,
  type AuctionFilterDto,
  type AuctionListingDto,
  type AuctionStatus,
  type ChannelSubscriptionDto,
  type ClaimInfoDto,
  type MailMessageDto,
} from "./hubTypes";

/**
 * Wire codec for `/hub` — reuses the game's real binary protocol as-is (see the web-companion
 * plan's "Encodage des messages" section): `[byte ProtoId][protobuf payload]`, byte-identical to
 * `/game`. `ProtoId` values below are copied from the `@ProtoId(n)` annotations in
 * `core/.../protocol/ClientMessage.kt` / `ServerMessage.kt` — each sealed class numbers its own
 * subclasses independently, so a Connect (`ClientMessage`, id 0) and a Welcome (`ServerMessage`,
 * id 0) share a byte value but never a direction. Only the subset `/hub` actually uses is
 * implemented; anything else is a client-side send() no-op or a decode the server never sends.
 *
 * Field numbers inside each message mirror the Kotlin class's *declaration order* — kotlinx
 * doesn't pin them with `@ProtoNumber` for these classes, so field 1 = the first constructor
 * property, field 2 = the second, etc. Reordering a Kotlin property breaks this exactly as it
 * would break the wasm client; `HubWireFixtureTest.kt` + `hubCodec.test.ts` catch drift.
 */

// ─── Outgoing (ClientMessage) ────────────────────────────────────────────────

export interface ConnectMsg {
  playerName: string;
  userName?: string;
  preferredLanguage?: string;
  token?: string;
  needsWorld?: boolean;
  connectionId?: string;
}

export interface SendMailMsg {
  to: string;
  subject: string;
  body: string;
  attachments?: Record<string, number>;
  copperAmount?: number;
}

export interface AuctionCreateListingMsg {
  itemType: string;
  quantity: number;
  duration: AuctionDuration;
  startingPrice: number;
  buyNowPrice?: number | null;
}

export interface AuctionSetFilterMsg {
  filter: AuctionFilterDto;
}

export interface ChatSendMsg {
  channel: string;
  text: string;
}

export interface ClaimSetTrustedMsg {
  claimId: string;
  playerName: string;
  trusted: boolean;
}

function encodeAuctionFilter(filter: AuctionFilterDto): Uint8Array {
  const w = new ProtoWriter();
  w.optionalString(1, filter.itemType);
  w.optionalString(2, filter.sellerName);
  w.optionalVarint(3, filter.minPrice ?? undefined);
  w.optionalVarint(4, filter.maxPrice ?? undefined);
  w.varint(5, filter.mineOnly);
  w.varint(6, filter.expiredOnly);
  w.varint(7, filter.myBidsOnly);
  return w.finish();
}

/** `{ name: "SendMail", payload: {...} }` -> the exact bytes `/hub` expects on the wire. */
const CLIENT_ENCODERS: Record<string, { id: number; encode: (payload: never) => Uint8Array }> = {
  Connect: {
    id: 0,
    encode: (m: ConnectMsg) => {
      const w = new ProtoWriter();
      w.string(1, m.playerName);
      w.string(2, m.userName ?? m.playerName);
      w.string(3, m.preferredLanguage ?? "en");
      w.string(4, m.token ?? "");
      w.varint(5, m.needsWorld ?? true);
      w.string(6, m.connectionId ?? "");
      return w.finish();
    },
  },
  ChatSend: {
    id: 11,
    encode: (m: ChatSendMsg) => {
      const w = new ProtoWriter();
      w.string(1, m.channel);
      w.string(2, m.text);
      return w.finish();
    },
  },
  SendMail: {
    id: 22,
    encode: (m: SendMailMsg) => {
      const w = new ProtoWriter();
      w.string(1, m.to);
      w.string(2, m.subject);
      w.string(3, m.body);
      w.stringToInt32Map(4, m.attachments ?? {});
      w.varint(5, m.copperAmount ?? 0);
      return w.finish();
    },
  },
  MarkMailSeen: {
    id: 23,
    encode: (m: { mailId: string }) => {
      const w = new ProtoWriter();
      w.string(1, m.mailId);
      return w.finish();
    },
  },
  DeleteMail: {
    id: 24,
    encode: (m: { mailId: string }) => {
      const w = new ProtoWriter();
      w.string(1, m.mailId);
      return w.finish();
    },
  },
  ClaimMailAttachments: {
    id: 25,
    encode: (m: { mailId: string }) => {
      const w = new ProtoWriter();
      w.string(1, m.mailId);
      return w.finish();
    },
  },
  AuctionCreateListing: {
    id: 37,
    encode: (m: AuctionCreateListingMsg) => {
      const w = new ProtoWriter();
      w.string(1, m.itemType);
      w.varint(2, m.quantity);
      w.varint(3, AUCTION_DURATIONS.indexOf(m.duration));
      w.varint(4, m.startingPrice);
      w.optionalVarint(5, m.buyNowPrice ?? undefined);
      return w.finish();
    },
  },
  AuctionPlaceBid: {
    id: 38,
    encode: (m: { listingId: string; amount: number }) => {
      const w = new ProtoWriter();
      w.string(1, m.listingId);
      w.varint(2, m.amount);
      return w.finish();
    },
  },
  AuctionBuyNow: {
    id: 39,
    encode: (m: { listingId: string }) => {
      const w = new ProtoWriter();
      w.string(1, m.listingId);
      return w.finish();
    },
  },
  AuctionCancelListing: {
    id: 40,
    encode: (m: { listingId: string }) => {
      const w = new ProtoWriter();
      w.string(1, m.listingId);
      return w.finish();
    },
  },
  AuctionSetFilter: {
    id: 41,
    encode: (m: AuctionSetFilterMsg) => {
      const w = new ProtoWriter();
      w.message(1, encodeAuctionFilter(m.filter));
      return w.finish();
    },
  },
  ClaimAbandon: {
    id: 43,
    encode: (m: { claimId: string }) => {
      const w = new ProtoWriter();
      w.string(1, m.claimId);
      return w.finish();
    },
  },
  ClaimSetTrusted: {
    id: 44,
    encode: (m: ClaimSetTrustedMsg) => {
      const w = new ProtoWriter();
      w.string(1, m.claimId);
      w.string(2, m.playerName);
      w.varint(3, m.trusted);
      return w.finish();
    },
  },
};

export function encodeHubMessage(name: keyof typeof CLIENT_ENCODERS, payload: unknown): Uint8Array {
  const entry = CLIENT_ENCODERS[name];
  if (!entry) throw new Error(`hub codec: no encoder for outgoing message "${name}"`);
  const body = entry.encode(payload as never);
  const out = new Uint8Array(body.length + 1);
  out[0] = entry.id;
  out.set(body, 1);
  return out;
}

// ─── Incoming (ServerMessage) ────────────────────────────────────────────────

export interface NotificationMsg {
  message: string;
  channel: string;
}

export interface ChatMessageMsg {
  channel: string;
  sender: string;
  message: string;
}

export interface ChannelsSyncMsg {
  subscribedChannels: ChannelSubscriptionDto[];
  knownChannels: string[];
}

export interface InventoryUpdateMsg {
  inventory: Record<string, number>;
}

export interface WalletUpdateMsg {
  copper: number;
}

export interface MailSyncMsg {
  mails: MailMessageDto[];
}

export interface MailReceivedMsg {
  mail: MailMessageDto;
}

export interface MailUpdateMsg {
  mail: MailMessageDto;
}

export interface MailDeletedMsg {
  mailId: string;
}

export interface AuctionListingsUpdateMsg {
  listings: AuctionListingDto[];
}

export interface ClaimSyncMsg {
  claims: ClaimInfoDto[];
}

export interface ClaimDeniedMsg {
  reason: string;
}

function decodeChannelSubscription(bytes: Uint8Array): ChannelSubscriptionDto {
  const f = readFields(bytes);
  return { name: readString(f, 1), autoFocus: readBool(f, 2) };
}

function decodeMailMessage(bytes: Uint8Array): MailMessageDto {
  const f = readFields(bytes);
  return {
    id: readString(f, 1),
    from: readString(f, 2),
    to: readString(f, 3),
    subject: readString(f, 4),
    body: readString(f, 5),
    attachments: readStringToInt32Map(f, 6),
    sentAt: readLong(f, 7),
    seen: readBool(f, 8),
    attachmentsClaimed: readBool(f, 9),
    copperAmount: readLong(f, 10),
  };
}

function decodeAuctionBid(bytes: Uint8Array): AuctionBidDto {
  const f = readFields(bytes);
  return {
    bidderId: readString(f, 1),
    bidderName: readString(f, 2),
    amount: readLong(f, 3),
    atMs: readLong(f, 4),
  };
}

function enumOrFallback<T extends string>(values: T[], index: number, fallback: T): T {
  return values[index] ?? fallback;
}

function decodeAuctionListing(bytes: Uint8Array): AuctionListingDto {
  const f = readFields(bytes);
  return {
    id: readString(f, 1),
    sellerId: readString(f, 2),
    sellerName: readString(f, 3),
    itemType: readString(f, 4),
    quantity: readInt(f, 5),
    createdAtMs: readLong(f, 6),
    expiresAtMs: readLong(f, 7),
    duration: enumOrFallback<AuctionDuration>(AUCTION_DURATIONS, readInt(f, 8), "H12"),
    startingPrice: readLong(f, 9),
    buyNowPrice: readOptionalLong(f, 10),
    currentBid: readOptionalLong(f, 11),
    currentBidderId: readOptionalString(f, 12),
    currentBidderName: readOptionalString(f, 13),
    status: enumOrFallback<AuctionStatus>(AUCTION_STATUSES, readInt(f, 14), "ACTIVE"),
    bidHistory: readMessages(f, 15).map(decodeAuctionBid),
  };
}

function decodeChunkPos(bytes: Uint8Array): { cx: number; cz: number } {
  const f = readFields(bytes);
  return { cx: readInt(f, 1), cz: readInt(f, 2) };
}

function decodeClaimInfo(bytes: Uint8Array): ClaimInfoDto {
  const f = readFields(bytes);
  return {
    id: readString(f, 1),
    chunks: readMessages(f, 2).map(decodeChunkPos),
    yMin: readInt(f, 3),
    yMax: readInt(f, 4),
    ownerId: readString(f, 5),
    ownerName: readString(f, 6),
    trustedPlayerNames: readRepeatedStrings(f, 7),
  };
}

interface ServerDecoder {
  name: string;
  decode: (fields: FieldTable) => unknown;
}

const SERVER_DECODERS: Record<number, ServerDecoder> = {
  7: {
    name: "Notification",
    decode: (f): NotificationMsg => ({ message: readString(f, 1), channel: readString(f, 2, "system") }),
  },
  8: {
    name: "ChatMessage",
    decode: (f): ChatMessageMsg => ({
      channel: readString(f, 1),
      sender: readString(f, 2),
      message: readString(f, 3),
    }),
  },
  9: {
    name: "ChannelsSync",
    decode: (f): ChannelsSyncMsg => ({
      subscribedChannels: readMessages(f, 1).map(decodeChannelSubscription),
      knownChannels: readRepeatedStrings(f, 2),
    }),
  },
  12: {
    name: "InventoryUpdate",
    decode: (f): InventoryUpdateMsg => ({ inventory: readStringToInt32Map(f, 1) }),
  },
  48: {
    name: "WalletUpdate",
    decode: (f): WalletUpdateMsg => ({ copper: readLong(f, 1) }),
  },
  49: {
    name: "MailSync",
    decode: (f): MailSyncMsg => ({ mails: readMessages(f, 1).map(decodeMailMessage) }),
  },
  50: {
    name: "MailReceived",
    decode: (f): MailReceivedMsg => ({ mail: decodeMailMessage(readMessages(f, 1)[0] ?? new Uint8Array()) }),
  },
  51: {
    name: "MailUpdate",
    decode: (f): MailUpdateMsg => ({ mail: decodeMailMessage(readMessages(f, 1)[0] ?? new Uint8Array()) }),
  },
  52: {
    name: "MailDeleted",
    decode: (f): MailDeletedMsg => ({ mailId: readString(f, 1) }),
  },
  72: {
    name: "AuctionListingsUpdate",
    decode: (f): AuctionListingsUpdateMsg => ({ listings: readMessages(f, 1).map(decodeAuctionListing) }),
  },
  73: {
    name: "ClaimSync",
    decode: (f): ClaimSyncMsg => ({ claims: readMessages(f, 1).map(decodeClaimInfo) }),
  },
  74: {
    name: "ClaimDenied",
    decode: (f): ClaimDeniedMsg => ({ reason: readString(f, 1) }),
  },
};

export interface DecodedHubMessage {
  name: string;
  payload: unknown;
}

/** Returns `null` for a ProtoId outside the hub's subset (movement, chunks, …) — safe to ignore. */
export function decodeHubMessage(bytes: Uint8Array): DecodedHubMessage | null {
  if (bytes.length === 0) return null;
  const id = bytes[0];
  const decoder = SERVER_DECODERS[id];
  if (!decoder) return null;
  const fields = readFields(bytes.subarray(1));
  return { name: decoder.name, payload: decoder.decode(fields) };
}
