'use client';

import { useMemo } from 'react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  ReferenceLine,
} from 'recharts';
import type { PnLHistoryDto } from '@/lib/api';
import { formatCurrency } from '@/lib/format';

interface PnLHistoryChartProps {
  history: PnLHistoryDto[];
}

function CustomTooltip({ active, payload, label }: { active?: boolean; payload?: { value: number }[]; label?: string }) {
  if (!active || !payload?.length) return null;
  const value = payload[0].value;
  const isProfit = value >= 0;
  return (
    <div className="bg-background border border-border rounded-lg shadow-md px-3 py-2 text-sm">
      <p className="text-muted-foreground mb-1">{label}</p>
      <p className={`font-semibold ${isProfit ? 'text-emerald-500' : 'text-red-500'}`}>
        {isProfit ? '+' : ''}{formatCurrency(value)}
      </p>
    </div>
  );
}

export function PnLHistoryChart({ history }: PnLHistoryChartProps) {
  const data = useMemo(() => history.map(h => ({
    label: h.dayLabel,
    value: h.netPnl,
  })), [history]);

  const minVal = useMemo(() => Math.min(...data.map(d => d.value), 0), [data]);
  const maxVal = useMemo(() => Math.max(...data.map(d => d.value), 0), [data]);
  const domainMin = useMemo(() => {
    if (minVal >= 0) return Math.min(0, minVal * 1.1);
    return minVal * 1.15;
  }, [minVal]);
  const domainMax = useMemo(() => {
    if (maxVal <= 0) return Math.max(0, maxVal * 1.1);
    return maxVal * 1.15;
  }, [maxVal]);

  const firstNonZero = data.findIndex(d => d.value !== 0);

  if (data.length === 0) {
    return (
      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-base">P&amp;L History</CardTitle>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          No trading history yet. Start buying and selling to see your P&amp;L trend here.
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-base">P&amp;L History</CardTitle>
        <p className="text-xs text-muted-foreground">
          Cumulative realized P&amp;L — sells minus buys, using average cost basis per item
        </p>
      </CardHeader>
      <CardContent>
        <div className="h-48">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" className="stroke-border" />
              <XAxis
                dataKey="label"
                tick={{ fontSize: 11 }}
                tickLine={false}
                axisLine={false}
                interval="preserveStartEnd"
                minTickGap={40}
              />
              <YAxis
                tick={{ fontSize: 11 }}
                tickLine={false}
                axisLine={false}
                tickFormatter={(v) => {
                  const abs = Math.abs(v);
                  if (abs >= 1000000) return (v / 1000000).toFixed(1) + "M";
                  if (abs >= 1000) return (v / 1000).toFixed(1) + "K";
                  return formatCurrency(v);
                }}
                domain={[domainMin, domainMax]}
                width={64}
              />
              <Tooltip content={<CustomTooltip />} />
              {firstNonZero > 0 && (
                <ReferenceLine
                  x={data[firstNonZero]?.label}
                  stroke="currentColor"
                  strokeDasharray="4 4"
                  className="text-muted-foreground"
                  label={{ value: 'First trade', position: 'insideTopRight', fontSize: 10, fill: 'currentColor' }}
                />
              )}
              <Line
                type="monotone"
                dataKey="value"
                stroke="#10b981"
                strokeWidth={2}
                dot={{ r: 3, fill: '#10b981', strokeWidth: 0 }}
                activeDot={{ r: 5 }}
                connectNulls={false}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
        <div className="flex justify-end mt-2">
          <p className="text-xs text-muted-foreground">
            {data.length} day{data.length !== 1 ? 's' : ''} of history
          </p>
        </div>
      </CardContent>
    </Card>
  );
}
