import { ArrowRight, Calculator, TrendingUp, Users, BarChart3, Droplets, ArrowUpDown } from 'lucide-react';
import Link from 'next/link';

const sections = [
  {
    icon: TrendingUp,
    title: '1. Base Spread',
    content: `Every item has a base spread — the fundamental difference between what players pay to buy and what they receive when selling. A 30% base spread means the buy price is 15% above the base, and the sell price is 15% below.`,
    formula: 'baseSpread = 0.30 (30%)',
    details: `This spread exists for two reasons: it gives the server a small profit margin to prevent exploitation, and it creates room for the market to adjust prices dynamically based on conditions.`,
  },
  {
    icon: BarChart3,
    title: '2. Supply & Demand',
    content: `When players buy more than they sell, the buy price rises and sell price falls. This creates a natural incentive for players to sell, restoring balance to the market.`,
    formula: 'imbalance = (buyRatio - 0.5) × 2',
    details: `If 80% of trades are buys, the "imbalance" is +0.6. This shifts the spread so buying becomes more expensive and selling more profitable, encouraging equilibrium.`,
  },
  {
    icon: Users,
    title: '3. Player Count Scaling',
    content: `More players means tighter spreads. A server with 20+ players gets much better prices than one with just 1-2 players. This uses a tanh curve for smooth, diminishing returns.`,
    formula: 'playerScaling = tanh(n × atanh(0.99) / fullEffectPlayers)',
    details: `With 20 players configured as "full effect", you get 99% of the benefit. Additional players beyond this provide minimal additional spread reduction, preventing exploits on large servers.`,
  },
  {
    icon: Calculator,
    title: '4. Global Volume Multiplier',
    content: `When the entire market is busy (high z-score), spreads tighten. When activity is low (negative z-score), spreads widen. This is based on the statistical z-score of recent trading volume.`,
    formula: 'multiplier = 1.0 (normal) → 0.5 (busy) → 2.0 (quiet)',
    details: `The z-score measures how many standard deviations the current activity is from the mean. A z-score of +2 means activity is 2 standard deviations above normal — spreads drop to 50%.`,
  },
  {
    icon: Droplets,
    title: '5. Liquidity Reduction',
    content: `Heavily-traded items get tighter spreads. If an item has 1000 weighted volume, its spread might be reduced to 1/(1 + 1000×0.05) = 2% of normal. This rewards items with active markets.`,
    formula: 'liquidity = 1 / (1 + totalWeightedVolume × coeff)',
    details: `This prevents price manipulation on rare items while allowing commonly-traded goods to have competitive prices. The coefficient controls how quickly spreads tighten.`,
  },
  {
    icon: ArrowUpDown,
    title: '6. Price Movement',
    content: `Each tick (default 5 minutes), prices adjust based on the trade ratio and player count. More players means faster price changes, but they're capped to prevent wild swings.`,
    formula: 'change% = tradeRatio × playerScaling × maxChange%',
    details: `With 3% max change and 20 players, a 70% buy ratio causes ~1.5% price increase per tick. Over 24 hours, this compounds significantly, driving prices toward equilibrium.`,
  },
];

export default function HowItWorks() {
  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
      {/* Header */}
      <div className="text-center mb-12">
        <h1 className="text-4xl font-bold text-white mb-4">
          How Auto-Tune Works
        </h1>
        <p className="text-xl text-gray-400">
          Understanding the market engine behind adaptive pricing
        </p>
      </div>

      {/* Quick Summary */}
      <div className="bg-emerald-950/30 border border-emerald-800/50 rounded-xl p-6 mb-12">
        <h2 className="text-lg font-semibold text-emerald-400 mb-3">
          Quick Summary
        </h2>
        <p className="text-gray-300">
          Auto-Tune calculates prices using a multi-factor model that considers player count,
          buy/sell ratios, global activity, and per-item liquidity. The result is a dynamic
          spread that tightens during active periods and widens when needed to maintain market
          stability. Prices evolve over time based on trade patterns, naturally finding
          equilibrium.
        </p>
        <Link
          href="/simulator"
          className="inline-flex items-center gap-2 mt-4 text-emerald-400 hover:text-emerald-300 transition-colors"
        >
          Try the interactive simulator
          <ArrowRight className="w-4 h-4" />
        </Link>
      </div>

      {/* Detailed Sections */}
      <div className="space-y-8">
        {sections.map((section) => (
          <div
            key={section.title}
            className="bg-gray-900 border border-gray-800 rounded-xl p-6"
          >
            <div className="flex items-start gap-4">
              <div className="w-10 h-10 bg-emerald-600/20 rounded-lg flex items-center justify-center flex-shrink-0">
                <section.icon className="w-5 h-5 text-emerald-500" />
              </div>
              <div className="flex-1">
                <h3 className="text-xl font-semibold text-white mb-2">
                  {section.title}
                </h3>
                <p className="text-gray-300 mb-4">{section.content}</p>
                
                {/* Formula box */}
                <div className="bg-gray-800 rounded-lg px-4 py-2 mb-4">
                  <code className="text-emerald-400 text-sm">{section.formula}</code>
                </div>
                
                <p className="text-sm text-gray-500">{section.details}</p>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Full Algorithm */}
      <div className="mt-12 bg-gray-900 border border-gray-800 rounded-xl p-6">
        <h2 className="text-xl font-semibold text-white mb-4">
          The Complete Algorithm
        </h2>
        <div className="space-y-4 text-gray-300">
          <p>
            Putting it all together, here&apos;s how Auto-Tune calculates prices:
          </p>
          <ol className="list-decimal list-inside space-y-2 text-gray-400">
            <li>Start with the base price for the item</li>
            <li>Calculate half-spread: baseSpread / 2</li>
            <li>Adjust for buy/sell imbalance using volume impact</li>
            <li>Apply liquidity reduction based on weighted volume</li>
            <li>Apply player count scaling factor</li>
            <li>Apply global volume multiplier from z-score</li>
            <li>Buy price = base × (1 + BPD), Sell price = base × (1 - SPD)</li>
          </ol>
        </div>
        <div className="mt-6 p-4 bg-gray-800 rounded-lg">
          <code className="text-emerald-400">
            spread = baseSpread × playerFactor × volumeMult × liquidity
          </code>
        </div>
      </div>

      {/* CTA */}
      <div className="mt-12 text-center">
        <Link
          href="/simulator"
          className="inline-flex items-center gap-2 px-6 py-3 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded-lg transition-colors shadow-lg shadow-emerald-600/25"
        >
          <Calculator className="w-5 h-5" />
          Try the Simulator
        </Link>
      </div>
    </div>
  );
}
