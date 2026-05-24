'use client';

import { Check, Minus, ArrowRight, Shield, Zap, Users, Globe, BarChart3 } from 'lucide-react';

const CATEGORIES = [
  {
    label: 'Pricing Engine',
    icon: BarChart3,
    items: [
      {
        label: 'Dynamic buy/sell prices based on real activity',
        autotune: true,
        essentials: false,
        shopgui: false,
        playershops: false,
        desc: 'Prices shift with supply and demand — not a static list admins set once.',
      },
      {
        label: 'Per-item price history and trends',
        autotune: true,
        essentials: false,
        shopgui: 'Limited — admin must manually update',
        playershops: false,
        desc: 'Players can see charts, admins can spot anomalies.',
      },
      {
        label: 'Configurable spread per item or globally',
        autotune: true,
        essentials: false,
        shopgui: 'Manual — no automatic equilibrium',
        playershops: false,
        desc: 'Tight spreads for common items, wider for rare — or vice versa.',
      },
      {
        label: 'Automatic market equilibrium pricing',
        autotune: true,
        essentials: false,
        shopgui: false,
        playershops: false,
        desc: 'Engine finds fair prices without admin intervention.',
      },
    ],
  },
  {
    label: 'Security & Integrity',
    icon: Shield,
    items: [
      {
        label: 'Exploit protection (duping, vault overflow)',
        autotune: true,
        essentials: false,
        shopgui: 'Partial — some protections need addons',
        playershops: false,
        desc: 'Anti-dump throttle, sell caps, cooldown for high-value items.',
      },
      {
        label: 'Per-player buy/sell limits',
        autotune: true,
        essentials: true,
        shopgui: true,
        playershops: 'Partial',
        desc: 'Prevent whales from dominating a market.',
      },
      {
        label: 'Loan circuit breakers (TIER1/2/3)',
        autotune: true,
        essentials: false,
        shopgui: false,
        playershops: false,
        desc: 'Debt-to-GDP ratio triggers automatic deleveraging. No manual rescue needed.',
      },
      {
        label: 'Transaction audit trail',
        autotune: true,
        essentials: false,
        shopgui: true,
        playershops: 'Partial',
        desc: 'Every trade, loan, and price change is recorded and queryable.',
      },
    ],
  },
  {
    label: 'Player Experience',
    icon: Users,
    items: [
      {
        label: 'Player-to-player auction house',
        autotune: true,
        essentials: false,
        shopgui: 'External plugin required',
        playershops: true,
        desc: 'Fully integrated auction matching engine — not a separate addon.',
      },
      {
        label: 'Price alerts (in-game + Discord)',
        autotune: true,
        essentials: false,
        shopgui: false,
        playershops: false,
        desc: 'Players set price thresholds; alerts fire when crossed.',
      },
      {
        label: 'Autosell on pickup (auto-sell hotbar)',
        autotune: true,
        essentials: 'Basic — sell only, no price management',
        shopgui: true,
        playershops: false,
        desc: 'Automatically sells picked-up items at current market prices.',
      },
      {
        label: 'Player portfolio + P&L tracking',
        autotune: true,
        essentials: false,
        shopgui: false,
        playershops: false,
        desc: 'Players see their trading history, net gain/loss, and position.',
      },
    ],
  },
  {
    label: 'Ecosystem & Scale',
    icon: Globe,
    items: [
      {
        label: 'Guild economy dashboard',
        autotune: true,
        essentials: false,
        shopgui: false,
        playershops: false,
        desc: 'Guilds get their own economic view — group debt, budgets, spending norms.',
      },
      {
        label: 'Market events (Gold Rush, Supply Glut)',
        autotune: true,
        essentials: false,
        shopgui: false,
        playershops: false,
        desc: 'Random events keep the market unpredictable and interesting.',
      },
      {
        label: 'Cross-server true price discovery',
        autotune: true,
        essentials: false,
        shopgui: false,
        playershops: false,
        desc: 'Opt-in network aggregates prices across servers for better starting points.',
      },
      {
        label: 'Bundled real-time web dashboard',
        autotune: true,
        essentials: false,
        shopgui: 'Paid addon',
        playershops: false,
        desc: 'Players see live prices and history without installing anything extra.',
      },
    ],
  },
];

type Val = boolean | string | null;

