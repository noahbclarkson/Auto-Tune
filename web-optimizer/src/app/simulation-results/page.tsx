'use client';

import { useState, useEffect, useMemo } from 'react';
import { Header } from '@/components/layout/header';
import { Footer } from '@/components/layout/footer';

interface SimResult {
  name: string;
  gdp: number;
  debt: number;
  debtGdp: number;
  avgBpd: number;
  avgSpd: number;
  avgVolatility: number;
  buyRatio: number;
  defaultRate: number;
  totalInterestPaid: number;
  interestEvents: number;
  defaultedEvents: number;
  durationDays: number;
  maxTick: number;
  startedAt: string;
  configJson: string;
  priceStability: {
    item: string;
    basePrice: number;
    minPrice: number;
    maxPrice: number;
    avgPrice: number;
    firstPrice: number;
    lastTrend: string;
  }[];
  mostActiveItems: { item: string; trades: number }[];
  archetypes: { archetype: string; players: number; trades: number }[];
}

type SortKey = keyof SimResult;
type SortDir = 'asc' | 'desc';

function fmt(v: number, decimals = 2) {
  return v.toFixed(decimals);
}

function debtGdpColor(d: number): string {
  if (d < 1) return 'text-emerald-400';
  if (d < 5) return 'text-amber-400';
  if (d < 10) return 'text-orange-400';
  return 'text-rose-500';
}

function debtGdpBg(d: number): string {
  if (d < 1) return 'bg-emerald-950/30';
  if (d < 5) return 'bg-amber-950/30';
  if (d < 10) return 'bg-orange-950/30';
  return 'bg-rose-950/30';
}

function volatilityColor(v: number): string {
  if (v === 0) return 'text-gray-500';
  if (v < 0.05) return 'text-emerald-400';
  if (v < 0.15) return 'text-sky-400';
  return 'text-rose-400';
}

function volatilityLabel(v: number): string {
  if (v === 0) return '—';
  if (v < 0.05) return 'STABLE';
  if (v < 0.15) return 'MODERATE';
  return 'UNSTABLE';
}

function HealthBadge({ debtGdp, buyRatio, volatility }: { debtGdp: number; buyRatio: number; volatility: number }) {
  const isHealthy = debtGdp < 3 && buyRatio >= 0.45 && buyRatio <= 0.55 && volatility < 0.05;
  const isOk = debtGdp < 10 && volatility < 0.15;
  if (isHealthy) return <span className="text-xs font-semibold text-emerald-400 bg-emerald-950/50 px-1.5 py-0.5 rounded">HEALTHY</span>;
  if (isOk) return <span className="text-xs font-semibold text-amber-400 bg-amber-950/50 px-1.5 py-0.5 rounded">MODERATE</span>;
  return <span className="text-xs font-semibold text-rose-400 bg-rose-950/50 px-1.5 py-0.5 rounded">UNHEALTHY</span>;
}

function VolatilityBar({ v }: { v: number }) {
  if (v === 0) return <span className="text-xs text-gray-500">—</span>;
  // Cap at 0.50 for display
  const pct = Math.min(v / 0.50, 1);
  const color = v < 0.05 ? 'bg-emerald-500' : v < 0.15 ? 'bg-sky-500' : 'bg-rose-500';
  return (
    <div className="flex items-center gap-1.5">
      <div className="w-12 h-1.5 rounded-full bg-gray-800 overflow-hidden">
        <div className={`h-full rounded-full ${color}`} style={{ width: `${pct * 100}%` }} />
      </div>
      <span className={`text-xs font-mono font-semibold ${volatilityColor(v)}`}>{v.toFixed(3)}</span>
    </div>
  );
}

