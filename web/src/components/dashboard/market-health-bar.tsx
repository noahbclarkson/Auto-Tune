'use client';

import { Card, CardContent } from '@/components/ui/card';
import { TrendingUp, TrendingDown, Minus, BarChart2, Users } from 'lucide-react';

interface MarketHealthBarProps {
  items: Array<{
    price: number;
    bpd: number;
    spd: number;
    change24h: number;
  }>;
  gdp: number | null;
  inflation: number | null;
  onlinePlayers: number;
}

interface HealthIndicator {
  label: string;
  value: string;
  sub: string;
  color: string;
  bg: string;
  icon: React.ReactNode;
}

function computeVolatility(items: Array<{ change24h: number }>): { label: string; color: string; bg: string; icon: React.ReactNode } {
  if (items.length === 0) return { label: 'No Data', color: 'text-muted-foreground', bg: 'bg-muted', icon: <BarChart2 className="w-3.5 h-3.5" /> };
  const mean = items.reduce((s, i) => s + Math.abs(i.change24h), 0) / items.length;
  const variance = items.reduce((s, i) => s + Math.pow(Math.abs(i.change24h) - mean, 2), 0) / items.length;
  const stdDev = Math.sqrt(variance);
  if (stdDev > 3) return { label: 'Volatile', color: 'text-red-500', bg: 'bg-red-500/10', icon: <TrendingUp className="w-3.5 h-3.5" /> };
  if (stdDev > 1.5) return { label: 'Active', color: 'text-amber-500', bg: 'bg-amber-500/10', icon: <TrendingUp className="w-3.5 h-3.5" /> };
  if (stdDev > 0.5) return { label: 'Normal', color: 'text-emerald-500', bg: 'bg-emerald-500/10', icon: <Minus className="w-3.5 h-3.5" /> };
  return { label: 'Stable', color: 'text-emerald-600 dark:text-emerald-400', bg: 'bg-emerald-500/10', icon: <BarChart2 className="w-3.5 h-3.5" /> };
}

function computeSpreadHealth(items: Array<{ bpd: number; spd: number }>): { label: string; color: string; bg: string } {
  if (items.length === 0) return { label: 'No Data', color: 'text-muted-foreground', bg: 'bg-muted' };
  const avgSpread = items.reduce((s, i) => s + i.bpd + i.spd, 0) / items.length;
  const avgSpreadPct = avgSpread * 100;
  if (avgSpreadPct > 12) return { label: 'Wide', color: 'text-red-500', bg: 'bg-red-500/10' };
  if (avgSpreadPct > 6) return { label: 'Normal', color: 'text-amber-500', bg: 'bg-amber-500/10' };
  return { label: 'Tight', color: 'text-emerald-500', bg: 'bg-emerald-500/10' };
}

export function MarketHealthBar({ items, inflation, onlinePlayers }: MarketHealthBarProps) {
  const volatility = computeVolatility(items);
  const spreadHealth = computeSpreadHealth(items);

  const inflationColor = inflation === null
    ? 'text-muted-foreground'
    : inflation > 3 ? 'text-red-500'
    : inflation > 1.5 ? 'text-amber-500'
    : inflation > 0.5 ? 'text-emerald-500'
    : 'text-sky-400';

  const inflationLabel = inflation === null
    ? 'No Data'
    : inflation > 2 ? 'High'
    : inflation > 0.5 ? 'Normal'
    : 'Deflation';

  const inflationBg = inflation === null
    ? 'bg-muted'
    : inflation > 2 ? 'bg-red-500/10'
    : inflation > 0.5 ? 'bg-emerald-500/10'
    : 'bg-sky-500/10';

  const inflationIcon = inflation === null
    ? <Minus className="w-3.5 h-3.5" />
    : inflation > 0.5 ? <TrendingUp className="w-3.5 h-3.5" />
    : <TrendingDown className="w-3.5 h-3.5" />;

  const playerColor = onlinePlayers >= 5 ? 'text-emerald-500' : onlinePlayers > 0 ? 'text-amber-500' : 'text-red-500';
  const playerBg = onlinePlayers >= 5 ? 'bg-emerald-500/10' : onlinePlayers > 0 ? 'bg-amber-500/10' : 'bg-red-500/10';

  const indicators: HealthIndicator[] = [
    {
      label: 'Market',
      value: volatility.label,
      sub: volatility.label === 'No Data' ? 'Waiting for data'
        : volatility.label === 'Volatile' ? 'Large swings'
        : volatility.label === 'Active' ? 'Notable movement'
        : volatility.label === 'Normal' ? 'Typical activity'
        : 'Minimal movement',
      color: volatility.color,
      bg: volatility.bg,
      icon: volatility.icon,
    },
    {
      label: 'Spreads',
      value: spreadHealth.label,
      sub: spreadHealth.label === 'No Data' ? 'Waiting for data'
        : spreadHealth.label === 'Wide' ? 'Low liquidity'
        : spreadHealth.label === 'Normal' ? 'Moderate spreads'
        : 'Good liquidity',
      color: spreadHealth.color,
      bg: spreadHealth.bg,
      icon: <BarChart2 className="w-3.5 h-3.5" />,
    },
    {
      label: 'Players',
      value: onlinePlayers > 0 ? `${onlinePlayers}` : 'None',
      sub: onlinePlayers >= 5 ? 'Healthy participation'
        : onlinePlayers > 0 ? 'Low participation'
        : 'Server empty',
      color: playerColor,
      bg: playerBg,
      icon: <Users className="w-3.5 h-3.5" />,
    },
    {
      label: 'Inflation',
      value: inflation !== null ? `${inflation > 0 ? '+' : ''}${inflation.toFixed(1)}%` : '--',
      sub: inflationLabel === 'No Data' ? 'No data yet'
        : inflationLabel === 'High' ? 'Rising fast'
        : inflationLabel === 'Normal' ? 'Prices stable'
        : 'Prices falling',
      color: inflationColor,
      bg: inflationBg,
      icon: inflationIcon,
    },
  ];

  return (
    <Card>
      <CardContent className="p-0">
        <div className="grid grid-cols-2 sm:grid-cols-4 divide-x divide-y sm:divide-y-0 divide-border">
          {indicators.map((ind) => (
            <div key={ind.label} className="px-4 py-3 flex items-start gap-3 hover:bg-muted/30 transition-colors">
              <div className={`shrink-0 w-8 h-8 rounded-lg flex items-center justify-center ${ind.bg} ${ind.color}`}>
                {ind.icon}
              </div>
              <div className="min-w-0">
                <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-medium leading-none mb-1">
                  {ind.label}
                </p>
                <p className={`text-sm font-bold ${ind.color} leading-none mb-0.5`}>
                  {ind.value}
                </p>
                <p className="text-[10px] text-muted-foreground leading-tight truncate">
                  {ind.sub}
                </p>
              </div>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
