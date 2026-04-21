import { PriceCalculator } from "@/components/prices/price-calculator";
import { Header } from "@/components/layout/header";
import { Footer } from "@/components/layout/footer";
import { fetchExchangeRates, fetchTruePrices, hasConfiguredApiUrl } from "@/lib/api-client";
import { TruePricesLive } from "@/components/prices/true-prices-live";
import { SimulatedTruePrices } from "@/components/prices/simulated-true-prices";

export const metadata = {
  title: "True Prices | Auto-Tune",
  description:
    "Cross-server price discovery using least-squares optimization on ratio matrices",
  openGraph: {
    title: "True Prices | Auto-Tune",
    description: "Cross-server price consensus computed from verified server submissions",
    images: [{ url: '/og-image.png', width: 1200, height: 630 }],
  },
  twitter: { card: 'summary_large_image', images: ['/og-image.png'] },
};

export default async function TruePricesPage() {
  const [truePricesResult, exchangeRatesResult] = await Promise.all([
    fetchTruePrices(),
    fetchExchangeRates(),
  ]);

  const prices = truePricesResult.data?.prices ?? [];
  const lastUpdated = truePricesResult.data?.last_updated;

  const hasLiveData = prices.length > 0;
  const liveError = truePricesResult.error ?? exchangeRatesResult.error;

  return (
    <div className="min-h-screen bg-gray-950 text-white">
      <Header />
      <main className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <div className="mb-8">
          <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">
            Data Engine
          </p>
          <h1 className="text-3xl sm:text-4xl font-bold text-white mb-3">True Prices</h1>
          <p className="text-gray-400 max-w-3xl text-sm sm:text-base leading-relaxed">
            Auto-Tune combines price ratios from multiple servers and solves a constrained
            least-squares system to estimate globally consistent item values.
          </p>
        </div>

        <div className="bg-gray-900/50 border border-gray-800/50 rounded-xl p-5 mb-8">
          <h2 className="text-sm font-semibold text-emerald-400 uppercase tracking-wide mb-2">
            How the true-prices model works
          </h2>
          <p className="text-gray-300 text-sm leading-relaxed">
            Each server contributes a ratio matrix where every value expresses one item relative
            to another. We aggregate those ratios in log-space, solve the best-fit graph of
            relative prices, then anchor one item to an absolute value so the whole market has a
            practical price scale.
          </p>
        </div>

        {hasLiveData ? (
          <>
            <TruePricesLive prices={prices} />
            {lastUpdated && (
              <p className="text-xs text-gray-500 mb-8">Last updated: {new Date(lastUpdated).toLocaleString()}</p>
            )}
          </>
        ) : (
          <>
            <SimulatedTruePrices />
          </>
        )}

        <PriceCalculator />
      </main>
      <Footer />
    </div>
  );
}
