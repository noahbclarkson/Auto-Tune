# Economy Concepts

Auto-Tune is a dynamic supply-and-demand economy plugin. Unlike static shop plugins, prices change based on player behavior — no admin manually sets prices.

## Core Metrics

### GDP (Gross Domestic Product)

The total value of all trades (buys + sells) over a rolling 24-hour window. Think of it as the economy's pulse.

| GDP Range | Meaning |
|-----------|---------|
| < $50K/day | Stagnant. Players aren't trading. |
| $50K–$500K/day | Normal for a small-to-medium server. |
| $500K–$2M/day | Healthy, active server. |
| > $2M/day | Thriving economy with strong player engagement. |

GDP is not a zero-sum measure — a $500K GDP day means $500K of value was exchanged, not lost. High GDP with low D/G means players are trading actively without over-leveraging.

---

### Debt/GDP Ratio

Total unpaid loan debt (active + defaulted) divided by GDP. This is the most misunderstood metric.

> ⚠️ **Active vs. Total debt:** Debt/GDP uses ALL unpaid debt — active loans PLUS defaulted loans still outstanding. After a default, the loan stops accruing interest but remains in the total until repaid. This means D/G can appear "recovered" immediately after defaults resolve, even though the economy just went through a crisis. Always read D/G together with GDP trend and volatility.

| D/G Range | Meaning | Action |
|-----------|---------|--------|
| < 1.0x | Healthy. Debt is easily serviced. | None needed. |
| 1.0x – 3.0x | Normal. Players are borrowing to buy dips. | Monitor. |
| 3.0x – 10.0x | Elevated. Debt is accumulating. | Check loan settings. |
| 10.0x – 30.0x | Warning (TIER2). Interest is reducing. | Review archetype mix. |
| > 30.0x | Circuit breaker (TIER3). Interest paused. | Emergency: use `/at admin recovery` or adjust config. |

The circuit breaker (TIER3 at 30× D/G by default) fires when debt becomes unsustainable. At that point, interest drops to 0% and the economy is given space to deleverage; with the default 50% hysteresis band, it unlocks only after D/G falls below 15×. With `counter-cyclical=true` (default), interest gradually reduces as D/G rises — preventing most cascade scenarios before the circuit breaker is needed.

---

### Buy Ratio

Percentage of trades that are buys (vs. sells) over the trade window.

| Buy % | Market State | Price Direction |
|-------|-------------|-----------------|
| > 80% | Extreme demand | Prices rising fast |
| 70–80% | Strong demand | Prices trending up |
| 55–70% | Moderate demand | Gradual upward pressure |
| 45–55% | Balanced ✅ | Stable prices |
| 30–45% | Moderate supply | Gradual downward pressure |
| < 30% | Oversell ⚠️ | Prices trending down |

In a default Minecraft economy, farmers gather and sell continuously — buy ratios of 80–90% are common. This is why Auto-Tune recommends a 2MM + 2GB archetype mix: MarketMakers provide sell pressure, GuildBuyers absorb oversupply on dips. Without them, prices settle 50–70% below base prices.

---

### Volatility

How much prices swing on average per market tick (5-minute intervals), measured as the coefficient of variation of price change rates.

| Volatility | Rating | Interpretation |
|------------|--------|----------------|
| < 0.005 | Very Stable | Prices barely move. Good for long-term investment. |
| 0.005–0.015 | Stable ✅ | Healthy range for a well-configured economy. |
| 0.015–0.050 | Moderate | Normal for low-player or event-heavy economies. |
| > 0.050 | Unstable ⚠️ | Price swings are felt. Check archetype mix and loan settings. |

Volatility is the **most important** economy health metric — more important than D/G alone. A high-D/G economy with low volatility means debt is managed but concentrated. A low-D/G economy with high volatility means prices are chaotic even if players aren't over-leveraged.

---

### The Floor Paradox

Setting a hard price floor (e.g. Diamond minimum $300) seems like an obvious protection for sellers. In practice, it creates a behavioral feedback loop that makes internal prices *worse*:

1. Natural equilibrium settles at ~$200 (50% below $500 base).
2. With a $300 floor, displayed price stays at $300 ✅
3. But players keep gathering and selling — the floor *feels* profitable.
4. Oversupply drives the *internal* price down to $150 even though displayed price is frozen.
5. The economy becomes increasingly artificial, detached from real supply/demand signals.

**90-day simulation finding:** Floor is NOT a long-run health mechanism.
- **14-day:** 60% floor → GDP +29%, D/G 0.71x (benefit)
- **90-day:** 60% floor → GDP **-19.1%**, D/G **+1.6x worse** vs no floor
- Floor suppresses natural price correction → inventory glut → GDP contraction over time
- **Short-term servers (<30 days):** 50-60% floor is fine — seller protection benefit outweighs long-run cost
- **Long-running servers (>60 days):** Disable or use 30%. Above 70% destroys the economy regardless of horizon.

---

### Spreads (BPD/SPD)

Every item has two prices: a **buy price** (what you pay) and a **sell price** (what you receive). The gap between them is the spread.

- **BPD (Buy Price Distance):** How far the buy price sits above the "true" price, as a percentage. If true price is $100 and BPD is 2%, the buy price is $102.
- **SPD (Sell Price Distance):** How far the sell price sits below the true price. If SPD is 2%, the sell price is $98.

The spread is the market maker's fee — it's what makes two-sided liquidity possible.

| Spread Width | Meaning |
|-------------|---------|
| < 1% | Very tight. Deep liquidity. |
| 1–3% | Normal. Healthy market. |
| 3–7% | Moderate. Reduced liquidity. |
| > 7% | Wide. Thin market — few participants or extreme imbalance. |

Spreads widen automatically when:
- **Low volume:** Few trades → less price certainty → wider spreads
- **High imbalance:** 90% sells → spread widens to discourage further selling
- **Low player count:** Each trade has outsized price impact
- **Market events:** Events can temporarily widen spreads as prices adjust

Tight spreads benefit everyone — players keep more of their trade value, GDP is higher, and the economy feels more responsive. Wide spreads indicate the economy needs more MarketMakers or GuildBuyers to provide two-sided liquidity.

---

## What a Healthy Economy Looks Like

From 5-seed, 30-day simulation of 2MM + 2GB + 60% floor:

| Metric | Value | Interpretation |
|--------|-------|---------------|
| GDP | $1.5M+ (no floor) | Active, trading economy |
| D/G | 13x (no floor) / 14.6x (60% floor) | Floor worsens D/G over 90d |
| Buy % | 65–75% | Slightly buy-heavy, not oversold |
| BPD | 0.8–1.2% | Tight spreads |
| Volatility | < 0.015 | Stable prices |

The Floor Paradox is real: at 90 days, 60% floor → GDP -19.1% and D/G +1.6x worse vs no floor. Short-run (14d) shows GDP +29% — the benefit reverses after ~60 days. Use floors as a display guardrail for short-term servers; disable for long-running servers (>60 days).
