/**
 * DTOs for the hub's message subset — re-exported from the game client's own types
 * (`game/types.ts`) rather than redeclared here: they're already exactly the shapes
 * `MailSync`/`AuctionListingsUpdate`/`ClaimSync` carry (both sides read the same
 * `core/.../protocol/*.kt` classes), and the in-game components below are reused as-is against
 * them. Only the field-number tables in `hubCodec.ts` are hub-specific.
 */
export type {
  MailData as MailMessageDto,
  ClaimData as ClaimInfoDto,
  AuctionData as AuctionListingDto,
  AuctionFilter as AuctionFilterDto,
  AuctionBidData as AuctionBidDto,
  ChannelSubscription as ChannelSubscriptionDto,
  ItemMetaEntry,
} from "../../game/types";
import type { AuctionData } from "../../game/types";

export type AuctionDuration = AuctionData["duration"];
export type AuctionStatus = AuctionData["status"];

export const AUCTION_DURATIONS: AuctionDuration[] = ["H12", "H24", "H48", "H96"];
export const AUCTION_STATUSES: AuctionStatus[] = ["ACTIVE", "SOLD", "EXPIRED", "CANCELLED"];
