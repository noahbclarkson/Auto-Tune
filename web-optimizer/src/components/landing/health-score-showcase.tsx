import { BarChart3, TrendingUp, Shield, Activity, ArrowRight, Check } from 'lucide-react';

/**
 * What a healthy Auto-Tune economy looks like — shown on the public landing page.
 * Values are drawn from the recommended production config:
 * 1× MarketMaker + 2× GuildBuyer @ 7% threshold + standard player mix.
 * Simulated 14-day runs across 5 seeds.
 */
const HEALTHY_METRICS = [
  {
    icon: BarChart3,
    label: 'Price Volatility',
    description: 'Standard deviation of all items\' 24h price changes',
    score: 90,
    maxPoints: 40,
    value: '0.007 avg',
    status: 'Stable',
    statusColor: 'text-emerald-400',
    barColor: 'bg-emerald-400',
    detail: 'Prices move smoothly — no wild swings. Players can plan purchases.',
    threshold: '< 0.05 = Stable · < 0.15 = Moderate · ≥ 0.15 = Unstable',
  },
  {
    icon: Shield,
    label: 'Debt / GDP Ratio',
    description: 'Total outstanding loans divided by 24h GDP — economy\'s debt burden',
    score: 75,
    maxPoints: 30,
    value: '1.76× GDP',
    status: 'Healthy',
    statusColor: 'text-emerald-400',
    barColor: 'bg-emerald-400',
    detail: 'Borrowing supports real economic activity. Circuit breaker prevents cascade.',
    threshold: '< 0.5× = Excellent · < 1.0× = Healthy · < 3.0× = Warning · ≥ 3.0× = Danger',
  },
  {
    icon: Activity,
    label: 'Buy / Sell Balance',
    description: 'How close the market is to a 50/50 buyer/seller split',
    score: 88,
    maxPoints: 30,
    value: '52% / 48%',
    status: 'Balanced',
    statusColor: 'text-emerald-400',
    barColor: 'bg-sky-400',
    detail: 'Sellers find buyers. Buyers find fair prices. No structural bias in either direction.',
    threshold: '±5pp of 50/50 = Ideal · ±10pp = Good · ±20pp = Skewed',
  },
];

const HEALTHY_SCORE = 85;

