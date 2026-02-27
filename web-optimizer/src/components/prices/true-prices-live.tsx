'use client';

import { useEffect, useState } from 'react';
import {
  fetchPriceHistory,
  type PriceHistoryPoint,
  type TruePrice,
} from '@/lib/api-client';
import { PriceHistoryChart } from '@/components/prices/price-history-chart';

interface TruePricesLiveProps {
  prices: TruePrice[];
}

export function TruePricesLive({ prices }: TruePricesLiveProps) {
  const [selectedItem, setSelectedItem] = useState<string | null>(prices[0]?.item ?? null);
  const [history, setHistory] = useState<PriceHistoryPoint[]>([]);
  const [isLoadingHistory, setIsLoadingHistory] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);

  useEffect(() => {
    if (!selectedItem) {
      setHistory([]);
      return;
    }

    let cancelled = false;
    setIsLoadingHistory(true);
    setHistoryError(null);

    fetchPriceHistory(selectedItem)
      .then((result) => {
        if (cancelled) return;
        if (result.error) {
          setHistoryError(result.error);
          setHistory([]);
          return;
        }

        setHistory(result.data?.history ?? []);
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        const message = error instanceof Error ? error.message : 'Failed to load history';
        setHistoryError(message);
        setHistory([]);
      })
      .finally(() => {
        if (!cancelled) setIsLoadingHistory(false);
      });

    return () => {
      cancelled = true;
    };
  }, [selectedItem]);

  return (
    <>
      <div className="bg-gray-900/50 border border-gray-800/50 rounded-xl p-6 mb-6">
        <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 mb-4">
          <h2 className="text-sm font-semibold text-emerald-400 uppercase tracking-wide">Live True Prices</h2>
          <p className="text-xs text-gray-500">Tap an item to view its history</p>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead>
              <tr className="border-b border-gray-800/80">
                <th className="text-left py-2 pr-4 text-gray-400 font-medium">Item</th>
                <th className="text-left py-2 pr-4 text-gray-400 font-medium">Price</th>
                <th className="text-left py-2 pr-4 text-gray-400 font-medium hidden sm:table-cell">Confidence</th>
                <th className="text-left py-2 text-gray-400 font-medium hidden sm:table-cell">Servers</th>
              </tr>
            </thead>
            <tbody>
              {prices.map((entry) => {
                const selected = selectedItem === entry.item;
                return (
                  <tr
                    key={entry.item}
                    className={`border-b border-gray-900 cursor-pointer transition-colors ${
                      selected ? 'bg-emerald-600/10' : 'hover:bg-gray-800/40'
                    }`}
                    onClick={() => setSelectedItem(entry.item)}
                  >
                    <td className="py-2 pr-4 text-gray-200">{entry.item}</td>
                    <td className="py-2 pr-4 text-white font-mono">${entry.price.toFixed(2)}</td>
                    <td className="py-2 pr-4 text-gray-300 hidden sm:table-cell">{(entry.confidence * 100).toFixed(1)}%</td>
                    <td className="py-2 text-gray-300 hidden sm:table-cell">{entry.servers}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {selectedItem && (
        <>
          {isLoadingHistory ? (
            <div className="bg-gray-900/50 border border-gray-800/50 rounded-xl p-6 mb-8 text-sm text-gray-300">
              Loading history for <span className="text-emerald-400">{selectedItem}</span>...
            </div>
          ) : historyError ? (
            <div className="bg-gray-900/50 border border-red-900/40 rounded-xl p-6 mb-8 text-sm text-red-300">
              Failed to load history: {historyError}
            </div>
          ) : history.length === 0 ? (
            <div className="bg-gray-900/50 border border-gray-800/50 rounded-xl p-6 mb-8 text-sm text-gray-400">
              No history data available for <span className="text-gray-200">{selectedItem}</span> yet.
            </div>
          ) : (
            <PriceHistoryChart item={selectedItem} points={history} />
          )}
        </>
      )}
    </>
  );
}
