import { Metadata } from 'next';
import Link from 'next/link';
import { ArrowLeft, Code2, Database, TrendingUp, AlertCircle, Layers } from 'lucide-react';

export const metadata: Metadata = {
  title: 'PlaceholderAPI Reference | Auto-Tune',
  description:
    'Complete reference for Auto-Tune PlaceholderAPI placeholders. Use %autotune_*% in scoreboards, TAB, Holograms, and other PlaceholderAPI-dependent plugins.',
  openGraph: {
    title: 'PlaceholderAPI Reference | Auto-Tune',
    description:
      'Use %autotune_*% placeholders in scoreboards, TAB, Holograms, and other PlaceholderAPI plugins.',
  },
};

const GLOBAL_PLACEHOLDERS = [
  {
    placeholder: '%autotune_gdp%',
    description: 'Current economy GDP (total 24h trading volume)',
    example: '$12,847',
    category: 'Global Economy',
    icon: Database,
  },
  {
    placeholder: '%autotune_debt%',
    description: 'Total outstanding loan debt across all players',
    example: '$3,291',
    category: 'Global Economy',
    icon: Database,
  },
  {
    placeholder: '%autotune_inflation%',
    description: 'Current inflation label (e.g. "Stable", "Mild", "Hyper")',
    example: 'Stable',
    category: 'Global Economy',
    icon: AlertCircle,
  },
  {
    placeholder: '%autotune_volume%',
    description: 'Global volume multiplier (e.g. 1.05x = 5% above normal activity)',
    example: '1.02x',
    category: 'Global Economy',
    icon: TrendingUp,
  },
  {
    placeholder: '%autotune_items%',
    description: 'Number of items currently tracked by the market engine',
    example: '64',
    category: 'Global Economy',
    icon: Layers,
  },
  {
    placeholder: '%autotune_frozen%',
    description: 'Whether the market is frozen (true/false)',
    example: 'false',
    category: 'Global Economy',
    icon: AlertCircle,
  },
];

const PER_ITEM_TYPES = [
  { type: 'price', description: 'Current mid-price', example: '$285.00' },
  { type: 'buy', description: 'Buy price (mid + BPD spread)', example: '$288.35' },
  { type: 'sell', description: 'Sell price (mid − SPD spread)', example: '$281.65' },
  { type: 'spread', description: 'Total spread % (BPD + SPD)', example: '2.4%' },
  { type: 'bpd', description: 'Buy price deviation % from mid', example: '1.20%' },
  { type: 'spd', description: 'Sell price deviation % from mid', example: '1.20%' },
  { type: 'trend', description: '24h price trend arrow (▲/▼/—)', example: '▲' },
];

const EXAMPLE_MATERIALS = [
  { key: 'DIAMOND', display: 'Diamond' },
  { key: 'GOLD_INGOT', display: 'Gold Ingot' },
  { key: 'EMERALD', display: 'Emerald' },
  { key: 'IRON_INGOT', display: 'Iron Ingot' },
  { key: 'NETHERITE_INGOT', display: 'Netherite Ingot' },
  { key: 'LAVA_BUCKET', display: 'Lava Bucket' },
];