function Cell({ val }: { val: Val }) {
  if (val === true) {
    return (
      <div className="flex items-center justify-center">
        <Check className="w-5 h-5 text-emerald-500" />
      </div>
    );
  }
  if (val === false) {
    return (
      <div className="flex items-center justify-center">
        <Minus className="w-5 h-5 text-gray-700" />
      </div>
    );
  }
  return (
    <div className="flex items-center justify-center">
      <span className="text-[11px] text-amber-400 text-center leading-tight max-w-[70px]" title={val ?? undefined}>
        {val}
      </span>
    </div>
  );
}

function PricingTier({ name, price, features }: { name: string; price: string; features: string[] }) {
  return (
    <div className={`p-5 rounded-xl border ${name === 'Auto-Tune' ? 'border-emerald-600/50 bg-emerald-950/30' : 'border-gray-800 bg-gray-950/40'}`}>
      <div className="mb-4">
        <p className={`text-sm font-bold ${name === 'Auto-Tune' ? 'text-emerald-400' : 'text-gray-400'}`}>{name}</p>
        <p className="text-2xl font-bold text-white mt-1">{price}</p>
      </div>
      <ul className="space-y-2">
        {features.map((f) => (
          <li key={f} className="flex items-start gap-2 text-xs text-gray-400">
            <Check className="w-3.5 h-3.5 text-emerald-500 mt-0.5 shrink-0" />
            {f}
          </li>
        ))}
      </ul>
    </div>
  );
}

