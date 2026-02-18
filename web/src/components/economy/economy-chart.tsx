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
} from 'recharts';
import type { EconomySnapshotDto } from '@/lib/api';
import { formatShortTime, formatShortDate } from '@/lib/format';

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

interface EconomyChartProps {
  history: EconomySnapshotDto[];
}

export function EconomyChart({ history }: EconomyChartProps) {
  const [period, setPeriod] = useState<Period>('7d');
  const [activeMetrics, setActiveMetrics] = useState<Set<Metric>>(new Set(['gdp', 'averagePriceChange']));

  const filteredData = useMemo(() => {
    const cutoff = Date.now() - PERIOD_MS[period];
    return history.filter((s) => s.timestamp >= cutoff);
  }, [history, period]);

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
      </CardContent>
    </Card>
  );
}
