'use client';

import { useMemo, useState } from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { formatCurrency, formatTimeAgo, formatDateTime } from '@/lib/format';
import { TransactionFeedDto } from '@/lib/api';
import { format, isSameDay, differenceInDays } from 'date-fns';
import { ArrowUpRight, ArrowDownRight, Clock, LayoutList } from 'lucide-react';

interface TradingTimelineProps {
  transactions: TransactionFeedDto[];
}

function TimelineDot({ type }: { type: 'BUY' | 'SELL' }) {
  return (
    <div className={`shrink-0 w-7 h-7 rounded-full flex items-center justify-center border-2 ${
      type === 'BUY'
        ? 'bg-emerald-500/10 border-emerald-500 text-emerald-600 dark:text-emerald-400'
        : 'bg-amber-500/10 border-amber-500 text-amber-600 dark:text-amber-400'
    }`}>
      {type === 'BUY'
        ? <ArrowDownRight className="w-3.5 h-3.5" />
        : <ArrowUpRight className="w-3.5 h-3.5" />
      }
    </div>
  );
}

function DateSeparator({ date }: { date: Date }) {
  const now = new Date();
  const daysAgo = differenceInDays(now, date);

  let label: string;
  if (daysAgo === 0) label = 'Today';
  else if (daysAgo === 1) label = 'Yesterday';
  else if (daysAgo < 7) label = format(date, 'EEEE'); // e.g. "Monday"
  else label = format(date, 'MMMM d, yyyy');

  return (
    <div className="flex items-center gap-3 my-3">
      <div className="h-px flex-1 bg-border" />
      <span className="text-xs font-medium text-muted-foreground px-2">{label}</span>
      <div className="h-px flex-1 bg-border" />
    </div>
  );
}

function TradeRow({ tx }: { tx: TransactionFeedDto }) {
  const isBuy = tx.type === 'BUY';
  const txDate = new Date(tx.timestamp * 1000);

  return (
    <div className="flex items-start gap-3 group">
      <TimelineDot type={tx.type} />

      <div className="flex-1 min-w-0 py-1">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="font-medium text-foreground text-sm">
            {tx.amount.toLocaleString()}× {tx.itemName}
          </span>
          <span className={`inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-semibold uppercase tracking-wide ${
            isBuy
              ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-400'
              : 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-400'
          }`}>
            {tx.type}
          </span>
        </div>
        <div className="flex items-center gap-3 mt-0.5 text-xs text-muted-foreground">
          <span>{format(txDate, 'HH:mm')}</span>
          <span className="text-muted-foreground/50">·</span>
          <span>{formatCurrency(tx.pricePerUnit)}/unit</span>
        </div>
      </div>

      <div className="shrink-0 text-right">
        <p className={`text-sm font-semibold font-mono ${
          isBuy ? 'text-destructive' : 'text-emerald-600 dark:text-emerald-400'
        }`}>
          {isBuy ? '-' : '+'}{formatCurrency(tx.totalPrice)}
        </p>
        <p className="text-[10px] text-muted-foreground mt-0.5">
          {formatTimeAgo(tx.timestamp)}
        </p>
      </div>
    </div>
  );
}

