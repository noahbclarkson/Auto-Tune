# Economy Concepts

Auto-Tune is a dynamic supply-and-demand economy plugin. Unlike static shop plugins, prices change based on player behavior.

## Core Concepts

### GDP (Gross Domestic Product)
The total value of all trades (buys + sells) over a 24-hour period. High GDP means a healthy, active economy. Low GDP means players aren't trading.

### Debt/GDP Ratio
The total active loan debt divided by the GDP.
- **< 1.0x**: Healthy. Debt is easily serviced by the economy.
- **1.0x - 3.0x**: Normal. Players are taking loans to buy dips.
- **> 10.0x**: Danger. The economy is over-leveraged, usually because of sustained oversupply (everyone selling, no one buying).

### Buy Ratio
The percentage of trades that are buys vs sells.
- **50%**: Perfect equilibrium.
- **> 70%**: High demand. Prices are rising.
- **< 30%**: High supply (oversell). Prices are crashing.

### Volatility
How much prices swing day-to-day. High volatility means players can make quick profits on arbitrage, but it's risky for long-term investments. Low volatility means prices are stable, but the economy is stagnant.

### The Floor Paradox
Setting a hard price floor (e.g. Diamond minimum $300) protects sellers, but creates behavioral oversupply. When prices hit the floor, players keep selling because the price is artificially high. This drives the *internal* price lower, even though the *displayed* price stays at $300. 
- **The fix**: Use a moderate floor (60%) combined with MarketMakers and GuildBuyers.

### Spreads (BPD/SPD)
- **BPD (Buy Price Distance)**: How far the buy price is above the "true" price.
- **SPD (Sell Price Distance)**: How far the sell price is below the "true" price.
- Spreads widen when volume is low or there is a massive imbalance (e.g. 90% sells). Tight spreads encourage trading. Wide spreads penalize it.
