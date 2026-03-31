'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { RefreshCw, ExternalLink } from 'lucide-react';
import { fetchExchangeRates } from '@/lib/api-client';
import type { ExchangeRate } from '@/lib/api-client';

export const metadata = {
  title: 'Exchange Rates | Auto-Tune',
  description: 'Per-server price multipliers relative to Auto-Tune true prices',
};

function getServerStatus(lastSeen: string): { label: string; className: string } {
  const deltaMs = Date.now() - new Date(lastSeen).getTime();
  const fiveMinutes = 5 * 60 * 1000;
  const oneHour = 60 * 60 * 1000;

  if (deltaMs < fiveMinutes) {
    return { label: 'Online', className: 'text-emerald-300 bg-emerald-500/10 border-emerald-500/30' };
  }
  if (deltaMs < oneHour) {
    return { label: 'Idle', className: 'text-amber-300 bg-amber-500/10 border-amber-500/30' };
  }
  return { label: 'Offline', className: 'text-gray-300 bg-gray-700/20 border-gray-600/40' };
}

function formatRate(rate: number): string {
  return `${rate.toFixed(2)}×`;
}

function RateRow({ rate }: { rate: ExchangeRate }) {
  const status = getServerStatus(rate.last_seen);
  const deviation = ((rate.rate - 1) * 100).toFixed(1);
  const deviationSign = rate.rate >= 1 ? '+' : '';
  const deviationColor =
    rate.rate < 0.95
      ? 'text-emerald-400'
      : rate.rate <= 1.05
        ? 'text-gray-300'
        : rate.rate <= 1.25
          ? 'text-amber-400'
          : 'text-red-400';

  return (
    <tr className="border-b border-gray-800/40 hover:bg-gray-800/20 transition-colors">
      <td className="py-3 px-4 text-gray-100 font-medium max-w-[8rem] truncate">{rate.name}</td>
      <td className="py-3 px-4">
        <span className={`font-mono font-semibold ${deviationColor}`}>{formatRate(rate.rate)}</span>
        <span className={`ml-2 text-xs ${deviationColor}`}>
          ({deviationSign}{deviation}%)
        </span>
      </td>
      <td className="py-3 px-4 text-gray-300 hidden sm:table-cell">{rate.player_count.toLocaleString()}</td>
      <td className="py-3 px-4 text-gray-400 text-sm hidden md:table-cell">{new Date(rate.last_seen).toLocaleString()}</td>
      <td className="py-3 px-4">
        <span className={`text-xs px-2.5 py-1 rounded-full border ${status.className}`}>
          {status.label}
        </span>
      </td>
    </tr>
  );
}

function ApiErrorFallback({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="bg-gray-900/50 border border-red-800/40 rounded-xl p-8 text-center">
      <p className="text-3xl mb-3">⚠️</p>
      <p className="text-gray-100 font-semibold text-lg mb-2">Exchange rate data unavailable</p>
      <p className="text-gray-400 text-sm mb-4 max-w-md mx-auto">
        We couldn&apos;t reach the pricing service right now. This is usually a temporary issue —
        try refreshing or check back shortly.
      </p>
      <div className="flex flex-col sm:flex-row items-center justify-center gap-3 mt-2">
        <button
          onClick={onRetry}
          className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-emerald-600/20 border border-emerald-600/40 text-emerald-400 text-sm hover:bg-emerald-600/30 transition-colors"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          Retry
        </button>
        <Link
          href="/"
          className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-gray-700/30 border border-gray-700/50 text-gray-300 text-sm hover:bg-gray-700/50 transition-colors"
        >
          ← Back to home
        </Link>
      </div>
    </div>
  );
}

