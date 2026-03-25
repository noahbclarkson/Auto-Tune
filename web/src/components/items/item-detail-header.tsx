'use client';

import { Badge } from '@/components/ui/badge';
import { formatCurrency, formatPercent } from '@/lib/format';
import type { ItemDto, ItemTrendDto } from '@/lib/api';

interface ItemDetailHeaderProps {
  item: ItemDto;
  trend: ItemTrendDto | null;
}

export function ItemDetailHeader({ item, trend }: ItemDetailHeaderProps) {
  const spreadPct = ((item.bpd + item.spd) * 100).toFixed(2);

  return (
    <div className="flex flex-col gap-2">
      {/* Title row */}
      <div className="flex items-center gap-3 flex-wrap">
        <h2 className="text-2xl font-bold text-foreground">{item.displayName}</h2>
        <Badge variant="secondary" className="capitalize text-[11px]">
          {item.section}
        </Badge>
        {item.buyable === false && (
          <Badge variant="warning" className="text-[11px]">Sell Only</Badge>
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
            className="text-[11px]"
          >
            {trend.direction}
            {trend.streak > 1 && ` ×${trend.streak}`}
          </Badge>
        )}
      </div>

      {/* Material + ID */}
      <div className="flex items-center gap-2 text-xs text-muted-foreground font-mono">
        <span className="bg-muted px-1.5 py-0.5 rounded text-[10px] uppercase tracking-wider">{item.material}</span>
        <span className="text-muted-foreground/50">ID: {item.id}</span>
      </div>

      {/* Price row */}
      <div className="flex items-center gap-5 text-sm flex-wrap">
        <div className="flex items-center gap-1.5">
          <span className="text-muted-foreground">Base</span>
          <span className="font-semibold text-foreground font-mono">{formatCurrency(item.price)}</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="inline-block w-2 h-2 rounded-full bg-emerald-500" />
          <span className="text-muted-foreground">Buy</span>
          <span className="font-semibold text-emerald-600 dark:text-emerald-400 font-mono">
            {formatCurrency(item.buyPrice)}
          </span>
          <span className="text-[11px] text-muted-foreground/70">
            (+{(item.bpd * 100).toFixed(2)}%)
          </span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="inline-block w-2 h-2 rounded-full bg-amber-500" />
          <span className="text-muted-foreground">Sell</span>
          <span className="font-semibold text-amber-600 dark:text-amber-400 font-mono">
            {formatCurrency(item.sellPrice)}
          </span>
          <span className="text-[11px] text-muted-foreground/70">
            (-{(item.spd * 100).toFixed(2)}%)
          </span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="text-muted-foreground">24h</span>
          <span className={`font-semibold font-mono ${
            item.change24h > 0
              ? 'text-emerald-600 dark:text-emerald-400'
              : item.change24h < 0
              ? 'text-red-500 dark:text-red-400'
              : 'text-foreground'
          }`}>
            {item.change24h > 0 ? '+' : ''}{formatPercent(item.change24h)}
          </span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="text-muted-foreground">Spread</span>
          <span className="font-semibold text-foreground font-mono">{spreadPct}%</span>
        </div>
      </div>
    </div>
  );
}
