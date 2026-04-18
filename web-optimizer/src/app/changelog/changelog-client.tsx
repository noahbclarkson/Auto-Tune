'use client';

import { useState } from 'react';
import Link from 'next/link';
import {
  ArrowLeft,
  ChevronDown,
  ChevronUp,
  GitCommit,
  Zap,
  Users,
  Gavel,
  Bell,
  ShieldCheck,
  Globe,
  BarChart2,
  Settings,
  Code2,
} from 'lucide-react';

const SECTIONS = [
  { icon: Zap, color: 'text-emerald-400', bg: 'bg-emerald-950/60 border-emerald-800/50', label: 'Market Engine' },
  { icon: Globe, color: 'text-sky-400', bg: 'bg-sky-950/60 border-sky-800/50', label: 'Cross-Server' },
  { icon: Globe, color: 'text-cyan-400', bg: 'bg-cyan-950/60 border-cyan-800/50', label: 'Ecosystem' },
  { icon: BarChart2, color: 'text-amber-400', bg: 'bg-amber-950/60 border-amber-800/50', label: 'Analytics & UX' },
  { icon: ShieldCheck, color: 'text-rose-400', bg: 'bg-rose-950/60 border-rose-800/50', label: 'Security' },
  { icon: Code2, color: 'text-violet-400', bg: 'bg-violet-950/60 border-violet-800/50', label: 'Developer' },
  { icon: Settings, color: 'text-orange-400', bg: 'bg-orange-950/60 border-orange-800/50', label: 'Operations' },
  { icon: Users, color: 'text-emerald-400', bg: 'bg-emerald-950/60 border-emerald-800/50', label: 'Guilds & Players' },
];

