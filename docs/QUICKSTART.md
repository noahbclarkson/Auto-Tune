# Admin Quickstart: The 5 Decisions Before You Launch

Welcome to Auto-Tune. Before putting the economy live, review these 5 configuration decisions. They are backed by thousands of simulation runs.

### 1. Archetype Mix: 2 MarketMakers + 2 GuildBuyers
The recommended mix prevents supply gluts while keeping spreads tight. MarketMakers anchor prices and provide liquidity. GuildBuyers buy on dips to prevent runaway price crashes.
- In `config.yml`, ensure you have a healthy mix of players who buy *and* sell.

### 2. Loans: Enabled (Default: ON)
Loans are required for a functioning economy. Without credit, large GuildBuyer purchases fail, and prices crash.
- **Circuit Breaker:** Enabled automatically. If Debt/GDP gets too high, interest tapers off to help the economy recover.
- **Default:** Players who default on loans are blocked from new loans for 7 days (`post-default-cooldown-hours: 168`).

### 3. Price Floor: 60% of Base ($300 Diamond)
A hard price floor prevents new players from joining an economy where mining is worthless.
- In `config.yml`, set the floor percent to `0.60`. This protects sellers during oversupply without destroying trade volume.

### 4. Market Events: Enabled
Events (e.g., Diamond Rush, Gold Glut) temporarily alter the pricing formula for specific items, making the economy feel alive.
- Admins can trigger these manually with `/at event invoke <name>`.

### 5. Economy Digest: Weekly Discord Report
Configure a Discord webhook to receive a weekly PDF summary of the economy (GDP, Debt, Volatility, Top Movers).
- In `config.yml`, under `market-digest`, set your `webhook-url`.

---
Once configured, run `/at admin reload` and verify with `/at admin health`.
