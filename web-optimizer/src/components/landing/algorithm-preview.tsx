import { generateSpreadCurve } from '@/lib/market-engine';

export function AlgorithmPreview() {
  const curveData = generateSpreadCurve(undefined, 10, 0, 0);
  
  // Pick a few key points to display
  const keyPoints = [0, 25, 50, 75, 100].map((i) => ({
    ...curveData[i],
    label: `${i}% buys`,
  }));

  return (
    <section className="py-20">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="bg-gray-900 border border-gray-800 rounded-xl p-8">
          <h2 className="text-2xl font-bold text-white mb-6 text-center">
            The Spread Formula
          </h2>
          
          <div className="mb-8 text-center">
            <div className="inline-block bg-gray-800 rounded-lg px-6 py-4 text-lg">
              <code className="text-emerald-400">
                spread = baseSpread × playerFactor × volumeMult × liquidity
              </code>
            </div>
          </div>
          
          <p className="text-gray-400 text-center mb-8">
            The total spread is a product of four factors: the base spread configured for the item,
            a player count factor that tightens spreads with more players, a global volume multiplier
            based on market activity, and a per-item liquidity reduction.
          </p>
          
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-700">
                  <th className="py-3 px-4 text-left text-gray-400">Buy Ratio</th>
                  <th className="py-3 px-4 text-right text-green-400">Buy Spread (BPD)</th>
                  <th className="py-3 px-4 text-right text-red-400">Sell Spread (SPD)</th>
                  <th className="py-3 px-4 text-right text-gray-300">Total Spread</th>
                </tr>
              </thead>
              <tbody>
                {keyPoints.map((point) => (
                  <tr key={point.buyRatio} className="border-b border-gray-800">
                    <td className="py-3 px-4 text-gray-300">{point.label}</td>
                    <td className="py-3 px-4 text-right text-green-400">{point.bpd.toFixed(2)}%</td>
                    <td className="py-3 px-4 text-right text-red-400">{point.spd.toFixed(2)}%</td>
                    <td className="py-3 px-4 text-right text-gray-300">{point.totalSpread.toFixed(2)}%</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          
          <p className="text-gray-500 text-sm mt-6 text-center">
            At 50% buys (balanced), both spreads are equal. As buy ratio increases, the buy spread 
            widens and sell spread narrows, encouraging players to sell more and restoring equilibrium.
          </p>
        </div>
      </div>
    </section>
  );
}