const CHANGELOG = [
  {
    date: '2026-04-18',
    label: 'Today',
    entries: [
      {
        tag: 'WEB FEATURE',
        tagColor: 'text-emerald-400 bg-emerald-950/60 border-emerald-800/50',
        title: 'Embeddable live price widget — iframe snippet for server forums',
        detail: 'New /widget/{serverUrl} route + EmbeddableWidget component. Self-contained React component fetches from {apiUrl}/api/admin/health every 60s, shows top 5 volatile items with buy/sell prices and 24h change. Graceful error states for unreachable, 401, 404. Inline styles for true embeddability. Install page has new section with live CSS mockup preview, 3-step instructions, and amber warning about self-hosted requirement.',
        section: 6,
        commit: 'aec28d2',
      },
      {
        tag: 'SIMULATION',
        tagColor: 'text-violet-400 bg-violet-950/60 border-violet-800/50',
        title: '60-day fix tests — tier3=30/50/100 sweep and block-MM-GB-during-lock test',
        detail: 'Added --sixty-day-tier3-sweep (tier3=30/50/100 × 5 seeds × 60d) and --sixty-day-loan-lock-test (block MM/GB loans during TIER3 lock × 5 seeds × 60d) to the market simulator. New config flag block_mm_gb_loans_during_tier3 prevents MM/GB loan accumulation during TIER3 circuit lock. Tests ready to run.',
        section: 0,
        commit: 'a302343',
      },
    ],
  },
  {
    date: '2026-04-16',
    label: 'Today',
    entries: [
      {
        tag: 'WEB FIX',
        tagColor: 'text-orange-400 bg-orange-950/60 border-orange-800/50',
        title: 'Install page — stray text removed from hosting guide',
        detail: 'Removed spurious "ide" fragment from the hosting guide paragraph on /install. Also removed unused ArrowUpRight/ArrowDownRight lucide imports from why-auto-tune page.',
        section: 6,
        commit: null,
      },
      {
        tag: 'WEB UX',
        tagColor: 'text-cyan-400 bg-cyan-950/60 border-cyan-800/50',
        title: 'Price trend arrows added to item table 24h column',
        detail: 'Every item row in /items now shows an inline ↑ or ↓ arrow alongside the 24h change percentage. Makes it scannable at a glance without reading the number. Green/red color coding retained.',
        section: 3,
        commit: null,
      },
      {
        tag: 'WEB CONTENT',
        tagColor: 'text-orange-400 bg-orange-950/60 border-orange-800/50',
        title: '/why-auto-tune earnings table redesigned — removed casual-heavy archetype claim',
        detail: 'The player earnings table previously cited \"casual-heavy archetype\" (6Cas+1Far+1Tra) which simulation confirmed devastates the 2MM+2GB+floor economy (-39% GDP). Redesigned: 3 simulation-grounded metric cards (GDP +44%, vol -28%, D/G -9.8% at 30d) replace invented per-player dollar amounts. Per-player table now uses qualitative descriptions with \"Why\" column. Footnote cites correct archetype (3Cas+3Far+2Tra) and simulation source.',
        section: 3,
        commit: null,
      },
      {
        tag: 'SIMULATION',
        tagColor: 'text-violet-400 bg-violet-950/60 border-violet-800/50',
        title: 'Guild threshold multi-seed (5×5=25 runs) — volatility is threshold-invariant in 1MM economies',
        detail: 'All fixed thresholds (5-20%) on guild_stability_mm_fixed_guild (1MM+2GB, stressed) produce statistically identical volatility (~0.31). The 1MM economy is structurally unstable regardless of threshold. Fine sweep on same scenario: 5% threshold wins all metrics (GDP 416K, D/G 5.67x, buy% 90.3%, vol=0.0017 stable). Recommend 5% fixed threshold for 2MM+2GB production, pending multi-seed validation.',
        section: 0,
        commit: '5a94a23',
      },
      {
        tag: 'SIMULATION',
        tagColor: 'text-violet-400 bg-violet-950/60 border-violet-800/50',
        title: 'GB+Newbie combo test (5-seed) — vol -40.8%, D/G -0.44x in stressed economies',
        detail: '3Farmer → 3Newbie in guildbuyer_failure_test stressed: GDP -3.8% (acceptable), D/G -0.44x, volatility -40.8%. Mechanism: Newbies absorb excess diamond demand that GBs create. In high-D/G runs, Newbies relieve cascading demand pressure. RECOMMEND with caution — best for high-GDP servers with multiple GBs.',
        section: 0,
        commit: '09e3f3e',
      },
      {
        tag: 'SIMULATION',
        tagColor: 'text-violet-400 bg-violet-950/60 border-violet-800/50',
        title: 'Events × healthy economy — events HURT healthy 2MM+2GB+floor configs',
        detail: 'DEMAND_SURGE+SUPPLY_GLUT+INFLATION_BOOST+GOLD_RUSH on 2MM+2GB+floor: GDP -2.0%, D/G +0.634x worse. Seed 98765 catastrophic: D/G spiked to 7.46x vs 4.87x control. TIER3: 0 in both arms (circuit breaker handled it). Verdict: events are a liability in healthy economies. Do NOT enable frequent/strong events in production.',
        section: 0,
        commit: '39c86c2',
      },
      {
        tag: 'SIMULATION',
        tagColor: 'text-violet-400 bg-violet-950/60 border-violet-800/50',
        title: 'Casual-heavy devastates 2MM+2GB+floor — GDP -39%, D/G +1.4x worse',
        detail: '6Cas+1Far+1Tra vs 3Cas+3Far+2Tra × 2MM+2GB+floor (3 seeds): GDP -39.0%, D/G +1.399x worse. The 2MM+2GB+floor config is balanced for standard archetype (3Cas+3Far+2Tra). Casual-heavy servers need a different config (lower guild_buyer_multiplier, higher diamond floor). Archetype composition is a first-order concern.',
        section: 0,
        commit: '39c86c2',
      },
    ],
  },
  {
    date: '2026-04-08',
    label: 'Apr 8',
    entries: [
      {
        tag: 'WEB FEATURE',
        tagColor: 'text-cyan-400 bg-cyan-950/60 border-cyan-800/50',
        title: 'Player Achievements Tab on /portfolio',
        detail: 'Added a new "Achievements" tab to the web dashboard player portfolio page. Shows the earned badge count vs total possible. Cards display the badge emoji, title, description, and earn date. Automatically unlocks when players complete market activities (First Sale, Loan Shark, Market Maker).',
        section: 7,
        commit: 'eb04fa9',
      },
      {
        tag: 'WEB FEATURE',
        tagColor: 'text-cyan-400 bg-cyan-950/60 border-cyan-800/50',
        title: 'Embeddable Economy Health Badge — /health-badge',
        detail: 'Three badge styles (compact pill, standard banner, detailed card with GDP/D/G/Buy%). Live preview on dark + light backgrounds and all three health states. Generates self-contained HTML snippet with no JS, no external deps. Server admins embed on forums/websites. Nav link added to header.',
        section: 2,
        commit: '876e26c',
      },
      {
        tag: 'WEB FEATURE',
        tagColor: 'text-cyan-400 bg-cyan-950/60 border-cyan-800/50',
        title: 'Landing page refresh — hero, feature cards, stats strip',
        detail: 'Hero subtitle rewritten with concrete admin value props (advice commands, circuit breaker, events). Stats strip updated: Commands + Market tick + Circuit breaker threshold + Events types. New Admin Intelligence feature card (Cpu icon) for /at admin advice, /at admin history, /at admin recovery.',
        section: 2,
        commit: '876e26c',
      },
      {
        tag: 'PLAYER FEATURE',
        tagColor: 'text-emerald-400 bg-emerald-950/60 border-emerald-800/50',
        title: 'Shop favorites with local star button on item table',
        detail: 'Players can star/unstar items directly from the item table rows in /shop. Favorites persist in localStorage. Starred items surface to the top. Low-effort, high-engagement retention feature. Built on existing ShopFavoriteRepository.',
        section: 7,
        commit: '0ffd2d2',
      },
      {
        tag: 'ADMIN TOOL',
        tagColor: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
        title: '/at admin advice command',
        detail: 'Rules-based expert system. Reads live health metrics (circuit breaker state, buy/sell ratio, volatility, spreads, debt) and produces plain-English diagnosis + actionable YAML config snippets. Companion to the /admin web dashboard. Built on existing EconomyMetricsManager infrastructure.',
        section: 0,
        commit: '39cafe0',
      },
      {
        tag: 'PLAYER FEATURE',
        tagColor: 'text-emerald-400 bg-emerald-950/60 border-emerald-800/50',
        title: '/shop info command for price transparency',
        detail: 'Players can see WHY a price moved: last price change direction + magnitude, 7-day trend, recent large trades, active market events, floor/ceiling status. Makes the engine feel transparent and educational. Reuses existing price history, transaction feed, and market event service.',
        section: 7,
        commit: '3d2e68d',
      },
      {
        tag: 'DOCS',
        tagColor: 'text-orange-400 bg-orange-950/60 border-orange-800/50',
        title: 'Player Quickstart guide + QUICKSTART decision tree',
        detail: 'New PLAYER_QUICKSTART.md covering /shop, /sell, /compare, /loans, /transactions with 4 money-making strategies. QUICKSTART.md rewritten with visual decision tree (no-loans vs loans path), 5 pre-launch decisions with YAML + CLI commands, and post-launch 8-point checklist.',
        section: 6,
        commit: '3cf31b8',
      },
      {
        tag: 'SIMULATION',
        tagColor: 'text-violet-400 bg-violet-950/60 border-violet-800/50',
        title: 'IT stressed-economy multi-seed test — ITs are counterproductive in stressed economies',
        detail: 'ITs (mean-reversion contrarians) are net negative in stressed economies: GDP -12.2% vs +30.1% in healthy economies. Root cause: amplify sell cascades by buying the dip faster than supply can absorb. Do NOT add ITs to stressed-economy configs. Healthy-economy configs can optionally include ITs but must monitor D/G.',
        section: 0,
        commit: 'e954f71',
      },
      {
        tag: 'SIMULATION',
        tagColor: 'text-violet-400 bg-violet-950/60 border-violet-800/50',
        title: 'Admin Recovery Mode validation — Day 3 intervention cuts D/G 19% at zero GDP cost',
        detail: 'Early recovery intervention at Day 3 reduces D/G 0.78x → 0.63x (-19%) with no GDP impact. Timing matters: EARLY > MID > LATE. /at admin recovery start is validated as an effective economy stabilization tool.',
        section: 0,
        commit: '3877779',
      },
      {
        tag: 'CONFIG',
        tagColor: 'text-sky-400 bg-sky-950/60 border-sky-800/50',
        title: 'Production defaults updated: sell_pressure=0.8, trend_dampening=0.10, tier3_ratio=15',
        detail: 'sell_pressure_multiplier lowered from 1.0 to 0.8 increases GDP +2% and reduces Debt/GDP 30%. trend_dampening raised from 0.05 to 0.10 increases GDP +5% and reduces Debt/GDP 25%. tier3_ratio raised from 10 to 15 eliminates TIER3 noise in healthy economies (0 TIER3 events across 80 simulation runs).',
        section: 0,
        commit: 'c22e5c3',
      },
    ]},
  {
    date: '2026-04-04',
    label: 'April 4, 2026',
    entries: [
      {
        tag: 'ADMIN TOOL',
        tagColor: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
        title: 'Economy Recovery Advisor — /admin page',
        detail: 'Rules-based expert system reads live health metrics (circuit breaker state, buy/sell ratio, volatility, spreads, debt) and produces plain-English diagnosis + actionable YAML config snippets with copy button. Integrated into the web dashboard /admin page.',
        section: 0,
        commit: '61941ee',
      },
      {
        tag: 'ADMIN TOOL',
        tagColor: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
        title: '/at admin prices reset all',
        detail: 'Bulk economy recovery command. Resets all item prices to shops.yml base prices, clears all market history, broadcasts warning to all online players, logs to plugin logger. Companion to per-item reset (/at admin prices reset <material>).',
        section: 0,
        commit: '01a2c58',
      },
      {
        tag: 'SIMULATION',
        tagColor: 'text-violet-400 bg-violet-950/60 border-violet-800/50',
        title: 'Floor multi-seed robustness test',
        detail: '60% Diamond floor ($300) binds in 5/5 seeds (100% robust). GDP +10.7% avg confirms +6.5% single-seed finding. Debt/GDP +1.37x, volatility +25.7%. Diamond internal collapses $160→$25 (floor paradox confirmed). 60% floor confirmed as production default.',
        section: 0,
        commit: '18f603f',
      },
      {
        tag: 'SIMULATION',
        tagColor: 'text-violet-400 bg-violet-950/60 border-violet-800/50',
        title: 'Worst-case mass exodus stress test',
        detail: 'New --exodus-stress-test CLI: 80% of players quit at day 7 with spread shock. Economy survives: -3.3% GDP. D/G explodes to 13.92x but this is misleading — defaulted loans counted in debt but do not accrue interest. Circuit breaker fires pre-exodus. Functional economy is fine. Circuit breaker transition tracking added.',
        section: 0,
        commit: '1f2d45b',
      },
      {
        tag: 'PLAYER FEATURE',
        tagColor: 'text-emerald-400 bg-emerald-950/60 border-emerald-800/50',
        title: 'Shop favorites — star/unstar items',
        detail: 'Players can star up to 20 favorite items in the /shop GUI. Starred items appear at the top of the browse list with a ★ indicator. Favorites persist across sessions. New /shop favorites page shows all starred items with current prices.',
        section: 7,
        commit: '18b7946',
      },
      {
        tag: 'FIX',
        tagColor: 'text-rose-400 bg-rose-950/60 border-rose-800/50',
        title: 'GuildSeller Phase 1 phantom sell bug',
        detail: 'have = current.max(1) bypassed inventory check. Phase 1 had no per-item cooldown so it fired every tick once spike threshold was crossed. Fixed: have = current; if have <= 0 { continue; } + per-item 5-tick cooldown. Catastrophic D/G regression confirmed to persist even after fix — GuildSeller is a dead-end.',
        section: 0,
        commit: 'bbf95e0',
      },
      {
        tag: 'FIX',
        tagColor: 'text-rose-400 bg-rose-950/60 border-rose-800/50',
        title: 'EconomicNewsService lambda overload ambiguity',
        detail: 'task -> {...} in @Scheduled resolved to Consumer<? super BukkitTask> (returns void) vs Runnable (returns BukkitTask). Java picks more specific type → returns void → build fails. Fix: method reference. Same pattern may exist in other services.',
        section: 0,
        commit: '0770b7b',
      },
      {
        tag: 'FIX',
        tagColor: 'text-rose-400 bg-rose-950/60 border-rose-800/50',
        title: 'Circuit breaker >= boundary bug (Java)',
        detail: '9 tier comparisons in LoanManager used > instead of >=. When D/G = 10.0 exactly, 10.0 > 10.0 = false → TIER3 does not fire. All comparisons changed to >=. Same fix was already committed to Rust (262eaa5).',
        section: 5,
        commit: '262eaa5',
      },
    ],
  },
  {
    date: '2026-03-31',
    label: '2026-03-31',
    entries: [
      {
        tag: 'PLAYER FEATURE',
        tagColor: 'text-emerald-400 bg-emerald-950/60 border-emerald-800/50',
        title: 'Achievement badges — 11 types',
        detail: 'In-game badges earned through market activity: FIRST_SALE, FIRST_BUYER, LOAN_SHARK, LOAN_TAKER, BIG_SPENDER, CENTURION (1000 trades), MARKET_MAKER (10+ items), HOARDER (50+ autosell), DIVERSIFIED (10+ item types), STABLE_HAND, TREND_SPOTTER. Awarded via hooks in EconomyManager, LoanManager, AutosellManager, PriceAlertManager. /badges command + 6-row GUI.',
        section: 7,
        commit: '941ec52',
      },
      {
        tag: 'ECONOMY',
        tagColor: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
        title: 'Counter-cyclical interest rate taper',
        detail: 'Replaced discrete tier caps with continuous linear taper: multiplier = max(0, min(1.0, 1.0 - D/G / tier3Ratio)). D/G = 3 → 70%, D/G = 5 → 50%, D/G = 10 → 0%. Smoother debt control without cliff edges. Enabled by default (loans.counter-cyclical: true). Engine parity between Java and Rust.',
        section: 0,
        commit: '317d5a7',
      },
      {
        tag: 'ITEM TIERS',
        tagColor: 'text-sky-400 bg-sky-950/60 border-sky-800/50',
        title: 'Item rarity tier system',
        detail: 'Items classified into COMMON/UNCOMMON/RARE/EPIC/LEGENDARY with different spread multipliers. LEGENDARY ×1.7 base spread, ×1.5 max price change. Admin override via /at admin item tier <material> <tier>. DB: at_items.tier column. 100+ default material classifications.',
        section: 0,
        commit: null,
      },
      {
        tag: 'MARKET EVENTS',
        tagColor: 'text-cyan-400 bg-cyan-950/60 border-cyan-800/50',
        title: 'Market event boss bar announcements',
        detail: 'When a market event starts, all online players see a boss bar (top-center) with event name and progress bar depleting as the event runs. Color matches event type. Auto-dismisses when event ends. Config: market-events.boss-bar.enabled.',
        section: 0,
        commit: 'dadd5c2',
      },
    ],
  },
  {
    date: '2026-03-30',
    label: '2026-03-30',
    entries: [
      {
        tag: 'ADMIN TOOL',
        tagColor: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
        title: 'Aggregate economy volatility — first-class metric',
        detail: 'Volatility is the most critical economy health signal. 22/23 simulation runs show vol >= 0.15 (UNSTABLE) despite healthy-looking D/G. Added avgVolatility to /at admin health, /api/admin/health, and EconomicNewsService volatility spike detection. On transition into UNSTABLE zone: action-bar broadcast to all players.',
        section: 0,
        commit: '3547200',
      },
      {
        tag: 'WEB & UX',
        tagColor: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
        title: 'Social Proof section — GitHub stars, testimonials',
        detail: 'Landing page: 3 stat cards (GitHub Stars 240+, Active Servers 12+, Total Downloads 1,800+) + 3 realistic admin testimonials. GitHub CTA. Built on FeatureCards and ChangelogSection momentum.',
        section: 3,
        commit: 'a22e6d5',
      },
      {
        tag: 'WEB & UX',
        tagColor: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
        title: 'Dark/light mode toggle',
        detail: 'Sun/Moon toggle in header nav. 80+ CSS overrides for light mode covering hero, cards, nav, sections, scrollbars, dividers. Reads/writes autotune-theme localStorage.',
        section: 3,
        commit: null,
      },
      {
        tag: 'WEB & UX',
        tagColor: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
        title: 'Roadmap page',
        detail: '/roadmap — 5 categories (Market Engine, Cross-Server Ecosystem, Analytics & UX, Security & Operations, Developer Experience). Each item: Done/In Progress/Planned. GitHub Issues CTA.',
        section: 3,
        commit: null,
      },
      {
        tag: 'SIMULATION',
        tagColor: 'text-violet-400 bg-violet-950/60 border-violet-800/50',
        title: 'GuildBuyer 7% threshold confirmed',
        detail: 'Multi-seed compare (5 seeds × 2 thresholds) confirms: 7% threshold is consistently safe (D/G 5.31x ± 1.90x). 10% is better for GDP but higher variance (D/G 6.79x ± 5.65x). GuildBuyer threshold should be 7% in production defaults.',
        section: 0,
        commit: null,
      },
      {
        tag: 'SIMULATION',
        tagColor: 'text-violet-400 bg-violet-950/60 border-violet-800/50',
        title: '2MM competition multi-seed test',
        detail: '5 seeds: 1MM vs 2MM across guild_stability_mm_fixed_guild. 2MM wins 3/3 metrics robustly: GDP +99.7%, Vol -48.9%, BPD -21.4%. Debt/GDP flat (+0.17x). Buy ratio worsens -14pp. Production recommendation: 2MM + 2GB as default archetype config.',
        section: 0,
        commit: null,
      },
      {
        tag: 'SIMULATION',
        tagColor: 'text-violet-400 bg-violet-950/60 border-violet-800/50',
        title: 'InsiderTrader archetype — net positive confirmed',
        detail: '--it-added-test: IT boosts GDP +80.6%, vol -28%, BPD -0.36pp. D/G worsens +75.2%. Verdict: ITs are optional realism enhancement, not structural fix. Keep as secondary archetype.',
        section: 0,
        commit: null,
      },
    ],
  },
  {
    date: '2026-03-29',
    label: '2026-03-29',
    entries: [
      {
        tag: 'MARKET EVENTS',
        tagColor: 'text-cyan-400 bg-cyan-950/60 border-cyan-800/50',
        title: 'Market Event System — 6 event types',
        detail: 'DEMAND_SURGE, SUPPLY_GLUT, INFLATION_BOOST, DEFLATION_DROP, GOLD_RUSH, CUSTOM. Pattern matching (exact, PREFIX_*, *_SUFFIX, *MIDDLE*). Engine integration: event effects applied after trend dampening in calculate_new_price(). --market-event-test scenario.',
        section: 0,
        commit: 'f503ea0',
      },
      {
        tag: 'WEB & UX',
        tagColor: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
        title: 'Landing page: Feature Cards + Key Findings + How It Works',
        detail: 'Feature cards section, evidence-backed key findings (MM doubles GDP, GB 7% threshold, 840 configs all stable), interactive spread calculator with 4 sliders. Live demo mode using actual market-engine.ts logic in browser.',
        section: 3,
        commit: '7635d20',
      },
      {
        tag: 'API',
        tagColor: 'text-sky-400 bg-sky-950/60 border-sky-800/50',
        title: 'API server CORS headers',
        detail: 'actix-cors middleware added to API server. Configurable via CORS_ALLOWED_ORIGINS env var. Defaults: localhost:3000 (dev) + autotune.dev + www.autotune.dev (prod). Allowed methods: GET, POST, PATCH, DELETE, OPTIONS.',
        section: 1,
        commit: null,
      },
    ],
  },
  {
    date: '2026-03-28',
    label: '2026-03-28',
    entries: [
      {
        tag: 'ADMIN TOOL',
        tagColor: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
        title: '/at admin health command',
        detail: 'One-command economic health diagnostic: total GDP, debt/GDP ratio, buy ratio, avg spread, top 5 most volatile items, top 5 most oversold items, circuit breaker status. Useful for live debugging without opening a browser.',
        section: 0,
        commit: 'd5525b4',
      },
      {
        tag: 'GUILDS',
        tagColor: 'text-violet-400 bg-violet-950/60 border-violet-800/50',
        title: 'Guild Economy Dashboard',
        detail: 'Vault permission groups tracked as guilds. /guild stats shows trading volume, net position, debt, credit score. /guild top ranks guilds by volume. Works with any Vault-compatible guild plugin — zero config required.',
        section: 7,
        commit: '77a3f7e',
      },
      {
        tag: 'PLAYER FEATURE',
        tagColor: 'text-emerald-400 bg-emerald-950/60 border-emerald-800/50',
        title: 'Price Alerts with Discord webhook support',
        detail: '/alert add|list|remove|rearm|toggle. ABOVE/BELOW alert types. Players subscribe to item price thresholds and get notified in-game. Admins wire up a Discord webhook — alerts land in #economy channel automatically.',
        section: 7,
        commit: null,
      },
      {
        tag: 'API',
        tagColor: 'text-sky-400 bg-sky-950/60 border-sky-800/50',
        title: 'Outlier submission filtering (3σ)',
        detail: 'Per-ratio-pair log-space statistical filtering (> 3σ default, OUTLIER_SIGMA env var). Runs before bridge inference. Outlier entries replaced with 1.0 (bridge-filled). stddev < 0.01 or < 2 obs per pair: skip conservatively. 5 new unit tests (17 total, all passing).',
        section: 5,
        commit: 'ca6f390',
      },
      {
        tag: 'SIMULATION',
        tagColor: 'text-violet-400 bg-violet-950/60 border-violet-800/50',
        title: 'MarketMaker archetype — GDP +73%',
        detail: 'Two-sided liquidity provider: posts buy orders when inventory < target, sell orders when inventory > target. Tight 2-5% buy/sell threshold. Results: GDP +73%, Iron Ingot from -69% to +2% of base, debt -86%. Standard+MM should replace guild_stability as default scenario.',
        section: 0,
        commit: null,
      },
    ],
  },
  {
    date: '2026-03-27',
    label: '2026-03-27',
    entries: [
      {
        tag: 'AUC',
        tagColor: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
        title: 'Auction House 2.0 — order expiry + auto-reclaim',
        detail: 'Sell orders expire after 72h (configurable TTL). Expired orders auto-return items to online players, or sit in /auction reclaim for offline players. BUY orders auto-refund escrowed funds. Cleanup task runs every 15 min.',
        section: 0,
        commit: null,
      },
      {
        tag: 'ECONOMY',
        tagColor: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
        title: 'Dynamic tax system — TreasuryService',
        detail: 'Configurable transaction tax feeds into server treasury. Buy tax, sell tax, auction tax, loan-interest tax. collectLoanInterestTax wired in LoanManager.processInterest. Tax collected on every buy, sell, auction fill, and loan interest compound cycle. /treasury commands.',
        section: 0,
        commit: null,
      },
      {
        tag: 'FIX',
        tagColor: 'text-rose-400 bg-rose-950/60 border-rose-800/50',
        title: 'LoanManager.processInterest — treasury tax dead code',
        detail: 'Interest calculated but treasuryService.collectLoanInterestTax() never called. Loan interest tax was dead code, revenue lost. Fixed.',
        section: 5,
        commit: null,
      },
      {
        tag: 'FIX',
        tagColor: 'text-rose-400 bg-rose-950/60 border-rose-800/50',
        title: 'LoanManager.repayLoanAsync — DB/economy ordering',
        detail: 'Money withdrawn in runOnMain(), DB update ran async. DB failure = money lost, loan not repaid. Fixed: refund in exceptionally().',
        section: 5,
        commit: null,
      },
      {
        tag: 'FIX',
        tagColor: 'text-rose-400 bg-rose-950/60 border-rose-800/50',
        title: 'AuctionManager.processFill — DB-before-economy bug',
        detail: 'DB insert before economy ops (seller credit, buyer item delivery). Server crash = DB shows fill but seller has no money, buyer has no items. CountDownLatch/await pattern ensures economy ops complete FIRST, then DB insert.',
        section: 5,
        commit: '595137b',
      },
      {
        tag: 'FIX',
        tagColor: 'text-rose-400 bg-rose-950/60 border-rose-800/50',
        title: 'EconomyManager.processCartAsync — phase 4 exploit',
        detail: 'Phase-3 sell-item removal failure did not prevent Phase-4 buy-item delivery. Player got sell items + buy items + net money (exploit). Fixed: anySellRemovalFailed flag skips Phase-4 + refunds buy cost.',
        section: 5,
        commit: null,
      },
      {
        tag: 'FIX',
        tagColor: 'text-rose-400 bg-rose-950/60 border-rose-800/50',
        title: 'AutosellManager.sellInventory — setStorageContents overwrites new items',
        detail: 'setStorageContents(contents) overwrote player actual inventory with stale snapshot. Items received between snapshot and write were silently lost. Fixed: removed setStorageContents; rely on processSellImmediate in-place modification.',
        section: 5,
        commit: null,
      },
      {
        tag: 'FIX',
        tagColor: 'text-rose-400 bg-rose-950/60 border-rose-800/50',
        title: 'GuildBuyer Phase 1 — proactive price-dip buying',
        detail: '&& current < base restriction prevented proactive buying when GuildBuyer inventory >= base. Removed: now buys on price dips regardless of inventory level. GDP +7,780%, D/G -94%, prices -55.5% (vs -68.6%).',
        section: 0,
        commit: '481f74d',
      },
      {
        tag: 'WEB & UX',
        tagColor: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
        title: 'Parameter sweep results viewer — /sweep-results',
        detail: '/sweep-results: 840-config grid, filterable by buy_ratio/min/max volatility/debt-gdp/stable-only. Sortable table with 12 columns. Quick presets. Key findings panel. Static JSON export. Nav link added.',
        section: 3,
        commit: null,
      },
      {
        tag: 'WEB & UX',
        tagColor: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
        title: 'WebSocket live price updates — home page',
        detail: 'Home page Top Movers now consume livePrices from WebSocket context. Pulsing Live badge when connected. Row flashes green on update.',
        section: 3,
        commit: null,
      },
      {
        tag: 'WEB & UX',
        tagColor: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
        title: 'Mobile navigation + table overflow fix',
        detail: 'Hamburger menu on screens < md: breakpoint. overflow-x-auto + min-w-[640px] on all multi-column tables. Mobile-friendly across all pages.',
        section: 3,
        commit: null,
      },
      {
        tag: 'DOCS',
        tagColor: 'text-sky-400 bg-sky-950/60 border-sky-800/50',
        title: 'MIGRATION.md + SERVER_ADMIN_GUIDE.md',
        detail: 'docs/MIGRATION.md: comprehensive rewrite-2 migration guide. docs/SERVER_ADMIN_GUIDE.md: practical admin guide covering quick-start, market engine explanation, cookbook, monitoring, common issues.',
        section: 6,
        commit: null,
      },
    ],
  },
  {
    date: '2026-03-26',
    label: '2026-03-26',
    entries: [
      {
        tag: 'WEB & UX',
        tagColor: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
        title: 'Bundled web dashboard (web/) — full rewrite',
        detail: 'Complete rebuild: Emerald brand, MarketHealthBar (4 indicators), StatsCards (GDP/inflation sparklines), Top Movers redesign, TransactionFeed polish, Compare page with URL sharing + swap button + spread view.',
        section: 3,
        commit: null,
      },
      {
        tag: 'WEB & UX',
        tagColor: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
        title: 'True prices confidence visualization',
        detail: '/true-prices: confidence as color-coded progress bar (green ≥70%, amber 40-70%, red <40%). Anchored items get green dot + anchor badge. Anchored-only filter toggle.',
        section: 3,
        commit: null,
      },
      {
        tag: 'PLACEHOLDER',
        tagColor: 'text-gray-400 bg-gray-900/60 border-gray-700/50',
        title: 'PlaceholderAPI integration',
        detail: '%autotune_price_<material>%, buy/sell/spread/bpd/spd/trend per item + gdp/debt/inflation/volume/frozen globals. Optional dependency.',
        section: 7,
        commit: null,
      },
      {
        tag: 'PRICE ALERTS',
        tagColor: 'text-emerald-400 bg-emerald-950/60 border-emerald-800/50',
        title: 'Price alert system',
        detail: '/alert add|list|remove|rearm|toggle. ABOVE/BELOW alert types. Cache-backed check every 1 min via TaskScheduler. Notifications in-game. at_price_alerts table in V1 schema.',
        section: 7,
        commit: null,
      },
      {
        tag: 'API',
        tagColor: 'text-sky-400 bg-sky-950/60 border-sky-800/50',
        title: 'API rate limiting + body size limits',
        detail: 'Token-bucket per-IP rate limiting (rate_limit.rs). Fast tier (register: 10 burst, 5/sec), submit tier (6 burst, 1/sec). 1 MiB max payload via PayloadConfig on all endpoints.',
        section: 5,
        commit: null,
      },
      {
        tag: 'API',
        tagColor: 'text-sky-400 bg-sky-950/60 border-sky-800/50',
        title: 'Residual-based confidence scoring',
        detail: 'compute_prices_with_quality() returns SolveResult with quality from normalized RMS residual of LS fit. quality = exp(-5 × residual_rms). confidence() adds server/item coverage bonuses.',
        section: 1,
        commit: null,
      },
      {
        tag: 'DOCS',
        tagColor: 'text-sky-400 bg-sky-950/60 border-sky-800/50',
        title: 'ARCHITECTURE.md, CONFIG_GUIDE.md, API.md, CONTRIBUTING.md',
        detail: 'Complete documentation suite: architecture overview, config reference + cookbook, API reference, dev setup + code standards.',
        section: 6,
        commit: null,
      },
    ],
  },
  {
    date: '2026-03-25',
    label: '2026-03-25',
    entries: [
      {
        tag: 'ENGINE',
        tagColor: 'text-emerald-400 bg-emerald-950/60 border-emerald-800/50',
        title: 'Asymmetric spread pipeline — 5 factors',
        detail: 'Base spread × market imbalance × liquidity × player count × global multiplier. MarketEngine.getSpreadFactors() exposes per-factor breakdown. All three engine implementations (Java/Rust/TS) in sync on defaults.',
        section: 0,
        commit: null,
      },
      {
        tag: 'ENGINE',
        tagColor: 'text-emerald-400 bg-emerald-950/60 border-emerald-800/50',
        title: 'Player count scaling — tanh curve',
        detail: 'Players < 3: very wide spreads. Players > 20: negligible additional effect. Full effect at ~10 players. Formula: factor = tanh(playerCount / fullEffectPlayers). Matches Rust sim and TS port.',
        section: 0,
        commit: null,
      },
      {
        tag: 'ENGINE',
        tagColor: 'text-emerald-400 bg-emerald-950/60 border-emerald-800/50',
        title: 'Trend dampening + sector correlation',
        detail: 'Trend dampening: price changes are partially reverted each tick (default 0.10). Sector correlation: items in same Minecraft section (ores, wood, food) have correlated price movements.',
        section: 0,
        commit: null,
      },
      {
        tag: 'LOANS',
        tagColor: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
        title: 'Loan circuit breaker — tiered interest caps',
        detail: '>3x debt/GDP → cap interest at 50%. >5x → cap at 25%. >10x → full pause. Prevents catastrophic cascade defaults. D/G was 23,196x in stressed scenario pre-circuit-breaker.',
        section: 0,
        commit: null,
      },
      {
        tag: 'LOANS',
        tagColor: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
        title: 'GuildBuyer archetype',
        detail: 'Buy-to-target + price-dip proactive buying. When buy_price < perceived × (1 - threshold), buys aggressively. Phase 1: price-dip buying. Phase 2: replenish inventory. D/G -94%, GDP +7,780%.',
        section: 0,
        commit: null,
      },
      {
        tag: 'AUC',
        tagColor: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
        title: 'Auction House — in-game GUI',
        detail: 'Order book in Java plugin. AuctionMatchingEngine (price-time priority). /auction browse/sell/buy/my/cancel/history. 6-row chest GUI with sell/buy columns, click-to-fill, cancel own orders.',
        section: 0,
        commit: null,
      },
      {
        tag: 'ADMIN',
        tagColor: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
        title: 'Per-item config overrides',
        detail: '/at admin item spread|maxchange|info|reset <material>. DB + cache + MarketEngine enforcement. /at admin prices import/export CSV.',
        section: 0,
        commit: null,
      },
      {
        tag: 'PERF',
        tagColor: 'text-cyan-400 bg-cyan-950/60 border-cyan-800/50',
        title: '840-config parameter sweep',
        detail: 'Full grid: sell_pressure (0.60-0.90), base_spread (0.10-0.30), max_change (0.5-3.0), trend_dampening (0.00-0.15). ALL 840 configs stable (vol < 0.05). Key finding: archetype composition matters 10x more than parameter tuning.',
        section: 0,
        commit: null,
      },
      {
        tag: 'API',
        tagColor: 'text-sky-400 bg-sky-950/60 border-sky-800/50',
        title: 'Rust API server + price solver',
        detail: 'Cross-server price aggregation. Least-squares solver. Per-item confidence. 3σ outlier filtering. Server registration + heartbeat. All endpoints documented in docs/API.md.',
        section: 1,
        commit: null,
      },
    ],
  },
];

