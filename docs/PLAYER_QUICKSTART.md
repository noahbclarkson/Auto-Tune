# Player Quickstart: How Auto-Tune Works

Welcome to an Auto-Tune economy. This guide explains how the market works and how to use it.

---

## What is Auto-Tune?

Auto-Tune is a **dynamic economy** — item prices change based on what everyone is buying and selling. Unlike a traditional server shop with fixed prices, prices here go up when lots of people are buying, and go down when lots of people are selling. **Prices always work. You can always buy and always sell.**

---

## The 5 Commands You Need

### `/shop` — Browse and buy items

Opens the item shop. Use the search bar to find items. Each item shows:
- **Buy price** — what you pay (slightly above market)
- **Sell price** — what you receive (slightly below market)
- **Trend arrow** — green ↑ if prices are rising, red ↓ if falling

Click an item to buy a stack.

> **Tip:** Prices in `/shop` update every 5 minutes. The trend arrow tells you which direction prices are heading.

---

### `/sell` — Sell items for market price

Sell any item in your inventory at the current market sell price. Items sell instantly — no waiting for a buyer.

> **Tip:** If you think prices are going to drop, sell now. If you think prices will rise, hold your items.

---

### `/compare` — Check if an item is cheap or expensive

Compare any two items to see their current prices vs. 7-day averages. Use this to find **underpriced items** (buy opportunity) or **overpriced items** (sell opportunity).

```
/compare DIAMOND IRON_INGOT
```

> **Tip:** If Diamond is 20% above its 7-day average while Iron is 10% below, Iron is the better buy right now.

---

### `/loans` — Borrow money to make big purchases

If you don't have enough coins for a large purchase, take a loan. Loans let you buy now and pay back later.

- Interest accrues daily (varies by economy settings)
- If Debt/GDP gets too high, interest pauses automatically (circuit breaker)
- Default on a loan = 7-day loan ban

> **Tip:** Loans work best for buying items that will hold or increase in value. Don't borrow to finance consumables you'll burn through.

---

### `/transactions` — Your trading history

See every buy and sell you've made, with timestamps and prices. Good for tracking your trading performance and figuring out which items are profitable.

---

## How Prices Work

### Buy Price vs. Sell Price

Every item has two prices:
- **Buy price** is slightly above the "true" market price
- **Sell price** is slightly below the true market price
- The gap between them is called the **spread** — it covers the market's operating costs

The spread is why you can't buy and immediately sell an item for the same price. That's normal — it means the market is working.

### Why Do Prices Change?

Prices move when there's sustained buying or selling pressure over time:

| Pressure | Effect | Timeframe |
|----------|--------|-----------|
| Lots of players buying one item | Price rises | Minutes to hours |
| Lots of players selling one item | Price falls | Minutes to hours |
| Market-wide demand surge | All prices rise | Hours to days |
| Market-wide oversupply | All prices fall | Hours to days |
| Seasonal/event | Targeted items rise/fall | During event |

**Prices update every 5 minutes.** You won't see prices jump 50% in one tick — changes are gradual and continuous.

### Price Trends

The trend indicator (↑↓→) shows the direction of the last price change, not a prediction. An item could be rising and then crash if sellers overwhelm buyers. Use `/compare` to see the 7-day average and judge for yourself.

---

## How to Make Money

### Strategy 1: Gather and Sell

The classic. Mine diamonds, farm crops, chop wood — then sell at `/sell`. Watch the sell price: if it's dropped significantly, consider holding until prices recover.

### Strategy 2: Buy Low, Sell High

Use `/compare` to find items trading below their 7-day average. Buy them, wait for prices to normalize, then sell. Requires patience and market awareness.

### Strategy 3: Arbitrage Between Items

Some item pairs are always correlated (e.g., Diamond Pickaxes and Diamonds). Watch for temporary dislocations and trade the spread.

### Strategy 4: Take Calculated Loans

If you see an investment opportunity — an item you know will rise — a short-term loan can amplify your buying power. Pay it back quickly before interest accrues.

---

## What Affects the Whole Economy

**Market Events** — Admins can trigger economy-wide events (Diamond Rush, Gold Glut, Inflation Boost). These temporarily accelerate or suppress price changes for specific items. Watch for boss bar announcements.

**The Circuit Breaker** — If total debt gets too high relative to the economy's size, interest pauses automatically. This prevents catastrophic collapse but means loans are temporarily free.

**Seasonal Patterns** — Server economies can develop rhythms. A PvP-focused server might see sword prices spike on war days. A building server might see stone prices drop during building contests.

---

## Common Mistakes to Avoid

**Panic-selling during a dip.** Prices recover. Panic-selling locks in losses.

**Taking loans you can't repay.** Interest compounds. A $10,000 loan at 7% annual interest grows fast. Only borrow what you can realistically repay.

**Ignoring the trend arrow.** If an item has been falling for days, there's usually a reason. Check `/compare` before buying.

**Selling everything at once.** Large sell orders can move the market against you. Split large sells across multiple transactions if you have thousands of items.

---

## Need Help?

- Ask an admin: `/at help` lists all commands
- Check `/at events` to see active market events
- Use `/transactions` to review your trading history
- For economy details: `/at admin health` (admins only)