function DetailPanel({ r }: { r: SimResult }) {
  // Parse config to get key params
  let config: Record<string, unknown> = {};
  try { config = JSON.parse(r.configJson); } catch { /* ignore */ }

  const loanCfg = config.loans as Record<string, number> | undefined;
  const spreadCfg = config.spread as Record<string, number> | undefined;
  const playerScaling = config.player_scaling as Record<string, number> | undefined;
  const volLabel = volatilityLabel(r.avgVolatility);
  const volColor = r.avgVolatility < 0.05 ? 'text-emerald-400' : r.avgVolatility < 0.15 ? 'text-sky-400' : 'text-rose-400';

  return (
    <div className="p-6 border-t border-gray-800 bg-gray-900/30">
      {/* Header metrics */}
      <div className="grid grid-cols-2 sm:grid-cols-5 gap-3 mb-6">
        {[
          { label: 'Duration', value: `${r.durationDays}d` },
          { label: 'Max tick', value: r.maxTick.toLocaleString() },
          { label: 'Started', value: r.startedAt.split(' ')[0] || '—' },
          { label: 'Interest events', value: r.interestEvents.toLocaleString() },
          {
            label: 'Volatility',
            value: r.avgVolatility > 0 ? r.avgVolatility.toFixed(4) : '—',
            color: r.avgVolatility > 0 ? (r.avgVolatility < 0.05 ? 'text-emerald-400' : r.avgVolatility < 0.15 ? 'text-sky-400' : 'text-rose-400') : 'text-gray-400',
            badge: volLabel,
            badgeColor: r.avgVolatility < 0.05 ? 'bg-emerald-950/50 text-emerald-400' : r.avgVolatility < 0.15 ? 'bg-sky-950/50 text-sky-400' : 'bg-rose-950/50 text-rose-400',
          },
        ].map(({ label, value, color, badge, badgeColor }) => (
          <div key={label} className="rounded-lg border border-gray-800 bg-gray-900/50 px-3 py-2">
            <p className="text-xs text-gray-500 uppercase tracking-wider mb-1">{label}</p>
            <div className="flex items-center gap-2">
              <p className={`text-sm font-bold font-mono ${color || 'text-white'}`}>{value}</p>
              {badge && <span className={`text-xs px-1.5 py-0.5 rounded font-semibold ${badgeColor}`}>{badge}</span>}
            </div>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Price stability */}
        <div>
          <h3 className="text-xs text-gray-500 uppercase tracking-widest font-medium mb-3">Price Stability (top items by range)</h3>
          <div className="space-y-2">
            {r.priceStability.map((ps) => {
              const range = ps.basePrice > 0 ? ((ps.maxPrice - ps.minPrice) / ps.minPrice * 100) : 0;
              const finalVsBase = ps.basePrice > 0 ? ((ps.avgPrice - ps.basePrice) / ps.basePrice * 100) : 0;
              const trendArrow = ps.lastTrend === 'UP' ? '↑' : ps.lastTrend === 'DOWN' ? '↓' : '→';
              const trendColor = ps.lastTrend === 'UP' ? 'text-emerald-400' : ps.lastTrend === 'DOWN' ? 'text-rose-400' : 'text-gray-400';
              return (
                <div key={ps.item} className="rounded-md border border-gray-800 bg-gray-900/40 px-3 py-2">
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-xs font-medium text-gray-200">{ps.item}</span>
                    <span className={`text-xs font-mono font-semibold ${trendColor}`}>{trendArrow}</span>
                  </div>
                  <div className="flex items-center gap-2 text-xs text-gray-500">
                    <span className="font-mono">${ps.basePrice.toFixed(0)}</span>
                    <span>→</span>
                    <span className="font-mono text-gray-300">${ps.avgPrice.toFixed(1)}</span>
                    <span className={`ml-auto font-mono ${finalVsBase >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
                      {finalVsBase >= 0 ? '+' : ''}{finalVsBase.toFixed(0)}%
                    </span>
                  </div>
                  <div className="mt-1 h-1 rounded-full bg-gray-800 overflow-hidden">
                    <div
                      className="h-full rounded-full bg-emerald-600/60"
                      style={{ width: `${Math.min(range, 100)}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Most active items */}
        <div>
          <h3 className="text-xs text-gray-500 uppercase tracking-widest font-medium mb-3">Most Active Items</h3>
          <div className="space-y-1.5">
            {r.mostActiveItems.map((mi, i) => (
              <div key={mi.item} className="flex items-center gap-3">
                <span className="text-xs text-gray-600 font-mono w-4">{i + 1}</span>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between">
                    <span className="text-xs text-gray-300 truncate">{mi.item}</span>
                    <span className="text-xs font-mono text-gray-500 ml-2">{mi.trades.toLocaleString()}</span>
                  </div>
                  <div className="mt-0.5 h-1 rounded-full bg-gray-800 overflow-hidden">
                    <div
                      className="h-full rounded-full bg-sky-600/60"
                      style={{ width: `${(mi.trades / r.mostActiveItems[0].trades * 100)}%` }}
                    />
                  </div>
                </div>
              </div>
            ))}
          </div>

          {/* Spread config */}
          {spreadCfg && (
            <div className="mt-6">
              <h3 className="text-xs text-gray-500 uppercase tracking-widest font-medium mb-3">Spread Config</h3>
              <div className="rounded-lg border border-gray-800 bg-gray-900/40 p-3 space-y-1.5">
                {[
                  { label: 'Base spread', value: `${((spreadCfg.base_spread as number) * 100).toFixed(0)}%` },
                  { label: 'Volume impact', value: `${((spreadCfg.volume_impact as number) * 100).toFixed(0)}%` },
                  { label: 'Player impact', value: `${((spreadCfg.player_impact as number) * 100).toFixed(0)}%` },
                  { label: 'Liquidity coeff', value: `${((spreadCfg.liquidity_coeff as number) * 100).toFixed(1)}%` },
                  { label: 'Full-effect traders', value: `${spreadCfg.liquidity_full_effect_traders || spreadCfg.liquidityFullEffectTraders || '?'}` },
                ].map(({ label, value }) => (
                  <div key={label} className="flex justify-between text-xs">
                    <span className="text-gray-500">{label}</span>
                    <span className="font-mono text-gray-300">{value}</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Archetypes + loan */}
        <div>
          {r.archetypes && r.archetypes.length > 0 && (
            <>
              <h3 className="text-xs text-gray-500 uppercase tracking-widest font-medium mb-3">Player Archetypes</h3>
              <div className="space-y-2 mb-6">
                {r.archetypes.map((a) => (
                  <div key={a.archetype} className="flex items-center justify-between">
                    <span className="text-xs text-gray-300">{a.archetype}</span>
                    <div className="flex items-center gap-3 text-xs text-gray-500">
                      <span>{a.players} players</span>
                      <span className="font-mono text-gray-400">{a.trades.toLocaleString()} tx</span>
                    </div>
                  </div>
                ))}
              </div>
            </>
          )}

          <h3 className="text-xs text-gray-500 uppercase tracking-widest font-medium mb-3">Loan Activity</h3>
          <div className="rounded-lg border border-gray-800 bg-gray-900/40 p-3 space-y-2">
            <div className="flex justify-between text-xs">
              <span className="text-gray-500">Total interest paid</span>
              <span className="font-mono text-amber-400">${r.totalInterestPaid.toLocaleString('en', { maximumFractionDigits: 0 })}</span>
            </div>
            <div className="flex justify-between text-xs">
              <span className="text-gray-500">Interest events</span>
              <span className="font-mono text-gray-300">{r.interestEvents.toLocaleString()}</span>
            </div>
            <div className="flex justify-between text-xs">
              <span className="text-gray-500">Defaulted events</span>
              <span className="font-mono text-rose-400">{r.defaultedEvents.toLocaleString()}</span>
            </div>
            <div className="flex justify-between text-xs">
              <span className="text-gray-500">Default rate</span>
              <span className={`font-mono font-semibold ${r.defaultRate > 10 ? 'text-rose-400' : r.defaultRate > 5 ? 'text-amber-400' : 'text-emerald-400'}`}>
                {r.defaultRate.toFixed(1)}%
              </span>
            </div>
            {loanCfg && (
              <>
                <div className="border-t border-gray-800 pt-2 flex justify-between text-xs">
                  <span className="text-gray-600">Base interest rate</span>
                  <span className="font-mono text-gray-400">{(loanCfg.base_interest_rate * 100).toFixed(0)}%</span>
                </div>
                <div className="flex justify-between text-xs">
                  <span className="text-gray-600">Tier 3 circuit breaker</span>
                  <span className="font-mono text-gray-400">{loanCfg.debt_gdp_tier3_ratio}×</span>
                </div>
                <div className="flex justify-between text-xs">
                  <span className="text-gray-600">Max loan multiplier</span>
                  <span className="font-mono text-gray-400">{loanCfg.max_loan_multiplier}× GDP</span>
                </div>
              </>
            )}
          </div>

          {/* Volatility interpretation */}
          <div className="mt-4 rounded-lg border border-gray-800 bg-gray-900/40 p-3">
            <div className="flex items-center gap-2 mb-2">
              <span className={`text-sm font-bold font-mono ${volColor}`}>{r.avgVolatility > 0 ? r.avgVolatility.toFixed(4) : '—'}</span>
              <span className={`text-xs px-1.5 py-0.5 rounded font-semibold ${r.avgVolatility < 0.05 ? 'bg-emerald-950/50 text-emerald-400' : r.avgVolatility < 0.15 ? 'bg-sky-950/50 text-sky-400' : 'bg-rose-950/50 text-rose-400'}`}>{volLabel}</span>
            </div>
            <p className="text-xs text-gray-500 leading-relaxed">
              {r.avgVolatility === 0
                ? 'Volatility data not available for this run.'
                : r.avgVolatility < 0.05
                ? 'Prices are stable — the market engine is well-regulated.'
                : r.avgVolatility < 0.15
                ? 'Some price oscillation — within acceptable bounds.'
                : 'Significant price oscillation — parameter review recommended.'}
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function SimulationResultsPage() {
  const [data, setData] = useState<SimResult[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [maxDebtGdp, setMaxDebtGdp] = useState(500);
  const [sortKey, setSortKey] = useState<SortKey>('avgVolatility');
  const [sortDir, setSortDir] = useState<SortDir>('asc');
  const [expanded, setExpanded] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const PAGE_SIZE = 15;

  useEffect(() => {
    fetch('/simulation-results.json')
      .then((r) => r.json())
      .then((json) => {
        setData(json);
        setLoading(false);
      })
      .catch(() => {
        setError('Could not load simulation results.');
        setLoading(false);
      });
  }, []);

  const filtered = useMemo(() => {
    return data.filter((r) => r.debtGdp <= maxDebtGdp);
  }, [data, maxDebtGdp]);

  const sorted = useMemo(() => {
    return [...filtered].sort((a, b) => {
      const av = a[sortKey] as number;
      const bv = b[sortKey] as number;
      return sortDir === 'asc' ? av - bv : bv - av;
    });
  }, [filtered, sortKey, sortDir]);

  const totalPages = Math.ceil(sorted.length / PAGE_SIZE);
  const pageRows = sorted.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  function toggleSort(key: SortKey) {
    if (sortKey === key) {
      setSortDir((d) => d === 'asc' ? 'desc' : 'asc');
    } else {
      setSortKey(key);
      setSortDir('asc');
    }
    setPage(0);
  }

  function SortIcon({ col }: { col: SortKey }) {
    if (sortKey !== col) return <span className="text-gray-600 ml-1">↕</span>;
    return <span className="text-emerald-400 ml-1">{sortDir === 'asc' ? '↑' : '↓'}</span>;
  }

  function Th({ col, label }: { col: SortKey; label: string }) {
    return (
      <th
        className="px-2 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer hover:text-emerald-400 transition-colors whitespace-nowrap"
        onClick={() => toggleSort(col)}
      >
        {label}<SortIcon col={col} />
      </th>
    );
  }

  const healthyRuns = data.filter((r) => r.debtGdp < 3 && r.buyRatio >= 0.45 && r.buyRatio <= 0.55 && r.avgVolatility < 0.05).length;
  const volatileRuns = data.filter((r) => r.avgVolatility > 0 && r.avgVolatility >= 0.15).length;

  return (
    <div className="min-h-screen bg-gray-950 text-white">
      <Header />
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        {/* Header */}
        <div className="mb-8">
          <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">
            Simulation Lab
          </p>
          <h1 className="text-3xl sm:text-4xl font-bold text-white mb-3">
            Simulation Run Results
          </h1>
          <p className="text-gray-400 max-w-3xl text-sm leading-relaxed">
            Analysis of {data.length > 0 ? `${data.length} ` : ''}market simulation runs. Each run
            simulates a 14-day Minecraft economy with different player archetype mixes and engine
            parameters. Compare debt/GDP health, buy ratios, volatility, and price stability
            across scenarios. Click any row to expand full details including config parameters,
            spread settings, and loan activity.
          </p>
        </div>

        {/* Stats */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-6">
          {[
            { label: 'Total runs', value: data.length.toString() },
            { label: 'Healthy', value: `${healthyRuns} (${data.length > 0 ? Math.round(healthyRuns / data.length * 100) : 0 }%)` },
            { label: 'Volatile (≥0.15)', value: `${volatileRuns}` },
            { label: 'Showing', value: filtered.length.toString() },
          ].map(({ label, value }) => (
            <div key={label} className="rounded-lg border border-gray-800 bg-gray-900/50 px-4 py-3">
              <p className="text-xs text-gray-500 uppercase tracking-wider mb-1">{label}</p>
              <p className="text-xl font-bold font-mono text-white">{value}</p>
            </div>
          ))}
        </div>

        {/* Filters */}
        <div className="rounded-xl border border-gray-800 bg-gray-900/50 p-4 mb-6">
          <p className="text-xs text-gray-500 uppercase tracking-wider mb-3 font-medium">Filter</p>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs text-gray-400 mb-1.5">
                Max debt/GDP: <span className="text-emerald-400 font-mono">{maxDebtGdp === 500 ? 'all' : fmt(maxDebtGdp) + '×'}</span>
              </label>
              <input
                type="range"
                min="0" max="500" step="1"
                value={maxDebtGdp}
                onChange={(e) => { setMaxDebtGdp(parseFloat(e.target.value)); setPage(0); }}
                className="w-full accent-emerald-500"
              />
              <div className="flex justify-between text-xs text-gray-600 mt-1">
                <span>0×</span><span>500×</span>
              </div>
            </div>
            <div className="flex flex-col justify-end">
              <div className="flex flex-wrap gap-2">
                {[
                  { label: 'All runs', fn: () => { setMaxDebtGdp(500); setPage(0); } },
                  { label: 'D/G &lt; 3×', fn: () => { setMaxDebtGdp(3); setPage(0); } },
                  { label: 'D/G &lt; 10×', fn: () => { setMaxDebtGdp(10); setPage(0); } },
                ].map(({ label, fn }) => (
                  <button
                    key={label}
                    onClick={fn}
                    className="px-2.5 py-1 rounded-md border border-gray-700 text-xs text-gray-300 hover:border-emerald-700 hover:text-emerald-400 transition-colors"
                  >
                    {label}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>

        {/* Volatility legend */}
        <div className="flex flex-wrap items-center gap-4 mb-4 text-xs text-gray-500">
          <span className="uppercase tracking-wider font-medium">Volatility scale:</span>
          <div className="flex items-center gap-1.5">
            <div className="w-8 h-1.5 rounded-full bg-emerald-500" />
            <span className="text-emerald-400">&lt; 0.05 STABLE</span>
          </div>
          <div className="flex items-center gap-1.5">
            <div className="w-8 h-1.5 rounded-full bg-sky-500" />
            <span className="text-sky-400">0.05–0.15 MODERATE</span>
          </div>
          <div className="flex items-center gap-1.5">
            <div className="w-8 h-1.5 rounded-full bg-rose-500" />
            <span className="text-rose-400">&ge; 0.15 UNSTABLE</span>
          </div>
        </div>

        {/* Table */}
        {loading ? (
          <div className="rounded-xl border border-gray-800 bg-gray-900/50 p-12 text-center">
            <p className="text-gray-400">Loading simulation results…</p>
          </div>
        ) : error ? (
          <div className="rounded-xl border border-amber-900/50 bg-amber-950/20 p-6">
            <p className="text-amber-300 text-sm">{error}</p>
            <p className="text-gray-500 text-xs mt-2">
              To regenerate: <code className="text-gray-400">python3 scripts/extract-sim-results.py</code>
            </p>
          </div>
        ) : sorted.length === 0 ? (
          <div className="rounded-xl border border-gray-800 bg-gray-900/50 p-12 text-center">
            <p className="text-gray-400">No runs match the current filter.</p>
          </div>
        ) : (
          <>
            <div className="rounded-xl border border-gray-800 bg-gray-900/50 overflow-hidden overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-gray-900 border-b border-gray-800">
                  <tr>
                    <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Scenario</th>
                    <Th col="debtGdp" label="D/G" />
                    <Th col="gdp" label="GDP" />
                    <Th col="debt" label="Debt" />
                    <Th col="buyRatio" label="Buy%" />
                    <Th col="avgBpd" label="BPD%" />
                    <Th col="avgSpd" label="SPD%" />
                    <Th col="avgVolatility" label="Vol" />
                    <Th col="defaultRate" label="Def%" />
                    <th className="px-2 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Health</th>
                  </tr>
                </thead>
                <tbody>
                  {pageRows.map((r) => (
                    <tr
                      key={r.name}
                      className={`border-b border-gray-800/50 hover:bg-gray-800/30 transition-colors cursor-pointer ${expanded === r.name ? 'bg-emerald-950/10' : ''}`}
                      onClick={() => setExpanded(expanded === r.name ? null : r.name)}
                    >
                      <td className="px-3 py-2.5">
                        <div className="flex items-center gap-2">
                          <span className={`text-gray-500 text-xs transition-transform ${expanded === r.name ? 'rotate-90' : ''}`}>▶</span>
                          <span className="font-mono text-xs text-sky-300">{r.name}</span>
                        </div>
                      </td>
                      <td className="px-2 py-2.5">
                        <span className={`font-mono text-xs font-semibold ${debtGdpColor(r.debtGdp)}`}>
                          {r.debtGdp < 1000 ? fmt(r.debtGdp) : '—'}
                        </span>
                      </td>
                      <td className="px-2 py-2.5">
                        <span className="font-mono text-xs text-gray-300">
                          {r.gdp > 1000000 ? (r.gdp / 1000000).toFixed(1) + 'M' :
                           r.gdp > 1000 ? (r.gdp / 1000).toFixed(0) + 'k' :
                           fmt(r.gdp, 0)}
                        </span>
                      </td>
                      <td className="px-2 py-2.5">
                        <span className="font-mono text-xs text-gray-300">
                          {r.debt > 1000000 ? (r.debt / 1000000).toFixed(1) + 'M' :
                           r.debt > 1000 ? (r.debt / 1000).toFixed(0) + 'k' :
                           fmt(r.debt, 0)}
                        </span>
                      </td>
                      <td className="px-2 py-2.5">
                        <span className={`font-mono text-xs font-semibold ${
                          r.buyRatio >= 0.48 && r.buyRatio <= 0.52 ? 'text-emerald-400' :
                          r.buyRatio < 0.40 ? 'text-rose-400' : 'text-amber-400'
                        }`}>
                          {fmt(r.buyRatio * 100, 1)}%
                        </span>
                      </td>
                      <td className="px-2 py-2.5">
                        <span className={`font-mono text-xs ${
                          r.avgBpd < 0.03 ? 'text-emerald-400' :
                          r.avgBpd < 0.06 ? 'text-amber-400' : 'text-rose-400'
                        }`}>
                          {(r.avgBpd * 100).toFixed(2)}%
                        </span>
                      </td>
                      <td className="px-2 py-2.5">
                        <span className={`font-mono text-xs ${
                          r.avgSpd < 0.03 ? 'text-emerald-400' :
                          r.avgSpd < 0.06 ? 'text-amber-400' : 'text-rose-400'
                        }`}>
                          {(r.avgSpd * 100).toFixed(2)}%
                        </span>
                      </td>
                      <td className="px-2 py-2.5">
                        <VolatilityBar v={r.avgVolatility} />
                      </td>
                      <td className="px-2 py-2.5">
                        <span className={`font-mono text-xs ${
                          r.defaultRate < 1 ? 'text-emerald-400' :
                          r.defaultRate < 5 ? 'text-amber-400' : 'text-rose-400'
                        }`}>
                          {fmt(r.defaultRate, 1)}%
                        </span>
                      </td>
                      <td className="px-2 py-2.5">
                        <HealthBadge debtGdp={r.debtGdp} buyRatio={r.buyRatio} volatility={r.avgVolatility} />
                      </td>
                    </tr>
                  ))}
                  {pageRows.map((r) => expanded === r.name ? (
                    <tr key={`${r.name}-detail`}>
                      <td colSpan={10} className="p-0">
                        <DetailPanel r={r} />
                      </td>
                    </tr>
                  ) : null)}
                </tbody>
              </table>
            </div>

            {/* Pagination */}
            {totalPages > 1 && (
              <div className="flex items-center justify-between mt-4">
                <p className="text-xs text-gray-500">
                  Showing {page * PAGE_SIZE + 1}–{Math.min((page + 1) * PAGE_SIZE, sorted.length)} of {sorted.length}
                </p>
                <div className="flex gap-2">
                  <button
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    disabled={page === 0}
                    className="px-3 py-1 rounded-md border border-gray-700 text-xs text-gray-300 hover:border-emerald-700 hover:text-emerald-400 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                  >
                    ← prev
                  </button>
                  <span className="px-3 py-1 text-xs text-gray-500">{page + 1} / {totalPages}</span>
                  <button
                    onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                    disabled={page >= totalPages - 1}
                    className="px-3 py-1 rounded-md border border-gray-700 text-xs text-gray-300 hover:border-emerald-700 hover:text-emerald-400 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                  >
                    next →
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </main>
      <Footer />
    </div>
  );
}
