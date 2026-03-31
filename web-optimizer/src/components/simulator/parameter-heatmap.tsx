'use client';

import { useState, useMemo } from 'react';

export interface SweepRow {
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

type MetricKey = 'buy_ratio' | 'avg_volatility' | 'debt_gdp_ratio' | 'avg_price_displacement_pct' | 'final_gdp';

const METRICS: { key: MetricKey; label: string; format: (v: number) => string; low: string; high: string; invert?: boolean }[] = [
  {
    key: 'buy_ratio',
    label: 'Buy Ratio',
    format: (v) => `${(v * 100).toFixed(1)}%`,
    low: 'Sell-heavy',
    high: 'Buy-heavy',
  },
  {
    key: 'avg_volatility',
    label: 'Volatility',
    format: (v) => v.toFixed(4),
    low: 'Stable',
    high: 'Unstable',
    invert: true,
  },
  {
    key: 'debt_gdp_ratio',
    label: 'Debt / GDP',
    format: (v) => v < 100 ? v.toFixed(2) + '×' : '∞',
    low: 'Healthy',
    high: 'Distressed',
    invert: true,
  },
  {
    key: 'avg_price_displacement_pct',
    label: 'Price Displacement',
    format: (v) => `${v >= 0 ? '+' : ''}${v.toFixed(1)}%`,
    low: 'Fair / overvalued',
    high: 'Underpriced',
  },
  {
    key: 'final_gdp',
    label: 'GDP',
    format: (v) => v >= 1e6 ? `${(v / 1e6).toFixed(2)}M` : `${(v / 1e3).toFixed(0)}k`,
    low: 'Low',
    high: 'High',
  },
];

const PARAM_CONFIGS: { key: keyof SweepRow; label: string; unit: string; values: number[] }[] = [
  { key: 'sell_pressure_multiplier', label: 'sell_pressure', unit: '', values: [] },
  { key: 'base_spread', label: 'base_spread', unit: '', values: [] },
  { key: 'max_price_change_percent', label: 'max_change', unit: '%', values: [] },
  { key: 'trend_dampening', label: 'trend_damp', unit: '', values: [] },
];

function colorFor(normalized: number, invert: boolean): string {
  // normalized 0 = low (good), 1 = high (bad or good depending on invert)
  const t = invert ? normalized : 1 - normalized;
  if (t < 0.33) return 'bg-rose-500';
  if (t < 0.66) return 'bg-amber-500';
  return 'bg-emerald-500';
}

function textColorFor(normalized: number, invert: boolean): string {
  const t = invert ? normalized : 1 - normalized;
  if (t < 0.33) return 'text-rose-200';
  if (t < 0.66) return 'text-amber-200';
  return 'text-emerald-200';
}

interface HeatmapCellProps {
  value: number;
  normalized: number;
  format: (v: number) => string;
  label: string;
  invert: boolean;
}

function HeatmapCell({ value, normalized, format, label, invert }: HeatmapCellProps) {
  const bg = colorFor(normalized, invert);
  const fg = textColorFor(normalized, invert);
  return (
    <div
      className={`relative flex flex-col items-center justify-center rounded-sm ${bg} transition-all hover:scale-105 hover:z-10 cursor-default group`}
      title={`${label}: ${format(value)}`}
    >
      <span className={`text-[10px] font-mono font-bold leading-tight ${fg}`}>
        {format(value)}
      </span>
      <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-1 hidden group-hover:block z-50 pointer-events-none">
        <div className="bg-gray-900 border border-gray-700 rounded px-2 py-1 text-xs text-white whitespace-nowrap shadow-lg">
          {label}: <span className="font-mono text-emerald-400">{format(value)}</span>
        </div>
      </div>
    </div>
  );
}

interface ParamHeatmapProps {
  data: SweepRow[];
  paramKey: keyof SweepRow;
  paramLabel: string;
  metric: MetricKey;
  allValues: number[];
}

function ParamHeatmap({ data, paramKey, paramLabel, metric, allValues }: ParamHeatmapProps) {
  const meta = METRICS.find((m) => m.key === metric)!;

  // For each value of this param, average the metric across all other params
  const grouped = useMemo(() => {
    const map = new Map<number, number[]>();
    for (const row of data) {
      const key = row[paramKey] as number;
      const val = row[metric] as number;
      if (!isFinite(val)) continue;
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(val);
    }
    return Array.from(map.entries())
      .sort(([a], [b]) => (a as number) - (b as number))
      .map(([val, vals]) => ({ value: val as number, avg: vals.reduce((s, v) => s + v, 0) / vals.length, count: vals.length }));
  }, [data, paramKey, metric]);

  const allAvgs = grouped.map((g) => g.avg);
  const minAvg = Math.min(...allAvgs);
  const maxAvg = Math.max(...allAvgs);
  const range = maxAvg - minAvg || 1;

  return (
    <div className="flex flex-col gap-1">
      <div className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-1">
        {paramLabel}
      </div>
      <div className="flex gap-1">
        {grouped.map(({ value, avg, count }) => {
          const normalized = range > 0 ? (avg - minAvg) / range : 0;
          return (
            <div key={value} className="flex flex-col items-center gap-0.5 flex-1 min-w-0">
              <div className="w-full h-10">
                <HeatmapCell
                  value={avg}
                  normalized={normalized}
                  format={meta.format}
                  label={`${paramLabel}=${value}`}
                  invert={meta.invert ?? false}
                />
              </div>
              <span className="text-[9px] text-gray-500 font-mono truncate w-full text-center" title={`n=${count}`}>
                {value}{PARAM_CONFIGS.find((p) => p.key === paramKey)?.unit ?? ''}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

interface ParameterHeatmapProps {
  data: SweepRow[];
}

export function ParameterHeatmap({ data }: ParameterHeatmapProps) {
  const [metric, setMetric] = useState<MetricKey>('buy_ratio');

  const meta = METRICS.find((m) => m.key === metric)!;

  // Collect all unique values for each param
  const paramValues = useMemo(() => {
    const result: Record<string, number[]> = {};
    for (const cfg of PARAM_CONFIGS) {
      const vals = Array.from(new Set(data.map((r) => r[cfg.key] as number))).sort((a, b) => a - b);
      result[cfg.key] = vals;
    }
    return result;
  }, [data]);

  return (
    <div className="rounded-xl border border-gray-800 bg-gray-900/50 p-5">
      <div className="flex flex-wrap items-center justify-between gap-4 mb-5">
        <div>
          <h3 className="text-base font-semibold text-white">Parameter Impact Heatmap</h3>
          <p className="text-xs text-gray-500 mt-0.5">
            How each parameter affects{' '}
            <span className="text-emerald-400 font-medium">{meta.label}</span>
            {' '}(averaged across all other parameters)
          </p>
        </div>
        {/* Metric selector */}
        <div className="flex flex-wrap gap-1.5">
          {METRICS.map((m) => (
            <button
              key={m.key}
              onClick={() => setMetric(m.key)}
              className={`px-2.5 py-1 rounded-md text-xs font-medium transition-colors ${
                metric === m.key
                  ? 'bg-emerald-700/60 text-emerald-300 border border-emerald-600/50'
                  : 'bg-gray-800 text-gray-400 border border-gray-700 hover:border-gray-600 hover:text-gray-300'
              }`}
            >
              {m.label}
            </button>
          ))}
        </div>
      </div>

      {/* Legend */}
      <div className="flex items-center gap-4 mb-4 text-[10px] text-gray-500">
        <span>Low ({meta.low}):</span>
        <div className="flex gap-0.5">
          <div className="w-5 h-3 rounded-sm bg-rose-500" />
          <div className="w-5 h-3 rounded-sm bg-amber-500" />
          <div className="w-5 h-3 rounded-sm bg-emerald-500" />
        </div>
        <span>High ({meta.high}):</span>
        <span className="ml-2 text-gray-600">|</span>
        <span className="text-gray-600">Hover cell for exact value + count</span>
      </div>

      {/* Heatmaps grid — 2×2 for 4 parameters */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
        {PARAM_CONFIGS.map((cfg) => (
          <ParamHeatmap
            key={cfg.key}
            data={data}
            paramKey={cfg.key}
            paramLabel={cfg.label}
            metric={metric}
            allValues={paramValues[cfg.key] ?? []}
          />
        ))}
      </div>

      {/* Key observations for current metric */}
      <div className="mt-5 pt-4 border-t border-gray-800">
        <h4 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">
          What this means for {meta.label}
        </h4>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs text-gray-500">
          {PARAM_CONFIGS.map((cfg) => {
            const vals = Array.from(new Set(data.map((r) => r[cfg.key] as number))).sort((a, b) => a - b);
            // Find which end of the spectrum is better
            const grouped = vals.map((v) => ({
              v,
              avg: data.filter((r) => r[cfg.key] === v).reduce((s, r) => s + (r[metric] as number), 0) /
                data.filter((r) => r[cfg.key] === v).length,
            }));
            const best = meta.invert
              ? grouped.reduce((a, b) => (a.avg < b.avg ? a : b))
              : grouped.reduce((a, b) => (a.avg > b.avg ? a : b));
            const worst = meta.invert
              ? grouped.reduce((a, b) => (a.avg > b.avg ? a : b))
              : grouped.reduce((a, b) => (a.avg < b.avg ? a : b));
            return (
              <div key={cfg.key} className="flex items-center gap-2">
                <span className="text-gray-400 font-medium min-w-[80px]">{cfg.label}:</span>
                <span className="text-emerald-400">Best ≈ {best.v}{cfg.unit}</span>
                <span className="text-gray-600">→</span>
                <span className="text-amber-400">Worst ≈ {worst.v}{cfg.unit}</span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
