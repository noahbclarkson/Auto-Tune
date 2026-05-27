import Link from 'next/link';
import { ArrowRight, TrendingUp, TrendingDown, Minus } from 'lucide-react';

/* ------------------------------------------------------------------ */
/* Mock price timeline — annotated events                              */
/* ------------------------------------------------------------------ */

// SVG path for a price chart: spike up (demand surge), settle, dip (oversell), recover
const PRICE_PATH =
  'M0,70 L20,68 L40,65 L60,55 L80,40 L100,28 L120,32 L140,42 L160,38 L180,35 L200,38 L220,50 L240,58 L260,70 L280,75 L300,72 L320,68';
const PRICE_FILL =
  'M0,70 L20,68 L40,65 L60,55 L80,40 L100,28 L120,32 L140,42 L160,38 L180,35 L200,38 L220,50 L240,58 L260,70 L280,75 L300,72 L320,68 L320,90 L0,90 Z';

const ANNOTATIONS = [
  { x: 100, label: 'Raid loot spike', sub: 'Buy rush → price surges', color: '#f43f5e', align: 'right' as const },
  { x: 200, label: 'Market cools', sub: 'Sellers return → rebalance', color: '#10b981', align: 'left' as const },
];

/* ------------------------------------------------------------------ */
/* Comparison table                                                    */
/* ------------------------------------------------------------------ */

const COMPARISONS = [
  {
    scenario: 'Player buys 1,000 Iron Ingots',
    static: 'Price stays $5.00 forever',
    autotune: 'Price rises to ~$5.50 over next few ticks, discouraging bulk buying',
    outcome: 'auto',
  },
  {
    scenario: 'Server has 1 player at 3am',
    static: 'Same price as peak hour',
    autotune: 'Spreads widen (low player count), reducing market efficiency off-peak',
    outcome: 'auto',
  },
  {
    scenario: 'Nobody buys Blaze Rods for a week',
    static: 'Still expensive — no signal',
    autotune: 'Sell pressure drops price gradually, making it attractive again',
    outcome: 'auto',
  },
  {
    scenario: 'One player games the shop 500×',
    static: 'Infinite profit loop',
    autotune: 'Spread widens and price shifts against them within a few ticks',
    outcome: 'auto',
  },
];

/* ------------------------------------------------------------------ */
/* Server benefits                                                     */
/* ------------------------------------------------------------------ */

const BENEFITS = [
  {
    icon: '📈',
    title: 'Prices reflect reality',
    body: 'When Diamonds are hot after a raid event, the price actually rises. When everyone dumps their wheat, it falls. Players can read the market and make real economic decisions.',
    accent: 'emerald',
  },
  {
    icon: '⚖️',
    title: 'Self-correcting equilibrium',
    body: 'High prices discourage buying and attract sellers. Low prices attract buyers. The market naturally gravitates toward the price where supply meets demand — no admin intervention needed.',
    accent: 'sky',
  },
  {
    icon: '🛡️',
    title: 'No more exploitation',
    body: 'Static shops can be "broken" — buy at 10, sell at 11, repeat forever. Auto-Tune\'s spreads widen and prices move against bulk trading, closing arbitrage loops automatically.',
    accent: 'amber',
  },
  {
    icon: '🌍',
    title: 'Economy feels alive',
    body: 'Players notice that Netherite spikes during wither fights, Iron drops on peaceful mode. A living economy creates genuine conversation, strategy, and long-term engagement on the server.',
    accent: 'violet',
  },
  {
    icon: '📊',
    title: 'Server-size aware',
    body: 'A 3-player server and a 200-player server have completely different trading dynamics. Auto-Tune scales spreads and price velocity by player count — it works well at any scale.',
    accent: 'rose',
  },
  {
    icon: '⏱️',
    title: 'Every 5 minutes',
    body: 'Prices tick every 5 minutes using recency-weighted trade history. The market reacts to what happened recently more than a week ago, keeping it responsive without being jittery.',
    accent: 'emerald',
  },
];

const accentClasses: Record<string, { border: string; bg: string; text: string; icon: string }> = {
  emerald: { border: 'border-emerald-800/50', bg: 'bg-emerald-950/20', text: 'text-emerald-400', icon: 'bg-emerald-950/40 border-emerald-800/40' },
  sky:     { border: 'border-sky-800/50',     bg: 'bg-sky-950/20',     text: 'text-sky-400',     icon: 'bg-sky-950/40 border-sky-800/40' },
  amber:   { border: 'border-amber-800/50',   bg: 'bg-amber-950/20',   text: 'text-amber-400',   icon: 'bg-amber-950/40 border-amber-800/40' },
  violet:  { border: 'border-violet-800/50',  bg: 'bg-violet-950/20',  text: 'text-violet-400',  icon: 'bg-violet-950/40 border-violet-800/40' },
  rose:    { border: 'border-rose-800/50',    bg: 'bg-rose-950/20',    text: 'text-rose-400',    icon: 'bg-rose-950/40 border-rose-800/40' },
};

