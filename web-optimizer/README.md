# Auto-Tune Price Optimizer — Web UI

Interactive web tool for exploring and simulating the **Auto-Tune** dynamic pricing algorithm used by the Auto-Tune Minecraft Paper plugin.

**Live site:** _(deploy to Vercel — see below)_

---

## What Is This?

Auto-Tune adjusts buy/sell prices dynamically based on real server activity:

- **Player count** — more players → tighter spreads (better prices for everyone)
- **Buy/sell ratio** — high demand pushes buy prices up; excess supply widens sell spreads
- **Market volume (z-score)** — unusually high activity tightens spreads; quiet markets widen them
- **Liquidity** — heavily-traded items get more favourable pricing

This web app lets you experiment with all those parameters in real-time via an interactive simulator, and explains the maths behind the algorithm.

---

## Pages

| Route | Description |
|-------|-------------|
| `/` | Landing page — overview, social proof, feature highlights |
| `/how-it-works` | Deep-dive explanation of the pricing algorithm |
| `/simulator` | Interactive simulator with sliders, charts, market events & stability forecast |
| `/sweep-results` | 840-config parameter sweep results — filter, sort & find optimal settings |
| `/simulation-results` | Cross-run simulation analysis from the Rust market simulator |
| `/true-prices` | Cross-server true-price discovery from ratio matrices |
| `/exchange-rates` | Per-server economy multipliers vs. global baseline |
| `/servers` | Registered servers & their submission status |
| `/roadmap` | What's coming next — organized by category |
| `/api-docs` | API reference for the Rust API server |

---

## Running Locally

### Prerequisites

- Node.js 18+ (LTS recommended)
- npm or yarn

### Steps

```bash
# 1. Clone the monorepo
git clone https://github.com/noahbclarkson/Auto-Tune.git
cd Auto-Tune/web-optimizer

# 2. Install dependencies
npm install

# 3. Start the dev server
npm run dev
```

The app will be available at [http://localhost:3000](http://localhost:3000).

### Other Commands

```bash
npm run build   # Production build (also type-checks)
npm run lint    # ESLint
npm start       # Start the built production server
```

---

## Deploying to Vercel

The easiest zero-config deployment:

### Option A — Vercel CLI

```bash
# Install Vercel CLI globally (once)
npm i -g vercel

# Inside web-optimizer/
vercel
```

Follow the prompts. On first deploy, Vercel auto-detects Next.js and sets the correct build settings.

### Option B — Vercel Dashboard

1. Go to [vercel.com](https://vercel.com) and create a new project.
2. Import the `noahbclarkson/Auto-Tune` GitHub repo.
3. Set the **Root Directory** to `web-optimizer`.
4. Leave everything else as default — Vercel auto-configures Next.js.
5. Click **Deploy**.

> **Root Directory:** Because this is a monorepo, make sure to configure `web-optimizer` as the root directory in your Vercel project settings. Vercel will then run `npm run build` from that folder automatically.

---

## Tech Stack

| Layer | Choice |
|-------|--------|
| Framework | [Next.js 14](https://nextjs.org) (App Router) |
| Styling | [Tailwind CSS](https://tailwindcss.com) |
| Charts | [Recharts](https://recharts.org) |
| Icons | [Lucide React](https://lucide.dev) |
| Language | TypeScript |

---

## Project Structure

```
web-optimizer/
├── src/
│   ├── app/
│   │   ├── page.tsx              # Landing page
│   │   ├── how-it-works/
│   │   │   └── page.tsx          # Algorithm explanation
│   │   ├── simulator/
│   │   │   └── page.tsx          # Interactive simulator
│   │   ├── true-prices/
│   │   │   └── page.tsx          # True-price table + local calculator
│   │   ├── exchange-rates/
│   │   │   └── page.tsx          # Server rate table + bar chart
│   │   ├── servers/
│   │   │   └── page.tsx          # Server registry + registration
│   │   ├── simulation-results/
│   │   │   └── page.tsx          # Simulation result analyzer
│   │   ├── sweep-results/
│   │   │   └── page.tsx          # Parameter sweep result viewer
│   │   ├── roadmap/
│   │   │   └── page.tsx          # Roadmap by category
│   │   └── api-docs/
│   │       └── page.tsx          # API reference
│   ├── components/
│   │   ├── landing/              # Hero, feature cards, changelog, social proof, theme toggle
│   │   ├── layout/               # Header, Footer
│   │   ├── simulator/            # ParameterPanel, PricePreview, SpreadChart, StabilityForecast, MarketEventsPanel, ProjectionChart
│   │   ├── prices/              # TruePricesLive, PriceCalculator, ExchangeRateChart
│   │   └── servers/             # ServerCard, RegisterServerModal
│   └── lib/
│       ├── market-engine.ts      # TS port of the pricing algorithm
│       ├── api-client.ts         # API fetch helpers
│       └── utils.ts              # Helpers
├── public/
├── tailwind.config.ts
├── next.config.ts
└── package.json
```

---

## Contributing

This UI lives in the `web-optimizer/` subdirectory of the [Auto-Tune monorepo](https://github.com/noahbclarkson/Auto-Tune). Open a PR against `rewrite-2` (the active development branch) with changes scoped to this folder.
