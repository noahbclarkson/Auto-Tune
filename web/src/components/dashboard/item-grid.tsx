'use client';

import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { formatCurrency, formatPercent } from '@/lib/format';
import type { ItemDto } from '@/lib/api';
import type { TrendDto } from '@/lib/api';

interface ItemGridProps {
  items: ItemDto[];
  trends?: TrendDto[];
  linkToDetail?: boolean;
}

function MiniSpreadBar({ bpd, spd }: { bpd: number; spd: number }) {
  const buyPct = bpd * 100;
  const sellPct = spd * 100;
  const total = buyPct + sellPct;
  const buyFrac = total > 0 ? buyPct / total : 0.5;

  return (
    <div className="relative h-1 rounded-full bg-muted overflow-hidden">
      <div
        className="absolute left-0 top-0 h-full bg-amber-500/30 rounded-l-full"
        style={{ width: `${(1 - buyFrac) * 100}%` }}
      />
      <div
        className="absolute top-0 h-full bg-emerald-500/40 rounded-r-full"
        style={{ width: `${buyFrac * 100}%` }}
      />
    </div>
  );
}

export function ItemGrid({ items, trends, linkToDetail = false }: ItemGridProps) {
  const trendMap = new Map((trends ?? []).map((t) => [t.itemId, t]));

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
      {items.map((item) => {
        const trend = trendMap.get(item.id);
        const spreadPct = ((item.bpd + item.spd) * 100).toFixed(1);
        const changeDir = item.change24h > 0 ? 'up' : item.change24h < 0 ? 'down' : 'flat';

        return (
          <Card
            key={item.id}
            className={`hover:border-primary/40 transition-all hover:-translate-y-0.5 cursor-pointer group ${
              linkToDetail ? '' : ''
            }`}
          >
            <CardContent className="p-4">
              {/* Header */}
              <div className="flex items-start justify-between gap-2 mb-3">
                <div className="min-w-0">
                  <p className="font-semibold text-foreground text-sm leading-tight truncate group-hover:text-primary transition-colors">
                    {linkToDetail ? (
                      <a href={`/items/detail/?id=${item.id}`}>{item.displayName}</a>
                    ) : (
                      item.displayName
                    )}
                  </p>
                  <div className="flex items-center gap-1.5 mt-1">
                    <Badge variant="secondary" className="capitalize text-[10px] py-0 px-1.5">
                      {item.section}
                    </Badge>
                    {item.buyable === false && (
                      <Badge variant="warning" className="text-[10px] py-0 px-1.5">Sell Only</Badge>
                    )}
                  </div>
                </div>

                {/* 24h change badge */}
                <div
                  className={`shrink-0 text-right px-2 py-1 rounded-lg text-xs font-bold font-mono ${
                    changeDir === 'up'
                      ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
                      : changeDir === 'down'
                      ? 'bg-red-500/10 text-red-500 dark:text-red-400'
                      : 'bg-muted text-muted-foreground'
                  }`}
                >
                  {item.change24h > 0 ? '+' : ''}{formatPercent(item.change24h)}
                </div>
              </div>

              {/* Prices */}
              <div className="grid grid-cols-3 gap-2 mb-3">
                <div className="text-center">
                  <p className="text-[10px] text-muted-foreground mb-0.5">Base</p>
                  <p className="text-sm font-bold font-mono text-foreground">{formatCurrency(item.price)}</p>
                </div>
                <div className="text-center">
                  <p className="text-[10px] text-muted-foreground mb-0.5">Buy</p>
                  <p className="text-sm font-bold font-mono text-emerald-600 dark:text-emerald-400">{formatCurrency(item.buyPrice)}</p>
                </div>
                <div className="text-center">
                  <p className="text-[10px] text-muted-foreground mb-0.5">Sell</p>
                  <p className="text-sm font-bold font-mono text-amber-600 dark:text-amber-400">{formatCurrency(item.sellPrice)}</p>
                </div>
              </div>

              {/* Spread bar */}
              <MiniSpreadBar bpd={item.bpd} spd={item.spd} />
              <div className="flex items-center justify-between mt-1 mb-3">
                <span className="text-[10px] text-muted-foreground">sell</span>
                <span className="text-[10px] text-muted-foreground">spread</span>
                <span className="text-[10px] text-muted-foreground">buy</span>
              </div>

              {/* Footer: spread + trend */}
              <div className="flex items-center justify-between">
                <span
                  className={`inline-flex items-center px-2 py-1 rounded text-[10px] font-medium ${
                    parseFloat(spreadPct) > 12
                      ? 'bg-red-500/10 text-red-500'
                      : parseFloat(spreadPct) > 6
                      ? 'bg-amber-500/10 text-amber-600 dark:text-amber-400'
                      : 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
                  }`}
                >
                  ±{spreadPct}%
                </span>

                {trend && (
                  <Badge
                    variant={
                      trend.direction === 'UP'
                        ? 'success'
                        : trend.direction === 'DOWN'
                        ? 'destructive'
                        : 'secondary'
                    }
                    className="text-[10px]"
                  >
                    {trend.direction}
                  </Badge>
                )}
              </div>
            </CardContent>
          </Card>
        );
      })}

      {items.length === 0 && (
        <div className="col-span-full text-center py-12 text-muted-foreground">
          No items found
        </div>
      )}
    </div>
  );
}
