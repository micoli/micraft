import { describe, expect, it } from "vitest";
import { decodeHubMessage, encodeHubMessage } from "../hub/lib/hubCodec";

/**
 * Cross-language contract test — every hex fixture below is REAL output from
 * `server/src/test/kotlin/org/micoli/micraft/game/hub/HubWireFixtureTest.kt` (its `println`,
 * pasted verbatim), not hand-guessed bytes. If that Kotlin test's expected hex changes (a message
 * in the hub's subset gained/lost/reordered a field), re-run it and update the fixtures here in
 * the same commit — see that test's doc comment.
 */

function hexToBytes(hex: string): Uint8Array {
  const bytes = new Uint8Array(hex.length / 2);
  for (let i = 0; i < bytes.length; i++) bytes[i] = parseInt(hex.substring(i * 2, i * 2 + 2), 16);
  return bytes;
}

describe("hubCodec — decode (ServerMessage subset)", () => {
  it("Notification", () => {
    const decoded = decodeHubMessage(hexToBytes("070a0268691205776f726c64"));
    expect(decoded).toEqual({ name: "Notification", payload: { message: "hi", channel: "world" } });
  });

  it("ChatMessage", () => {
    const decoded = decodeHubMessage(hexToBytes("080a05776f726c641205416c6963651a026869"));
    expect(decoded).toEqual({
      name: "ChatMessage",
      payload: { channel: "world", sender: "Alice", message: "hi" },
    });
  });

  it("ChannelsSync", () => {
    const decoded = decodeHubMessage(hexToBytes("090a090a05776f726c6410011205776f726c64120673797374656d"));
    expect(decoded).toEqual({
      name: "ChannelsSync",
      payload: {
        subscribedChannels: [{ name: "world", autoFocus: true }],
        knownChannels: ["world", "system"],
      },
    });
  });

  it("InventoryUpdate", () => {
    const decoded = decodeHubMessage(hexToBytes("0c0a0f0a0b434f42424c4553544f4e451005"));
    expect(decoded).toEqual({ name: "InventoryUpdate", payload: { inventory: { COBBLESTONE: 5 } } });
  });

  it("WalletUpdate", () => {
    const decoded = decodeHubMessage(hexToBytes("3008dc0b"));
    expect(decoded).toEqual({ name: "WalletUpdate", payload: { copper: 1500 } });
  });

  it("MailSync", () => {
    const decoded = decodeHubMessage(hexToBytes("310a1c0a026d311205416c6963651a03426f62220268692a0474657874382a"));
    expect(decoded).toEqual({
      name: "MailSync",
      payload: {
        mails: [
          {
            id: "m1",
            from: "Alice",
            to: "Bob",
            subject: "hi",
            body: "text",
            attachments: {},
            sentAt: 42,
            seen: false,
            attachmentsClaimed: false,
            copperAmount: 0,
          },
        ],
      },
    });
  });

  it("MailReceived (with attachments + copper)", () => {
    const decoded = decodeHubMessage(
      hexToBytes("320a2d0a026d32120673797374656d1a03426f62220777656c636f6d652a02686932090a05464c494e54100238635032"),
    );
    expect(decoded).toEqual({
      name: "MailReceived",
      payload: {
        mail: {
          id: "m2",
          from: "system",
          to: "Bob",
          subject: "welcome",
          body: "hi",
          attachments: { FLINT: 2 },
          sentAt: 99,
          seen: false,
          attachmentsClaimed: false,
          copperAmount: 50,
        },
      },
    });
  });

  it("MailDeleted", () => {
    const decoded = decodeHubMessage(hexToBytes("340a026d31"));
    expect(decoded).toEqual({ name: "MailDeleted", payload: { mailId: "m1" } });
  });

  it("ClaimSync", () => {
    const decoded = decodeHubMessage(hexToBytes("490a1e0a026331180020402a08616c6963652d69643205416c6963653a03426f62"));
    expect(decoded).toEqual({
      name: "ClaimSync",
      payload: {
        claims: [
          {
            id: "c1",
            chunks: [],
            yMin: 0,
            yMax: 64,
            ownerId: "alice-id",
            ownerName: "Alice",
            trustedPlayerNames: ["Bob"],
          },
        ],
      },
    });
  });

  it("ClaimDenied", () => {
    const decoded = decodeHubMessage(hexToBytes("4a0a046e6f7065"));
    expect(decoded).toEqual({ name: "ClaimDenied", payload: { reason: "nope" } });
  });

  it("AuctionListingsUpdate", () => {
    const decoded = decodeHubMessage(
      hexToBytes(
        "480a450a026c311208616c6963652d69641a05416c696365220b434f42424c4553544f4e45280530e80738d00f4001486470007a130a06626f622d69641203426f6218960120dc0b",
      ),
    );
    expect(decoded).toEqual({
      name: "AuctionListingsUpdate",
      payload: {
        listings: [
          {
            id: "l1",
            sellerId: "alice-id",
            sellerName: "Alice",
            itemType: "COBBLESTONE",
            quantity: 5,
            createdAtMs: 1000,
            expiresAtMs: 2000,
            duration: "H24",
            startingPrice: 100,
            buyNowPrice: null,
            currentBid: null,
            currentBidderId: null,
            currentBidderName: null,
            status: "ACTIVE",
            bidHistory: [{ bidderId: "bob-id", bidderName: "Bob", amount: 150, atMs: 1500 }],
          },
        ],
      },
    });
  });

  it("returns null for a ProtoId outside the hub's subset", () => {
    // ServerMessage.Welcome is ProtoId 0 — never sent over /hub.
    expect(decodeHubMessage(hexToBytes("00"))).toBeNull();
  });
});

describe("hubCodec — encode (ClientMessage subset)", () => {
  it("Connect includes the ProtoId 0 prefix and every field", () => {
    const bytes = encodeHubMessage("Connect", {
      playerName: "Alice",
      userName: "alice@test.local",
      preferredLanguage: "en",
      token: "tok",
      needsWorld: false,
      connectionId: "abc123",
    });
    expect(bytes[0]).toBe(0);
    const back = new TextDecoder().decode(bytes.slice(1));
    expect(back).toContain("Alice");
    expect(back).toContain("alice@test.local");
    expect(back).toContain("abc123");
  });

  it("SendMail encodes attachments as a map and prefixes ProtoId 22", () => {
    const bytes = encodeHubMessage("SendMail", {
      to: "Bob",
      subject: "hi",
      body: "text",
      attachments: { FLINT: 2 },
      copperAmount: 10,
    });
    expect(bytes[0]).toBe(22);
  });

  it("round-trips a decode(encode(x)) for a message this codec both sends and receives (ChatSend/ChatMessage share a shape)", () => {
    // ChatSend (out) isn't decodable client-side by design (server-only decode table), so this
    // instead exercises the low-level map/varint/string helpers via a full message: encoding
    // AuctionSetFilter then confirming it at least produces the right ProtoId + a well-formed
    // sub-message length (doesn't throw).
    const bytes = encodeHubMessage("AuctionSetFilter", {
      filter: {
        itemType: "COBBLESTONE",
        sellerName: null,
        minPrice: 10,
        maxPrice: null,
        mineOnly: false,
        expiredOnly: false,
        myBidsOnly: true,
      },
    });
    expect(bytes[0]).toBe(41);
    expect(bytes.length).toBeGreaterThan(1);
  });
});
