'use client';

import { useEffect, useState, useCallback } from 'react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { api, type TransactionFeedDto } from '@/lib/api';
import { formatCurrency, formatTimeAgo } from '@/lib/format';

interface ItemTransactionsTableProps {
  apiBase: string;
  itemId: number;
}

export function ItemTransactionsTable({ apiBase, itemId }: ItemTransactionsTableProps) {
  const [transactions, setTransactions] = useState<TransactionFeedDto[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    try {
      const data = await api.items.transactions(apiBase, itemId, 50);
      setTransactions(data);
    } catch {
      // silently fail
    } finally {
      setLoading(false);
    }
  }, [apiBase, itemId]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-base">Transaction History</CardTitle>
      </CardHeader>
      <CardContent>
        {loading ? (
          <p className="text-sm text-muted-foreground py-4 text-center">Loading...</p>
        ) : transactions.length === 0 ? (
          <p className="text-sm text-muted-foreground py-4 text-center">No transactions yet</p>
        ) : (
          <div className="rounded-md border border-border">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border bg-muted/50">
                  <th className="px-3 py-2 text-left font-medium text-muted-foreground">Type</th>
                  <th className="px-3 py-2 text-right font-medium text-muted-foreground">Amount</th>
                  <th className="px-3 py-2 text-right font-medium text-muted-foreground">Price/Unit</th>
                  <th className="px-3 py-2 text-right font-medium text-muted-foreground">Total</th>
                  <th className="px-3 py-2 text-right font-medium text-muted-foreground">Time</th>
                </tr>
              </thead>
              <tbody>
                {transactions.map((tx) => (
                  <tr key={tx.id} className="border-b border-border last:border-0">
                    <td className="px-3 py-2">
                      <Badge
                        variant={tx.type === 'BUY' ? 'success' : 'warning'}
                        className="text-[10px]"
                      >
                        {tx.type}
                      </Badge>
                    </td>
                    <td className="px-3 py-2 text-right text-foreground">{tx.amount}</td>
                    <td className="px-3 py-2 text-right text-muted-foreground">
                      {formatCurrency(tx.pricePerUnit)}
                    </td>
                    <td className="px-3 py-2 text-right font-medium text-foreground">
                      {formatCurrency(tx.totalPrice)}
                    </td>
                    <td className="px-3 py-2 text-right text-muted-foreground text-xs">
                      {formatTimeAgo(tx.timestamp)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