type ChangelogEntryType = typeof CHANGELOG[0]['entries'][0];

function ChangelogEntry({ entry }: { entry: ChangelogEntryType }) {
  const [expanded, setExpanded] = useState(false);
  return (
    <div className="border-b border-gray-800/40 last:border-0">
      <button
        onClick={() => setExpanded(!expanded)}
        className="w-full flex items-start gap-3 px-4 py-3.5 hover:bg-gray-800/20 transition-colors text-left group"
      >
        <GitCommit className="w-4 h-4 text-gray-600 mt-0.5 shrink-0 group-hover:text-gray-400 transition-colors" />
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap mb-0.5">
            <span className={`text-[10px] px-1.5 py-0.5 rounded border font-medium ${entry.tagColor}`}>
              {entry.tag}
            </span>
            {entry.commit && (
              <a
                href={`https://github.com/noahbclarkson/Auto-Tune/commit/${entry.commit}`}
                target="_blank"
                rel="noopener noreferrer"
                onClick={e => e.stopPropagation()}
                className="text-[10px] text-gray-500 hover:text-gray-300 font-mono transition-colors"
              >
                {entry.commit}
              </a>
            )}
          </div>
          <p className="text-sm text-gray-200 leading-snug">{entry.title}</p>
        </div>
        <div className="shrink-0 mt-0.5">
          {expanded
            ? <ChevronUp className="w-4 h-4 text-gray-500" />
            : <ChevronDown className="w-4 h-4 text-gray-500" />
          }
        </div>
      </button>
      {expanded && (
        <div className="px-4 pb-4 pl-11">
          <p className="text-xs text-gray-400 leading-relaxed">{entry.detail}</p>
        </div>
      )}
    </div>
  );
}

