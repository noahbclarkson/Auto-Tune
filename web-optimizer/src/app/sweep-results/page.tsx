'use client';

import { useState, useMemo } from 'react';
import { Header } from '@/components/layout/header';
import { Footer } from '@/components/layout/footer';

interface SweepRow {
  sell_pressure_multiplier: number;
  base_spread: number;
  max_price_change_percent: number;
  trend_dampening: number;
  avg_price_displacement_pct: number;
  avg_volatility: number;
  avg_final_bpd_pct: number;
  avg_final_spd_pct: number;
  final_gdp: number;
  final_debt: number;
  debt_gdp_ratio: number;
  total_tx: number;
  buy_ratio: number;
  stable: number;
  active_loans: number;
  defaulted_loans: number;
}

type SortKey = keyof SweepRow;
type SortDir = 'asc' | 'desc';

function fmt(v: number, decimals = 2) {
  return v.toFixed(decimals);
}

function MetricPill({ label, value, color }: { label: string; value: string; color: string }) {
  return (
    <span className={`inline-flex items-center px-1.5 py-0.5 rounded text-xs font-mono ${color}`}>
      {value} <span className="text-gray-500 ml-1">{label}</span>
    </span>
  );
}

export default function SweepResultsPage() {
  const [minBuyRatio, setMinBuyRatio] = useState(0);
  const [maxVolatility, setMaxVolatility] = useState(0.05);
  const [maxDebtGdp, setMaxDebtGdp] = useState(10);
  const [stableOnly, setStableOnly] = useState(false);
  const [sortKey, setSortKey] = useState<SortKey>('buy_ratio');
  const [sortDir, setSortDir] = useState<SortDir>('asc');
  const [page, setPage] = useState(0);
  const PAGE_SIZE = 20;

  const [data, setData] = useState<SweepRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useState(() => {
    fetch('/sweep-results.json')
      .then((r) => r.text())
      .then((txt) => {
        const lines = txt.trim().split('\n');
        // Skip metadata lines (lines that don't start with a number)
        const dataLines = lines.filter((l) => /^\d/.test(l.trim()));
        const header = 'sell_pressure_multiplier,base_spread,max_price_change_percent,trend_dampening,avg_price_displacement_pct,avg_volatility,avg_final_bpd_pct,avg_final_spd_pct,final_gdp,final_debt,debt_gdp_ratio,total_tx,buy_ratio,stable,active_loans,defaulted_loans'.split(',');
        const rows: SweepRow[] = [];
        for (const line of dataLines) {
          const vals = line.split(',');
          if (vals.length < header.length) continue;
          const row: Record<string, number> = {};
          header.forEach((h, i) => { row[h] = parseFloat(vals[i]); });
          rows.push(row as unknown as SweepRow);
        }
        setData(rows);
        setLoading(false);
      })
      .catch(() => {
        setError('Could not load sweep results. Run the simulation sweep first.');
        setLoading(false);
      });
  });

  const filtered = useMemo(() => {
    return data.filter((r) => {
      if (stableOnly && r.stable < 0.5) return false;
      if (r.buy_ratio < minBuyRatio) return false;
      if (r.avg_volatility > maxVolatility) return false;
      if (r.debt_gdp_ratio > maxDebtGdp) return false;
      return true;
    });
  }, [data, stableOnly, minBuyRatio, maxVolatility, maxDebtGdp]);

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
            Parameter Sweep Results
          </h1>
          <p className="text-gray-400 max-w-3xl text-sm leading-relaxed">
            840 configurations tested across sell_pressure (0.5–1.0), base_spread (0.10–0.30),
            max_price_change (0.5–2.0), and trend_dampening (0.0–0.15). Each config runs a 7-day
            standard-economy scenario with 11 players. Filter and sort to find settings that match
            your desired economy behaviour.
          </p>
        </div>

        {/* Stats */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-6">
          {[
            { label: 'Total configs', value: data.length.toString() },
            { label: 'Showing', value: filtered.length.toString() },
            { label: 'Stable', value: data.filter((r) => r.stable >= 0.5).length.toString() },
            { label: 'Per page', value: PAGE_SIZE.toString() },
          ].map(({ label, value }) => (
            <div key={label} className="rounded-lg border border-gray-800 bg-gray-900/50 px-4 py-3">
              <p className="text-xs text-gray-500 uppercase tracking-wider mb-1">{label}</p>
              <p className="text-xl font-bold font-mono text-white">{value}</p>
            </div>
          ))}
        </div>

        {/* Filters */}
        <div className="rounded-xl border border-gray-800 bg-gray-900/50 p-4 mb-6">
          <p className="text-xs text-gray-500 uppercase tracking-wider mb-3 font-medium">Filters</p>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div>
              <label className="block text-xs text-gray-400 mb-1.5">
                Min buy ratio: <span className="text-emerald-400 font-mono">{fmt(minBuyRatio * 100, 0)}%</span>
              </label>
              <input
                type="range"
                min="0" max="1" step="0.01"
                value={minBuyRatio}
                onChange={(e) => { setMinBuyRatio(parseFloat(e.target.value)); setPage(0); }}
                className="w-full accent-emerald-500"
              />
            </div>
            <div>
              <label className="block text-xs text-gray-400 mb-1.5">
                Max volatility: <span className="text-emerald-400 font-mono">{maxVolatility}</span>
              </label>
              <input
                type="range"
                min="0" max="0.20" step="0.005"
                value={maxVolatility}
                onChange={(e) => { setMaxVolatility(parseFloat(e.target.value)); setPage(0); }}
                className="w-full accent-emerald-500"
              />
            </div>
            <div>
              <label className="block text-xs text-gray-400 mb-1.5">
                Max debt/GDP: <span className="text-emerald-400 font-mono">{fmt(maxDebtGdp)}×</span>
              </label>
              <input
                type="range"
                min="0" max="500" step="5"
                value={maxDebtGdp}
                onChange={(e) => { setMaxDebtGdp(parseFloat(e.target.value)); setPage(0); }}
                className="w-full accent-emerald-500"
              />
            </div>
            <div className="flex items-end">
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={stableOnly}
                  onChange={(e) => { setStableOnly(e.target.checked); setPage(0); }}
                  className="accent-emerald-600 w-4 h-4"
                />
                <span className="text-sm text-gray-300">Stable only (vol &lt; 0.05)</span>
              </label>
            </div>
          </div>

          {/* Quick presets */}
          <div className="mt-3 pt-3 border-t border-gray-800 flex flex-wrap gap-2">
            <span className="text-xs text-gray-500">Presets:</span>
            {[
              { label: 'Balanced economy', fn: () => { setMinBuyRatio(0.45); setMaxVolatility(0.05); setMaxDebtGdp(5); setStableOnly(false); setPage(0); } },
              { label: 'Low debt', fn: () => { setMinBuyRatio(0); setMaxVolatility(0.05); setMaxDebtGdp(3); setStableOnly(false); setPage(0); } },
              { label: 'Most stable', fn: () => { setMinBuyRatio(0); setMaxVolatility(0.01); setMaxDebtGdp(500); setStableOnly(true); setPage(0); } },
              { label: 'Reset', fn: () => { setMinBuyRatio(0); setMaxVolatility(0.05); setMaxDebtGdp(10); setStableOnly(false); setPage(0); } },
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

        {/* Table */}
        {loading ? (
          <div className="rounded-xl border border-gray-800 bg-gray-900/50 p-12 text-center">
            <p className="text-gray-400">Loading sweep results…</p>
          </div>
        ) : error ? (
          <div className="rounded-xl border border-amber-900/50 bg-amber-950/20 p-6">
            <p className="text-amber-300 text-sm">{error}</p>
            <p className="text-gray-500 text-xs mt-2">
              To regenerate: <code className="text-gray-400">cd scripts/market-simulation && cargo run --release -- --sweep</code> (then convert to JSON)
            </p>
          </div>
        ) : (
          <>
            <div className="rounded-xl border border-gray-800 bg-gray-900/50 overflow-hidden overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-gray-900 border-b border-gray-800">
                  <tr>
                    <Th col="sell_pressure_multiplier" label="sp" />
                    <Th col="base_spread" label="bs" />
                    <Th col="max_price_change_percent" label="mc" />
                    <Th col="trend_dampening" label="td" />
                    <Th col="buy_ratio" label="buy%" />
                    <Th col="avg_volatility" label="vol" />
                    <Th col="avg_price_displacement_pct" label="disp%" />
                    <Th col="avg_final_bpd_pct" label="bpd%" />
                    <Th col="debt_gdp_ratio" label="D/G" />
                    <Th col="final_gdp" label="GDP" />
                    <Th col="total_tx" label="tx" />
                    <Th col="stable" label="stable" />
                  </tr>
                </thead>
                <tbody>
                  {pageRows.map((r, i) => (
                    <tr
                      key={i}
                      className={`border-b border-gray-800/50 hover:bg-gray-800/30 transition-colors ${r.stable < 0.5 ? 'bg-amber-950/10' : ''}`}
                    >
                      <td className="px-2 py-2 font-mono text-xs text-sky-300">{fmt(r.sell_pressure_multiplier)}</td>
                      <td className="px-2 py-2 font-mono text-xs text-gray-300">{fmt(r.base_spread)}</td>
                      <td className="px-2 py-2 font-mono text-xs text-gray-300">{fmt(r.max_price_change_percent)}</td>
                      <td className="px-2 py-2 font-mono text-xs text-gray-300">{fmt(r.trend_dampening, 3)}</td>
                      <td className="px-2 py-2">
                        <span className={`font-mono text-xs font-semibold ${r.buy_ratio >= 0.48 && r.buy_ratio <= 0.52 ? 'text-emerald-400' : r.buy_ratio < 0.40 ? 'text-rose-400' : 'text-amber-400'}`}>
                          {fmt(r.buy_ratio * 100, 1)}%
                        </span>
                      </td>
                      <td className="px-2 py-2">
                        <span className={`font-mono text-xs ${r.avg_volatility < 0.05 ? 'text-emerald-400' : 'text-amber-400'}`}>
                          {r.avg_volatility.toFixed(4)}
                        </span>
                      </td>
                      <td className="px-2 py-2 font-mono text-xs text-gray-300">{fmt(r.avg_price_displacement_pct)}%</td>
                      <td className="px-2 py-2 font-mono text-xs text-gray-300">{fmt(r.avg_final_bpd_pct)}%</td>
                      <td className="px-2 py-2">
                        <span className={`font-mono text-xs font-semibold ${r.debt_gdp_ratio < 1 ? 'text-emerald-400' : r.debt_gdp_ratio < 5 ? 'text-amber-400' : 'text-rose-400'}`}>
                          {r.debt_gdp_ratio < 1000 ? fmt(r.debt_gdp_ratio) : '∞'}
                        </span>
                      </td>
                      <td className="px-2 py-2 font-mono text-xs text-gray-300">
                        {r.final_gdp >= 1e6 ? `${(r.final_gdp / 1e6).toFixed(1)}M` : r.final_gdp >= 1000 ? `${(r.final_gdp / 1000).toFixed(0)}k` : fmt(r.final_gdp, 0)}
                      </td>
                      <td className="px-2 py-2 font-mono text-xs text-gray-500">{r.total_tx.toLocaleString()}</td>
                      <td className="px-2 py-2">
                        {r.stable >= 0.5
                          ? <span className="text-emerald-400 text-xs font-medium">✓</span>
                          : <span className="text-amber-500 text-xs">—</span>}
                      </td>
                    </tr>
                  ))}
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
                    className="px-3 py-1.5 rounded border border-gray-700 text-xs text-gray-300 disabled:opacity-30 hover:border-emerald-700 transition-colors"
                  >
                    ← prev
                  </button>
                  {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
                    const p = Math.max(0, Math.min(totalPages - 5, page - 2)) + i;
                    return (
                      <button
                        key={p}
                        onClick={() => setPage(p)}
                        className={`px-3 py-1.5 rounded border text-xs transition-colors ${p === page ? 'border-emerald-700 text-emerald-400' : 'border-gray-700 text-gray-300 hover:border-emerald-700'}`}
                      >
                        {p + 1}
                      </button>
                    );
                  })}
                  <button
                    onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                    disabled={page >= totalPages - 1}
                    className="px-3 py-1.5 rounded border border-gray-700 text-xs text-gray-300 disabled:opacity-30 hover:border-emerald-700 transition-colors"
                  >
                    next →
                  </button>
                </div>
              </div>
            )}

            {/* Key findings */}
            <div className="mt-8 rounded-xl border border-gray-800 bg-gray-900/40 p-5">
              <h3 className="text-sm font-semibold text-white mb-3">Key findings from 840-config sweep</h3>
              <ul className="space-y-1.5 text-sm text-gray-400">
                <li>• <span className="text-gray-300">All 840 configs are stable</span> (volatility &lt; 0.05) — the engine is mathematically robust</li>
                <li>• <span className="text-gray-300">Underselling is structural</span>: even the best configs have 34–63% price displacement below base</li>
                <li>• <span className="text-emerald-400">Best buy ratio</span>: sp=0.80, bs=0.25, mc=1.5 → 62.3% buy ratio, stable</li>
                <li>• <span className="text-emerald-400">Least displacement</span>: sp=0.80, bs=0.15, mc=0.75, td=0.10 → −33.9%, 45.1% buy ratio, stable</li>
                <li>• <span className="text-gray-300">sp=0.80 is the sweet spot</span> for balanced economies — consistently triggers tier3 circuit breaker if debt builds</li>
                <li>• <span className="text-gray-300">Debt/GDP below 1×</span> requires buyer-heavy player mix (Hoarders + GuildBuyers)</li>
              </ul>
            </div>
          </>
        )}
      </main>
      <Footer />
    </div>
  );
}
