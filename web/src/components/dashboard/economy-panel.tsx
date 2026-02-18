'use client';

import { useEffect, useState, useCallback } from 'react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';
import type { EconomySnapshotDto, GdpData, InflationData, DebtData } from '@/lib/api';
import { formatCurrency } from '@/lib/format';

interface EconomyPanelProps {
  apiBase: string;
}

export function EconomyPanel({ apiBase }: EconomyPanelProps) {
  const [gdp, setGdp] = useState<GdpData | null>(null);
  const [inflation, setInflation] = useState<InflationData | null>(null);
  const [debt, setDebt] = useState<DebtData | null>(null);
  const [history, setHistory] = useState<EconomySnapshotDto[]>([]);

  const fetchData = useCallback(() => {
    Promise.all([
      fetch(`${apiBase}/api/economy/gdp`).then((r) => (r.ok ? r.json() : null)),
      fetch(`${apiBase}/api/economy/inflation`).then((r) => (r.ok ? r.json() : null)),
      fetch(`${apiBase}/api/economy/debt`).then((r) => (r.ok ? r.json() : null)),
      fetch(`${apiBase}/api/economy/history?limit=30`).then((r) => (r.ok ? r.json() : [])),
    ])
      .then(([g, i, d, h]) => {
        setGdp(g);
        setInflation(i);
        setDebt(d);
        setHistory((h as EconomySnapshotDto[]).reverse());
      })
      .catch(() => {});
  }, [apiBase]);

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 30000);
    return () => clearInterval(interval);
  }, [fetchData]);

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-base">Economy</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="grid grid-cols-1 gap-3 text-sm">
          <div className="flex justify-between">
            <span className="text-muted-foreground">GDP (24h)</span>
            <span className="font-medium text-foreground">
              {gdp ? formatCurrency(gdp.gdp) : '--'}
            </span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">Inflation</span>
            <span className="font-medium text-foreground">
              {inflation ? `${inflation.averagePriceChange.toFixed(2)}% (${inflation.label})` : '--'}
            </span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">Total Debt</span>
            <span className="font-medium text-foreground">
              {debt ? formatCurrency(debt.totalDebt) : '--'}
            </span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">Active Loans</span>
            <span className="font-medium text-foreground">{debt ? debt.activeLoans : '--'}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">Debt per Capita</span>
            <span className="font-medium text-foreground">
              {debt ? formatCurrency(debt.debtPerCapita) : '--'}
            </span>
          </div>
        </div>

        {history.length > 0 && (
          <div>
            <p className="mb-2 text-xs font-medium text-muted-foreground">GDP History</p>
            <div className="h-36">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={history}>
                  <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
                  <XAxis
                    dataKey="timestamp"
                    tickFormatter={(ts) =>
                      new Date(ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                    }
                    stroke="hsl(var(--muted-foreground))"
                    fontSize={10}
                  />
                  <YAxis stroke="hsl(var(--muted-foreground))" fontSize={10} />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: 'hsl(var(--card))',
                      border: '1px solid hsl(var(--border))',
                      borderRadius: '8px',
                      fontSize: '12px',
                    }}
                    formatter={(value) => [formatCurrency(Number(value ?? 0)), 'GDP']}
                    labelFormatter={(ts) => new Date(ts).toLocaleString()}
                  />
                  <Line
                    type="monotone"
                    dataKey="gdp"
                    stroke="hsl(var(--primary))"
                    dot={false}
                    strokeWidth={2}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