function PlaceholderTable({ placeholders }: { placeholders: typeof GLOBAL_PLACEHOLDERS }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-gray-800">
            <th className="text-left py-3 px-4 font-semibold text-gray-300">Placeholder</th>
            <th className="text-left py-3 px-4 font-semibold text-gray-300">Description</th>
            <th className="text-left py-3 px-4 font-semibold text-gray-300">Example</th>
          </tr>
        </thead>
        <tbody>
          {placeholders.map((p) => (
            <tr key={p.placeholder} className="border-b border-gray-800/50 hover:bg-gray-900/40">
              <td className="py-3 px-4">
                <code className="text-emerald-400 text-xs font-mono bg-emerald-950/30 px-2 py-1 rounded">
                  {p.placeholder}
                </code>
              </td>
              <td className="py-3 px-4 text-gray-400">{p.description}</td>
              <td className="py-3 px-4 text-gray-500 font-mono text-xs">{p.example}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default function PlaceholdersPage() {
  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      {/* Header */}
      <div className="border-b border-gray-800/60 bg-gray-950">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
          <div className="flex items-center gap-3 mb-2">
            <Link
              href="/docs"
              className="flex items-center gap-1 text-sm text-gray-400 hover:text-gray-200 transition-colors"
            >
              <ArrowLeft className="w-4 h-4" />
              Docs
            </Link>
            <span className="text-gray-600">/</span>
            <span className="text-sm text-gray-400">PlaceholderAPI Reference</span>
          </div>
          <div className="flex items-center gap-3 mb-4">
            <Code2 className="w-6 h-6 text-emerald-400" />
            <span className="text-sm font-medium text-emerald-400 uppercase tracking-wider">
              Reference
            </span>
          </div>
          <h1 className="text-4xl font-bold text-white mb-3">PlaceholderAPI Reference</h1>
          <p className="text-lg text-gray-400 max-w-2xl leading-relaxed">
            Use Auto-Tune placeholders in scoreboards, TAB, Holograms, and any plugin that
            supports PlaceholderAPI expansions. Requires PlaceholderAPI to be installed on your server.
          </p>
        </div>
      </div>

      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-10 space-y-10">
        {/* Installation */}
        <section>
          <h2 className="text-xl font-bold text-white mb-4">Installation</h2>
          <div className="bg-gray-900 border border-gray-800 rounded-xl p-6">
            <p className="text-sm text-gray-400 mb-4">
              Auto-Tune&apos;s PlaceholderAPI expansion registers automatically when PlaceholderAPI is
              present. No configuration needed — just install the plugin and PAPI placeholders will
              work.
            </p>
            <div className="flex items-start gap-3 p-4 bg-amber-950/20 border border-amber-800/30 rounded-lg">
              <AlertCircle className="w-4 h-4 text-amber-400 mt-0.5 flex-shrink-0" />
              <div className="text-sm text-amber-200/80">
                <strong className="text-amber-300">Optional dependency:</strong> Auto-Tune works
                perfectly without PlaceholderAPI. The expansion is only needed if you want to
                display economy data in external plugins like TAB, Holograms, or scoreboard plugins.
              </div>
            </div>
          </div>
        </section>

        {/* Global placeholders */}
        <section>
          <h2 className="text-xl font-bold text-white mb-4">Global Placeholders</h2>
          <p className="text-sm text-gray-400 mb-4">
            Economy-wide statistics — no material argument needed.
          </p>
          <div className="bg-gray-900 border border-gray-800 rounded-xl overflow-hidden">
            <PlaceholderTable placeholders={GLOBAL_PLACEHOLDERS} />
          </div>
        </section>

        {/* Per-item placeholders */}
        <section>
          <h2 className="text-xl font-bold text-white mb-4">Per-Item Placeholders</h2>
          <p className="text-sm text-gray-400 mb-4">
            Format:{' '}
            <code className="text-emerald-400 font-mono text-xs bg-emerald-950/30 px-1.5 py-0.5 rounded">
              %autotune_&lt;type&gt;_&lt;MATERIAL&gt;%
            </code>
          </p>

          {/* Type table */}
          <div className="bg-gray-900 border border-gray-800 rounded-xl overflow-hidden mb-6">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-800">
                    <th className="text-left py-3 px-4 font-semibold text-gray-300">Type</th>
                    <th className="text-left py-3 px-4 font-semibold text-gray-300">Description</th>
                    <th className="text-left py-3 px-4 font-semibold text-gray-300">Example output</th>
                  </tr>
                </thead>
                <tbody>
                  {PER_ITEM_TYPES.map((t) => (
                    <tr key={t.type} className="border-b border-gray-800/50 hover:bg-gray-900/40">
                      <td className="py-3 px-4">
                        <code className="text-emerald-400 font-mono text-xs bg-emerald-950/30 px-2 py-1 rounded">
                          autotune_{t.type}_
                        </code>
                      </td>
                      <td className="py-3 px-4 text-gray-400">{t.description}</td>
                      <td className="py-3 px-4 text-gray-500 font-mono text-xs">{t.example}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Materials table */}
          <h3 className="text-base font-semibold text-white mb-3">Available Materials</h3>
          <p className="text-sm text-gray-400 mb-4">
            Use the material key in UPPER_SNAKE_CASE. Any material tracked by Auto-Tune (in{' '}
            <code className="text-gray-300 font-mono text-xs bg-gray-800 px-1.5 py-0.5 rounded">shops.yml</code>)
            can be queried.
          </p>
          <div className="bg-gray-900 border border-gray-800 rounded-xl overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-800">
                    <th className="text-left py-3 px-4 font-semibold text-gray-300">Placeholder key</th>
                    <th className="text-left py-3 px-4 font-semibold text-gray-300">Material</th>
                    <th className="text-left py-3 px-4 font-semibold text-gray-300">Example</th>
                  </tr>
                </thead>
                <tbody>
                  {EXAMPLE_MATERIALS.map((m) => (
                    <tr key={m.key} className="border-b border-gray-800/50 hover:bg-gray-900/40">
                      <td className="py-3 px-4">
                        <code className="text-emerald-400 font-mono text-xs bg-emerald-950/30 px-2 py-1 rounded">
                          %autotune_price_{m.key}%
                        </code>
                      </td>
                      <td className="py-3 px-4 text-gray-300">{m.display}</td>
                      <td className="py-3 px-4 text-gray-500 font-mono text-xs">
                        %autotune_price_{m.key}%
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </section>

        {/* Full examples */}
        <section>
          <h2 className="text-xl font-bold text-white mb-4">Usage Examples</h2>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="bg-gray-900 border border-gray-800 rounded-xl p-5">
              <h3 className="text-sm font-semibold text-white mb-3">TAB Scoreboard</h3>
              <pre className="text-xs font-mono text-gray-300 bg-gray-950 p-3 rounded-lg overflow-x-auto">
{`Your Balance: %player_balance%
Diamond: %autotune_price_DIAMOND%
Buy/Sell: %autotune_buy_DIAMOND% / %autotune_sell_DIAMOND%
24h Trend: %autotune_trend_DIAMOND%
Economy GDP: %autotune_gdp%`}
              </pre>
            </div>
            <div className="bg-gray-900 border border-gray-800 rounded-xl p-5">
              <h3 className="text-sm font-semibold text-white mb-3">Hologram Lines</h3>
              <pre className="text-xs font-mono text-gray-300 bg-gray-950 p-3 rounded-lg overflow-x-auto">
{`⬥ Market Prices ⬥
Diamond: %autotune_price_DIAMOND%
Gold: %autotune_price_GOLD_INGOT%
Volume: %autotune_volume%
Market: %autotune_frozen% (frozen)`}
              </pre>
            </div>
            <div className="bg-gray-900 border border-gray-800 rounded-xl p-5">
              <h3 className="text-sm font-semibold text-white mb-3">Conditionals</h3>
              <pre className="text-xs font-mono text-gray-300 bg-gray-950 p-3 rounded-lg overflow-x-auto">
{`%if-autotune_frozen_true%MARKET FROZEN%endif%
%if-autotune_trend_DIAMOND_eq_▲%PRICE RISING%endif%`}
              </pre>
            </div>
            <div className="bg-gray-900 border border-gray-800 rounded-xl p-5">
              <h3 className="text-sm font-semibold text-white mb-3">Multi-Item Header</h3>
              <pre className="text-xs font-mono text-gray-300 bg-gray-950 p-3 rounded-lg overflow-x-auto">
{`Market: %autotune_gdp% GDP | %autotune_volume% vol
Diamonds: %autotune_price_DIAMOND% (%autotune_trend_DIAMOND%)
Gold: %autotune_price_GOLD_INGOT% (%autotune_trend_GOLD_INGOT%)`}
              </pre>
            </div>
          </div>
        </section>

        {/* Note on price updates */}
        <section>
          <div className="flex items-start gap-3 p-5 bg-emerald-950/20 border border-emerald-800/30 rounded-xl">
            <TrendingUp className="w-5 h-5 text-emerald-400 mt-0.5 flex-shrink-0" />
            <div>
              <h3 className="text-sm font-semibold text-emerald-300 mb-1">Live Updates</h3>
              <p className="text-sm text-emerald-200/70 leading-relaxed">
                PlaceholderAPI expansions are queried every tick by dependent plugins.
                Auto-Tune&apos;s placeholder values update in real-time as the market engine refreshes
                prices — approximately every 5 minutes or when significant trade activity occurs.
                The placeholder expansion is designed to be lightweight; it does not trigger any
                economy calculations on its own.
              </p>
            </div>
          </div>
        </section>

        {/* Navigation */}
        <section className="border-t border-gray-800/60 pt-8">
          <h2 className="text-lg font-bold text-white mb-4">Continue Reading</h2>
          <div className="grid gap-3 sm:grid-cols-3">
            {[
              { href: '/docs', label: 'Documentation', desc: 'All guides and references' },
              { href: '/install', label: 'Install Guide', desc: 'Get Auto-Tune running on your server' },
              { href: '/simulator', label: 'Simulator', desc: 'Test config changes before deploying' },
            ].map(({ href, label, desc }) => (
              <Link
                key={href}
                href={href}
                className="flex items-start gap-3 p-4 bg-gray-900 border border-gray-800 rounded-lg hover:border-gray-700 transition-colors group"
              >
                <div>
                  <p className="text-sm font-medium text-white group-hover:text-emerald-300 transition-colors">
                    {label}
                  </p>
                  <p className="text-xs text-gray-500 mt-0.5">{desc}</p>
                </div>
              </Link>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
}