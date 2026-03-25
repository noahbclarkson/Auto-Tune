'use client';

import { Card, CardContent } from '@/components/ui/card';
import { Package, Users, TrendingUp, Percent } from 'lucide-react';
import { formatCurrency, formatPercent } from '@/lib/format';
import { LineChart, Line, ResponsiveContainer } from 'recharts';

interface GdpHistoryPoint { gdp: number; }
interface InflationHistoryPoint { averagePriceChange: number; }

interface StatsCardsProps {
  totalItems: number;
  onlinePlayers: number;
  gdp: number | null;
  inflation: number | null;
  gdpHistory?: GdpHistoryPoint[];
  inflationHistory?: InflationHistoryPoint[];
}

function MiniSparkline({ data, color, positive }: { data: number[]; color: string; positive: boolean }) {
  const points = data.map((v, i) => ({ v, i }));
  const gradientId = `spark-${color.replace(/\//g, '-')}-${Math.random().toString(36).slice(2, 6)}`;
  const fillColor = positive ? '#10b981' : '#ef4444';

  return (
    <div className="w-16 h-8">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={points} margin={{ top: 2, right: 2, bottom: 2, left: 2 }}>
          <Line
            type="monotone"
            dataKey="v"
            stroke={fillColor}
            strokeWidth={1.5}
            dot={false}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

export function StatsCards({
  totalItems, onlinePlayers, gdp, inflation,
  gdpHistory = [], inflationHistory = [],
}: StatsCardsProps) {
  const gdpSpark = gdpHistory.slice(-20).map(p => p.gdp);
  const inflSpark = inflationHistory.slice(-20).map(p => p.averagePriceChange);
  const inflTrend = inflSpark.length >= 2 ? inflSpark[inflSpark.length - 1] - inflSpark[0] : 0;

  const cards = [
    {
      label: 'Total Items',
      value: totalItems.toLocaleString(),
      icon: Package,
      color: 'text-primary dark:text-primary',
      bg: 'bg-primary/10 dark:bg-primary/20',
      trend: null,
    },
    {
      label: 'Online Players',
      value: onlinePlayers.toString(),
      icon: Users,
      color: 'text-emerald-600 dark:text-emerald-400',
      bg: 'bg-emerald-50 dark:bg-emerald-900/30',
      trend: null,
    },
    {
      label: 'GDP (24h)',
      value: gdp !== null ? formatCurrency(gdp) : '--',
      icon: TrendingUp,
      color: 'text-amber-600 dark:text-amber-400',
      bg: 'bg-amber-50 dark:bg-amber-900/30',
      spark: gdpSpark.length > 1 ? (
        <MiniSparkline data={gdpSpark} color="gdp" positive={gdpSpark[gdpSpark.length - 1] >= gdpSpark[0]} />
      ) : undefined,
    },
    {
      label: 'Inflation',
      value: inflation !== null ? formatPercent(inflation) : '--',
      icon: Percent,
      color: 'text-purple-600 dark:text-purple-400',
      bg: 'bg-purple-50 dark:bg-purple-900/30',
      sub: inflation !== null
        ? (inflTrend > 0.1 ? `↑ trending up` : inflTrend < -0.1 ? `↓ trending down` : `→ stable`)
        : undefined,
      trend: inflation !== null ? (inflTrend > 0 ? '+' : '') + inflTrend.toFixed(2) + 'pp' : null,
      spark: inflSpark.length > 1 ? (
        <MiniSparkline data={inflSpark} color="inflation" positive={inflTrend >= 0} />
      ) : undefined,
    },
  ];

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      {cards.map((card) => (
        <Card key={card.label}>
          <CardContent className="p-4">
            <div className="flex items-start justify-between gap-2">
              <div className="flex items-center gap-3 min-w-0">
                <div className={`rounded-lg p-2.5 shrink-0 ${card.bg}`}>
                  <card.icon className={`h-5 w-5 ${card.color}`} />
                </div>
                <div className="min-w-0">
                  <p className="text-sm text-muted-foreground">{card.label}</p>
                  <p className="text-xl font-bold text-foreground">{card.value}</p>
                  {card.trend && (
                    <p className={`text-xs font-medium ${card.trend.startsWith('+') ? 'text-emerald-500' : card.trend.startsWith('-') ? 'text-red-500' : 'text-muted-foreground'}`}>
                      {card.trend}
                    </p>
                  )}
                  {card.sub && (
                    <p className="text-xs text-muted-foreground">{card.sub}</p>
                  )}
                </div>
              </div>
              {card.spark}
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
