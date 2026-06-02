'use client';

import { useEffect, useState, useCallback } from 'react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { api, type TransactionFeedDto } from '@/lib/api';
import { formatCurrency, formatTimeAgo } from '@/lib/format';

interface TransactionFeedProps {
  apiBase: string;
}

export function TransactionFeed({ apiBase }: TransactionFeedProps) {
  const [transactions, setTransactions] = useState<TransactionFeedDto[]>([]);

  const fetchTransactions = useCallback(async () => {
    try {
      const data = await api.transactions.recent(apiBase, 20);
      setTransactions(data);
    } catch {
      // silently fail
    }
  }, [apiBase]);

  useEffect(() => {
    fetchTransactions();
    const interval = setInterval(fetchTransactions, 15000);
    return () => clearInterval(interval);
  }, [fetchTransactions]);

  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between">
          <CardTitle className="text-base">Recent Transactions</CardTitle>
          <span className="text-xs text-muted-foreground">{transactions.length} shown</span>
        </div>
      </CardHeader>
      <CardContent>
        {transactions.length === 0 ? (
          <p className="text-sm text-muted-foreground py-4 text-center">No transactions yet</p>
        ) : (
          <div className="space-y-1 max-h-80 overflow-y-auto">
            {transactions.map((tx) => (
              <div
                key={tx.id}
                className="flex items-center justify-between py-2 px-2 rounded-lg hover:bg-muted/40 transition-colors"
              >
                <div className="flex items-center gap-2.5 min-w-0">
                  <div className={`shrink-0 w-7 h-7 rounded-md flex items-center justify-center text-[10px] font-bold ${
                    tx.type === 'BUY'
                      ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
                      : 'bg-amber-500/10 text-amber-600 dark:text-amber-400'
                  }`}>
                    {tx.type === 'BUY' ? 'B' : 'S'}
                  </div>
                  <span className="truncate font-medium text-foreground text-sm">
                    {tx.amount}× {tx.itemName}
                  </span>
                </div>
                <div className="flex items-center gap-3 shrink-0 ml-2">
                  <span className={`font-semibold text-sm ${
                    tx.type === 'BUY' ? 'text-emerald-600 dark:text-emerald-400' : 'text-amber-600 dark:text-amber-400'
                  }`}>
                    {formatCurrency(tx.totalPrice)}
                  </span>
                  <span className="text-xs text-muted-foreground tabular-nums">{formatTimeAgo(tx.timestamp)}</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
