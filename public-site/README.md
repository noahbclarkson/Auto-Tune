# Auto-Tune Public Site

Standalone Next.js site for server admins evaluating, installing, configuring, and testing Auto-Tune.

This app is the public front door for the project. It is separate from:

- `web/`: the dashboard bundled into the Paper plugin JAR and served by Javalin.
- `api-server/`: the Rust price network API for server registration, true prices, and exchange rates.
- `scripts/market-simulation/`: the Rust simulation lab used to validate market behavior.

## Key Routes

| Route | Purpose |
| --- | --- |
| `/` | Public overview and project structure |
| `/install` | Installation guide for Paper server admins |
| `/setup` | Guided config generator |
| `/simulator` | Browser market simulator backed by `src/lib/market-engine.ts` |
| `/config-preview` | Compare config changes before applying them |
| `/docs` | Index of repo documentation |
| `/true-prices` | Live true-price data from the Rust API, when configured |
| `/exchange-rates` | Server price multipliers from the Rust API |
| `/servers` | Registered server list and registration form |
| `/api-docs` | Cross-server API reference |
| `/health-badge` | Static HTML badge generator |

## Local Development

```bash
cd public-site
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000).

For live network pages, run the Rust API server and set:

```bash
NEXT_PUBLIC_API_URL=http://localhost:8080
```

Without that API, the network pages show explicit offline or empty states.

## Build

```bash
npm run build
```

The site uses `output: 'export'`, so pages must be static-export friendly. Live API data should be fetched from client components after hydration, not from server components at build time.

## Engine Sync

`src/lib/market-engine.ts` mirrors the Java and Rust market engines. Keep these implementations in sync:

- Java: `src/main/java/com/noahblclarkson/autotune/manager/MarketEngine.java`
- Rust: `scripts/market-simulation/src/engine.rs`
- TypeScript: `public-site/src/lib/market-engine.ts`
