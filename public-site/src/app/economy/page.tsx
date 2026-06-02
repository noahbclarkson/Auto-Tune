'use client';

import { useState } from 'react';
import { MarketConfig, DEFAULT_CONFIG } from '@/lib/market-engine';
import { StabilityForecast } from '@/components/simulator/stability-forecast';
import { SpreadChart } from '@/components/simulator/spread-chart';
import { calculatePrices } from '@/lib/market-engine';
import { formatPrice, formatPercent } from '@/lib/utils';
import {
  TrendingUp,
  TrendingDown,
  Activity,
  Zap,
  Shield,
  BarChart3,
  DollarSign,
  Layers,
  ArrowRight,
} from 'lucide-react';

interface ScenarioPreset {
  label: string;
  description: string;
  badge: string;
  badgeColor: string;
  buyRatio: number;
  onlinePlayers: number;
  zScore: number;
  weightedVolume: number;
  distinctTraders: number;
  config: MarketConfig;
}

const SCENARIOS: ScenarioPreset[] = [
  {
    label: 'Healthy Server',
    description: 'Active server with balanced buy/sell pressure and healthy volume.',
    badge: 'Healthy',
    badgeColor: 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30',
    buyRatio: 0.50,
    onlinePlayers: 12,
    zScore: 0.0,
    weightedVolume: 80,
    distinctTraders: 8,
    config: DEFAULT_CONFIG,
  },
  {
    label: 'Farm-Heavy Economy',
    description: 'Gathering-focused server where most players are sellers. Natural market bias.',
    badge: 'Caution',
    badgeColor: 'bg-amber-500/15 text-amber-400 border-amber-500/30',
    buyRatio: 0.28,
    onlinePlayers: 10,
    zScore: 0.4,
    weightedVolume: 90,
    distinctTraders: 7,
    config: { ...DEFAULT_CONFIG, baseSpread: 0.20, volumeImpact: 0.8 },
  },
  {
    label: 'Boom Conditions',
    description: 'Surge in buying activity. Prices rising fast, spreads tightening.',
    badge: 'Volatile',
    badgeColor: 'bg-amber-500/15 text-amber-400 border-amber-500/30',
    buyRatio: 0.78,
    onlinePlayers: 15,
    zScore: 1.8,
    weightedVolume: 120,
    distinctTraders: 10,
    config: { ...DEFAULT_CONFIG, maxPriceChangePercent: 2.0 },
  },
  {
    label: 'New Server',
    description: 'Few players, low volume. Price signals are noisy — spreads are wide.',
    badge: 'Caution',
    badgeColor: 'bg-amber-500/15 text-amber-400 border-amber-500/30',
    buyRatio: 0.55,
    onlinePlayers: 4,
    zScore: 0.2,
    weightedVolume: 25,
    distinctTraders: 3,
    config: { ...DEFAULT_CONFIG, baseSpread: 0.30 },
  },
  {
    label: 'Crash Recovery',
    description: 'Post-crash deflation. Sellers dominant, circuit breaker may have fired.',
    badge: 'Volatile',
    badgeColor: 'bg-rose-500/15 text-rose-400 border-rose-500/30',
    buyRatio: 0.18,
    onlinePlayers: 8,
    zScore: -1.6,
    weightedVolume: 40,
    distinctTraders: 5,
    config: DEFAULT_CONFIG,
  },
];

function VolatilityBar({ value }: { value: number }) {
  const color =
    value < 0.05 ? 'bg-emerald-500' :
    value < 0.15 ? 'bg-amber-500' :
    'bg-rose-500';
  return (
    <div className="space-y-1">
      <div className="flex justify-between text-xs">
        <span className="text-gray-400">Volatility (CV)</span>
        <span className={`font-mono font-medium ${
          value < 0.05 ? 'text-emerald-400' : value < 0.15 ? 'text-amber-400' : 'text-rose-400'
        }`}>{value.toFixed(4)}</span>
      </div>
      <div className="h-2 rounded-full bg-gray-800 overflow-hidden">
        <div
          className={`h-full rounded-full transition-all ${color}`}
          style={{ width: `${Math.min(100, value * 500)}%` }}
        />
      </div>
    </div>
  );
}

function MetricCard({
  icon: Icon,
  label,
  value,
  sub,
  accent,
}: {
  icon: React.ElementType;
  label: string;
  value: string;
  sub: string;
  accent: string;
}) {
  return (
    <div className="bg-gray-900/60 border border-gray-800 rounded-xl p-4">
      <div className="flex items-center gap-2 mb-3">
        <div className={`w-7 h-7 rounded-lg flex items-center justify-center ${accent}`}>
          <Icon className="w-3.5 h-3.5" />
        </div>
        <p className="text-xs text-gray-400 uppercase tracking-wider font-medium">{label}</p>
      </div>
      <p className="text-xl font-bold text-white mb-1">{value}</p>
      <p className="text-xs text-gray-500">{sub}</p>
    </div>
  );
}

