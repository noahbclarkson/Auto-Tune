'use client';

import { GitCommit, Zap, Users, Gavel, Bell, ShieldCheck, Globe } from 'lucide-react';

const UPDATES = [
  // ── 2026-04-14: Nav re-org + network effect hero ─────────────────────────
  {
    date: '2026-04-14',
    icon: Globe,
    accent: 'text-emerald-400',
    accentBg: 'bg-emerald-950/60 border-emerald-800/50',
    tag: 'ECOSYSTEM',
    title: 'Nav re-organization — 4 sections + cross-server hero refresh',
    detail: '16 nav items grouped into 4 logical sections: Install · Learn · Tools · Community. Desktop: flat row with dividers. Mobile: hamburger + dropdown with section headers. Hero title updated to lead with the cross-server network effect ("Prices that get smarter with every server"). New Network Effect feature card added as card #1, explaining the data flywheel: submit → aggregate → anchor → distribute. Stats strip updated to "Cross-server: true-price network".',
    href: null,
  },
  // ── 2026-04-13: Web & Ecosystem — Why Auto-Tune page ───────────────────
  {
    date: '2026-04-13',
    icon: Users,
    accent: 'text-emerald-400',
    accentBg: 'bg-emerald-950/60 border-emerald-800/50',
    tag: 'ECOSYSTEM',
    title: 'New /why-auto-tune — standalone value proposition page',
    detail: 'New 7-section narrative page makes the case for Auto-Tune over static shops visceral. Includes demand/supply scenario cards with real price deltas, SVG sparkline charts (6-week Diamond history: flat $250 vs Auto-Tune $230→$341), simulation-backed player earnings table (farmer +114%, arbitrageur −87%), and the economics of supply/demand pricing in games. Fixed broken #get-started anchor in CompareSection CTA.',
    href: '/why-auto-tune',
  },
  // ── 2026-04-13: Simulation Lab — Definitive archetype verdicts ──────────
  {
    date: '2026-04-13',
    icon: ShieldCheck,
    accent: 'text-rose-400',
    accentBg: 'bg-rose-950/60 border-rose-800/50',
    tag: 'SIM LAB',
    title: 'Definitive archetype table — IT+VT combo is catastrophic',
    detail: 'IT+VT combo in healthy economy: GDP −25.6% (IT alone was +30.1%). VT dominates and reverses all of IT\'s gains. VT in stressed economy: −12.7% GDP (vs −9.2% in healthy). VT is always harmful. IT+VT together is a 55-percentage-point GDP reversal. Archetype table: 2MM+2GB ✅ production, +2IT ⚠️ optional, +2VT ❌ never, +IT+VT ❌ catastrophic.',
    href: null,
  },
  // ── 2026-04-08: Ecosystem work ────────────────────────────────────────────
  {
    date: '2026-04-08',
    icon: ShieldCheck,
    accent: 'text-emerald-400',
    accentBg: 'bg-emerald-950/60 border-emerald-800/50',
    tag: 'ECOSYSTEM',
    title: 'Embeddable server health badge — one-click Discord embed',
    detail: 'Server admins can embed a live economy health badge on their forum or website. Three styles (compact/standard/detailed), self-contained HTML snippet generator, zero external dependencies. Drives organic referral traffic.',
    href: '/health-badge',
  },
  {
    date: '2026-04-08',
    icon: Users,
    accent: 'text-sky-400',
    accentBg: 'bg-sky-950/60 border-sky-800/50',
    tag: 'ECOSYSTEM',
    title: 'Landing page refresh + Player Quickstart docs',
    detail: 'Hero rewritten with concrete admin value props. New /health-badge route, Admin Intelligence feature card (/at admin advice/history/recovery). docs/PLAYER_QUICKSTART.md created — player-facing guide covering /shop, /sell, /compare, /loans, /transactions with 4 money-making strategies.',
    href: '/changelog',
  },
  {
    date: '2026-04-04',
    icon: Zap,
    accent: 'text-amber-400',
    accentBg: 'bg-amber-950/60 border-amber-800/50',
    tag: 'ADMIN TOOL',
    title: 'Economy Recovery Advisor + price reset all',
    detail: 'Rules-based expert system reads live health metrics and produces plain-English diagnosis + actionable YAML config snippets with copy button. Also: bulk /at admin prices reset all for full economy recovery.',
    href: '/changelog',
  },
  {
    date: '2026-04-04',
    icon: Users,
    accent: 'text-emerald-400',
    accentBg: 'bg-emerald-950/60 border-emerald-800/50',
    tag: 'PLAYER FEATURE',
    title: 'Shop favorites — star items in /shop',
    detail: 'Star up to 20 favorite items. Starred items appear at the top of /shop with a ★ indicator. Favorites persist across sessions.',
    href: '/changelog',
  },
  {
    date: '2026-04-04',
    icon: ShieldCheck,
    accent: 'text-rose-400',
    accentBg: 'bg-rose-950/60 border-rose-800/50',
    tag: 'FIX',
    title: 'GuildSeller bug + circuit breaker boundary fix',
    detail: 'GuildSeller Phase 1 phantom sell bug (have = current.max(1)) fixed. Circuit breaker Java LoanManager updated with >= boundary check. GuildSeller remains a dead-end archetype.',
    href: '/changelog',
  },
  {
    date: '2026-03-29',
    icon: ShieldCheck,
    accent: 'text-amber-400',
    accentBg: 'bg-amber-950/60 border-amber-800/50',
    tag: 'ADMIN TOOL',
    title: '/at admin audit — one-command health check',
    detail: 'Run a full system diagnostic without digging through logs. The audit command checks Vault, config sanity, economy state, database integrity, and circuit breaker status — returns ✅/⚠️/❌ for each section.',
    href: null,
  },
  {
    date: '2026-03-28',
    icon: Users,
    accent: 'text-violet-400',
    accentBg: 'bg-violet-950/60 border-violet-800/50',
    tag: 'GUILDS',
    title: 'Guild Economy Dashboard',
    detail: 'Vault permission groups are now tracked as guilds. Players see their guild\'s trading volume, net position, debt, and rank. Works with any Vault-compatible guild plugin — zero config required.',
    href: null,
  },
  {
    date: '2026-03-27',
    icon: Bell,
    accent: 'text-rose-400',
    accentBg: 'bg-rose-950/60 border-rose-800/50',
    tag: 'PLAYER FEATURE',
    title: 'Price Alerts with Discord webhook support',
    detail: 'Players subscribe to item price thresholds and get notified when crossed. Admins wire up a Discord webhook — alerts land in your #economy channel automatically. ABOVE and BELOW alert types.',
    href: null,
  },
  {
    date: '2026-03-27',
    icon: Gavel,
    accent: 'text-amber-400',
    accentBg: 'bg-amber-950/60 border-amber-800/50',
    tag: 'AUCTION',
    title: 'Auction House 2.0 — order expiry + auto-reclaim',
    detail: 'Sell orders now expire after a configurable TTL (default 72h). Expired orders auto-return items to online players, or sit in /auction reclaim for offline players. BUY orders auto-refund escrowed funds.',
    href: null,
  },
  {
    date: '2026-03-26',
    icon: Globe,
    accent: 'text-cyan-400',
    accentBg: 'bg-cyan-950/60 border-cyan-800/50',
    tag: 'CROSS-SERVER',
    title: 'Cross-Server True Prices API',
    detail: 'Servers submit anonymised price ratios to an optional API. A least-squares solver computes globally consistent true prices used as starting baselines for new servers. 3σ outlier filter prevents spoofing.',
    href: '/true-prices',
  },
  {
    date: '2026-03-25',
    icon: Zap,
    accent: 'text-emerald-400',
    accentBg: 'bg-emerald-950/60 border-emerald-800/50',
    tag: 'CORE ENGINE',
    title: 'Market Event System — scheduled price events',
    detail: 'Gold Rush, Supply Glut, Demand Surge, Inflation Boost, and custom events. Each event has configurable duration, affected items (exact, prefix, suffix, or pattern match), and a price multiplier. Market reacts in real-time.',
    href: null,
  },
];

