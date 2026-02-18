'use client';

import { Card, CardContent } from '@/components/ui/card';
import { Package, Users, TrendingUp, Percent } from 'lucide-react';
import { formatCurrency, formatPercent } from '@/lib/format';

interface StatsCardsProps {
  totalItems: number;
  onlinePlayers: number;
  gdp: number | null;
  inflation: number | null;
}

export function StatsCards({ totalItems, onlinePlayers, gdp, inflation }: StatsCardsProps) {
  const cards = [
    {
      label: 'Total Items',
      value: totalItems.toString(),
      icon: Package,
      color: 'text-blue-600 dark:text-blue-400',
      bg: 'bg-blue-50 dark:bg-blue-900/30',
    },
    {
      label: 'Online Players',
      value: onlinePlayers.toString(),
      icon: Users,
      color: 'text-emerald-600 dark:text-emerald-400',
      bg: 'bg-emerald-50 dark:bg-emerald-900/30',
    },
    {
      label: 'GDP (24h)',
      value: gdp !== null ? formatCurrency(gdp) : '--',
      icon: TrendingUp,
      color: 'text-amber-600 dark:text-amber-400',
      bg: 'bg-amber-50 dark:bg-amber-900/30',
    },
    {
      label: 'Inflation',
      value: inflation !== null ? formatPercent(inflation) : '--',
      icon: Percent,
      color: 'text-purple-600 dark:text-purple-400',
      bg: 'bg-purple-50 dark:bg-purple-900/30',
    },
  ];

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      {cards.map((card) => (
        <Card key={card.label}>
          <CardContent className="p-4">
            <div className="flex items-center gap-3">
              <div className={`rounded-lg p-2.5 ${card.bg}`}>
                <card.icon className={`h-5 w-5 ${card.color}`} />
              </div>
              <div>
                <p className="text-sm text-muted-foreground">{card.label}</p>
                <p className="text-xl font-bold text-foreground">{card.value}</p>
              </div>
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