export default function EconomyPage() {
  const [active, setActive] = useState(0);
  const scenario = SCENARIOS[active];

  const prices = calculatePrices(
    100,
    scenario.buyRatio,
    scenario.onlinePlayers,
    scenario.zScore,
    scenario.weightedVolume,
    scenario.distinctTraders,
    scenario.config,
  );

  const spreadBps = ((prices.buyPrice - prices.sellPrice) / prices.sellPrice) * 100;
  const buyPct = scenario.buyRatio * 100;
  const sellPct = 100 - buyPct;

  // Synthetic GDP/debt for illustration
  const gdp = 1_250_000;
  const debtRatio = scenario.buyRatio < 0.25 ? 0.82 : scenario.buyRatio > 0.75 ? 0.21 : 0.35;
  const totalDebt = gdp * debtRatio;

  return (
    <div className="min-h-screen bg-gray-950 text-white">
      <main className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-12 space-y-10">

        {/* Hero */}
        <div className="space-y-4">
          <p className="text-xs text-emerald-400 uppercase tracking-widest font-semibold">How It Works</p>
          <h1 className="text-3xl sm:text-4xl font-bold text-white">
            The Auto-Tune Economy Engine
          </h1>
          <p className="text-gray-400 text-base max-w-3xl leading-relaxed">
            Auto-Tune prices items using real supply-and-demand math — not static YAML tables.
            Every buy and sell moves prices. Every player shapes the market.
            The engine self-corrects, resists manipulation, and keeps the economy alive.
          </p>
        </div>

        {/* Four core concepts */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <MetricCard
            icon={DollarSign}
            label="GDP"
            value={formatPrice(gdp)}
            sub="Gross domestic product — total value of all trades"
            accent="bg-amber-500/20 text-amber-400"
          />
          <MetricCard
            icon={Layers}
            label="Total Debt"
            value={formatPrice(totalDebt)}
            sub={`${(debtRatio * 100).toFixed(1)}% of GDP — all active loans`}
            accent="bg-rose-500/20 text-rose-400"
          />
          <MetricCard
            icon={Zap}
            label="Buy Pressure"
            value={`${buyPct.toFixed(0)}%`}
            sub={`${sellPct.toFixed(0)}% sell · ${scenario.distinctTraders} active traders`}
            accent="bg-emerald-500/20 text-emerald-400"
          />
          <MetricCard
            icon={BarChart3}
            label="Spread (BPS)"
            value={`${spreadBps.toFixed(1)} bps`}
            sub="Buy-sell gap · tighter = more liquid"
            accent="bg-sky-500/20 text-sky-400"
          />
        </div>

        {/* Two-column: concept explainer + scenario picker */}
        <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">

          {/* Left: key concepts */}
          <div className="lg:col-span-2 space-y-4">
            <h2 className="text-lg font-semibold text-white">Economy Concepts</h2>

            <div className="space-y-3">
              <div className="rounded-xl bg-gray-900/60 border border-gray-800 p-4">
                <div className="flex items-start gap-3">
                  <TrendingUp className="w-4 h-4 text-emerald-400 mt-0.5 flex-shrink-0" />
                  <div>
                    <p className="text-sm font-medium text-white mb-1">Prices converge to equilibrium</p>
                    <p className="text-xs text-gray-400 leading-relaxed">
                      When Diamond sells more than it buys, the price drops.
                      When it buys more, the price rises. The market finds the right price without admin intervention.
                    </p>
                  </div>
                </div>
              </div>

              <div className="rounded-xl bg-gray-900/60 border border-gray-800 p-4">
                <div className="flex items-start gap-3">
                  <Activity className="w-4 h-4 text-sky-400 mt-0.5 flex-shrink-0" />
                  <div>
                    <p className="text-sm font-medium text-white mb-1">Spreads compress with activity</p>
                    <p className="text-xs text-gray-400 leading-relaxed">
                      More traders and volume = tighter buy/sell spreads.
                      A 20-player server has much tighter spreads than a 3-player server —
                      the market becomes more efficient as it grows.
                    </p>
                  </div>
                </div>
              </div>

              <div className="rounded-xl bg-gray-900/60 border border-gray-800 p-4">
                <div className="flex items-start gap-3">
                  <Shield className="w-4 h-4 text-amber-400 mt-0.5 flex-shrink-0" />
                  <div>
                    <p className="text-sm font-medium text-white mb-1">Circuit breaker prevents collapse</p>
                    <p className="text-xs text-gray-400 leading-relaxed">
                      When total debt exceeds 30× GDP, loan interest pauses automatically.
                      This prevents cascade default loops while the economy deleverages.
                      It re-enables once debt drops below 15× GDP.
                    </p>
                  </div>
                </div>
              </div>

              <div className="rounded-xl bg-gray-900/60 border border-gray-800 p-4">
                <div className="flex items-start gap-3">
                  <BarChart3 className="w-4 h-4 text-purple-400 mt-0.5 flex-shrink-0" />
                  <div>
                    <p className="text-sm font-medium text-white mb-1">Player archetypes shape outcomes</p>
                    <p className="text-xs text-gray-400 leading-relaxed">
                      The mix of gatherers, traders, and investors determines price dynamics.
                      2 MarketMakers + 2 GuildBuyers per server is the recommended production archetype —
                      this produces near-symmetric trade flows.
                    </p>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Right: interactive scenario */}
          <div className="lg:col-span-3 space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-semibold text-white">Live Scenario</h2>
              <span className="text-xs text-gray-500">Try different market conditions</span>
            </div>

            {/* Scenario tabs */}
            <div className="flex flex-wrap gap-2">
              {SCENARIOS.map((s, i) => (
                <button
                  key={s.label}
                  onClick={() => setActive(i)}
                  className={`px-3 py-1.5 rounded-full text-xs font-medium border transition-all ${
                    i === active
                      ? 'bg-emerald-500/20 border-emerald-500/40 text-emerald-300'
                      : 'bg-gray-900 border-gray-700 text-gray-400 hover:border-gray-600 hover:text-gray-300'
                  }`}
                >
                  {s.label}
                </button>
              ))}
            </div>

            {/* Active scenario card */}
            <div className="rounded-xl bg-gray-900/80 border border-gray-800 p-5 space-y-5">
              <div className="flex items-start justify-between">
                <div>
                  <div className="flex items-center gap-2 mb-1">
                    <p className="text-sm font-semibold text-white">{scenario.label}</p>
                    <span className={`text-xs px-2 py-0.5 rounded-full border font-medium ${scenario.badgeColor}`}>
                      {scenario.badge}
                    </span>
                  </div>
                  <p className="text-xs text-gray-400">{scenario.description}</p>
                </div>
                <ArrowRight className="w-4 h-4 text-gray-600 flex-shrink-0 mt-1" />
              </div>

              {/* Inline key metrics */}
              <div className="grid grid-cols-3 gap-3">
                <div className="rounded-lg bg-gray-800/60 p-3 text-center">
                  <p className="text-xs text-gray-400 mb-1">Buy %</p>
                  <p className="text-lg font-bold text-emerald-400">{(scenario.buyRatio * 100).toFixed(0)}%</p>
                </div>
                <div className="rounded-lg bg-gray-800/60 p-3 text-center">
                  <p className="text-xs text-gray-400 mb-1">Players</p>
                  <p className="text-lg font-bold text-sky-400">{scenario.onlinePlayers}</p>
                </div>
                <div className="rounded-lg bg-gray-800/60 p-3 text-center">
                  <p className="text-xs text-gray-400 mb-1">z-score</p>
                  <p className={`text-lg font-bold ${Math.abs(scenario.zScore) > 1.5 ? 'text-rose-400' : 'text-gray-300'}`}>
                    {scenario.zScore > 0 ? '+' : ''}{scenario.zScore.toFixed(1)}σ
                  </p>
                </div>
              </div>

              {/* Spread chart */}
              <SpreadChart
                config={scenario.config}
                basePrice={100}
                buyRatio={scenario.buyRatio}
                onlinePlayers={scenario.onlinePlayers}
                zScore={scenario.zScore}
                weightedVolume={scenario.weightedVolume}
                distinctTraders={scenario.distinctTraders}
              />

              {/* Volatility bar */}
              <div className="rounded-lg bg-gray-800/40 p-3">
                <VolatilityBar
                  value={
                    scenario.buyRatio < 0.25 ? 0.1832 :
                    scenario.buyRatio > 0.75 ? 0.1274 :
                    scenario.onlinePlayers < 5 ? 0.0891 :
                    0.0347
                  }
                />
              </div>

              {/* Stability forecast */}
              <StabilityForecast
                buyRatio={scenario.buyRatio}
                onlinePlayers={scenario.onlinePlayers}
                zScore={scenario.zScore}
                weightedVolume={scenario.weightedVolume}
                distinctTraders={scenario.distinctTraders}
                config={scenario.config}
              />
            </div>
          </div>
        </div>

        {/* Circuit breaker deep-dive */}
        <div className="rounded-2xl border border-amber-800/40 bg-gradient-to-br from-amber-950/30 to-transparent p-6 space-y-4">
          <div className="flex items-center gap-3">
            <Shield className="w-5 h-5 text-amber-400" />
            <h2 className="text-lg font-semibold text-white">The Circuit Breaker</h2>
          </div>
          <p className="text-sm text-gray-300 leading-relaxed max-w-4xl">
            Auto-Tune&apos;s loan system includes a tiered circuit breaker that activates when debt becomes
            dangerous. Unlike a simple on/off switch, it uses <strong className="text-amber-300">counter-cyclical
            interest</strong> — as debt rises, interest automatically falls, slowing accumulation before
            the hard pause kicks in.
          </p>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            {[
              {
                tier: 'TIER 1',
                ratio: 'D/G &gt; 3×',
                interest: '50%',
                color: 'text-gray-400',
                border: 'border-gray-700',
                bg: 'bg-gray-900/50',
              },
              {
                tier: 'TIER 2',
                ratio: 'D/G &gt; 5×',
                interest: '25%',
                color: 'text-amber-400',
                border: 'border-amber-800/50',
                bg: 'bg-amber-900/20',
              },
              {
                tier: 'TIER 3',
                ratio: 'D/G &gt; 30×',
                interest: '0%',
                color: 'text-rose-400',
                border: 'border-rose-800/50',
                bg: 'bg-rose-900/20',
              },
            ].map((t) => (
              <div key={t.tier} className={`rounded-xl border ${t.border} ${t.bg} p-4`}>
                <div className="flex items-center justify-between mb-2">
                  <span className={`text-xs font-bold ${t.color}`}>{t.tier}</span>
                  <span className="text-xs text-gray-500">Circuit</span>
                </div>
                <p className="text-2xl font-bold text-white mb-1">{t.interest}</p>
                <p className="text-xs text-gray-400">interest when active</p>
                <div className="mt-3 pt-3 border-t border-gray-800">
                  <p className="text-xs text-gray-500">Triggers at</p>
                  <p className={`text-sm font-mono font-semibold ${t.color}`}>{t.ratio}</p>
                </div>
              </div>
            ))}
          </div>

          <p className="text-xs text-gray-500">
            The circuit breaker uses a <strong className="text-gray-400">50% hysteresis band</strong> —
            once TIER3 fires at D/G &gt; 30×, it stays locked until D/G drops below 15×.
            This prevents rapid oscillation near the boundary.
          </p>
        </div>

        {/* Market events */}
        <div className="space-y-4">
          <h2 className="text-lg font-semibold text-white">Market Events</h2>
          <p className="text-sm text-gray-400 -mt-2">
            Admins can trigger one-time events that shift supply or demand for specific items.
            Events are temporary — the market self-corrects after they end.
          </p>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {[
              { emoji: '📈', name: 'Demand Surge', desc: 'Sudden spike in demand for one item. Price rises fast.', color: 'bg-emerald-900/30 border-emerald-800/40' },
              { emoji: '📉', name: 'Supply Glut', desc: 'Oversupply of one item. Price falls, spreads widen.', color: 'bg-rose-900/30 border-rose-800/40' },
              { emoji: '💰', name: 'Inflation Boost', desc: 'Economy-wide price pressure. All items rise 10–20%.', color: 'bg-amber-900/30 border-amber-800/40' },
              { emoji: '⛏️', name: 'Gold Rush', desc: 'Rare mining event. Gold supply surges, prices crash.', color: 'bg-yellow-900/30 border-yellow-800/40' },
              { emoji: '🎉', name: 'Festival', desc: 'Temporary buy-heavy event. Prices rise across the board.', color: 'bg-purple-900/30 border-purple-800/40' },
              { emoji: '🌾', name: 'Bounty', desc: 'One item gets a bounty. Huge demand spike, rapid price rise.', color: 'bg-sky-900/30 border-sky-800/40' },
            ].map((e) => (
              <div key={e.name} className={`rounded-xl border p-4 ${e.color}`}>
                <div className="flex items-center gap-2 mb-2">
                  <span className="text-xl">{e.emoji}</span>
                  <p className="text-sm font-semibold text-white">{e.name}</p>
                </div>
                <p className="text-xs text-gray-400 leading-relaxed">{e.desc}</p>
              </div>
            ))}
          </div>
        </div>

      </main>
    </div>
  );
}
