'use client';

import { useState, useMemo } from 'react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
  ReferenceLine,
} from 'recharts';
import type { EconomySnapshotDto, CircuitEventDto } from '@/lib/api';
import { formatLargeCurrency, formatShortTime, formatShortDate } from '@/lib/format';

type Period = '24h' | '7d' | '30d';
type Metric = 'gdp' | 'averagePriceChange' | 'totalDebt' | 'transactionVolume';

const PERIOD_MS: Record<Period, number> = {
  '24h': 24 * 60 * 60 * 1000,
  '7d': 7 * 24 * 60 * 60 * 1000,
  '30d': 30 * 24 * 60 * 60 * 1000,
};

const METRIC_LABELS: Record<Metric, string> = {
  gdp: 'GDP',
  averagePriceChange: 'Inflation',
  totalDebt: 'Total Debt',
  transactionVolume: 'Transaction Volume',
};

const METRIC_COLORS: Record<Metric, string> = {
  gdp: '#3b82f6',
  averagePriceChange: '#a855f7',
  totalDebt: '#ef4444',
  transactionVolume: '#10b981',
};

const TIER_COLORS: Record<string, string> = {
  NORMAL: '#10b981',
  TIER1: '#f59e0b',
  TIER2: '#f97316',
  TIER3: '#ef4444',
  ADMIN_RECOVERY: '#a855f7',
};

function tierLabel(tier: string) {
  return tier === 'ADMIN_RECOVERY' ? 'Recovery' : tier;
}

function formatDebtGdpRatio(ratio: number) {
  if (!Number.isFinite(ratio)) return 'D/G --';
  return `D/G ${ratio >= 10 ? ratio.toFixed(1) : ratio.toFixed(2)}×`;
}

function formatInterestMultiplier(multiplier: number) {
  if (!Number.isFinite(multiplier)) return 'interest --';
  return `${multiplier.toFixed(multiplier < 0.1 ? 2 : 1)}× interest`;
}

function eventTone(event: CircuitEventDto) {
  if (event.newTier === 'ADMIN_RECOVERY') {
    return 'border-purple-500/30 bg-purple-500/10 text-purple-700 dark:text-purple-300';
  }
  if (event.newTier === 'TIER3' || event.debtGdpRatio >= 30) {
    return 'border-red-500/30 bg-red-500/10 text-red-600 dark:text-red-300';
  }
  if (event.newTier === 'TIER2' || event.debtGdpRatio >= 15) {
    return 'border-orange-500/30 bg-orange-500/10 text-orange-600 dark:text-orange-300';
  }
  if (event.newTier === 'TIER1' || event.debtGdpRatio >= 5) {
    return 'border-amber-500/30 bg-amber-500/10 text-amber-600 dark:text-amber-300';
  }
  return 'border-emerald-500/30 bg-emerald-500/10 text-emerald-600 dark:text-emerald-300';
}

function eventGuidance(event: CircuitEventDto) {
  if (event.newTier === 'ADMIN_RECOVERY') {
    return event.adminInitiated
      ? 'Manual recovery active — early intervention performs best.'
      : 'Recovery active — watch D/G before reopening loans.';
  }
  if (event.newTier === 'TIER3') {
    return event.debtGdpRatio >= 30
      ? 'Interest paused. Start recovery if D/G stays above 15×.'
      : 'Circuit engaged — watch for a rebound after unlock.';
  }
  if (event.newTier === 'TIER2') {
    return 'High debt pressure — prepare recovery if trend keeps rising.';
  }
  if (event.newTier === 'TIER1') {
    return 'Early warning — monitor debt and loan defaults.';
  }
  if (event.newTier === 'NORMAL') {
    return event.previousTier === 'TIER3' || event.previousTier === 'ADMIN_RECOVERY'
      ? 'Circuit cleared — monitor the next 7 days for relapse.'
      : 'Stable — no admin action needed.';
  }
  return 'Review details before changing economy settings.';
}

function eventTitle(event: CircuitEventDto) {
  const details = event.details ? `\n${event.details}` : '';
  return [
    `${event.previousTier ?? 'START'} → ${tierLabel(event.newTier)}`,
    formatDebtGdpRatio(event.debtGdpRatio),
    `Debt ${formatLargeCurrency(event.totalDebt)} / GDP ${formatLargeCurrency(event.gdp)}`,
    formatInterestMultiplier(event.interestMultiplier),
  ].join('\n') + details;
}

interface EconomyChartProps {
  history: EconomySnapshotDto[];
  circuitEvents?: CircuitEventDto[];
}

