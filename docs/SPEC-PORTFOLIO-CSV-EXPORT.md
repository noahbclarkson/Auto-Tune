# SPEC: Portfolio CSV Export — web/

**Status:** Draft | **Priority:** TIER 1  
**Type:** Button on `/portfolio` page → browser download  
**Location:** `web/src/components/portfolio/export-button.tsx` + `web/src/app/portfolio/page.tsx`  

---

## Why This Feature

Players want to own their data. A CSV export of their trading history is a high-retention feature: it makes Auto-Tune feel like a real trading platform, not just a Minecraft plugin. Traders can import it into Excel/Sheets for personal analysis.

---

## Implementation

### New API Endpoint (Java Plugin)
```
GET /api/portfolio/{playerName}/transactions.csv
```
Returns: `text/csv` with headers:
```
date,type,material,quantity,pricePerUnit,totalValue,balanceAfter
2026-04-03T14:22:01Z,BUY,DIAMOND,12,245.90,2950.80,7049.20
2026-04-03T15:01:33Z,SELL,IRON_INGOT,64,8.50,544.00,7593.20
```

- Query params: `?from=YYYY-MM-DD&to=YYYY-MM-DD&limit=1000`
- Requires the requesting player to match `{playerName}` OR have admin permission
- `balanceAfter` comes from the transaction record's `balance_after` field

### Frontend Button
Location: Top of `/portfolio` page, next to the page title.

```
[ 📋 Export History (CSV) ]
```

Button style: secondary/outline variant, Lucide `Download` icon.

On click: fires `<a href="/api/portfolio/{playerName}/transactions.csv">` click → browser handles download.

### Date Range Selector (optional enhancement)
```
[ Last 7 days ▾ ] [ 📋 Export ]
```
Dropdown: Last 7 days | Last 30 days | Last 90 days | All time

Appends `?from=...&to=...` to the CSV download URL.

---

## Java Endpoint

```java
@Path("/api/portfolio/{playerName}/transactions.csv")
@GET
public void exportTransactions(...) {
    // Validate player name matches requester or admin
    // Query at_transactions WHERE player_name = :playerName AND date BETWEEN :from AND :to
    // ORDER BY date DESC
    // Build CSV string
    // Return Response.ok(csvBytes).type("text/csv")
    //   .header("Content-Disposition", "attachment; filename=\"autotune-trades-{playerName}.csv\"")
}
```

Uses existing `TransactionRepository` — no new DB schema needed.

---

## Security

- Players can only export their own transaction history (or admins can export any)
- No PII beyond player name (already visible in-game)
- Rate limit: 1 export per minute per player (prevent abuse)
- Max rows: 10,000 (pagination not needed for personal history)

---

## Not in Scope

- PDF export (CSV only for v1)
- Scheduled email delivery (players can cron their own exports)
- Cross-player aggregate export for admins (different endpoint, different security model)
