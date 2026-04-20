'use client';

import Link from 'next/link';
import { FlaskConical, ArrowRight, TrendingUp, TrendingDown, Activity, Shield, Users } from 'lucide-react';

/* ------------------------------------------------------------------ */
/* Simulation-verified results banner — "proved, not guessed"              */
/* ------------------------------------------------------------------ */

const METRICS = [
  {
    icon: TrendingUp,
    label: 'GDP doubles',
    value: '+101%',
    detail: 'with 2MM+2GB+floor vs 1MM+GB baseline',
    color: 'text-emerald-400',
    bg: 'bg-emerald-950/40 border-emerald-800/40',
  },
  {
    icon: Activity,
    label: 'Volatility halves',
    value: '−48%',
    detail: 'Coefficient of variation, 5-seed average',
    color: 'text-sky-400',
    bg: 'bg-sky-950/40 border-sky-800/40',
  },
  {
    icon: TrendingDown,
    label: 'Spreads compress',
    value: '−22%',
    detail: 'Buy/sell spread, tighter liquidity',
    color: 'text-amber-400',
    bg: 'bg-amber-950/40 border-amber-800/40',
  },
  {
    icon: Shield,
    label: 'Circuit breaker',
    value: 'TIER3 fires',
    detail: 'only at D/G > 15× — not in normal operation',
    color: 'text-rose-400',
    bg: 'bg-rose-950/40 border-rose-800/40',
  },
];

export function SimResultsBanner() {
  return (
    <section className="border-b border-gray-800/50 bg-gray-900/40">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Header */}
        <div className="flex items-start justify-between gap-4 mb-5">
          <div className="flex items-center gap-2">
            <FlaskConical className="w-4 h-4 text-sky-400" />
            <p className="text-xs text-sky-400 uppercase tracking-widest font-semibold">Simulation Lab</p>
          </div>
          <Link
            href="/findings"
            className="inline-flex items-center gap-1.5 text-xs text-sky-400 hover:text-sky-300 transition-colors shrink-0"
          >
            22 findings · 80+ runs
            <ArrowRight className="w-3 h-3" />
          </Link>
        </div>

        {/* Key results */}
        <div className="mb-4">
          <p className="text-sm font-semibold text-white mb-0.5">
            The recommended config — verified across 5 seeds × 14–60 days
          </p>
          <p className="text-xs text-gray-400">
            2 MarketMakers + 2 GuildBuyers @ 5% threshold + 60% Diamond floor + counter-cyclical interest.
            Results from automated simulation, not guesswork.
          </p>
        </div>

        {/* Metric chips */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-5">
          {METRICS.map(({ icon: Icon, label, value, detail, color, bg }) => (
            <div key={label} className={`rounded-xl border ${bg} p-3`}>
              <div className="flex items-center gap-1.5 mb-1.5">
                <Icon className={`w-3.5 h-3.5 ${color}`} />
                <span className="text-[10px] text-gray-500 uppercase tracking-wide">{label}</span>
              </div>
              <p className={`text-xl font-bold font-mono ${color}`}>{value}</p>
              <p className="text-[10px] text-gray-500 mt-0.5 leading-relaxed">{detail}</p>
            </div>
          ))}
        </div>

        {/* Finding footnote */}
        <div className="flex flex-wrap items-center gap-3 text-xs text-gray-500">
          <span>Per:</span>
          {[
            { href: '/findings#archetypes', label: 'Archetype decisions' },
            { href: '/findings#config', label: 'Config decisions' },
            { href: '/findings#behavior', label: 'Economy behavior' },
          ].map(({ href, label }) => (
            <Link
              key={href}
              href={href}
              className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-gray-800 border border-gray-700 text-gray-400 hover:text-white hover:border-gray-600 transition-colors"
            >
              <Users className="w-3 h-3" />
              {label}
            </Link>
          ))}
          <span className="ml-auto text-amber-400/80">
            ⚠️ 60d stability: see findings before deploying on servers running 30+ days
          </span>
        </div>
      </div>
    </section>
  );
}
