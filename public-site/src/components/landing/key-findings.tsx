import Link from 'next/link';
import { ArrowRight, TrendingUp, ShieldCheck, Zap } from 'lucide-react';

const findings = [
  {
    icon: TrendingUp,
    tag: 'GDP +100%',
    tagColor: 'text-emerald-400 bg-emerald-950/60 border-emerald-800/50',
    title: 'MarketMakers nearly double GDP',
    detail: 'A single MarketMaker archetype (tight 2-sided liquidity provider) replacing one Hoarder in the standard economy produced 931K GDP vs 433K — a 100% increase. Iron Ingot prices went from −69% to +2% of base.',
    metric: { label: 'GDP improvement', value: '931K vs 433K', accent: 'text-emerald-400' },
    href: '/simulation-results',
  },
  {
    icon: ShieldCheck,
    tag: 'D/G 1.76×',
    tagColor: 'text-sky-400 bg-sky-950/60 border-sky-800/50',
    title: '5% GuildBuyer threshold is the safe default',
    detail: 'The old default of 15–30% causes catastrophic debt spirals (D/G 5–20×) because selective buying triggers massive credit. At 5%, buying is incremental — creating a natural price floor. 30-day data confirms 7% advantage disappears by 30d while D/G is +2.89× worse. D/G holds at 1.76× across 5 simulation seeds at 14d.',
    metric: { label: 'Recommended threshold', value: '5% (not 15–30%)', accent: 'text-sky-400' },
    href: '/simulation-results',
  },
  {
    icon: Zap,
    tag: 'Vol −40%',
    tagColor: 'text-amber-400 bg-amber-950/60 border-amber-800/50',
    title: 'GB+Newbie halves volatility',
    detail: 'Adding 2 Newbies to a 2MM+2GB economy cut volatility by 40.8% across 5 seeds — the biggest volatility reduction seen in any archetype combination. GDP cost was only −3.8%. Best for servers with multiple GuildBuyers.',
    metric: { label: '5-seed average', value: '17.3% → 10.3% CV', accent: 'text-amber-400' },
    href: '/simulation-results',
  },
];

export function KeyFindings() {
  return (
    <section className="py-20 border-t border-gray-800/40">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="mb-10">
          <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">Evidence</p>
          <h2 className="text-2xl sm:text-3xl font-bold text-white">
            What the simulation proved
          </h2>
          <p className="text-gray-400 text-sm mt-2 max-w-xl">
            These findings are from automated simulation runs — not guesswork. Each result is from
            14-day economy simulations with real archetype decision logic.
          </p>
        </div>

        <div className="grid md:grid-cols-3 gap-5">
          {findings.map((f) => {
            const Icon = f.icon;
            return (
              <div
                key={f.title}
                className="group bg-gray-900/60 border border-gray-800 rounded-xl p-5 hover:border-gray-700 transition-colors"
              >
                <div className="flex items-center gap-2 mb-3">
                  <div className="w-7 h-7 rounded-md bg-gray-800 border border-gray-700 flex items-center justify-center">
                    <Icon className="w-3.5 h-3.5 text-gray-400" />
                  </div>
                  <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-mono font-semibold border ${f.tagColor}`}>
                    {f.tag}
                  </span>
                </div>

                <h3 className="text-base font-semibold text-white mb-2 leading-snug">{f.title}</h3>
                <p className="text-sm text-gray-400 leading-relaxed mb-4">{f.detail}</p>

                <div className={`flex items-center gap-2 px-3 py-2 rounded-lg border border-gray-800 bg-gray-950/60 mb-4`}>
                  <span className="text-xs text-gray-500">{f.metric.label}:</span>
                  <span className={`text-xs font-mono font-bold ${f.metric.accent}`}>{f.metric.value}</span>
                </div>

                <Link
                  href={f.href}
                  className="inline-flex items-center gap-1.5 text-xs text-gray-500 hover:text-emerald-400 transition-colors"
                >
                  View simulation data <ArrowRight className="w-3 h-3" />
                </Link>
              </div>
            );
          })}
        </div>

        <div className="mt-8 text-center">
          <Link
            href="/simulation-results"
            className="inline-flex items-center gap-2 px-5 py-2.5 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded-lg transition-colors shadow-lg shadow-emerald-600/20 text-sm"
          >
            Explore all 22 simulation runs
          </Link>
        </div>
      </div>
    </section>
  );
}
