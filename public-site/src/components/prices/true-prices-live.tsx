'use client';

import { useEffect, useState } from 'react';
import {
  fetchPriceHistory,
  type PriceHistoryPoint,
  type TruePrice,
} from '@/lib/api-client';
import { PriceHistoryChart } from '@/components/prices/price-history-chart';
import { Anchor, Users, TrendingUp, AlertTriangle } from 'lucide-react';

interface TruePricesLiveProps {
  prices: TruePrice[];
}

const STALE_THRESHOLD_MS = 24 * 60 * 60 * 1000; // 24 hours

function freshnessLabel(lastUpdated: string | null): { label: string; color: string; bg: string; stale: boolean } {
  if (!lastUpdated) {
    return { label: 'Unknown', color: 'text-gray-500', bg: 'bg-gray-900/60 border-gray-700/50', stale: true };
  }
  const age = Date.now() - new Date(lastUpdated).getTime();
  if (age > STALE_THRESHOLD_MS) {
    const hours = Math.round(age / (1000 * 60 * 60));
    const label = hours >= 24 ? `${Math.round(hours / 24)}d old` : `${hours}h old`;
    return { label, color: 'text-rose-400', bg: 'bg-rose-950/40 border-rose-800/50', stale: true };
  }
  const minutes = Math.round(age / (1000 * 60));
  if (minutes < 60) {
    return { label: `${minutes}m ago`, color: 'text-emerald-400', bg: 'bg-emerald-950/40 border-emerald-800/50', stale: false };
  }
  const hours = Math.round(minutes / 60);
  return { label: `${hours}h ago`, color: 'text-emerald-400', bg: 'bg-emerald-950/40 border-emerald-800/50', stale: false };
}

function confidenceColor(conf: number): string {
  if (conf >= 0.70) return 'text-emerald-400';
  if (conf >= 0.40) return 'text-amber-400';
  return 'text-rose-400';
}

function confidenceBg(conf: number): string {
  if (conf >= 0.70) return 'bg-emerald-950/40 border-emerald-800/40';
  if (conf >= 0.40) return 'bg-amber-950/40 border-amber-800/40';
  return 'bg-rose-950/40 border-rose-800/40';
}

function ConfidenceBar({ conf }: { conf: number }) {
  return (
    <div className="flex items-center gap-2">
      <div className="flex-1 h-1.5 rounded-full bg-gray-800 overflow-hidden">
        <div
          className={`h-full rounded-full transition-all ${
            conf >= 0.70 ? 'bg-emerald-500' : conf >= 0.40 ? 'bg-amber-500' : 'bg-rose-500'
          }`}
          style={{ width: `${Math.round(conf * 100)}%` }}
        />
      </div>
      <span className={`text-xs font-mono font-semibold shrink-0 ${confidenceColor(conf)}`}>
        {(conf * 100).toFixed(0)}%
      </span>
    </div>
  );
}

