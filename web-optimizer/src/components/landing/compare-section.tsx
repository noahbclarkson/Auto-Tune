import { ArrowRight, Check, Minus, X } from 'lucide-react';

const CATEGORIES = [
  {
    label: 'Pricing',
    items: [
      { label: 'Dynamic buy/sell prices', autotune: true, essentials: false, shopgui: false, playershops: false },
      { label: 'Per-item price history', autotune: true, essentials: false, shopgui: 'limited', playershops: false },
      { label: 'Configurable spread per item', autotune: true, essentials: false, shopgui: 'manual', playershops: false },
      { label: 'Automatic equilibrium pricing', autotune: true, essentials: false, shopgui: false, playershops: false },
    ],
  },
  {
    label: 'Security',
    items: [
      { label: 'Exploit protection (duping, vault overflow)', autotune: true, essentials: false, shopgui: 'partial', playershops: false },
      { label: 'Per-player buy/sell limits', autotune: true, essentials: true, shopgui: true, playershops: 'partial' },
      { label: 'Loan circuit breakers', autotune: true, essentials: false, shopgui: false, playershops: false },
      { label: 'Transaction audit trail', autotune: true, essentials: false, shopgui: true, playershops: 'partial' },
    ],
  },
  {
    label: 'Player Features',
    items: [
      { label: 'Auction house (player-to-player)', autotune: true, essentials: false, shopgui: 'external', playershops: true },
      { label: 'Price alerts (in-game + Discord)', autotune: true, essentials: false, shopgui: false, playershops: false },
      { label: 'Autosell on pickup', autotune: true, essentials: 'basic', shopgui: true, playershops: false },
      { label: 'Player portfolio + P&L tracking', autotune: true, essentials: false, shopgui: false, playershops: false },
    ],
  },
  {
    label: 'Ecosystem',
    items: [
      { label: 'Guild economy dashboard', autotune: true, essentials: false, shopgui: false, playershops: false },
      { label: 'Market events (Gold Rush, Supply Glut)', autotune: true, essentials: false, shopgui: false, playershops: false },
      { label: 'Cross-server true price discovery', autotune: true, essentials: false, shopgui: false, playershops: false },
      { label: 'Bundled real-time web dashboard', autotune: true, essentials: false, shopgui: 'paid-addon', playershops: false },
    ],
  },
];

type Val = boolean | string | null;
function Cell({ val }: { val: Val }) {
  if (val === true) return <Check className="w-4 h-4 text-emerald-500 mx-auto" />;
  if (val === false) return <Minus className="w-4 h-4 text-gray-600 mx-auto" />;
  // string = partial / limited / external / manual / basic / paid-addon
  return (
    <span
      className="text-[10px] text-amber-400 mx-auto text-center leading-tight"
      title={val ?? undefined}
    >
      {val}
    </span>
  );
}

export function CompareSection() {
  return (
    <section className="py-20 border-t border-gray-800/40">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="text-center max-w-2xl mx-auto mb-12">
          <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">Compared to Alternatives</p>
          <h2 className="text-2xl sm:text-3xl font-bold text-white mb-4">
            Why server admins switch to Auto-Tune
          </h2>
          <p className="text-gray-400 text-sm leading-relaxed">
            Most Minecraft economy plugins use static pricing. Players learn the prices, game the system, and the economy flatlines. Auto-Tune adapts — prices shift with real activity, keeping the market interesting.
          </p>
        </div>

        {/* Comparison table */}
        <div className="rounded-2xl border border-gray-800 overflow-hidden overflow-x-auto mb-8">
          <table className="w-full min-w-[640px] text-sm">
            <thead>
              <tr className="border-b border-gray-800 bg-gray-900/80">
                <th className="py-3.5 px-5 text-left font-semibold text-white w-1/2">Feature</th>
                <th className="py-3.5 px-4 text-center font-semibold text-emerald-400 w-[12%]">
                  <div className="flex flex-col items-center gap-1">
                    <span className="text-xs uppercase tracking-wider">Auto-Tune</span>
                  </div>
                </th>
                <th className="py-3.5 px-4 text-center font-medium text-gray-500 w-[12%]">
                  <div className="flex flex-col items-center gap-1">
                    <span className="text-xs uppercase tracking-wider">Essentials</span>
                  </div>
                </th>
                <th className="py-3.5 px-4 text-center font-medium text-gray-500 w-[12%]">
                  <div className="flex flex-col items-center gap-1">
                    <span className="text-xs uppercase tracking-wider">ShopGUI+</span>
                  </div>
                </th>
                <th className="py-3.5 px-4 text-center font-medium text-gray-500 w-[12%]">
                  <div className="flex flex-col items-center gap-1">
                    <span className="text-xs uppercase tracking-wider">PlayerShops</span>
                  </div>
                </th>
              </tr>
            </thead>
            <tbody>
              {CATEGORIES.map((cat) => (
                <>
                  <tr key={cat.label} className="border-b border-gray-800 bg-gray-900/40">
                    <td colSpan={5} className="px-5 py-2.5">
                      <span className="text-xs font-semibold text-gray-400 uppercase tracking-wider">{cat.label}</span>
                    </td>
                  </tr>
                  {cat.items.map((item) => (
                    <tr key={item.label} className="border-b border-gray-800/50 hover:bg-gray-800/20 transition-colors">
                      <td className="py-2.5 px-5 text-gray-300 text-sm">{item.label}</td>
                      <td className="py-2.5 px-4 text-center"><Cell val={item.autotune} /></td>
                      <td className="py-2.5 px-4 text-center"><Cell val={item.essentials} /></td>
                      <td className="py-2.5 px-4 text-center"><Cell val={item.shopgui} /></td>
                      <td className="py-2.5 px-4 text-center"><Cell val={item.playershops} /></td>
                    </tr>
                  ))}
                </>
              ))}
            </tbody>
          </table>
        </div>

        {/* Legend */}
        <div className="flex flex-wrap items-center justify-center gap-4 text-xs text-gray-500 mb-8">
          <span className="flex items-center gap-1.5">
            <Check className="w-3.5 h-3.5 text-emerald-500" /> Full support
          </span>
          <span className="flex items-center gap-1.5">
            <Minus className="w-3.5 h-3.5 text-gray-600" /> Not available
          </span>
          <span className="flex items-center gap-1.5">
            <span className="text-[10px] text-amber-400 border border-amber-800/50 px-1.5 py-0.5 rounded">partial</span>
            Partial or limited support
          </span>
        </div>

        {/* CTA */}
        <div className="text-center">
          <p className="text-sm text-gray-400 mb-4">
            Install Auto-Tune in under 5 minutes — no database setup required.
          </p>
          <a
            href="#get-started"
            className="inline-flex items-center gap-2 px-5 py-2.5 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded-lg transition-colors shadow-lg shadow-emerald-600/20 text-sm"
          >
            Get Started <ArrowRight className="w-4 h-4" />
          </a>
        </div>
      </div>
    </section>
  );
}