export function ChangelogSection() {
  return (
    <section className="py-20 border-t border-gray-800/40">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-end justify-between mb-10">
          <div>
            <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">Recent Work</p>
            <h2 className="text-2xl sm:text-3xl font-bold text-white">
              What shipped recently
            </h2>
            <p className="text-gray-400 text-sm mt-1.5 max-w-lg">
              Active development on the <code className="text-gray-400 text-xs bg-gray-800 px-1.5 py-0.5 rounded">rewrite-2</code> branch. Every feature is tested with automated market simulations before it ships.
            </p>
          </div>
        </div>

        <div className="relative">
          {/* Timeline line */}
          <div className="absolute left-6 top-0 bottom-0 w-px bg-gray-800 hidden sm:block" />

          <div className="space-y-4">
            {UPDATES.map((update) => {
              const Icon = update.icon;
              return (
                <div key={update.title} className="flex gap-5 group">
                  {/* Icon + timeline dot */}
                  <div className="shrink-0 relative z-10">
                    <div className={`w-12 h-12 rounded-xl border flex items-center justify-center ${update.accentBg}`}>
                      <Icon className={`w-5 h-5 ${update.accent}`} />
                    </div>
                  </div>

                  {/* Content card */}
                  <div className="flex-1 min-w-0 bg-gray-900/60 border border-gray-800 rounded-xl p-5 hover:border-gray-700 transition-colors">
                    <div className="flex items-center gap-3 mb-2 flex-wrap">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-mono font-semibold border ${update.accentBg} ${update.accent}`}>
                        {update.tag}
                      </span>
                      <span className="text-xs text-gray-500 font-mono">{update.date}</span>
                    </div>
                    <h3 className="text-base font-semibold text-white mb-1.5">{update.title}</h3>
                    <p className="text-sm text-gray-400 leading-relaxed">{update.detail}</p>
                  </div>
                </div>
              );
            })}
          </div>

          {/* CTA */}
          <div className="mt-6 pt-4 border-t border-gray-800/40 text-center">
            <a
              href="/changelog"
              className="inline-flex items-center gap-2 text-sm text-emerald-400 hover:text-emerald-300 font-medium transition-colors"
            >
              View full changelog →
            </a>
          </div>
        </div>
      </div>
    </section>
  );
}
