'use client';

import { Card, CardContent } from '@/components/ui/card';
import { formatCurrency } from '@/lib/format';

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

function computeVolatility(
  items: Array<{ change24h: number }>,
): { label: string; color: string; description: string } {
  if (items.length === 0) {
    return { label: 'Unknown', color: 'text-muted-foreground', description: 'No item data' };
  }
  // Standard deviation of 24h changes
  const mean = items.reduce((s, i) => s + Math.abs(i.change24h), 0) / items.length;
  const variance =
    items.reduce((s, i) => s + Math.pow(Math.abs(i.change24h) - mean, 2), 0) / items.length;
  const stdDev = Math.sqrt(variance);
  if (stdDev > 3) return { label: 'Volatile', color: 'text-red-500', description: 'Large swings across items' };
  if (stdDev > 1.5) return { label: 'Active', color: 'text-amber-500', description: 'Notable price movement' };
  if (stdDev > 0.5) return { label: 'Normal', color: 'text-emerald-500', description: 'Typical market activity' };
  return { label: 'Stable', color: 'text-emerald-600 dark:text-emerald-400', description: 'Minimal price movement' };
}

function computeSpreadHealth(
  items: Array<{ bpd: number; spd: number }>,
): { label: string; color: string; description: string } {
  if (items.length === 0) {
    return { label: 'Unknown', color: 'text-muted-foreground', description: 'No item data' };
  }
  const avgSpread = items.reduce((s, i) => s + i.bpd + i.spd, 0) / items.length;
  const avgSpreadPct = avgSpread * 100;
  if (avgSpreadPct > 12) return { label: 'Wide', color: 'text-red-500', description: 'Spreads are very wide — low liquidity' };
  if (avgSpreadPct > 6) return { label: 'Normal', color: 'text-amber-500', description: 'Moderate spreads — typical market' };
  return { label: 'Tight', color: 'text-emerald-500', description: 'Spreads are tight — good liquidity' };
}

function getGdpTrend(history: Array<{ gdp: number }>): { label: string; arrow: string; color: string } {
  if (history.length < 2) return { label: '--', arrow: '', color: 'text-muted-foreground' };
  const recent = history[history.length - 1].gdp;
  const older = history[Math.max(0, history.length - 5)].gdp;
  if (older === 0) return { label: '--', arrow: '', color: 'text-muted-foreground' };
  const pct = ((recent - older) / older) * 100;
  if (pct > 2) return { label: `+${pct.toFixed(1)}%`, arrow: '↑', color: 'text-emerald-500' };
  if (pct < -2) return { label: `${pct.toFixed(1)}%`, arrow: '↓', color: 'text-red-500' };
  return { label: 'Flat', arrow: '→', color: 'text-muted-foreground' };
}

export function MarketHealthBar({ items, gdp, inflation, onlinePlayers }: MarketHealthBarProps) {
  const volatility = computeVolatility(items);
  const spreadHealth = computeSpreadHealth(items);

  const healthItems = [
    {
      label: 'Market',
      value: volatility.label,
      sub: volatility.description,
      color: volatility.color,
    },
    {
      label: 'Spreads',
      value: spreadHealth.label,
      sub: spreadHealth.description,
      color: spreadHealth.color,
    },
    {
      label: 'Players',
      value: onlinePlayers > 0 ? `${onlinePlayers} online` : 'No one online',
      sub: onlinePlayers >= 5 ? 'Healthy participation' : onlinePlayers > 0 ? 'Low participation' : 'Server empty',
      color: onlinePlayers >= 5 ? 'text-emerald-500' : onlinePlayers > 0 ? 'text-amber-500' : 'text-red-500',
    },
    {
      label: 'Inflation',
      value: inflation !== null ? `${inflation > 0 ? '+' : ''}${inflation.toFixed(2)}%` : '--',
      sub: inflation === null ? 'No data' : inflation > 2 ? 'High — prices rising fast' : inflation > 0.5 ? 'Normal inflation' : inflation < -0.5 ? 'Deflation' : 'Stable prices',
      color: inflation === null ? 'text-muted-foreground' : inflation > 2 ? 'text-red-500' : inflation > 0.5 ? 'text-amber-500' : inflation < -0.5 ? 'text-sky-400' : 'text-emerald-500',
    },
  ];

  return (
    <Card>
      <CardContent className="p-3">
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          {healthItems.map((item) => (
            <div key={item.label} className="min-w-0">
              <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-medium">
                {item.label}
              </p>
              <p className={`text-base font-bold ${item.color} leading-none mt-0.5`}>
                {item.value}
              </p>
              <p className="text-[10px] text-muted-foreground mt-0.5 leading-tight truncate">
                {item.sub}
              </p>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
