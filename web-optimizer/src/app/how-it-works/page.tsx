import { Metadata } from 'next';
import { ArrowRight, Calculator } from 'lucide-react';
import Link from 'next/link';
import { SpreadCalculator } from '@/components/landing/spread-calculator';
import { SpreadSimulator } from '@/components/landing/spread-simulator';
import { SpreadFlowDiagram } from '@/components/landing/spread-flow-diagram';

export const metadata: Metadata = {
  title: 'How It Works | Auto-Tune',
  description:
    'Understand the math behind Auto-Tune\'s supply-and-demand pricing engine. Asymmetric spreads, player scaling, trend dampening, and sector correlation explained.',
  openGraph: {
    title: 'How It Works | Auto-Tune',
    description: "Understand the math behind Auto-Tune's supply-and-demand pricing engine.",
    images: [{ url: '/og-image.png', width: 1200, height: 630, alt: "How Auto-Tune's market engine works" }],
  },
  twitter: {
    card: 'summary_large_image',
    images: ['/og-image.png'],
  },
};

const steps = [
  {
    num: '01',
    tag: 'FOUNDATION',
    title: 'Base Spread',
    formula: 'halfSpread = baseSpread / 2',
    summary: 'Every item starts with a symmetric half-spread on each side. Default is 20%, so each side starts at 10%.',
    detail: 'This guarantees the server a baseline margin, preventing zero-cost arbitrage. The spread is the "canvas" the other factors paint onto.',
    accent: 'emerald',
  },
  {
    num: '02',
    tag: 'DEMAND',
    title: 'Buy/Sell Imbalance',
    formula: 'imbalance = (buyRatio − 0.5) × 2   ∈ [−1, +1]',
    summary: 'If 80% of trades are buys, imbalance is +0.6. BPD is widened and SPD is narrowed, raising buy cost and improving sell value.',
    detail: 'This is the core supply-and-demand signal. A positive imbalance penalises buyers and rewards sellers, nudging the market back toward 50/50. volumeImpact controls how strongly imbalance shifts the spread.',
    accent: 'sky',
  },
  {
    num: '03',
    tag: 'LIQUIDITY',
    title: 'Per-Item Liquidity',
    formula: 'liq = 1 / (1 + volume × (coeff/fullTraders) × clampedTraders)',
    summary: 'Items with high weighted volume and many distinct traders get tighter spreads. A heavily traded market becomes more efficient.',
    detail: 'The coefficient scales with unique trader count (capped at fullEffectTraders), so diversity of participants matters — not just raw volume. This prevents one player spamming trades to game the spread.',
    accent: 'violet',
  },
  {
    num: '04',
    tag: 'POPULATION',
    title: 'Player Count Scaling',
    formula: 'ps = tanh(n × atanh(0.99) / fullEffectPlayers)',
    summary: 'More players means tighter spreads. A server with 10+ players gets nearly the full benefit. A server with 1 player has minimal effect.',
    detail: 'The tanh curve provides smooth, bounded scaling. At fullEffectPlayers (default 10), the scaling reaches 99% — adding more players beyond that has diminishing returns, preventing exploitation on large servers.',
    accent: 'amber',
  },
  {
    num: '05',
    tag: 'MARKET ACTIVITY',
    title: 'Global Volume Multiplier',
    formula: 'z > +1: mult = 1.0 − 0.2t  →  0.8 (tighter)\nz < −1: mult = 1.0 + 0.3t  →  1.3 (wider)',
    summary: 'The z-score of the most recent volume bucket vs. the mean drives a small spread adjustment. High activity → 80% of normal. Low activity → 130% of normal.',
    detail: 'This uses 10 equally-sized time buckets within the trade window. The most recent bucket is compared statistically to the others. A busy market (z > +2) tightens spreads to 80%; a quiet market (z < −2) widens them to 130%. This is intentionally subtle — the range is 0.8×–1.3×, not 0.5×–2.0×.',
    accent: 'rose',
  },
  {
    num: '06',
    tag: 'PRICE MOVEMENT',
    title: 'Price Change per Tick',
    formula: 'change% = tradeRatio × playerScaling × maxChange%',
    summary: 'Each tick (5 min), the base price shifts based on trade imbalance and player count. Sell pressure and trend dampening moderate wild swings.',
    detail: 'tradeRatio ∈ [−1, +1] where +1 = all buys. With 10 players and 1.5% max change, a 70% buy ratio causes +0.9% per tick. Consecutive-direction streaks trigger dampening (1/(1+streak×0.05), floor 25%).',
    accent: 'emerald',
  },
];

const accentBorder: Record<string, string> = {
  emerald: 'border-emerald-800/60 bg-emerald-950/20',
  sky:     'border-sky-800/60 bg-sky-950/20',
  violet:  'border-violet-800/60 bg-violet-950/20',
  amber:   'border-amber-800/60 bg-amber-950/20',
  rose:    'border-rose-800/60 bg-rose-950/20',
};

const accentText: Record<string, string> = {
  emerald: 'text-emerald-400',
  sky:     'text-sky-400',
  violet:  'text-violet-400',
  amber:   'text-amber-400',
  rose:    'text-rose-400',
};

