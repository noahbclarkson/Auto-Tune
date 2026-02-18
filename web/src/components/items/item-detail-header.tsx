'use client';

import { Badge } from '@/components/ui/badge';
import { formatCurrency, formatPercent } from '@/lib/format';
import type { ItemDto, ItemTrendDto } from '@/lib/api';

interface ItemDetailHeaderProps {
  item: ItemDto;
  trend: ItemTrendDto | null;
}

export function ItemDetailHeader({ item, trend }: ItemDetailHeaderProps) {
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-3">
        <h2 className="text-2xl font-bold text-foreground">{item.displayName}</h2>
        <Badge variant="secondary" className="capitalize">
          {item.section}
        </Badge>
        {item.buyable === false && (
          <Badge variant="warning">Sell Only</Badge>
        )}
        {trend && (
          <Badge
            variant={
              trend.direction === 'UP'
                ? 'success'
                : trend.direction === 'DOWN'
                ? 'destructive'
                : 'secondary'
            }
          >
            {trend.direction} {trend.streak > 0 && `(${trend.streak})`}
          </Badge>
        )}
      </div>
      <div className="flex items-center gap-6 text-sm text-muted-foreground">
        <span>
          Base: <span className="font-medium text-foreground">{formatCurrency(item.price)}</span>
        </span>
        <span>
          Buy:{' '}
          <span className="font-medium text-emerald-600 dark:text-emerald-400">
            {formatCurrency(item.buyPrice)}
          </span>
        </span>
        <span>
          Sell:{' '}
          <span className="font-medium text-amber-600 dark:text-amber-400">
            {formatCurrency(item.sellPrice)}
          </span>
        </span>
        <span
          className={`font-medium ${
            item.change24h > 0
              ? 'text-emerald-600 dark:text-emerald-400'
              : item.change24h < 0
              ? 'text-red-500 dark:text-red-400'
              : ''
          }`}
        >
          {formatPercent(item.change24h)} (24h)
        </span>
      </div>
    </div>
  );
}