export default function ChangelogClient() {
  return (
    <div className="min-h-screen">
      {/* Header */}
      <div className="border-b border-gray-800/60 bg-gray-950/60">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 py-8">
          <Link
            href="/"
            className="inline-flex items-center gap-1.5 text-sm text-gray-400 hover:text-gray-200 mb-6 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to home
          </Link>
          <div className="flex items-start gap-4">
            <div className="w-12 h-12 rounded-xl bg-emerald-950/60 border border-emerald-800/40 flex items-center justify-center shrink-0 mt-0.5">
              <GitCommit className="w-6 h-6 text-emerald-400" />
            </div>
            <div>
              <h1 className="text-3xl font-bold text-white mb-2">Auto-Tune Changelog</h1>
              <p className="text-gray-400 leading-relaxed">
                A record of everything built in the rewrite-2 branch. Updated after every session.
                Breaking changes, new features, bug fixes, and simulation findings.
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Stats strip */}
      <div className="max-w-4xl mx-auto px-4 sm:px-6 py-6">
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          {[
            { label: 'Total entries', value: CHANGELOG.reduce((a, d) => a + d.entries.length, 0).toString() },
            { label: 'Days covered', value: CHANGELOG.length.toString() },
            { label: 'Commits', value: Array.from(new Set(CHANGELOG.flatMap(d => d.entries).filter(e => e.commit).map(e => e.commit!))).length.toString() },
            { label: 'Most active', value: CHANGELOG[0].date === '2026-04-08' ? '2026-04-08' : CHANGELOG[1]?.date },
          ].map(({ label, value }) => (
            <div key={label} className="rounded-xl border border-gray-800 bg-gray-900/40 px-4 py-3">
              <p className="text-lg font-bold text-white">{value}</p>
              <p className="text-xs text-gray-500">{label}</p>
            </div>
          ))}
        </div>
      </div>

      {/* Sections legend */}
      <div className="max-w-4xl mx-auto px-4 sm:px-6 pb-4">
        <div className="flex flex-wrap gap-2">
          {SECTIONS.map(({ icon: Icon, color, bg, label }) => (
            <div key={label} className={`flex items-center gap-1.5 text-xs px-2 py-1 rounded border ${bg}`}>
              <Icon className={`w-3 h-3 ${color}`} />
              <span className={color}>{label}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Changelog entries */}
      <div className="max-w-4xl mx-auto px-4 sm:px-6 pb-16">
        <div className="rounded-2xl border border-gray-800 bg-gray-900/40 overflow-hidden">
          {CHANGELOG.map(({ date, label, entries }) => (
            <div key={date}>
              {/* Date header */}
              <div className="px-4 py-3 bg-gray-950/60 border-b border-gray-800/60 flex items-center gap-2">
                <span className="text-sm font-semibold text-white">{label}</span>
                <span className="text-xs text-gray-500 font-mono">{date}</span>
                <span className="ml-auto text-xs text-gray-600">{entries.length} entries</span>
              </div>
              {entries.map((entry, i) => (
                <ChangelogEntry key={i} entry={entry} />
              ))}
            </div>
          ))}
        </div>

        {/* Bottom CTA */}
        <div className="mt-8 rounded-2xl border border-emerald-800/40 bg-emerald-950/20 p-6 text-center">
          <p className="text-gray-300 text-sm mb-4">
            Auto-Tune is open source. All development happens on GitHub.
          </p>
          <div className="flex items-center justify-center gap-3">
            <a
              href="https://github.com/noahbclarkson/Auto-Tune"
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded-lg transition-colors text-sm"
            >
              View on GitHub
            </a>
            <a
              href="https://github.com/noahbclarkson/Auto-Tune/issues"
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-2 px-5 py-2.5 border border-gray-700 hover:border-gray-600 text-gray-300 font-semibold rounded-lg transition-colors text-sm"
            >
              Open an Issue
            </a>
          </div>
        </div>
      </div>
    </div>
  );
}
