import { PriceCalculator } from '@/components/prices/price-calculator';
import { Header } from '@/components/layout/header';
import { Footer } from '@/components/layout/footer';

export const metadata = {
  title: 'True Prices | Auto-Tune',
  description:
    'Cross-server price discovery using least-squares optimization on ratio matrices',
};

export default function TruePricesPage() {
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

        <PriceCalculator />
      </main>
      <Footer />
    </div>
  );
}
