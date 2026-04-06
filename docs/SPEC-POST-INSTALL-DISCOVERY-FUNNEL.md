# SPEC: Post-Install Discovery Funnel — web/

**Status:** Draft | **Priority:** TIER 1 (high retention impact)  
**Type:** Dismissible onboarding overlay on `/shop` and `/portfolio`  
**Location:** `web/src/components/onboarding/discovery-overlay.tsx` + integrated into shop/portfolio pages  

---

## Why This Feature

New players who install Auto-Tune and open `/shop` don't know what makes it special. They see prices and a buy button — they don't know about `/compare`, `/loans`, `/portfolio`, `/auction`, or that prices *move*. The Discovery Funnel shows them 3 contextual tips on first visit, increasing feature engagement and retention.

**Hypothesis:** Players who see 1+ discovery tips are more likely to use `/compare` and `/portfolio`, which increases their economic engagement and makes them less likely to churn.

---

## Design

### When it shows
- First visit to `/shop` (localStorage key: `autotune_discovery_shop`)
- First visit to `/portfolio` (localStorage key: `autotune_discovery_portfolio`)
- Only shown once per route (persisted in localStorage)

### Visual treatment
- Floating card (absolute positioned, top-right of content area)
- Backdrop blur behind the card
- Max-width: 320px
- Emerald border + glow effect
- "New to Auto-Tune?" header with dismiss X button
- 3 tip items with icons
- Auto-dismiss after 8 seconds OR on any user interaction with the page

### Shop page tips
```
💡 Tip 1: "Prices change based on supply and demand — check /compare to see how your item's price compares to yesterday."
💡 Tip 2: "Taking a loan can help you buy in bulk when prices are low. Try /loans."
💡 Tip 3: "Shift-click any item in /shop to see its full price history and trend."
```
(Show only 1 random tip per visit, cycling through 3)

### Portfolio page tips
```
💡 Tip 1: "Your portfolio tracks your realized profits and losses from every trade."
💡 Tip 2: "Watch the Trading Timeline to see your buy/sell pattern over time."
💡 Tip 3: "The P&L chart shows if you're a net buyer or net seller — adjust your strategy!"
```

---

## Component Interface

```typescript
interface DiscoveryOverlayProps {
  /** 'shop' | 'portfolio' */
  page: 'shop' | 'portfolio';
  children: React.ReactNode;
}
```

Usage:
```tsx
<DiscoveryOverlay page="shop">
  <ShopContent />
</DiscoveryOverlay>
```

The overlay renders absolutely positioned over `children`, not wrapping them (preserves layout).

---

## LocalStorage Keys

- `autotune_discovery_shop_seen`: timestamp when shop overlay was dismissed
- `autotune_discovery_portfolio_seen`: timestamp when portfolio overlay was dismissed

Check: `localStorage.getItem(key)` !== null → don't show.

---

## Animation

- **Enter:** Fade in (opacity 0→1) + slide down (translateY -8px → 0), 300ms ease-out
- **Exit:** Fade out (opacity 1→0), 200ms ease-in
- **Timer:** 8s auto-dismiss uses CSS animation `animation: pulse 2s ease-in-out 4` (total 8s)

---

## Mobile

- On mobile, show at bottom of screen (fixed bottom bar style)
- Max-width: 100% with padding
- Tip text smaller (text-xs)

---

## Implementation Notes

- `'use client'` component
- Uses `useEffect` + `useState` for visibility + localStorage check
- `setTimeout` for 8s auto-dismiss
- Tips array randomized on mount (random sort once per session)
- Does NOT block page interaction (pointer-events: none on overlay container, auto on card)
- Uses existing Lucide icons from the project
- No new dependencies

---

## Integration Points

1. `web/src/app/shop/page.tsx` — wrap main content in `<DiscoveryOverlay page="shop">`
2. `web/src/app/portfolio/page.tsx` — wrap main content in `<DiscoveryOverlay page="portfolio">`
3. Footer component already shows "New to Auto-Tune?" in some pages — this replaces that with a proper overlay

## Not in Scope

- Tip customization by admin (static tips only for v1)
- Re-showing tips on update (localStorage-based, permanent dismiss)
- Multi-language i18n (English only for v1)