export function EconomyChart({ history, circuitEvents = [] }: EconomyChartProps) {
  const [period, setPeriod] = useState<Period>('7d');
  const [activeMetrics, setActiveMetrics] = useState<Set<Metric>>(
    new Set<Metric>(['gdp', 'averagePriceChange']),
  );

  const filteredData = useMemo(() => {
    const cutoff = Date.now() - PERIOD_MS[period];
    return history.filter((s) => s.timestamp >= cutoff);
  }, [history, period]);

  const filteredEvents = useMemo(() => {
    const cutoff = Date.now() - PERIOD_MS[period];
    return circuitEvents.filter((event) => event.timestamp >= cutoff);
  }, [circuitEvents, period]);

  function toggleMetric(metric: Metric) {
    setActiveMetrics((prev) => {
      const next = new Set(prev);
      if (next.has(metric)) {
        if (next.size > 1) next.delete(metric);
      } else {
        next.add(metric);
      }
      return next;
    });
  }

  const periods: Period[] = ['24h', '7d', '30d'];
  const allMetrics: Metric[] = ['gdp', 'averagePriceChange', 'totalDebt', 'transactionVolume'];

  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between flex-wrap gap-2">
          <CardTitle className="text-base">Economy History</CardTitle>
          <div className="flex gap-1">
            {periods.map((p) => (
              <button
                key={p}
                onClick={() => setPeriod(p)}
                className={`rounded px-2 py-0.5 text-xs font-medium transition-colors ${
                  period === p
                    ? 'bg-primary text-primary-foreground'
                    : 'text-muted-foreground hover:bg-muted hover:text-foreground'
                }`}
              >
                {p}
              </button>
            ))}
          </div>
        </div>
        <div className="flex flex-wrap gap-2 pt-2">
          {allMetrics.map((metric) => (
            <button
              key={metric}
              onClick={() => toggleMetric(metric)}
              className={`rounded-md px-2.5 py-1 text-xs font-medium transition-colors border ${
                activeMetrics.has(metric)
                  ? 'border-transparent text-white'
                  : 'border-border text-muted-foreground hover:bg-muted'
              }`}
              style={
                activeMetrics.has(metric)
                  ? { backgroundColor: METRIC_COLORS[metric] }
                  : undefined
              }
            >
              {METRIC_LABELS[metric]}
            </button>
          ))}
        </div>
      </CardHeader>
      <CardContent>
        {filteredData.length === 0 ? (
          <div className="flex h-72 items-center justify-center text-sm text-muted-foreground">
            No data for this period
          </div>
        ) : (
          <div className="h-72">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={filteredData}>
                <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
                <XAxis
                  dataKey="timestamp"
                  tickFormatter={(ts) =>
                    period === '24h' ? formatShortTime(ts) : formatShortDate(ts)
                  }
                  stroke="hsl(var(--muted-foreground))"
                  fontSize={11}
                />
                <YAxis stroke="hsl(var(--muted-foreground))" fontSize={11} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: 'hsl(var(--card))',
                    border: '1px solid hsl(var(--border))',
                    borderRadius: '8px',
                    fontSize: '12px',
                  }}
                  labelFormatter={(ts) => new Date(ts).toLocaleString()}
                />
                <Legend
                  verticalAlign="top"
                  height={24}
                  iconType="line"
                  wrapperStyle={{ fontSize: 11 }}
                />
                {filteredEvents.map((event) => (
                  <ReferenceLine
                    key={event.id}
                    x={event.timestamp}
                    stroke={TIER_COLORS[event.newTier] ?? '#64748b'}
                    strokeDasharray="3 3"
                    ifOverflow="extendDomain"
                  />
                ))}
                {allMetrics.map(
                  (metric) =>
                    activeMetrics.has(metric) && (
                      <Line
                        key={metric}
                        type="monotone"
                        dataKey={metric}
                        name={METRIC_LABELS[metric]}
                        stroke={METRIC_COLORS[metric]}
                        dot={false}
                        strokeWidth={2}
                        isAnimationActive={false}
                      />
                    ),
                )}
              </LineChart>
            </ResponsiveContainer>
          </div>
        )}
        {filteredEvents.length > 0 && (
          <div className="mt-3 space-y-2 text-xs">
            <p className="font-medium text-muted-foreground">Recent circuit events</p>
            <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
            {filteredEvents.slice(-8).map((event) => (
              <div
                key={event.id}
                className={`rounded-lg border px-3 py-2 ${eventTone(event)}`}
                title={eventTitle(event)}
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="font-semibold">
                    <span
                      className="mr-1.5 inline-block h-2 w-2 rounded-full"
                      style={{ backgroundColor: TIER_COLORS[event.newTier] ?? '#64748b' }}
                    />
                    {event.previousTier ?? 'START'} → {tierLabel(event.newTier)}
                  </span>
                  <span className="text-[11px] opacity-80">{formatShortDate(event.timestamp)}</span>
                </div>
                <div className="mt-1 flex flex-wrap gap-x-3 gap-y-1 font-medium text-[11px] opacity-90">
                  <span>{formatDebtGdpRatio(event.debtGdpRatio)}</span>
                  <span>{formatInterestMultiplier(event.interestMultiplier)}</span>
                </div>
                <p className="mt-1 leading-snug text-[11px] opacity-80">{eventGuidance(event)}</p>
              </div>
            ))}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
