'use client';

import { useState } from 'react';

interface ImpactCard {
  id: string;
  emoji: string;
  title: string;
  finding: string;
  verdict: 'recommended' | 'warning' | 'caution' | 'neutral';
  verdictLabel: string;
  detail: string;
  configHint?: string;
}

const IMPACT_CARDS: ImpactCard[] = [
  {
    id: 'sell-pressure',
    emoji: '⚖️',
    title: 'Sell Pressure: 0.80 vs 1.0',
    finding: '+5.2% GDP but D/G +39.9% worse',
    verdict: 'caution',
    verdictLabel: 'Stability trade-off',
    detail:
      'Lowering sell pressure from 1.0 to 0.80 makes prices slightly stickier (gains +5.2% GDP) but dramatically worsens debt accumulation — D/G jumps +39.9%. The symmetric default (1.0) is correct for stability. Admins wanting more growth can set 0.80, but watch D/G closely.',
    configHint: 'economy.sell-pressure-multiplier: 0.80 (use for growth) or 1.0 (use for stability)',
  },
  {
    id: 'trend-dampening',
    emoji: '📉',
    title: 'Trend Dampening: 0.10 vs 0.05',
    finding: '+7.7% GDP, same D/G',
    verdict: 'recommended',
    verdictLabel: 'Free improvement',
    detail:
      'Raising trend dampening from 0.05 to 0.10 is the single most impactful single-parameter change. It prevents runaway price trends, giving the economy more time to self-correct between large trades. D/G is essentially unchanged. Confirm in 840-config, 5-seed simulation.',
    configHint: 'economy.trend-dampening: 0.10 (confirmed GDP-optimal)',
  },
  {
    id: 'two-mm',
    emoji: '💰',
    title: 'Money Multiplier: 2MM vs 1MM',
    finding: '+101% GDP, -48% volatility',
    verdict: 'recommended',
    verdictLabel: 'Doubling recommended',
    detail:
      'Two MarketMakers instead of one is the single biggest economy health improvement. GDP doubles (+101%), volatility halves (-48%), spreads compress 22%. MarketMaker provides two-sided liquidity that eliminates the structural sell bias. Confirmed across 5-seed statistical simulation.',
    configHint: ' archetype: 2× MarketMaker + 2× GuildBuyer (not 1× MM)',
  },
  {
    id: 'floor',
    emoji: '🛡️',
    title: 'Diamond Floor: 60% of Base',
    finding: '+10.7% GDP, D/G +19.1% (worth it)',
    verdict: 'recommended',
    verdictLabel: 'Seller protection',
    detail:
      '60% Diamond floor ($300 on base $500) binds 100% of the time in healthy economies — sellers are protected from crash below $300. GDP improves +10.7%. D/G worsens +19.1% because floor suppresses internal price discovery (behavioral paradox). The seller protection is worth the D/G cost in normal conditions. Below 60%: floor rarely binds. Above 70%: economy chokes.',
    configHint: 'economy.price-floor: { DIAMOND: 300 } or { PERCENTAGE: 0.60 }',
  },
  {
    id: 'counter-cyclical',
    emoji: '🔄',
    title: 'Counter-Cyclical Interest: Enabled',
    finding: 'Smooth debt reduction, no TIER3 noise',
    verdict: 'recommended',
    verdictLabel: 'Default — keep on',
    detail:
      'Counter-cyclical interest (multiplier = max(0, 1 - D/G/tier3)) reduces interest rate as debt rises, preventing doom spirals. With tier3=30, the circuit only fires in genuine catastrophe (>D/G 30×). Disable only if you understand debt dynamics deeply.',
    configHint: 'loans.counter-cyclical: true (default, do not disable)',
  },
  {
    id: 'tier3',
    emoji: '⛓️',
    title: 'TIER3 Ratio: 15 → 30',
    finding: '0 TIER3 events vs 50+ at 60d (doom loop)',
    verdict: 'recommended',
    verdictLabel: 'Critical fix',
    detail:
      'At tier3=15, the counter-cyclical formula zeros interest when D/G crosses 15×. This creates a doom loop: zero interest → debt compounds → D/G climbs past 20× → circuit stays locked. With tier3=30, D/G=20 → 33% interest (survivable). 60-day simulation: tier3=15 had 50+ TIER3 oscillations. tier3=30: 0 events.',
    configHint: 'loans.tier3-ratio: 30 (raised from 15 in rewrite-2)',
  },
  {
    id: 'newbie',
    emoji: '🌱',
    title: 'Newbie Archetype: +33% GDP in Stressed',
    finding: '-42% D/G, -68% volatility in stressed economies',
    verdict: 'neutral',
    verdictLabel: 'Context-dependent',
    detail:
      'Newbie players (2.2× buy rate, minimal selling) are dramatically stabilizing in stressed economies: +33% GDP, -42% D/G, -35% volatility. But in healthy economies: +41.5% GDP BUT D/G worsens +34.7%. Newbie is the anti-Farmer — net buyer, absorbs sell pressure. Use to stabilize volatile or new servers. Avoid adding to already-healthy economies if D/G is a concern.',
  },
  {
    id: 'guildbuyer-threshold',
    emoji: '🎯',
    title: 'GuildBuyer Threshold: 5% (not 15%)',
    finding: '5% = stable. 15-30% = D/G 5-20×, vol explosions',
    verdict: 'recommended',
    verdictLabel: 'Critical setting',
    detail:
      'GuildBuyer triggers when item price falls >threshold below perceived value. Default random range (15-30%) is catastrophic — MMs buy enormous amounts on credit before a tiny dip threshold fires. At 5% threshold: D/G stays healthy, vol <0.007 (stable). At 15%+ threshold: D/G 5-20× with multi-seed runs. 30-day data confirms 7% advantage disappears while D/G is +2.89× worse. Use 5% or add a Diamond price floor.',
    configHint: 'guildbuyer.threshold-percent: 5 (not 15, 20, or random)',
  },
];

