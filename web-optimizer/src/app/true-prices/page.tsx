import { PriceCalculator } from "@/components/prices/price-calculator";
import { Header } from "@/components/layout/header";
import { Footer } from "@/components/layout/footer";

export const metadata = {
  title: "True Prices | Auto-Tune",
  description: "Cross-server price discovery using least-squares optimization on ratio matrices",
};

export default function TruePricesPage() {
  return (
    <div className="min-h-screen bg-slate-950 text-white">
      <Header />
      <main className="container mx-auto px-4 py-12">
        <div className="max-w-4xl mx-auto">
          <h1 className="text-4xl font-bold text-emerald-400 mb-4">
            True Price Calculator
          </h1>
          <p className="text-slate-400 mb-8 text-lg">
            Calculate &quot;true&quot; item prices from cross-server ratio matrices using
            least-squares optimization. No matter how servers scale their prices,
            the ratios reveal the real relative value.
          </p>

          <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 mb-8">
            <h2 className="text-xl font-semibold text-emerald-400 mb-4">How It Works</h2>
            <ol className="list-decimal list-inside space-y-2 text-slate-300">
              <li>Each server provides a ratio matrix: r[i][j] = price_i / price_j</li>
              <li>We aggregate ratios across servers using geometric mean in log-space</li>
              <li>Build a least-squares system: x_i - x_j = log(r_ij)</li>
              <li>Anchor one item (e.g., dirt = $0.10) to set absolute scale</li>
              <li>Solve and exponentiate to get true prices</li>
            </ol>
          </div>

          <PriceCalculator />
        </div>
      </main>
      <Footer />
    </div>
  );
}
