import { TrendingUp, Scale, Zap } from 'lucide-react';

const features = [
  {
    icon: TrendingUp,
    title: 'Adaptive Pricing',
    description:
      'Prices automatically adjust based on market conditions. High demand? Prices rise. Oversupply? Prices fall. The market finds equilibrium naturally.',
  },
  {
    icon: Scale,
    title: 'Supply & Demand',
    description:
      'Buy/sell ratios drive spread adjustments. When players buy more than they sell, the buy price rises and sell price drops to restore balance.',
  },
  {
    icon: Zap,
    title: 'Real-time Spreads',
    description:
      'Dynamic spreads respond to player count, global activity, and item liquidity. Active markets get tighter spreads, inactive ones widen for stability.',
  },
];

export function FeatureCards() {
  return (
    <section className="py-20 bg-gray-900/50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <h2 className="text-3xl font-bold text-center text-white mb-12">
          How Auto-Tune Works
        </h2>
        
        <div className="grid md:grid-cols-3 gap-8">
          {features.map((feature) => (
            <div
              key={feature.title}
              className="bg-gray-900 border border-gray-800 rounded-xl p-6 hover:border-emerald-600/50 transition-colors"
            >
              <div className="w-12 h-12 bg-emerald-600/20 rounded-lg flex items-center justify-center mb-4">
                <feature.icon className="w-6 h-6 text-emerald-500" />
              </div>
              <h3 className="text-xl font-semibold text-white mb-2">
                {feature.title}
              </h3>
              <p className="text-gray-400">
                {feature.description}
              </p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