const accentNum: Record<string, string> = {
  emerald: 'text-emerald-600',
  sky:     'text-sky-600',
  violet:  'text-violet-600',
  amber:   'text-amber-600',
  rose:    'text-rose-600',
};

export default function HowItWorks() {
  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      {/* Header */}
      <div className="mb-10">
        <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">Deep Dive</p>
        <h1 className="text-3xl sm:text-4xl font-bold text-white mb-3">
          How Auto-Tune Works
        </h1>
        <p className="text-gray-400 max-w-xl">
          The market engine runs six sequential calculations every 5-minute tick. Here is exactly
          what happens, with the formulas used in production.
        </p>
      </div>

      {/* Quick summary */}
      <div className="rounded-xl border border-gray-800 bg-gray-900/60 p-5 mb-10">
        <div className="flex items-start gap-4">
          <div className="shrink-0 w-8 h-8 rounded bg-emerald-600/20 border border-emerald-600/30 flex items-center justify-center">
            <span className="text-emerald-400 text-xs font-bold">∑</span>
          </div>
          <div>
            <h2 className="font-semibold text-white mb-1">The complete formula</h2>
            <div className="bg-gray-950 rounded-lg px-4 py-2.5 border border-gray-800 mb-3">
              <code className="text-emerald-400 text-sm font-mono">
                bpd = halfSpread × imbalance × liquidity × playerFactor × globalVol
              </code>
            </div>
            <p className="text-sm text-gray-400">
              Five multiplicative factors applied in order. Each factor can only compress or expand the spread —
              it cannot flip buy and sell prices. The base price changes separately via the trade ratio.
            </p>
          </div>
        </div>
        <Link href="/simulator" className="inline-flex items-center gap-1.5 mt-4 text-sm text-emerald-400 hover:text-emerald-300 transition-colors">
          Experiment in the interactive simulator <ArrowRight className="w-3.5 h-3.5" />
        </Link>
      </div>

      {/* Steps */}
      <div className="relative">
        {/* Vertical line */}
        <div className="absolute left-[28px] top-8 bottom-8 w-px bg-gray-800 hidden sm:block" />

        <div className="space-y-5">
          {steps.map((step) => {
            const border = accentBorder[step.accent];
            const text = accentText[step.accent];
            const num = accentNum[step.accent];
            return (
              <div key={step.num} className="flex gap-5">
                {/* Step dot */}
                <div className="shrink-0 relative">
                  <div className={`w-14 h-14 rounded-xl border flex flex-col items-center justify-center ${border}`}>
                    <span className={`text-lg font-bold font-mono leading-none ${num}`}>{step.num}</span>
                  </div>
                </div>

                {/* Content */}
                <div className="flex-1 min-w-0 pb-2">
                  <div className="flex items-center gap-2 mb-2">
                    <span className={`text-xs font-mono font-semibold uppercase tracking-wider ${text}`}>{step.tag}</span>
                  </div>
                  <h3 className="text-lg font-semibold text-white mb-2">{step.title}</h3>
                  <p className="text-sm text-gray-300 mb-3">{step.summary}</p>

                  {/* Formula */}
                  <div className="bg-gray-950 border border-gray-800 rounded-lg px-4 py-2.5 mb-3">
                    <pre className={`text-xs font-mono leading-relaxed whitespace-pre-wrap ${text}`}>{step.formula}</pre>
                  </div>

                  <p className="text-xs text-gray-500 leading-relaxed">{step.detail}</p>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Full pipeline summary */}
      <div className="mt-10 rounded-xl border border-gray-800 bg-gray-900/40 p-5">
        <h2 className="text-base font-semibold text-white mb-3">Pipeline Order</h2>
        <ol className="space-y-1.5 text-sm text-gray-400">
          {steps.map((s, i) => (
            <li key={s.num} className="flex items-center gap-3">
              <span className="w-5 h-5 rounded bg-gray-800 flex items-center justify-center text-xs text-gray-500 shrink-0">{i + 1}</span>
              <span className={`font-medium ${accentText[s.accent]}`}>{s.title}</span>
              <span className="text-gray-600 text-xs">{s.tag}</span>
            </li>
          ))}
        </ol>
        <div className="mt-4 pt-4 border-t border-gray-800">
          <code className="text-xs font-mono text-gray-400">
            buyPrice = basePrice × (1 + BPD) · sellPrice = basePrice × (1 − SPD)
          </code>
        </div>
      </div>

      {/* Spread Flow Diagram */}
      <div className="mt-10">
        <SpreadFlowDiagram />
      </div>

      {/* Interactive Spread Simulator */}
      <div className="mt-10">
        <div className="mb-4">
          <h2 className="text-xl font-bold text-white mb-1">Try It: Spread Simulator</h2>
          <p className="text-sm text-gray-400">
            Adjust the market conditions below to see how the spread factors compound into real buy and sell prices.
          </p>
        </div>
        <SpreadSimulator />
      </div>

      <SpreadCalculator />

      {/* CTA */}
      <div className="mt-8 flex justify-center">
        <Link
          href="/simulator"
          className="inline-flex items-center gap-2 px-5 py-2.5 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded-lg transition-colors shadow-lg shadow-emerald-600/20 text-sm"
        >
          <Calculator className="w-4 h-4" />
          Open the Simulator
        </Link>
      </div>
    </div>
  );
}
