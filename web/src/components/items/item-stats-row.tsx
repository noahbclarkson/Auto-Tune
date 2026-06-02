'use client';

import { Card, CardContent } from '@/components/ui/card';
import { formatPercent } from '@/lib/format';
import type { ItemDto } from '@/lib/api';

interface ItemStatsRowProps {
  item: ItemDto;
}

/** Visual spread bar: shows base price with buy/sell markers relative to it. */
function SpreadBar({ item }: { item: ItemDto }) {
  const buyPct = item.bpd * 100;
  const sellPct = item.spd * 100;
  const totalSpread = buyPct + sellPct;

  // Determine how far above/below the markers are on a 0..totalSpread scale
  const buyPos = totalSpread > 0 ? (buyPct / totalSpread) * 100 : 50;
  const basePos = totalSpread > 0 ? (buyPct / totalSpread) * 100 : 50;

  return (
    <div className="mt-3">
      <div className="flex items-center justify-between text-xs text-muted-foreground mb-1">
        <span className="text-emerald-600 dark:text-emerald-400 font-medium">+{(item.bpd * 100).toFixed(2)}%</span>
        <span className="text-muted-foreground text-[10px] font-mono">spread</span>
        <span className="text-amber-600 dark:text-amber-400 font-medium">-{(item.spd * 100).toFixed(2)}%</span>
      </div>
      {/* Track */}
      <div className="relative h-2 rounded-full bg-muted overflow-hidden">
        {/* Sell side */}
        <div
          className="absolute left-0 top-0 h-full bg-amber-500/30 rounded-l-full transition-all"
          style={{ width: `${100 - buyPos}%` }}
        />
        {/* Buy side */}
        <div
          className="absolute top-0 h-full bg-emerald-500/40 transition-all"
          style={{ left: `${buyPos}%`, width: `${100 - buyPos}%` }}
        />
        {/* Base marker */}
        <div
          className="absolute top-1/2 -translate-y-1/2 w-0.5 h-3 bg-foreground/70 rounded-full"
          style={{ left: `${basePos}%`, transform: 'translateX(-50%) translateY(-50%)' }}
        />
      </div>
      <div className="flex justify-between text-[10px] text-muted-foreground/70 mt-0.5 font-mono">
        <span>sell</span>
        <span>base</span>
        <span>buy</span>
      </div>
    </div>
  );
}

export function ItemStatsRow({ item }: ItemStatsRowProps) {
  return (
    <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
      <Card>
        <CardContent className="p-4 text-center">
          <p className="text-xs text-muted-foreground mb-1">24h Change</p>
          <p
            className={`text-xl font-bold font-mono ${
              item.change24h > 0
                ? 'text-emerald-600 dark:text-emerald-400'
                : item.change24h < 0
                ? 'text-red-500 dark:text-red-400'
                : 'text-foreground'
            }`}
          >
            {item.change24h > 0 ? '+' : ''}{formatPercent(item.change24h)}
          </p>
          <SpreadBar item={item} />
        </CardContent>
      </Card>
      <Card>
        <CardContent className="p-4 text-center">
          <p className="text-xs text-muted-foreground mb-1">Total Spread</p>
          <p className="text-xl font-bold font-mono text-foreground">
            {((item.bpd + item.spd) * 100).toFixed(2)}%
          </p>
          <p className="text-[10px] text-muted-foreground/70 mt-1 font-mono">
            {item.section}
          </p>
        </CardContent>
      </Card>
      <Card>
        <CardContent className="p-4 text-center">
          <p className="text-xs text-muted-foreground mb-1">Buy Premium (BPD)</p>
          <p className="text-xl font-bold font-mono text-emerald-600 dark:text-emerald-400">
            +{(item.bpd * 100).toFixed(2)}%
          </p>
          <p className="text-[10px] text-muted-foreground/70 mt-1 font-mono">
            ${item.buyPrice.toFixed(2)}
          </p>
        </CardContent>
      </Card>
      <Card>
        <CardContent className="p-4 text-center">
          <p className="text-xs text-muted-foreground mb-1">Sell Discount (SPD)</p>
          <p className="text-xl font-bold font-mono text-amber-600 dark:text-amber-400">
            -{(item.spd * 100).toFixed(2)}%
          </p>
          <p className="text-[10px] text-muted-foreground/70 mt-1 font-mono">
            ${item.sellPrice.toFixed(2)}
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
