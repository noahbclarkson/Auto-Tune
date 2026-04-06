# Auction House — DEPRECATED

> **This document describes the old auction house system that existed in the Rust API server.**
> As of rewrite-2 (2026-03-25), the auction house has been **fully migrated to the Java plugin**.
> The Rust API server no longer has any auction functionality.

## What Changed

- **Old**: Cross-server auction via `POST /api/auction/...` endpoints on the Rust API server
- **New**: In-game auction GUI via `/auction` command in the Java plugin

All auction commands are now in-game only:
- `/auction browse` — browse active orders
- `/auction sell <price> <qty>` — place a sell order
- `/auction buy <item> <price> <qty>` — place a buy order
- `/auction my` — view your orders
- `/auction cancel <order-id>` — cancel an order
- `/auction history` — view your fill history

Auction GUI opens automatically when running `/auction`.

## Why It Moved

Auctions are an in-game experience, not a cross-server concern. Moving them to the Java plugin:
- Works offline (no external server dependency)
- Uses the server's existing economy (Vault)
- Leverages the plugin's existing transaction and inventory systems
- No cross-server coordination needed

## API Server Status

The Rust API server's auction routes (`/api/auction/...`) return `410 Gone` with a message pointing to `/auction` in-game.

The following stale files remain in the API server for reference only:
- `src/routes/auction.rs` — returns 410 Gone
- `migrations/0004_auction_house.sql` — deprecated schema, not used

These will be removed in a future cleanup.
