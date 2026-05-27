'use client';

import Link from 'next/link';
import { generateSpreadCurve } from '@/lib/market-engine';
import { ArrowRight } from 'lucide-react';

const STEPS = [
  { label: 'Base Spread',   color: '#10b981' },
  { label: '÷ Imbalance',  color: '#34d399' },
  { label: '÷ Liquidity',  color: '#6ee7b7' },
  { label: '÷ Players',    color: '#a7f3d0' },
  { label: '÷ Volume',     color: '#d1fae5' },
];

export function AlgorithmPreview() {
  const curveData = generateSpreadCurve(undefined, 10, 0, 0, 5);
  const keyPoints = [0, 25, 50, 75, 100].map((i) => ({
    ...curveData[i],
    label: `${i}%`,
  }));

  // Max total spread for bar scaling
  const maxSpread = Math.max(...keyPoints.map((p) => p.totalSpread));

  return (
    <section className="py-20 bg-gray-900/30 border-y border-gray-800/40">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid lg:grid-cols-2 gap-12 items-start">

          {/* Left — formula breakdown */}
          <div>
            <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">Spread Pipeline</p>
            <h2 className="text-2xl font-bold text-white mb-4">Five factors, one spread</h2>
            <p className="text-gray-400 text-sm leading-relaxed mb-8">
              Each tick, the spread for every item passes through a multiplicative pipeline.
              Each stage compresses or expands the bid–ask gap based on real market conditions.
            </p>

            {/* Pipeline steps */}
            <div className="space-y-0">
              {STEPS.map((step, i) => (
                <div key={step.label} className="flex items-center gap-3">
                  <div className="flex flex-col items-center">
                    <div
                      className="w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold text-gray-900 shrink-0"
                      style={{ backgroundColor: step.color }}
                    >
                      {i + 1}
                    </div>
                    {i < STEPS.length - 1 && (
                      <div className="w-px h-6 bg-gray-700" />
                    )}
                  </div>
                  <div className="flex-1 py-1">
                    <span className="text-sm font-medium text-gray-200">{step.label}</span>
                  </div>
                </div>
              ))}
            </div>

            <div className="mt-6 bg-gray-950 border border-gray-800 rounded-lg px-4 py-3">
              <code className="text-emerald-400 text-sm font-mono">
                spread = base × imbalance × liquidity × playerFactor × gvm
              </code>
            </div>

            <Link
              href="/simulator"
              className="inline-flex items-center gap-2 mt-6 text-sm text-emerald-400 hover:text-emerald-300 transition-colors"
            >
              Experiment in the simulator <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>

          {/* Right — spread table by buy ratio */}
          <div>
            <p className="text-xs text-gray-500 uppercase tracking-wider mb-4">
              Default config · 10 players · balanced volume
            </p>
            <div className="rounded-xl border border-gray-800 overflow-hidden">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-gray-900 border-b border-gray-800">
                    <th className="py-2.5 px-4 text-left text-xs text-gray-500 font-medium">Buy Ratio</th>
                    <th className="py-2.5 px-4 text-right text-xs text-emerald-500 font-medium">BPD (ask)</th>
                    <th className="py-2.5 px-4 text-right text-xs text-rose-500 font-medium">SPD (bid)</th>
                    <th className="py-2.5 px-4 text-right text-xs text-gray-500 font-medium hidden sm:table-cell">Total</th>
                    <th className="py-2.5 px-3 text-left text-xs text-gray-600 font-medium w-28 hidden md:table-cell">Spread</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-800/60">
                  {keyPoints.map((point) => {
                    const barWidth = (point.totalSpread / maxSpread) * 100;
                    return (
                      <tr key={point.buyRatio} className="bg-gray-900/40 hover:bg-gray-800/40 transition-colors">
                        <td className="py-3 px-4 text-gray-300 font-mono text-xs">{point.label} buys</td>
                        <td className="py-3 px-4 text-right text-emerald-400 font-mono text-xs">{point.bpd.toFixed(2)}%</td>
                        <td className="py-3 px-4 text-right text-rose-400 font-mono text-xs">{point.spd.toFixed(2)}%</td>
                        <td className="py-3 px-4 text-right text-gray-400 font-mono text-xs hidden sm:table-cell">{point.totalSpread.toFixed(2)}%</td>
                        <td className="py-3 px-3 hidden md:table-cell">
                          <div className="h-1.5 rounded-full bg-gray-800 w-24 overflow-hidden">
                            <div
                              className="h-full rounded-full bg-emerald-600/70"
                              style={{ width: `${barWidth}%` }}
                            />
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <p className="text-xs text-gray-600 mt-3">
              At 50% buys the spread is symmetric. Imbalance shifts the bid/ask asymmetrically
              to restore equilibrium.
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}