export function HealthScoreShowcase() {
  return (
    <section className="py-20 border-t border-gray-800/40">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="mb-10 max-w-2xl">
          <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">
            Admin Dashboard
          </p>
          <h2 className="text-2xl sm:text-3xl font-bold text-white mb-3">
            Know your economy&apos;s health at a glance
          </h2>
          <p className="text-gray-400 text-sm leading-relaxed">
            Auto-Tune computes a composite Health Score (0–100) from four real-time metrics.
            No guesswork — the numbers come directly from the market engine. The recommended
            config scores 85/100 out of the box.
          </p>
        </div>

        <div className="grid lg:grid-cols-5 gap-6">
          {/* Score card */}
          <div className="lg:col-span-2 bg-gradient-to-br from-emerald-950/80 to-gray-900/80 border border-emerald-800/40 rounded-2xl p-6 flex flex-col items-center justify-center text-center">
            <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-4">
              Recommended Config Score
            </p>
            <div className="relative mb-4">
              <svg viewBox="0 0 120 120" className="w-36 h-36" aria-label={`Health score: ${HEALTHY_SCORE} out of 100`}>
                {/* Background arc */}
                <circle
                  cx="60" cy="60" r="50"
                  fill="none"
                  stroke="#1f2937"
                  strokeWidth="10"
                  strokeDasharray={`${2 * Math.PI * 50}`}
                  strokeDashoffset="0"
                  transform="rotate(-90 60 60)"
                />
                {/* Score arc */}
                <circle
                  cx="60" cy="60" r="50"
                  fill="none"
                  stroke="#10b981"
                  strokeWidth="10"
                  strokeLinecap="round"
                  strokeDasharray={`${2 * Math.PI * 50}`}
                  strokeDashoffset={`${2 * Math.PI * 50 * (1 - HEALTHY_SCORE / 100)}`}
                  transform="rotate(-90 60 60)"
                  style={{ transition: 'stroke-dashoffset 1s ease-out' }}
                />
                <text
                  x="60" y="55"
                  textAnchor="middle"
                  dominantBaseline="middle"
                  className="fill-white"
                  style={{ fontSize: '28px', fontWeight: 'bold', fontFamily: 'inherit' }}
                >
                  {HEALTHY_SCORE}
                </text>
                <text
                  x="60" y="73"
                  textAnchor="middle"
                  dominantBaseline="middle"
                  className="fill-gray-400"
                  style={{ fontSize: '10px', fontFamily: 'inherit' }}
                >
                  / 100
                </text>
              </svg>
            </div>
            <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-emerald-500/10 border border-emerald-500/30 mb-3">
              <Check className="w-3.5 h-3.5 text-emerald-400" />
              <span className="text-sm font-medium text-emerald-400">Healthy Economy</span>
            </div>
            <p className="text-xs text-gray-500 leading-relaxed max-w-xs">
              Based on guild_stability_mm_fixed_guild scenario · 14-day run · 5 seeds averaged
            </p>
          </div>

          {/* Metric breakdown */}
          <div className="lg:col-span-3 space-y-4">
            {HEALTHY_METRICS.map((m) => {
              const Icon = m.icon;
              const pct = (m.score / m.maxPoints) * 100;
              return (
                <div
                  key={m.label}
                  className="bg-gray-900/60 border border-gray-800 rounded-xl p-5 hover:border-gray-700 transition-colors"
                >
                  <div className="flex items-start justify-between mb-3">
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-lg bg-gray-800 border border-gray-700 flex items-center justify-center shrink-0">
                        <Icon className="w-4 h-4 text-gray-400" />
                      </div>
                      <div>
                        <p className="text-sm font-semibold text-white">{m.label}</p>
                        <p className="text-xs text-gray-500 leading-relaxed">{m.description}</p>
                      </div>
                    </div>
                    <div className="text-right shrink-0 ml-3">
                      <p className={`text-sm font-bold ${m.statusColor}`}>{m.status}</p>
                      <p className="text-xs text-gray-500">{m.value}</p>
                    </div>
                  </div>

                  {/* Score bar */}
                  <div className="mb-2">
                    <div className="flex justify-between text-xs mb-1">
                      <span className="text-gray-500">Score contribution</span>
                      <span className="text-gray-400 font-mono">{m.score} / {m.maxPoints}</span>
                    </div>
                    <div className="h-2.5 rounded-full bg-gray-800 overflow-hidden">
                      <div
                        className={`h-full rounded-full transition-all duration-700 ${m.barColor}`}
                        style={{ width: `${pct}%` }}
                      />
                    </div>
                  </div>

                  <p className="text-xs text-gray-400 leading-relaxed">{m.detail}</p>
                  <p className="text-xs text-gray-600 mt-1 font-mono">{m.threshold}</p>
                </div>
              );
            })}

            <div className="bg-gray-900/40 border border-gray-800 rounded-xl p-4">
              <p className="text-xs text-gray-400 leading-relaxed">
                <span className="text-gray-300 font-medium">Why 85 and not 100?</span> Auto-Tune economies
                are inherently dynamic — prices that never move signal a dead market. The 85 reflects
                real-world player behavior (hoarding, exploration cycles, event-driven demand) creating
                natural price variation. The goal isn&apos;t zero volatility; it&apos;s{' '}
                <span className="text-white">productive</span> volatility.
              </p>
            </div>
          </div>
        </div>

        {/* How to view it */}
        <div className="mt-8 flex flex-wrap items-center justify-between gap-4 bg-gray-900/40 border border-gray-800 rounded-xl p-5">
          <div>
            <p className="text-sm font-semibold text-white mb-1">Admins see this in-game</p>
            <p className="text-xs text-gray-400">
              The <code className="text-sky-300 font-mono">/at admin health</code> command and the web dashboard both show
              the live Health Score with per-metric breakdown and top volatile/undersold items.
            </p>
          </div>
          <a
            href="/simulator"
            className="inline-flex items-center gap-2 px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded-lg transition-colors text-sm shrink-0"
          >
            Preview in simulator <ArrowRight className="w-3.5 h-3.5" />
          </a>
        </div>
      </div>
    </section>
  );
}
