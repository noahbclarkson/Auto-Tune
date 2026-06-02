'use client';

import { useEffect, useState, useCallback } from 'react';
import Link from 'next/link';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { ArrowRight } from 'lucide-react';
import { formatCurrency, formatPercent } from '@/lib/format';
import type { ItemDto } from '@/lib/api';

interface SimilarItemsProps {
  apiBase: string;
  currentItem: ItemDto;
}

export function SimilarItems({ apiBase, currentItem }: SimilarItemsProps) {
  const [items, setItems] = useState<ItemDto[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchSimilar = useCallback(async () => {
    try {
      const res = await fetch(`${apiBase}/api/items`);
      if (!res.ok) return;
      const data: ItemDto[] = await res.json();
      const similar = data
        .filter((i) => i.id !== currentItem.id && i.section === currentItem.section)
        .slice(0, 6);
      setItems(similar);
    } catch {
      // silently fail
    } finally {
      setLoading(false);
    }
  }, [apiBase, currentItem]);

  useEffect(() => {
    fetchSimilar();
  }, [fetchSimilar]);

  if (loading || items.length === 0) return null;

  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between">
          <CardTitle className="text-base">More in {currentItem.section}</CardTitle>
          <Link
            href={`/items/?section=${encodeURIComponent(currentItem.section)}`}
            className="text-xs text-muted-foreground hover:text-foreground flex items-center gap-0.5 transition-colors"
          >
            Browse all <ArrowRight className="h-3 w-3" />
          </Link>
        </div>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-3">
          {items.map((item) => (
            <Link
              key={item.id}
              href={`/items/detail/?id=${item.id}`}
              className="group rounded-lg border border-border bg-card p-3 hover:border-primary/40 hover:bg-muted/40 transition-all"
            >
              <p className="text-xs font-semibold text-foreground truncate mb-1.5 group-hover:text-primary transition-colors">
                {item.displayName}
              </p>
              <div className="space-y-0.5">
                <div className="flex justify-between text-[10px]">
                  <span className="text-emerald-600 dark:text-emerald-400">Buy</span>
                  <span className="font-mono font-medium text-emerald-600 dark:text-emerald-400">
                    {formatCurrency(item.buyPrice)}
                  </span>
                </div>
                <div className="flex justify-between text-[10px]">
                  <span className="text-amber-600 dark:text-amber-400">Sell</span>
                  <span className="font-mono font-medium text-amber-600 dark:text-amber-400">
                    {formatCurrency(item.sellPrice)}
                  </span>
                </div>
                <div className="flex justify-between text-[10px]">
                  <span className="text-muted-foreground">24h</span>
                  <span
                    className={`font-mono font-medium ${
                      item.change24h > 0
                        ? 'text-emerald-600 dark:text-emerald-400'
                        : item.change24h < 0
                        ? 'text-red-500 dark:text-red-400'
                        : 'text-muted-foreground'
                    }`}
                  >
                    {formatPercent(item.change24h)}
                  </span>
                </div>
              </div>
            </Link>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
