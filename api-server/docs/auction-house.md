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

## API Server Cleanup (2026-04-25)

The auction house migration and documentation have been cleaned up:
- `migrations/0004_auction_house.sql` — **removed** (never applied by any live server, dead code)
- `src/routes/auction.rs` — already absent from codebase (auction routes were removed at migration time)
- `docs/auction-house.md` — this file, retained for historical reference

The API server now handles only price submission and true-price discovery. No auction functionality remains.