export function TradingTimeline({ transactions }: TradingTimelineProps) {
  const [viewMode, setViewMode] = useState<'timeline' | 'table'>('timeline');

  const grouped = useMemo(() => {
    if (transactions.length === 0) return [];

    // Sort ascending (oldest first for timeline display)
    const sorted = [...transactions].sort((a, b) => a.timestamp - b.timestamp);

    const groups: { date: Date; txs: TransactionFeedDto[] }[] = [];
    for (const tx of sorted) {
      const txDate = new Date(tx.timestamp * 1000);
      const last = groups[groups.length - 1];
      if (last && isSameDay(last.date, txDate)) {
        last.txs.push(tx);
      } else {
        groups.push({ date: txDate, txs: [tx] });
      }
    }
    return groups;
  }, [transactions]);

  const totalBuys = transactions.filter(t => t.type === 'BUY').reduce((s, t) => s + t.totalPrice, 0);
  const totalSells = transactions.filter(t => t.type === 'SELL').reduce((s, t) => s + t.totalPrice, 0);
  const netFlow = totalSells - totalBuys;

  return (
    <Card>
      <CardContent className="p-0">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-border">
          <div className="flex items-center gap-3">
            <Clock className="w-4 h-4 text-muted-foreground" />
            <span className="text-sm font-semibold">Trading History</span>
            <span className="text-xs text-muted-foreground">({transactions.length} trades)</span>
          </div>

          <div className="flex items-center gap-2">
            {/* Net flow indicator */}
            {transactions.length > 0 && (
              <div className={`text-xs font-semibold mr-2 ${
                netFlow >= 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-destructive'
              }`}>
                Net: {netFlow >= 0 ? '+' : ''}{formatCurrency(netFlow)}
              </div>
            )}

            {/* View toggle */}
            <div className="flex rounded-lg border border-border overflow-hidden">
              <button
                onClick={() => setViewMode('timeline')}
                className={`px-2.5 py-1 text-xs font-medium transition-colors ${
                  viewMode === 'timeline'
                    ? 'bg-primary text-primary-foreground'
                    : 'text-muted-foreground hover:text-foreground'
                }`}
              >
                Timeline
              </button>
              <button
                onClick={() => setViewMode('table')}
                className={`px-2.5 py-1 text-xs font-medium transition-colors ${
                  viewMode === 'table'
                    ? 'bg-primary text-primary-foreground'
                    : 'text-muted-foreground hover:text-foreground'
                }`}
              >
                <LayoutList className="w-3 h-3" />
              </button>
            </div>
          </div>
        </div>

        {/* Timeline view */}
        {viewMode === 'timeline' && (
          <div className="divide-y divide-border/50 px-5 py-3 max-h-[480px] overflow-y-auto">
            {grouped.length === 0 ? (
              <div className="py-8 text-center text-sm text-muted-foreground">
                No trades yet
              </div>
            ) : (
              grouped.map(({ date, txs }) => (
                <div key={date.toISOString()}>
                  <DateSeparator date={date} />
                  <div className="space-y-0">
                    {txs.map((tx) => (
                      <TradeRow key={tx.id} tx={tx} />
                    ))}
                  </div>
                </div>
              ))
            )}
          </div>
        )}

        {/* Table fallback */}
        {viewMode === 'table' && (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border bg-muted/40">
                  <th className="px-4 py-2.5 text-left text-xs font-medium text-muted-foreground">When</th>
                  <th className="px-3 py-2.5 text-left text-xs font-medium text-muted-foreground">Item</th>
                  <th className="px-3 py-2.5 text-center text-xs font-medium text-muted-foreground">Type</th>
                  <th className="px-3 py-2.5 text-right text-xs font-medium text-muted-foreground">Qty</th>
                  <th className="px-3 py-2.5 text-right text-xs font-medium text-muted-foreground">Unit</th>
                  <th className="px-4 py-2.5 text-right text-xs font-medium text-muted-foreground">Total</th>
                </tr>
              </thead>
              <tbody>
                {[...transactions].sort((a, b) => b.timestamp - a.timestamp).map((tx) => {
                  const isBuy = tx.type === 'BUY';
                  return (
                    <tr key={tx.id} className="border-b border-border/50 last:border-0 hover:bg-muted/30 transition-colors">
                      <td className="px-4 py-2.5 text-muted-foreground text-xs whitespace-nowrap">
                        {formatDateTime(tx.timestamp)}
                      </td>
                      <td className="px-3 py-2.5 font-medium text-foreground">{tx.itemName}</td>
                      <td className="px-3 py-2.5 text-center">
                        <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
                          isBuy
                            ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400'
                            : 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400'
                        }`}>{tx.type}</span>
                      </td>
                      <td className="px-3 py-2.5 text-right font-mono">{tx.amount.toLocaleString()}</td>
                      <td className="px-3 py-2.5 text-right font-mono text-muted-foreground">{formatCurrency(tx.pricePerUnit)}</td>
                      <td className={`px-4 py-2.5 text-right font-mono font-semibold ${isBuy ? 'text-destructive' : 'text-emerald-600 dark:text-emerald-400'}`}>
                        {isBuy ? '-' : '+'}{formatCurrency(tx.totalPrice)}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
