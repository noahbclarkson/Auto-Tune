'use client';

import { useEffect, useState, useCallback } from 'react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
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
        <CardTitle className="text-base">Recent Transactions</CardTitle>
      </CardHeader>
      <CardContent>
        {transactions.length === 0 ? (
          <p className="text-sm text-muted-foreground py-4 text-center">No transactions yet</p>
        ) : (
          <div className="space-y-2 max-h-80 overflow-y-auto">
            {transactions.map((tx) => (
              <div
                key={tx.id}
                className="flex items-center justify-between py-1.5 text-sm border-b border-border last:border-0"
              >
                <div className="flex items-center gap-2 min-w-0">
                  <Badge
                    variant={tx.type === 'BUY' ? 'success' : 'warning'}
                    className="text-[10px] shrink-0"
                  >
                    {tx.type}
                  </Badge>
                  <span className="truncate font-medium text-foreground">
                    {tx.amount}x {tx.itemName}
                  </span>
                </div>
                <div className="flex items-center gap-3 shrink-0 ml-2">
                  <span className="font-medium text-foreground">{formatCurrency(tx.totalPrice)}</span>
                  <span className="text-xs text-muted-foreground">{formatTimeAgo(tx.timestamp)}</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
