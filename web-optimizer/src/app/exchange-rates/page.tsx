import Link from 'next/link';
import { Header } from '@/components/layout/header';
import { Footer } from '@/components/layout/footer';
import { ExchangeRateChart } from '@/components/prices/exchange-rate-chart';
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
      <td className="py-3 px-4 text-gray-100 font-medium">{rate.name}</td>
      <td className="py-3 px-4">
        <span className={`font-mono font-semibold ${deviationColor}`}>{formatRate(rate.rate)}</span>
        <span className={`ml-2 text-xs ${deviationColor}`}>
          ({deviationSign}{deviation}%)
        </span>
      </td>
      <td className="py-3 px-4 text-gray-300">{rate.player_count.toLocaleString()}</td>
      <td className="py-3 px-4 text-gray-400 text-sm">{new Date(rate.last_seen).toLocaleString()}</td>
      <td className="py-3 px-4">
        <span className={`text-xs px-2.5 py-1 rounded-full border ${status.className}`}>
          {status.label}
        </span>
      </td>
    </tr>
  );
}

export default async function ExchangeRatesPage() {
  const result = await fetchExchangeRates();
  const rates = result.data?.rates ?? [];
  const base = result.data?.base ?? 'true_prices';
  const hasData = rates.length > 0;

  return (
    <div className="min-h-screen bg-gray-950 text-white">
      <Header />
      <main className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        {/* Page heading */}
        <div className="mb-8">
          <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">
            Market Insight
          </p>
          <h1 className="text-3xl sm:text-4xl font-bold text-white mb-3">Exchange Rates</h1>
          <p className="text-gray-400 max-w-3xl text-sm sm:text-base leading-relaxed">
            Each server's price level relative to the global{' '}
            <Link href="/true-prices" className="text-emerald-400 hover:underline">
              true prices
            </Link>{' '}
            baseline (<code className="text-xs bg-gray-800 px-1 py-0.5 rounded">{base}</code>). A rate
            of 1.30× means items cost 30% more than the true-price reference on that server.
          </p>
        </div>

        {/* Context link */}
        <div className="mb-6 flex items-center gap-2 text-sm text-gray-500">
          <span>Want to see individual servers?</span>
          <Link href="/servers" className="text-emerald-400 hover:underline">
            View all registered servers →
          </Link>
        </div>

        {hasData ? (
          <>
            {/* Bar chart */}
            <ExchangeRateChart rates={rates} />

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
                      <th className="py-2.5 px-4 text-xs font-medium text-gray-500 uppercase tracking-wide">Players</th>
                      <th className="py-2.5 px-4 text-xs font-medium text-gray-500 uppercase tracking-wide">Last Seen</th>
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
            {result.error && (
              <p className="text-amber-300 text-xs mt-4">{result.error}</p>
            )}
          </div>
        )}
      </main>
      <Footer />
    </div>
  );
}