export default function ComparePage() {
  return (
    <div className="min-h-screen bg-background text-foreground">
      {/* Header */}
      <header className="border-b border-zinc-800 bg-zinc-950">
        <div className="mx-auto max-w-7xl px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="h-8 w-8 rounded-lg bg-emerald-600 flex items-center justify-center">
              <BarChart3 className="h-4 w-4 text-white" />
            </div>
            <div>
              <h1 className="text-sm font-bold text-white">Auto-Tune vs Alternatives</h1>
              <p className="text-xs text-zinc-400">Honest feature comparison for server admins</p>
            </div>
          </div>
          <div className="flex items-center gap-3 text-xs text-zinc-400">
            <a href="/simulator" className="hover:text-emerald-400 transition-colors">Simulator</a>
            <span>·</span>
            <a href="/how-it-works" className="hover:text-emerald-400 transition-colors">How It Works</a>
            <span>·</span>
            <a href="/install" className="hover:text-emerald-400 transition-colors">Install</a>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-6 py-10 space-y-12">
        {/* Intro */}
        <div className="text-center max-w-3xl mx-auto">
          <h2 className="text-2xl sm:text-3xl font-bold text-white mb-4">
            Most Minecraft economy plugins use static prices.
            <br />
            <span className="text-emerald-400">Auto-Tune doesn't.</span>
          </h2>
          <p className="text-gray-400 text-sm leading-relaxed">
            Once players learn the prices, they game the system. Auto-Tune adapts — prices shift with real supply and demand, keeping the market interesting for everyone. Here's how it compares to the alternatives.
          </p>
          <div className="flex items-center justify-center gap-4 mt-6">
            <a
              href="/install"
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded-lg transition-colors text-sm"
            >
              Install Auto-Tune <ArrowRight className="w-4 h-4" />
            </a>
            <a
              href="/how-it-works"
              className="inline-flex items-center gap-2 px-5 py-2.5 border border-zinc-700 hover:border-zinc-600 text-gray-300 font-semibold rounded-lg transition-colors text-sm"
            >
              How it works
            </a>
          </div>
        </div>

        {/* Comparison table */}
        <div>
          <h3 className="text-lg font-semibold text-white mb-6">Feature Comparison</h3>
          <div className="rounded-xl border border-zinc-800 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-zinc-800 bg-zinc-900/80">
                  <th className="py-4 px-5 text-left font-semibold text-white w-1/2">Feature</th>
                  <th className="py-4 px-4 text-center font-semibold text-emerald-400 w-[12.5%]">Auto-Tune</th>
                  <th className="py-4 px-4 text-center font-medium text-gray-500 w-[12.5%]">EssentialsX</th>
                  <th className="py-4 px-4 text-center font-medium text-gray-500 w-[12.5%]">ShopGUI+</th>
                  <th className="py-4 px-4 text-center font-medium text-gray-500 w-[12.5%]">PlayerShops</th>
                </tr>
              </thead>
              <tbody>
                {CATEGORIES.map((cat) => (
                  <>
                    <tr key={cat.label} className="border-b border-zinc-800 bg-zinc-900/40">
                      <td colSpan={5} className="px-5 py-3">
                        <div className="flex items-center gap-2">
                          <cat.icon className="w-4 h-4 text-zinc-500" />
                          <span className="text-xs font-semibold text-zinc-400 uppercase tracking-wider">{cat.label}</span>
                        </div>
                      </td>
                    </tr>
                    {cat.items.map((item, i) => (
                      <tr key={`${cat.label}-${i}`} className="border-b border-zinc-800/50 hover:bg-zinc-800/20 transition-colors">
                        <td className="py-3.5 px-5">
                          <p className="text-gray-200 text-sm font-medium">{item.label}</p>
                          <p className="text-xs text-gray-500 mt-0.5">{item.desc}</p>
                        </td>
                        <td className="py-3.5 px-4"><Cell val={item.autotune} /></td>
                        <td className="py-3.5 px-4"><Cell val={item.essentials} /></td>
                        <td className="py-3.5 px-4"><Cell val={item.shopgui} /></td>
                        <td className="py-3.5 px-4"><Cell val={item.playershops} /></td>
                      </tr>
                    ))}
                  </>
                ))}
              </tbody>
            </table>
          </div>

          {/* Legend */}
          <div className="flex flex-wrap items-center gap-6 mt-4 text-xs text-gray-500">
            <span className="flex items-center gap-1.5">
              <Check className="w-3.5 h-3.5 text-emerald-500" /> Full support
            </span>
            <span className="flex items-center gap-1.5">
              <Minus className="w-3.5 h-3.5 text-gray-700" /> Not available
            </span>
            <span className="flex items-center gap-1.5">
              <span className="text-[10px] text-amber-400 border border-amber-800/50 px-1.5 py-0.5 rounded">text</span>
              Partial or limited
            </span>
          </div>
        </div>

        {/* Pricing comparison */}
        <div>
          <h3 className="text-lg font-semibold text-white mb-6">Cost Comparison</h3>
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            <PricingTier
              name="Auto-Tune"
              price="Free / Open Source"
              features={[
                'Full dynamic pricing',
                'Auction house',
                'Loan circuit breakers',
                'Web dashboard',
                'Anti-dump protection',
              ]}
            />
            <PricingTier
              name="EssentialsX"
              price="Free (core)"
              features={[
                'Static shop prices',
                'Basic autosell',
                'No market engine',
                'No auction house',
                'Addons cost extra',
              ]}
            />
            <PricingTier
              name="ShopGUI+"
              price="~$10 USD"
              features={[
                'Per-item shop GUI',
                'Limited price updates',
                'No dynamic pricing',
                'Auction requires separate plugin',
                'Web dashboard is paid addon',
              ]}
            />
            <PricingTier
              name="PlayerShops"
              price="~$8 USD"
              features={[
                'Player-operated shops',
                'No automatic pricing',
                'No auction house',
                'Price gouging possible',
                'No circuit breakers',
              ]}
            />
          </div>
        </div>

        {/* Why Auto-Tune stands out */}
        <div className="p-6 rounded-xl border border-emerald-900/40 bg-emerald-950/20">
          <h3 className="text-base font-semibold text-white mb-4">What Auto-Tune does that nothing else does</h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {[
              {
                icon: Zap,
                title: 'Dynamic equilibrium',
                desc: 'Prices find fair market values automatically — no admin guessing. The engine computes equilibrium from actual trade activity.',
              },
              {
                icon: Shield,
                title: 'Whale anti-dump',
                desc: 'Players who try to flood the market hit sell caps and spread shocks. The economy stays interesting even against motivated exploiters.',
              },
              {
                icon: Globe,
                title: 'Cross-server prices',
                desc: 'Opt-in network means new servers start with real price data instead of guessing. Better for everyone.',
              },
            ].map(({ icon: Icon, title, desc }) => (
              <div key={title} className="p-4 rounded-lg bg-zinc-900/50 border border-zinc-800">
                <div className="flex items-center gap-2 mb-2">
                  <Icon className="w-4 h-4 text-emerald-500" />
                  <p className="text-sm font-semibold text-gray-200">{title}</p>
                </div>
                <p className="text-xs text-gray-400 leading-relaxed">{desc}</p>
              </div>
            ))}
          </div>
        </div>

        {/* Final CTA */}
        <div className="text-center pb-8">
          <p className="text-gray-400 text-sm mb-4">
            Install Auto-Tune in under 5 minutes. No database setup required.
          </p>
          <a
            href="/install"
            className="inline-flex items-center gap-2 px-6 py-3 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded-lg transition-colors shadow-lg shadow-emerald-600/20 text-sm"
          >
            Get started — it's free <ArrowRight className="w-4 h-4" />
          </a>
        </div>
      </main>
    </div>
  );
}