export function DynamicEconomy() {
  return (
    <>
      {/* ============================================================ */}
      {/* Section 1 — Prices move with demand                          */}
      {/* ============================================================ */}
      <section className="py-20">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid lg:grid-cols-2 gap-12 lg:gap-20 items-center">

            {/* Left — price chart */}
            <div className="order-2 lg:order-1">
              <div className="rounded-2xl border border-gray-800 bg-gray-900/70 overflow-hidden">
                {/* Chart header */}
                <div className="flex items-center justify-between px-4 py-3 border-b border-gray-800 bg-gray-900">
                  <div className="flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse-slow" />
                    <span className="text-xs font-mono text-gray-300 uppercase tracking-wider">Diamond — 24h</span>
                  </div>
                  <div className="flex items-center gap-1.5 text-xs font-mono text-emerald-400">
                    <TrendingUp className="w-3.5 h-3.5" />
                    <span>+4.2%</span>
                  </div>
                </div>

                {/* Price chart */}
                <div className="p-4">
                  <div className="flex items-end gap-2 mb-1">
                    <span className="text-2xl font-bold text-white font-mono">$547.50</span>
                    <span className="text-sm text-emerald-400 mb-0.5">↑ from $525.00</span>
                  </div>
                  <p className="text-xs text-gray-500 mb-4">Base price · buy/sell spread applied on top</p>

                  <div className="relative">
                    <svg viewBox="0 0 320 90" className="w-full h-28" preserveAspectRatio="none">
                      <defs>
                        <linearGradient id="priceAreaGrad" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="0%" stopColor="#10b981" stopOpacity="0.20" />
                          <stop offset="100%" stopColor="#10b981" stopOpacity="0" />
                        </linearGradient>
                      </defs>
                      {/* Baseline */}
                      <line x1="0" y1="70" x2="320" y2="70" stroke="#1f2937" strokeWidth="1" strokeDasharray="4 3" />
                      {/* Fill */}
                      <path d={PRICE_FILL} fill="url(#priceAreaGrad)" />
                      {/* Line */}
                      <path d={PRICE_PATH} fill="none" stroke="#10b981" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                      {/* Annotation dots */}
                      {ANNOTATIONS.map((a) => (
                        <circle key={a.x} cx={a.x} cy={a.x === 100 ? 28 : 38} r="3.5" fill={a.color} />
                      ))}
                    </svg>

                    {/* Annotation labels */}
                    <div className="flex justify-between mt-2 px-1">
                      {ANNOTATIONS.map((a) => (
                        <div key={a.x} className={`text-xs ${a.align === 'right' ? 'text-right' : 'text-left'}`}>
                          <p style={{ color: a.color }} className="font-semibold">{a.label}</p>
                          <p className="text-gray-600">{a.sub}</p>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>

                {/* Footer price row */}
                <div className="grid grid-cols-3 divide-x divide-gray-800 border-t border-gray-800 text-center">
                  {[
                    { label: 'Base', value: '$525.00', color: 'text-gray-300' },
                    { label: 'Buy (ask)', value: '$547.50', color: 'text-emerald-400' },
                    { label: 'Sell (bid)', value: '$498.75', color: 'text-rose-400' },
                  ].map(({ label, value, color }) => (
                    <div key={label} className="py-3 px-2">
                      <p className="text-xs text-gray-600 mb-0.5">{label}</p>
                      <p className={`text-sm font-mono font-semibold ${color}`}>{value}</p>
                    </div>
                  ))}
                </div>
              </div>

              {/* Micro ticks strip */}
              <div className="mt-3 flex items-center gap-2 text-xs text-gray-600 font-mono">
                <span className="text-gray-700">Recent ticks →</span>
                {['+0.8%', '+1.2%', '+0.9%', '+0.3%', '−0.1%', '−0.4%'].map((v, i) => (
                  <span
                    key={i}
                    className={`px-1.5 py-0.5 rounded border ${v.startsWith('+') ? 'border-emerald-900/60 text-emerald-600' : 'border-rose-900/60 text-rose-600'}`}
                  >
                    {v}
                  </span>
                ))}
              </div>
            </div>

            {/* Right — copy */}
            <div className="order-1 lg:order-2">
              <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">Dynamic Pricing</p>
              <h2 className="text-2xl sm:text-3xl font-bold text-white mb-5 leading-tight">
                Prices go up.<br />
                Prices go down.<br />
                <span className="text-gray-400">Just like a real economy.</span>
              </h2>

              <p className="text-gray-400 leading-relaxed mb-5">
                Every 5-minute tick, Auto-Tune looks at what was actually traded. If more players
                are buying than selling, the base price nudges upward. If sellers outnumber buyers,
                it drifts down. Prices are always moving toward where the market wants to be.
              </p>

              <p className="text-gray-400 leading-relaxed mb-6">
                A raid event sends Diamond prices soaring as everyone rushes to gear up. A few ticks
                later, the high price discourages buying and attracts players who had stockpiles to
                sell. The market self-corrects — no admin needed.
              </p>

              {/* Feedback loop */}
              <div className="rounded-xl border border-gray-800 bg-gray-900/50 p-4">
                <p className="text-xs text-gray-500 uppercase tracking-wider mb-3">The feedback loop</p>
                <div className="flex flex-col gap-1">
                  {[
                    { icon: <TrendingUp className="w-3 h-3" />, color: 'text-rose-400', bg: 'border-rose-900/50 bg-rose-950/20', text: 'High demand → price rises' },
                    { icon: <Minus className="w-3 h-3" />, color: 'text-amber-400', bg: 'border-amber-900/50 bg-amber-950/20', text: 'High price → buyers back off' },
                    { icon: <TrendingDown className="w-3 h-3" />, color: 'text-sky-400', bg: 'border-sky-900/50 bg-sky-950/20', text: 'Sellers enter → price softens' },
                    { icon: <Minus className="w-3 h-3" />, color: 'text-emerald-400', bg: 'border-emerald-900/50 bg-emerald-950/20', text: 'Balance restored → price stabilises' },
                  ].map(({ icon, color, bg, text }) => (
                    <div key={text} className={`flex items-center gap-2.5 px-3 py-2 rounded-lg border ${bg}`}>
                      <span className={color}>{icon}</span>
                      <span className="text-sm text-gray-300">{text}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ============================================================ */}
      {/* Section 2 — What it does for your server                     */}
      {/* ============================================================ */}
      <section className="py-20 bg-gray-900/30 border-y border-gray-800/40">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center max-w-2xl mx-auto mb-12">
            <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">Server Impact</p>
            <h2 className="text-2xl sm:text-3xl font-bold text-white mb-4">
              What a living economy does for your server
            </h2>
            <p className="text-gray-400 leading-relaxed">
              Static admin shops create flat, gameable economies. Auto-Tune turns your shop
              into a system that reacts, balances, and rewards smart play — automatically.
            </p>
          </div>

          <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4 mb-14">
            {BENEFITS.map((b) => {
              const a = accentClasses[b.accent];
              return (
                <div key={b.title} className={`rounded-xl border ${a.border} ${a.bg} p-5 hover:-translate-y-0.5 transition-transform`}>
                  <div className={`w-9 h-9 rounded-lg border ${a.icon} flex items-center justify-center text-lg mb-4`}>
                    {b.icon}
                  </div>
                  <h3 className={`text-sm font-semibold mb-2 ${a.text}`}>{b.title}</h3>
                  <p className="text-sm text-gray-400 leading-relaxed">{b.body}</p>
                </div>
              );
            })}
          </div>

          {/* Comparison table */}
          <div className="rounded-xl border border-gray-800 overflow-hidden">
            <div className="px-4 py-3 bg-gray-900 border-b border-gray-800">
              <p className="text-xs text-gray-500 uppercase tracking-wider">Static shop vs Auto-Tune — real scenarios</p>
            </div>
            <div className="divide-y divide-gray-800/60">
              {COMPARISONS.map((row) => (
                <div key={row.scenario} className="grid md:grid-cols-3 bg-gray-900/40 hover:bg-gray-800/20 transition-colors">
                  <div className="px-4 py-3 border-b md:border-b-0 md:border-r border-gray-800/60">
                    <p className="text-xs text-gray-400 font-medium">{row.scenario}</p>
                  </div>
                  <div className="px-4 py-3 border-b md:border-b-0 md:border-r border-gray-800/60 flex items-start gap-2">
                    <span className="mt-0.5 w-4 h-4 rounded-full bg-gray-800 border border-gray-700 flex items-center justify-center shrink-0">
                      <span className="text-gray-500 text-xs">✗</span>
                    </span>
                    <p className="text-xs text-gray-500">{row.static}</p>
                  </div>
                  <div className="px-4 py-3 flex items-start gap-2">
                    <span className="mt-0.5 w-4 h-4 rounded-full bg-emerald-950 border border-emerald-800 flex items-center justify-center shrink-0">
                      <span className="text-emerald-400 text-xs">✓</span>
                    </span>
                    <p className="text-xs text-emerald-300/80">{row.autotune}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="text-center mt-10">
            <Link
              href="/simulator"
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded-lg transition-colors shadow-lg shadow-emerald-600/20 text-sm"
            >
              See the numbers live
              <ArrowRight className="w-4 h-4" />
            </Link>
          </div>
        </div>
      </section>
    </>
  );
}