function ServerCountBadge({ servers, stale }: { servers: number; stale: boolean }) {
  if (servers === 0) {
    return (
      <span className="inline-flex items-center gap-0.5 text-[10px] font-semibold text-rose-400 bg-rose-950/40 border border-rose-800/50 px-1 py-0.5 rounded">
        <AlertTriangle className="w-2.5 h-2.5" /> no servers
      </span>
    );
  }
  if (servers === 1) {
    return (
      <span className="inline-flex items-center gap-0.5 text-[10px] font-semibold text-amber-400 bg-amber-950/40 border border-amber-800/50 px-1 py-0.5 rounded">
        1 server
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-0.5 text-[10px] font-semibold text-emerald-400 bg-emerald-950/40 border border-emerald-800/50 px-1 py-0.5 rounded">
      <Users className="w-2.5 h-2.5" /> {servers} servers
    </span>
  );
}

export function TruePricesLive({ prices }: TruePricesLiveProps) {
  const [selectedItem, setSelectedItem] = useState<string | null>(prices[0]?.item ?? null);
  const [history, setHistory] = useState<PriceHistoryPoint[]>([]);
  const [isLoadingHistory, setIsLoadingHistory] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [showAnchored, setShowAnchored] = useState(false);

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
          <div className="flex items-center gap-3">
            <label className="flex items-center gap-1.5 text-xs text-gray-500 cursor-pointer">
              <input
                type="checkbox"
                checked={showAnchored}
                onChange={(e) => setShowAnchored(e.target.checked)}
                className="accent-emerald-500 w-3.5 h-3.5"
              />
              Anchored only
            </label>
            <p className="text-xs text-gray-500">Click item for history</p>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead>
              <tr className="border-b border-gray-800/80">
                <th className="text-left py-2 pr-4 text-gray-400 font-medium">Item</th>
                <th className="text-left py-2 pr-4 text-gray-400 font-medium">True Price</th>
                <th className="text-left py-2 pr-4 text-gray-400 font-medium hidden sm:table-cell">Confidence</th>
                <th className="text-left py-2 text-gray-400 font-medium hidden md:table-cell">Coverage</th>
              </tr>
            </thead>
            <tbody>
              {prices
                .filter((entry) => !showAnchored || entry.anchored)
                .map((entry) => {
                const selected = selectedItem === entry.item;
                const freshness = freshnessLabel(entry.lastUpdated);
                const confColor = confidenceColor(entry.confidence);
                const confBg = confidenceBg(entry.confidence);
                return (
                  <tr
                    key={entry.item}
                    className={`border-b border-gray-900 cursor-pointer transition-colors ${
                      selected ? 'bg-emerald-600/10' : 'hover:bg-gray-800/40'
                    }`}
                    onClick={() => setSelectedItem(entry.item)}
                  >
                    <td className="py-2.5 pr-4">
                      <div className="flex flex-wrap items-center gap-2">
                        <div className={`w-1.5 h-1.5 rounded-full shrink-0 ${entry.anchored ? 'bg-emerald-500' : 'bg-gray-600'}`} />
                        <span className={`font-medium ${selected ? 'text-white' : 'text-gray-200'}`}>{entry.item}</span>
                        {entry.anchored && (
                          <span className="inline-flex items-center gap-0.5 text-[10px] font-semibold text-emerald-400 bg-emerald-950/60 border border-emerald-800/50 px-1 py-0.5 rounded uppercase tracking-wide">
                            <Anchor className="w-2.5 h-2.5" /> anchor
                          </span>
                        )}
                        {freshness.stale && (
                          <span className={`inline-flex items-center gap-0.5 text-[10px] font-semibold ${freshness.color} ${freshness.bg} border px-1 py-0.5 rounded`}>
                            <AlertTriangle className="w-2.5 h-2.5" /> {freshness.label}
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="py-2.5 pr-4">
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-white">${entry.price.toFixed(2)}</span>
                      </div>
                    </td>
                    <td className="py-2.5 pr-4 hidden sm:table-cell">
                      <ConfidenceBar conf={entry.confidence} />
                    </td>
                    <td className="py-2.5 hidden md:table-cell">
                      <div className="flex flex-col gap-1">
                        <ServerCountBadge servers={entry.servers} stale={freshness.stale} />
                        {!freshness.stale && (
                          <span className={`text-[10px] font-mono ${freshness.color}`}>{freshness.label}</span>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          {prices.length === 0 && (
            <p className="text-center py-6 text-gray-500 text-sm">No prices match the current filter.</p>
          )}
        </div>

        {/* Confidence + coverage legend */}
        <div className="flex flex-wrap items-center gap-4 mt-4 pt-3 border-t border-gray-800/60">
          <span className="text-xs text-gray-600">Confidence:</span>
          <div className="flex items-center gap-1.5">
            <div className="w-3 h-1.5 rounded-full bg-emerald-500" />
            <span className="text-xs text-emerald-400">≥70% high</span>
          </div>
          <div className="flex items-center gap-1.5">
            <div className="w-3 h-1.5 rounded-full bg-amber-500" />
            <span className="text-xs text-amber-400">40–70% moderate</span>
          </div>
          <div className="flex items-center gap-1.5">
            <div className="w-3 h-1.5 rounded-full bg-rose-500" />
            <span className="text-xs text-rose-400">&lt;40% low</span>
          </div>
          <div className="flex items-center gap-1.5">
            <div className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
            <span className="text-xs text-gray-500">anchor item</span>
          </div>
          <div className="flex items-center gap-1.5">
            <div className="w-1.5 h-1.5 rounded-full bg-rose-500" />
            <span className="text-xs text-gray-500">stale (&gt;24h)</span>
          </div>
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