const VERDICT_STYLES = {
  recommended: {
    badge: 'bg-emerald-900/60 text-emerald-300 border border-emerald-700/50',
    dot: 'text-emerald-400',
    label: 'text-emerald-300',
  },
  warning: {
    badge: 'bg-amber-900/60 text-amber-300 border border-amber-700/50',
    dot: 'text-amber-400',
    label: 'text-amber-300',
  },
  caution: {
    badge: 'bg-orange-900/60 text-orange-300 border border-orange-700/50',
    dot: 'text-orange-400',
    label: 'text-orange-300',
  },
  neutral: {
    badge: 'bg-gray-800/60 text-gray-300 border border-gray-700/50',
    dot: 'text-gray-400',
    label: 'text-gray-400',
  },
};

export function ConfigImpactPreview() {
  const [expanded, setExpanded] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);

  const visible = expanded ? IMPACT_CARDS : IMPACT_CARDS.slice(0, 3);

  return (
    <div className="rounded-xl border border-gray-800 bg-gray-900/60 overflow-hidden">
      {/* Header */}
      <div className="px-4 py-3 border-b border-gray-800 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="text-cyan-400 text-sm">📊</span>
          <h3 className="text-sm font-semibold text-white">What the Evidence Says</h3>
        </div>
        <button
          onClick={() => setExpanded((e) => !e)}
          className="text-xs text-gray-500 hover:text-gray-300 transition-colors"
        >
          {expanded ? 'Show less' : `+${IMPACT_CARDS.length - 3} more findings`}
        </button>
      </div>

      <div className="p-4 space-y-3">
        {/* Source note */}
        <div className="rounded-lg bg-cyan-950/20 border border-cyan-900/30 px-3 py-2">
          <p className="text-xs text-cyan-200/80 leading-relaxed">
            <strong className="text-cyan-300">840-config simulation lab:</strong> All findings backed by multi-seed
            evidence (5 seeds: 42, 12345, 98765, 77777, 11111). Stressed + healthy economies tested separately.
            See{' '}
            <a href="/changelog" className="underline hover:text-cyan-100">
              changelog
            </a>{' '}
            for full method.
          </p>
        </div>

        {/* Cards */}
        {visible.map((card) => {
          const style = VERDICT_STYLES[card.verdict];
          const isSelected = selected === card.id;
          return (
            <div
              key={card.id}
              className={`rounded-lg border transition-all cursor-pointer ${isSelected ? `${style.badge} border-opacity-80` : 'border-gray-800 bg-gray-800/40 hover:bg-gray-800/60'} `}
              onClick={() => setSelected(isSelected ? null : card.id)}
            >
              <div className="flex items-start gap-2.5 px-3 py-2.5">
                <span className="text-base shrink-0 mt-0.5">{card.emoji}</span>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-xs font-semibold text-white">{card.title}</span>
                    <span
                      className={`inline-flex items-center gap-1 text-[10px] font-medium px-1.5 py-0.5 rounded ${style.badge}`}
                    >
                      <span className={style.dot}>●</span>
                      {card.verdictLabel}
                    </span>
                  </div>
                  <p className={`text-xs mt-0.5 ${style.label} font-medium`}>{card.finding}</p>
                </div>
                <span className="text-gray-600 text-xs shrink-0 mt-0.5">{isSelected ? '▲' : '▼'}</span>
              </div>

              {/* Expanded detail */}
              {isSelected && (
                <div className="px-3 pb-3 border-t border-gray-800/50 mt-1 pt-2">
                  <p className="text-xs text-gray-400 leading-relaxed">{card.detail}</p>
                  {card.configHint && (
                    <div className="mt-2 rounded bg-gray-900/80 border border-gray-700/50 px-2.5 py-1.5">
                      <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-0.5">Config hint</p>
                      <code className="text-xs text-emerald-400 font-mono">{card.configHint}</code>
                    </div>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
