# Auto-Tune Documentation

_Welcome! This folder contains everything you need to understand, configure, and extend Auto-Tune._

---

## I just want to run Auto-Tune on my server

→ Start with the **[Server Admin Guide](SERVER_ADMIN_GUIDE.md)**

It covers: installation, your first 30 minutes, how the market engine works, the configuration cookbook, monitoring your economy, and the most common issues.

**Reading order:** Server Admin Guide → Config Guide (for details)

---

## I want to understand how the engine works

→ Start with **[Economy Concepts](ECONOMY_CONCEPTS.md)** (plain-English explanation of GDP, buy ratio, spread, debt/GDP, volatility, and the loan system — no economics background needed)

→ Then **[Architecture Guide](ARCHITECTURE.md)** for the technical system overview

**Reading order:** Economy Concepts → Architecture → Server Admin Guide → Config Guide

---

## I want to tune my economy's parameters

→ **[Config Guide](CONFIG_GUIDE.md)**

Covers: every config key in `config.yml`, what each parameter does, how it affects the engine, and recommended ranges grounded in simulation evidence.

---

## I'm migrating from an old Auto-Tune version

→ **[Migration Guide](MIGRATION.md)**

Covers: what's changed in rewrite-2, what commands have moved, how to upgrade your database schema, and what's been removed.

---

## I'm contributing code to Auto-Tune

→ **[Contributing Guide](CONTRIBUTING.md)**

Covers: dev setup (Java 21, Rust, Node.js), how to build and test, code standards, and the PR checklist.

For architecture context, read the **[Architecture Guide](ARCHITECTURE.md)** first.

---

## I want to see what's changed recently

→ **[Changelog](CHANGELOG.md)**

Chronological record of every significant change to rewrite-2, grouped by date.

---

## I have a problem

→ **[FAQ](FAQ.md)**

Covers: pricing behavior, economy distress, loan issues, configuration, market events, dashboard problems, cross-server setup, performance, and troubleshooting.

---

## I want to integrate with the bundled dashboard API

→ **[Dashboard API Reference](DASHBOARD_API.md)**

Complete reference for the Java plugin's bundled REST + WebSocket API (port 8989). Covers items, economy, transactions, loans, portfolio, leaderboard, badges, market events, admin endpoints, alerts, and live prices via WebSocket.

---

## I want to integrate with the cross-server API

→ **[API Reference](API.md)**

Reference for the Rust API server (port 8080). Covers server registration, price submission, true-price querying, exchange rates, heartbeat, and rate limits.

---

## Quick reference: commands

| Command | Description |
|---------|-------------|
| `/shop` | Browse and buy items |
| `/sell` | Sell items from inventory |
| `/autosell` | Auto-sell config and toggle |
| `/auction` | Auction house GUI |
| `/loans` | Take, repay, and manage loans |
| `/transactions` | View your trade history |
| `/at admin …` | Admin commands (health, config, prices, items) |
| `/at event …` | Market event management |
| `/at digest …` | Economy digest report |
| `/treasury` | Server treasury management |

See the Server Admin Guide for the full commands reference table.

---

## Docs map

| File | What it covers | Audience |
|------|---------------|---------|
| `SERVER_ADMIN_GUIDE.md` | Installing, configuring, monitoring | Server admins |
| `CONFIG_GUIDE.md` | Every config parameter, recommended ranges | Server admins |
| `MIGRATION.md` | Upgrading from old Auto-Tune versions | Server admins |
| `ECONOMY_CONCEPTS.md` | Plain-English economics: GDP, buy ratio, spreads, debt/GDP, volatility, loans | Admins, developers |
| `ARCHITECTURE.md` | How the whole system works | Developers |
| `CHANGELOG.md` | What's changed, by date | Everyone |
| `DASHBOARD_API.md` | Bundled dashboard REST + WebSocket API | Developers |
| `API.md` | Cross-server Rust API | Developers |
| `FAQ.md` | Common admin questions with direct answers | Server admins |
| `README.md` | This file | Everyone |
