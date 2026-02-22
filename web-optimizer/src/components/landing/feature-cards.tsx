import Link from 'next/link';
import { ArrowRight } from 'lucide-react';

const features = [
  {
    tag: 'PRICING',
    title: 'Supply & Demand Pricing',
    description:
      'Trade ratios drive prices each tick. A market where 70% of trades are buys sees prices rise ~1.05% per tick at 10 online players. Prices converge to equilibrium automatically.',
    stat: { label: 'Max change / tick', value: '1.5%' },
    code: 'change = tradeRatio × playerScaling × maxChange%',
    accent: 'emerald',
  },
  {
    tag: 'SPREADS',
    title: 'Dynamic Bid–Ask Spreads',
    description:
      'Five factors compound to set the spread each tick: base spread, buy/sell imbalance, per-item liquidity (trader-scaled), player count, and global market z-score.',
    stat: { label: 'Spread factors', value: '5 layers' },
    code: 'bpd = halfSpread × imbalance × liq × players × vol',
    accent: 'sky',
  },
  {
    tag: 'STABILITY',
    title: 'Trend Dampening & Floors',
    description:
      'Consecutive-tick streaks trigger dampening: 1 / (1 + streak × 0.05), with a 25% floor. Sell pressure multiplier and a $0.01 price floor prevent economic death spirals.',
    stat: { label: 'Min dampening floor', value: '25%' },
    code: 'dampening = max(1/(1+streak×d), floor)',
    accent: 'amber',
  },
];

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
} as const;

export function FeatureCards() {
  return (
    <section className="py-20">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-end justify-between mb-10">
          <div>
            <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">Engine Overview</p>
            <h2 className="text-2xl sm:text-3xl font-bold text-white">
              Three systems, one economy
            </h2>
          </div>
          <Link
            href="/how-it-works"
            className="hidden sm:flex items-center gap-1.5 text-sm text-gray-400 hover:text-emerald-400 transition-colors"
          >
            Full breakdown <ArrowRight className="w-3.5 h-3.5" />
          </Link>
        </div>

        <div className="grid md:grid-cols-3 gap-5">
          {features.map((f) => {
            const a = accentMap[f.accent];
            return (
              <div
                key={f.title}
                className="group bg-gray-900/70 border border-gray-800 rounded-xl p-5 hover:border-gray-700 transition-all hover:-translate-y-0.5"
              >
                {/* Tag */}
                <div className={`inline-flex items-center px-2 py-0.5 rounded-full border text-xs font-mono font-medium mb-4 ${a.tag}`}>
                  {f.tag}
                </div>

                <h3 className="text-base font-semibold text-white mb-2">{f.title}</h3>
                <p className="text-sm text-gray-400 leading-relaxed mb-4">{f.description}</p>

                {/* Stat */}
                <div className={`flex items-center gap-3 px-3 py-2 rounded-lg border ${a.bar} mb-4`}>
                  <span className={`text-xl font-bold font-mono ${a.stat}`}>{f.stat.value}</span>
                  <span className="text-xs text-gray-500">{f.stat.label}</span>
                </div>

                {/* Formula */}
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
