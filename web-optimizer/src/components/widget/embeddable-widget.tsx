'use client';

import { useState, useEffect } from 'react';

interface WidgetPrice {
  item: string;
  buy_price: number;
  sell_price: number;
  change_24h: number;
  change_percent: number;
}

interface WidgetData {
  serverName: string;
  healthScore: number;
  topItems: WidgetPrice[];
  lastUpdated: string;
}

function formatPrice(p: number): string {
  if (p >= 1000000) return `$${(p / 1000000).toFixed(1)}M`;
  if (p >= 1000) return `$${(p / 1000).toFixed(1)}K`;
  return `$${p.toFixed(2)}`;
}

function getHealthColor(score: number): { text: string; bg: string; label: string } {
  if (score >= 75) return { text: 'text-emerald-400', bg: 'bg-emerald-500/10', label: 'Healthy' };
  if (score >= 45) return { text: 'text-amber-400', bg: 'bg-amber-500/10', label: 'Elevated' };
  return { text: 'text-rose-400', bg: 'bg-rose-500/10', label: 'Critical' };
}

export function EmbeddableWidget({ apiUrl }: { apiUrl: string }) {
  const [data, setData] = useState<WidgetData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const controller = new AbortController();

    async function fetchWidget() {
      try {
        // Fetch economy summary
        const res = await fetch(`${apiUrl}/api/admin/health`, {
          signal: controller.signal,
          headers: { Accept: 'application/json' },
        });

        if (!res.ok) {
          if (res.status === 401 || res.status === 404) {
            setError('Widget endpoint not publicly accessible. Enable read access in config.');
          } else {
            setError(`Server error (${res.status}). Is the server online?`);
          }
          setLoading(false);
          return;
        }

        const json = await res.json();
        const topItems: WidgetPrice[] = (json.topVolatileItems ?? json.topUndersoldItems ?? []).slice(0, 5).map((item: Record<string, unknown>) => ({
          item: String(item.item ?? item.material ?? 'Unknown'),
          buy_price: Number(item.avgBuyPrice ?? 0),
          sell_price: Number(item.avgSellPrice ?? 0),
          change_24h: Number(item.change24h ?? 0),
          change_percent: Number(item.changePercent24h ?? 0),
        }));

        setData({
          serverName: json.serverName ?? 'Server',
          healthScore: Number(json.healthScore ?? 0),
          topItems,
          lastUpdated: new Date().toISOString(),
        });
      } catch (e: unknown) {
        if (e instanceof Error && e.name !== 'AbortError') {
          setError(`Cannot connect to ${apiUrl}. Check the server URL.`);
        }
      } finally {
        setLoading(false);
      }
    }

    fetchWidget();
    // Refresh every 60 seconds
    const interval = setInterval(fetchWidget, 60_000);
    return () => {
      clearInterval(interval);
      controller.abort();
    };
  }, [apiUrl]);

  if (loading) {
    return (
      <div className="bg-gray-950 border border-gray-800 rounded-xl p-5 text-center text-gray-500 text-xs">
        Loading prices…
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-gray-950 border border-gray-800 rounded-xl p-5 text-center">
        <p className="text-rose-400 text-xs font-medium mb-1">Widget unavailable</p>
        <p className="text-gray-500 text-xs">{error}</p>
      </div>
    );
  }

  if (!data) return null;

  const health = getHealthColor(data.healthScore);

  return (
    <div
      className="bg-gray-950 border border-gray-800 rounded-xl overflow-hidden"
      style={{ fontFamily: 'system-ui, sans-serif', minWidth: 280, maxWidth: 360 }}
    >
      {/* Header */}
      <div className={`flex items-center justify-between px-4 py-2.5 border-b border-gray-800 ${health.bg}`}>
        <div className="flex items-center gap-2">
          <span className="text-gray-300 text-xs font-semibold">{data.serverName}</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className={`text-xs font-bold ${health.text}`}>{data.healthScore}</span>
          <span className="text-gray-600 text-xs">/100</span>
          <span className={`text-xs font-medium ${health.text}`}>{health.label}</span>
        </div>
      </div>

      {/* Price table */}
      <div className="divide-y divide-gray-800/50">
        {data.topItems.length === 0 ? (
          <div className="px-4 py-4 text-center text-gray-500 text-xs">No price data yet</div>
        ) : (
          data.topItems.map((item) => {
            const changeUp = item.change_percent > 0;
            const changeColor = item.change_percent === 0
              ? 'text-gray-500'
              : changeUp
              ? 'text-emerald-400'
              : 'text-rose-400';
            const changeSign = changeUp ? '+' : '';

            return (
              <div key={item.item} className="flex items-center justify-between px-4 py-2">
                <div className="flex-1 min-w-0">
                  <p className="text-gray-200 text-xs font-medium truncate">{item.item}</p>
                </div>
                <div className="flex items-center gap-3 ml-3">
                  <div className="text-right">
                    <p className="text-gray-300 text-xs font-semibold">{formatPrice(item.buy_price)}</p>
                    <p className="text-gray-600 text-[10px]">buy</p>
                  </div>
                  <div className="text-right">
                    <p className="text-gray-300 text-xs font-semibold">{formatPrice(item.sell_price)}</p>
                    <p className="text-gray-600 text-[10px]">sell</p>
                  </div>
                  <div className="w-14 text-right">
                    <p className={`text-xs font-semibold ${changeColor}`}>
                      {changeSign}{item.change_percent.toFixed(1)}%
                    </p>
                    <p className="text-gray-600 text-[10px]">24h</p>
                  </div>
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* Footer */}
      <div className="px-4 py-2 border-t border-gray-800 flex items-center justify-between">
        <span className="text-gray-600 text-[10px]">via Auto-Tune</span>
        <span className="text-gray-600 text-[10px]">
          {new Date(data.lastUpdated).toLocaleTimeString()}
        </span>
      </div>
    </div>
  );
}
