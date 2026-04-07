# Auto-Tune Documentation

Welcome to the Auto-Tune documentation. Auto-Tune is a Minecraft supply-and-demand economy plugin where prices automatically adjust based on player trading behavior.

## For Server Admins (Start Here)

- **[Quickstart](QUICKSTART.md)** — The 5 decisions to make before launching. Includes a visual decision guide.
- **[Server Admin Guide](SERVER_ADMIN_GUIDE.md)** — Full manual for running an Auto-Tune economy.
- **[Economy Concepts](ECONOMY_CONCEPTS.md)** — How the market engine actually works (GDP, Debt, Volatility, Spreads, Floor Paradox).
- **[Config Guide](CONFIG_GUIDE.md)** — Detailed parameter documentation and tuning cookbook.
- **[FAQ](FAQ.md)** — 30+ common admin questions answered directly.
- **[Migration](MIGRATION.md)** — Upgrading from older versions.

## For Players

- **[Player Quickstart](PLAYER_QUICKSTART.md)** — How to use /shop, /sell, /loans, /compare. Explains how prices work and how to make money in the economy.

## Developer & API Guides

- **[Architecture](ARCHITECTURE.md)** — Plugin internals and market engine design.
- **[Dashboard API](DASHBOARD_API.md)** — Local Java plugin API endpoints (`:8989`).
- **[Cross-Server API](API.md)** — Rust API server documentation (cross-server price discovery).
- **[OpenAPI Spec](openapi.yaml)** — OpenAPI 3.1 specification for the API server.
- **[Contributing](CONTRIBUTING.md)** — How to build and test.

## Releases

- **[Changelog](CHANGELOG.md)** — Version history.

## Reading Order by Role

**New admin:** Quickstart → Player Quickstart (to understand your players' view) → Server Admin Guide → Economy Concepts

**Experienced admin:** Server Admin Guide → Config Guide → FAQ

**Developer:** Architecture → Contributing → Dashboard API / Cross-Server API

**Player:** Player Quickstart
