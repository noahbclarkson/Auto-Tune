import Link from 'next/link';
import { ArrowRight, Users, Gavel, Bell, ShieldCheck, Wifi, Cpu, Zap } from 'lucide-react';

const accentMap = {
  emerald: {
    tag: 'text-emerald-400 bg-emerald-950/60 border-emerald-800/50',
    code: 'text-emerald-300',
    bar: 'bg-emerald-600/20 border-emerald-600/30',
    stat: 'text-emerald-400',
  },
  sky: {
    tag: 'text-sky-400 bg-sky-950/60 border-sky-800/50',
    code: 'text-sky-300',
    bar: 'bg-sky-600/20 border-sky-600/30',
    stat: 'text-sky-400',
  },
  amber: {
    tag: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
    code: 'text-amber-300',
    bar: 'bg-amber-600/20 border-amber-600/30',
    stat: 'text-amber-400',
  },
  violet: {
    tag: 'text-violet-400 bg-violet-950/60 border-violet-800/50',
    code: 'text-violet-300',
    bar: 'bg-violet-600/20 border-violet-600/30',
    stat: 'text-violet-400',
  },
  rose: {
    tag: 'text-rose-400 bg-rose-950/60 border-rose-800/50',
    code: 'text-rose-300',
    bar: 'bg-rose-600/20 border-rose-600/30',
    stat: 'text-rose-400',
  },
} as const;

type AccentKey = keyof typeof accentMap;

const features: { tag: string; title: string; description: string; stat: { label: string; value: string }; code: string; accent: AccentKey; icon: React.ElementType }[] = [
  {
    tag: 'PRICING',
    title: 'Supply & Demand Pricing',
    description:
      'Trade ratios drive prices each tick. A market where 70% of trades are buys sees prices rise ~1.05% per tick at 10 online players. Prices converge to equilibrium automatically.',
    stat: { label: 'Max change / tick', value: '1.5%' },
    code: 'change = tradeRatio × playerScaling × maxChange%',
    accent: 'emerald',
    icon: ArrowRight,
  },
  {
    tag: 'SPREADS',
    title: 'Dynamic Bid–Ask Spreads',
    description:
      'Five factors compound to set the spread each tick: base spread, buy/sell imbalance, per-item liquidity (trader-scaled), player count, and global market z-score.',
    stat: { label: 'Spread factors', value: '5 layers' },
    code: 'bpd = halfSpread × imbalance × liq × players × vol',
    accent: 'sky',
    icon: ArrowRight,
  },
  {
    tag: 'GUILDS',
    title: 'Guild Economy Tracking',
    description:
      'Auto-Tune reads your Vault permission groups and tracks per-guild trading volume, net position, and debt. Players see their guild rank with /guild top.',
    stat: { label: 'Guild commands', value: '/guild top' },
    code: '/guild · /guild stats · /guild top',
    accent: 'violet',
    icon: Users,
  },
  {
    tag: 'AUCTION',
    title: 'Auction House',
    description:
      'Players post sell orders at their price, with a configurable duration. Buyers browse, purchase, and receive items instantly. Expired orders are reclaimed automatically.',
    stat: { label: 'Order expiry', value: 'configurable' },
    code: '/auction · /auction post · /auction reclaim',
    accent: 'amber',
    icon: Gavel,
  },
  {
    tag: 'ALERTS',
    title: 'Price Alerts',
    description:
      'Players subscribe to items and receive notifications when prices cross their thresholds. Admins can configure alert channels — Discord webhook, in-game, or both.',
    stat: { label: 'Discord + in-game', value: 'webhook ready' },
    code: '/autotune alert add <item> <threshold>',
    accent: 'rose',
    icon: Bell,
  },
  {
    tag: 'SETUP',
    title: 'Server Setup Wizard',
    description:
      'Not sure where to start? The guided setup wizard helps you choose archetype mix, server size, and loan settings, then exports a ready-to-use config.yml.',
    stat: { label: '5-step wizard', value: 'zero config needed' },
    code: '/setup · archetype picker · YAML export',
    accent: 'emerald',
    icon: Zap,
  },
  {
    tag: 'AI OPS',
    title: 'Admin Intelligence',
    description:
      'Built-in economic advisor analyzes your economy in real-time and tells you exactly what to do. /at admin advice surfaces debt ratios, volatility, and suggests circuit breakers, market events, or config changes.',
    stat: { label: 'Advice engine', value: 'rule + sim grounded' },
    code: '/at admin advice · /at admin history · /at admin recovery',
    accent: 'sky',
    icon: Cpu,
  },
];

export function FeatureCards() {
  return (
    <section className="py-20">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-end justify-between mb-10">
          <div>
            <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">Everything Included</p>
            <h2 className="text-2xl sm:text-3xl font-bold text-white">
              More than just pricing
            </h2>
            <p className="text-gray-400 text-sm mt-1.5 max-w-lg">
              A complete economy platform: dynamic spreads, guild tracking, auction house, loan circuit breakers, cross-server price discovery, and real-time dashboards.
            </p>
          </div>
          <Link
            href="/how-it-works"
            className="hidden sm:flex items-center gap-1.5 text-sm text-gray-400 hover:text-emerald-400 transition-colors"
          >
            Algorithm docs <ArrowRight className="w-3.5 h-3.5" />
          </Link>
        </div>

        <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-5">
          {features.map((f) => {
            const a = accentMap[f.accent];
            const Icon = f.icon;
            return (
              <div
                key={f.title}
                className="group bg-gray-900/70 border border-gray-800 rounded-xl p-5 hover:border-gray-700 transition-all hover:-translate-y-0.5"
              >
                {/* Tag + icon */}
                <div className="flex items-center justify-between mb-4">
                  <div className={`inline-flex items-center px-2 py-0.5 rounded-full border text-xs font-mono font-medium ${a.tag}`}>
                    {f.tag}
                  </div>
                  <div className={`w-7 h-7 rounded-md border flex items-center justify-center ${a.bar}`}>
                    <Icon className={`w-3.5 h-3.5 ${a.stat}`} />
                  </div>
                </div>

                <h3 className="text-base font-semibold text-white mb-2">{f.title}</h3>
                <p className="text-sm text-gray-400 leading-relaxed mb-4">{f.description}</p>

                {/* Stat */}
                <div className={`flex items-center gap-3 px-3 py-2 rounded-lg border ${a.bar} mb-3`}>
                  <span className={`text-lg font-bold font-mono ${a.stat}`}>{f.stat.value}</span>
                  <span className="text-xs text-gray-500">{f.stat.label}</span>
                </div>

                {/* Command / code */}
                <div className="bg-gray-950/80 rounded-md px-3 py-2 border border-gray-800">
                  <code className={`text-xs font-mono break-all leading-relaxed ${a.code}`}>{f.code}</code>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