export function ExchangeRatesClient() {
  const [rates, setRates] = useState<ExchangeRate[]>([]);
  const [base, setBase] = useState<string>('true_prices');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    const result = await fetchExchangeRates();
    setLoading(false);
    if (result.error !== null || result.data === null) {
      setError(result.error ?? 'Unknown error');
    } else {
      setRates(result.data.rates ?? []);
      setBase(result.data.base ?? 'true_prices');
    }
  }

  useEffect(() => {
    load();
  }, []);

  const hasData = rates.length > 0;

  return (
    <div className="min-h-screen bg-gray-950 text-white">
      {/* Inline header — avoids needing the Header component which may import server-only things */}
      <div className="border-b border-gray-800/60 bg-gray-950/80 backdrop-blur-sm sticky top-0 z-10">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Link href="/" className="flex items-center gap-2">
              <svg width="24" height="24" viewBox="0 0 32 32" fill="none">
                <path d="M16 4L4 10v12l12 6 12-6V10L16 4z" stroke="currentColor" strokeWidth="2" className="text-emerald-400" fill="none"/>
                <path d="M16 4v16m-8-8l8 4 8-4" stroke="currentColor" strokeWidth="1.5" className="text-emerald-400" opacity=".6"/>
              </svg>
              <span className="text-white font-semibold text-sm">Auto-Tune</span>
            </Link>
            <nav className="hidden md:flex items-center gap-4 ml-6 text-sm text-gray-500">
              <Link href="/how-it-works" className="hover:text-gray-300 transition-colors">How it works</Link>
              <Link href="/install" className="hover:text-gray-300 transition-colors">Install</Link>
              <Link href="/simulator" className="hover:text-gray-300 transition-colors">Simulator</Link>
              <Link href="/true-prices" className="hover:text-gray-300 transition-colors">True Prices</Link>
              <Link href="/servers" className="hover:text-gray-300 transition-colors">Servers</Link>
            </nav>
          </div>
          <div className="flex items-center gap-3">
            <a
              href="https://github.com/noahbclarkson/Auto-Tune"
              target="_blank"
              rel="noopener noreferrer"
              className="text-xs text-gray-500 hover:text-gray-300 transition-colors"
            >
              GitHub
            </a>
          </div>
        </div>
      </div>

      <main className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        {/* Page heading */}
        <div className="mb-8">
          <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">
            Market Insight
          </p>
          <h1 className="text-3xl sm:text-4xl font-bold text-white mb-3">Exchange Rates</h1>
          <p className="text-gray-400 max-w-3xl text-sm sm:text-base leading-relaxed">
            Each server&apos;s price level relative to the global{' '}
            <Link href="/true-prices" className="text-emerald-400 hover:underline">
              true prices
            </Link>{' '}
            baseline (<code className="text-xs bg-gray-800 px-1 py-0.5 rounded">{base}</code>). A rate
            of 1.30× means items cost 30% more than the true-price reference on that server.
          </p>
        </div>

        {/* Context link */}
        <div className="mb-6 flex flex-wrap items-center gap-2 text-sm text-gray-500">
          <span>Want to see individual servers?</span>
          <Link href="/servers" className="text-emerald-400 hover:underline">
            View all registered servers →
          </Link>
        </div>

        {/* ── Content ── */}
        {loading ? (
          <div className="flex items-center justify-center py-20">
            <RefreshCw className="w-6 h-6 text-gray-600 animate-spin" />
          </div>
        ) : error ? (
          <ApiErrorFallback onRetry={load} />
        ) : hasData ? (
          <>
            {/* Bar chart */}
            <div className="mb-6 bg-gray-900/50 border border-gray-800/50 rounded-xl overflow-hidden">
              <div className="p-4 border-b border-gray-800/50">
                <h2 className="text-sm font-semibold text-emerald-400 uppercase tracking-wide">
                  Rate vs. True Prices Baseline
                </h2>
              </div>
              <div className="p-4 space-y-2">
                {rates.map((rate) => {
                  const deviation = ((rate.rate - 1) * 100).toFixed(1);
                  const sign = rate.rate >= 1 ? '+' : '';
                  const barColor = rate.rate < 0.95
                    ? 'bg-emerald-500'
                    : rate.rate <= 1.05
                      ? 'bg-gray-500'
                      : rate.rate <= 1.25
                        ? 'bg-amber-500'
                        : 'bg-red-500';
                  const width = Math.min(100, Math.abs(rate.rate - 1) * 200);
                  return (
                    <div key={rate.server_id} className="flex items-center gap-3">
                      <span className="text-xs font-medium text-gray-300 w-28 truncate">{rate.name}</span>
                      <div className="flex-1 h-2 bg-gray-800 rounded overflow-hidden">
                        <div
                          className={`h-full ${barColor} rounded`}
                          style={{ width: `${width}%`, marginLeft: rate.rate < 1 ? 'auto' : 0 }}
                        />
                      </div>
                      <span className="text-xs font-mono text-gray-400 w-16 text-right">
                        {sign}{deviation}%
                      </span>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Table */}
            <div className="bg-gray-900/50 border border-gray-800/50 rounded-xl overflow-hidden">
              <div className="px-5 py-4 border-b border-gray-800/50">
                <h2 className="text-sm font-semibold text-emerald-400 uppercase tracking-wide">
                  All Servers
                </h2>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-800/60 text-left">
                      <th className="py-2.5 px-4 text-xs font-medium text-gray-500 uppercase tracking-wide">Server</th>
                      <th className="py-2.5 px-4 text-xs font-medium text-gray-500 uppercase tracking-wide">Rate</th>
                      <th className="py-2.5 px-4 text-xs font-medium text-gray-500 uppercase tracking-wide hidden sm:table-cell">Players</th>
                      <th className="py-2.5 px-4 text-xs font-medium text-gray-500 uppercase tracking-wide hidden md:table-cell">Last Seen</th>
                      <th className="py-2.5 px-4 text-xs font-medium text-gray-500 uppercase tracking-wide">Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rates.map((rate) => (
                      <RateRow key={rate.server_id} rate={rate} />
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </>
        ) : (
          // API succeeded but no rates computed yet
          <div className="bg-gray-900/50 border border-gray-800/50 rounded-xl p-8 text-center">
            <p className="text-2xl mb-3">📊</p>
            <p className="text-gray-200 font-medium mb-2">No exchange rate data yet</p>
            <p className="text-gray-400 text-sm mb-4">
              Exchange rates are computed once multiple servers have submitted price data.
            </p>
            <Link
              href="/servers"
              className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-emerald-600/20 border border-emerald-600/40 text-emerald-400 text-sm hover:bg-emerald-600/30 transition-colors"
            >
              View registered servers
            </Link>
          </div>
        )}
      </main>
    </div>
  );
}
