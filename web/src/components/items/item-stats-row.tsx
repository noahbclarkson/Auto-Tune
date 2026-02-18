'use client';

import { Card, CardContent } from '@/components/ui/card';
import { formatPercent } from '@/lib/format';
import type { ItemDto } from '@/lib/api';

interface ItemStatsRowProps {
  item: ItemDto;
}

export function ItemStatsRow({ item }: ItemStatsRowProps) {
  const totalSpread = ((item.bpd + item.spd) * 100).toFixed(2);

  return (
    <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
      <Card>
        <CardContent className="p-4 text-center">
          <p className="text-sm text-muted-foreground">24h Change</p>
          <p
            className={`text-lg font-bold ${
              item.change24h > 0
                ? 'text-emerald-600 dark:text-emerald-400'
                : item.change24h < 0
                ? 'text-red-500 dark:text-red-400'
                : 'text-foreground'
            }`}
          >
            {formatPercent(item.change24h)}
          </p>
        </CardContent>
      </Card>
      <Card>
        <CardContent className="p-4 text-center">
          <p className="text-sm text-muted-foreground">Total Spread</p>
          <p className="text-lg font-bold text-foreground">{totalSpread}%</p>
        </CardContent>
      </Card>
      <Card>
        <CardContent className="p-4 text-center">
          <p className="text-sm text-muted-foreground">BPD</p>
          <p className="text-lg font-bold text-foreground">{(item.bpd * 100).toFixed(3)}%</p>
        </CardContent>
      </Card>
      <Card>
        <CardContent className="p-4 text-center">
          <p className="text-sm text-muted-foreground">SPD</p>
          <p className="text-lg font-bold text-foreground">{(item.spd * 100).toFixed(3)}%</p>
        </CardContent>
      </Card>
    </div>
  );
}
