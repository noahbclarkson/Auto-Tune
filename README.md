# Auto-Tune

![GitHub Workflow Status](https://img.shields.io/github/workflow/status/noahbclarkson/Auto-Tune/Java%20CI%20with%20Maven)
![GitHub issues](https://img.shields.io/github/issues/noahbclarkson/Auto-Tune)
![GitHub pull requests](https://img.shields.io/github/issues-pr/noahbclarkson/Auto-Tune)
![GitHub release (latest by date)](https://img.shields.io/github/v/release/noahbclarkson/Auto-Tune)
[![Discord](https://img.shields.io/discord/748222485975269508.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/bNVVPe5)

> **Active development is on the `rewrite-2` branch.** The `main` branch contains the stable release.
> The rewrite-2 branch is a near-complete rebuild of Auto-Tune with a redesigned market engine,
> Guice DI, Javalin web server, bundled Next.js dashboard, enchantment pricing, loan circuit breakers,
> and a cross-server price solver. Not all features are complete — see PLAN.md for status.
<img src="https://github.com/noahbclarkson/Auto-Tune/blob/rewrite-2/.github/AtLogo.png?raw=true" width="100"/>

## :star: Overview

*Auto-Tune is a Minecraft plugin that allows you to create an automated economy for your server. Prices of items will be automatically adjusted based on supply and demand. When an item is purchased by many players but sold by few, Auto-Tune will raise the price to lower demand/increase supply and vice versa. Auto-Tune fixes a critical problem in Minecraft server economies and provides a better experience for players and servers. You can check out our feature set below.*

## :heavy_check_mark: Features

- :ballot_box_with_check: ```Advanced automatic pricing model based on supply and demand```
- :ballot_box_with_check: ```Configurable GUI shop, with positioning and naming options```
- :ballot_box_with_check: ```Easy selling panel to sell items quickly```
- :ballot_box_with_check: ```Automatic selling, configurable player side```
- :ballot_box_with_check: ```Fully supports all enchantments and items```
- :ballot_box_with_check: ```Stored detailed history of transactions```
- :ballot_box_with_check: ```Exploit protection with max-buys/sells, volatility settings, and more```
- :ballot_box_with_check: ```Limit player's to only be able to purchase items they have collected before```
- :ballot_box_with_check: ```Advanced loaning with interest settings```
- :ballot_box_with_check: ```Integrated web-server for viewing price information```
- :ballot_box_with_check: ```Calculates GDP, debt, inflation and more```
- :ballot_box_with_check: ```Incredibly fast data collection and creation with corruption protection```
- :ballot_box_with_check: ```All messages are configurable```
- :ballot_box_with_check: ```Tutorial to help new players```
- :ballot_box_with_check: ```And much, much more...```

## :chart_with_upwards_trend: Market Algorithm

Auto-Tune uses a supply-and-demand pricing model with asymmetric spreads, player scaling, and volume-based adjustments. Every configurable interval (default: 5 minutes / 6000 ticks), the **Market Engine** recalculates all item prices and spreads.

### Price Updates

1. **Trade window**: All transactions within a configurable window (default: 7 days) are collected. Each transaction is **recency-weighted** — recent trades count more and older trades fade linearly to zero:
   ```
   weight = max(0, 1.0 - age_ms / window_ms)
   weightedAmount = transaction_amount * weight
   ```

2. **Trade ratio**: Net buying vs selling pressure is computed:
   ```
   tradeRatio = (weightedBuys - weightedSells) / (weightedBuys + weightedSells)
   ```
   Range: `[-1.0, +1.0]`. Positive = buying pressure, negative = selling pressure.

3. **Player scaling**: Price changes are damped by the online player count using a `tanh` curve:
   ```
   playerScaling = tanh(onlineCount * atanh(0.99) / fullEffectPlayers)
   ```
   At the configured `fullEffectPlayers` (default: 10), scaling reaches ~99%. At 0 players, no price changes occur.

4. **Price change**: The final price change per tick is capped:
   ```
   priceChange = currentPrice * tradeRatio * playerScaling * (maxPriceChangePercent / 100)
   ```
   Default cap: **3% per tick**. There are no hard min/max price bounds — prices are purely market-driven.

### BPD / SPD Spread System

Buy and sell prices diverge from the base price through independent **Buy Price Deviation (BPD)** and **Sell Price Deviation (SPD)** values:

```
buyPrice  = basePrice * (1 + BPD)
sellPrice = basePrice * (1 - SPD)
```

The spread calculation applies four adjustments in sequence:

1. **Base spread** (default: 0.20 → split 0.10 / 0.10):
   ```
   bpd = baseSpread / 2
   spd = baseSpread / 2
   ```

2. **Volume imbalance** — shifts spread toward the dominant trade direction:
   ```
   imbalance = (buyRatio - 0.5) * 2.0
   bpd += max(0,  imbalance) * halfSpread * volumeImpact
   spd += max(0, -imbalance) * halfSpread * volumeImpact
   ```
   Heavy buying → BPD widens (buying gets more expensive). Heavy selling → SPD widens (selling becomes less profitable).

3. **Liquidity reduction** — frequently traded items get tighter spreads:
   ```
   liquidityReduction = 1.0 / (1.0 + totalWeightedVolume * liquidityCoeff)
   bpd *= liquidityReduction
   spd *= liquidityReduction
   ```

4. **Player count reduction** — more players → tighter spreads:
   ```
   playerReduction = 1.0 - playerImpact * playerScaling
   bpd *= playerReduction
   spd *= playerReduction
   ```

5. **Global volume multiplier** — a z-score analysis across 10 equal time buckets within the trade window:
   - `|z| <= 1`: multiplier = 1.0 (normal activity)
   - `z > 1` (high activity): multiplier drops toward 0.5 (tighter spreads)
   - `z < -1` (low activity): multiplier rises toward 2.0 (wider spreads)

### Price Trends

Based on the last 10 price history entries:
- **UP**: price increased > 0.5%
- **DOWN**: price decreased > 0.5%
- **STABLE**: change within ±0.5%

### Economy Metrics

Captured every 5 minutes as snapshots:

| Metric | Calculation |
|--------|-------------|
| **GDP** | Sum of all transaction totals in the last 24 hours |
| **Inflation** | Average price change across all items (> 1% = "High Inflation", < -1% = "Deflation") |
| **Total Debt** | Sum of all active loan balances |
| **Debt Per Capita** | Total debt / online player count |
| **Transaction Volume** | Total trade volume in the last 24 hours |

### Loan System

- Interest rate: `baseRate * (1 + (500 - creditScore) / 1000)` when credit score modifier is enabled
- Compounds every 24 hours (configurable)
- Max loan amount: player's total traded value × `maxLoanMultiplier` (default: 2.0)
- Defaulting deducts credit score points (default: 50)

### Default Configuration

| Parameter | Default | Description |
|-----------|---------|-------------|
| `update-interval` | 6000 ticks (5 min) | Price recalculation frequency |
| `max-price-change-percent` | 1.5% | Max base price change per tick |
| `trade-window-days` | 7 | Time window for trade analysis |
| `base-spread` | 0.20 (20%) | Total spread split between BPD/SPD |
| `spread.volume-impact` | 0.8 | Imbalance effect on spread |
| `spread.player-impact` | 0.6 | Player count effect on spread |
| `spread.liquidity-coeff` | 0.01 | High-volume spread reduction |
| `spread.liquidity-full-effect-traders` | 10 | Traders needed for full liquidity effect |
| `player-scaling.full-effect-players` | 10 | Player count for ~99% spread scaling |
| `loans.base-interest-rate` | 0.05 (5%) | Loan interest per compound |
| `loans.compound-interval-hours` | 24 | Hours between interest compounds |
| `loans.debt-gdp-circuit-breaker-ratio` | 10.0 | Pauses interest when debt exceeds GDP × this |

> The `scripts/market_curves.py` script generates visualizations of all these curves and relationships.

## :question: Why use Auto-Tune

Auto-Tune identifies and fixes a significant problem in Minecraft servers that has remained underdeveloped and ignored for too long. This issue is the poor implementation of an economy and markets into Minecraft.

Previous solutions that allow for trading between players have lacked flexibility, player engagement, and realism. These issues are due to server economy plugins that cannot adapt to the speed at which the economy in Minecraft changes. We designed Auto-Tune with this in mind. By automating the price-setting process, server admins can sit back and watch the prices fluctuate as the supply and demand of items bounce back and forth.

Not only does this assist administrators in managing a server's economy, but it also allows the server players to engage with the economy more rigorously. We have strenuously tested Auto-Tune to be fit for any environment and created systems designed to assist server admins in building the best economy possible for the specific needs of their server. Auto-Tune is a powerful and highly customizable plugin that has a feature set rich enough to satisfy any server. We at Auto-Tune are passionate and optimistic about Minecraft plugin development and building a community that loves Minecraft and economics!

## 🎀 Examples

### Auto-Tune Default Shop Setup

<img src="https://github.com/noahbclarkson/Auto-Tune/blob/rewrite-2/.github/Auto-Tune-Shop.gif?raw=true" width="500"/>

## :computer: Usage

### :clipboard: Server setup

1. Download the latest version of Auto-Tune from the [releases](https://github.com/noahbclarkson/Auto-Tune/releases) tab on Github. Development versions can be found under the [actions](https://github.com/noahbclarkson/Auto-Tune/actions) tab on Github (where each commit produces a build artifact which is the latest version of the plugin).
2. Please use [Paper](https://papermc.io/) or a fork of Paper as your server software.
3. Make sure the required dependencies are installed ([Vault](https://www.spigotmc.org/resources/vault.34315/) and an economy plugin such as [Essentials](https://essentialsx.net))
4. Put the ```.jar``` files in the ```/plugins``` folder of your server.
5. Start/restart the server.
6. Edit your configuration settings in ```config.yml```, ```shops.yml``` and ```messages.yml```.
7. Restart the server again and Auto-Tune will be running with all your settings configured.

### :hammer: Building from source

> **Note:** This project targets **Java 21**. The build requires a full JDK (not JRE).

1. Clone the project (use `rewrite-2` branch for latest development):
   ```bash
   git clone -b rewrite-2 https://github.com/noahbclarkson/Auto-Tune.git
   cd Auto-Tune
   ```
2. Install a Java 21 JDK (e.g. [Eclipse Temurin](https://adoptium.net/) or your system package manager).
3. Build:
   ```bash
   ./gradlew build   # Java plugin (outputs to build/libs/)
   ```
   The plugin JAR bundles the web dashboard automatically (Next.js static export → shadow JAR).
4. For Rust components (API server, price solver, market simulation):
   ```bash
   cargo build --release    # from the repo root
   ```

### :globe_with_meridians: Web Dashboard & Public Site

The plugin bundles a **Next.js dashboard** (`web/`) served by the built-in Javalin web server at `http://your-server:8989`. It shows live prices, trends, GDP, loans, and more with WebSocket updates.

For server admins, the **public site** (`public-site/`) is a standalone frontend with:
- **True Prices** - cross-server price discovery via least-squares optimization
- **Exchange Rates** - per-server deviation from the global baseline
- **Interactive Simulator** - experiment with market parameters before changing config
- **Server Registry** - inspect registered servers and their submission status

Deploy the public site separately; see `public-site/README.md`.

### :sparkles: Contributing to the project

Feel free to create a fork of the repository and open a pull request to contribute. If you have any serious issues please report them on the issues tab. For other problems please use the discord below. Please respect the license.

## :bell: Join the community

> [![Discord](https://img.shields.io/discord/748222485975269508.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/bNVVPe5)
>
> Report bugs via [GitHub Issues](https://github.com/noahbclarkson/Auto-Tune/issues).
