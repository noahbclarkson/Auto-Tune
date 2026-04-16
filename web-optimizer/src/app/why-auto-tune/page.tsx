import { Metadata } from 'next';
import Link from 'next/link';
import { ArrowRight, TrendingUp, TrendingDown, Users, Zap, Shield, BarChart2, Clock } from 'lucide-react';

export const metadata: Metadata = {
  title: 'Why Auto-Tune | Auto-Tune',
  description:
    'Static Minecraft shop prices are trivially gameable. Auto-Tune\'s supply-and-demand engine adapts prices automatically — creating a real economy that rewards smart players.',
  openGraph: {
    title: 'Why Auto-Tune | Auto-Tune',
    description: "Static shop prices are trivially gameable. Auto-Tune adapts.",
    images: [{ url: '/og-image.png', width: 1200, height: 630, alt: 'Why Auto-Tune' }],
  },
  twitter: { card: 'summary_large_image', images: ['/og-image.png'] },
};

// ─── Mock price history for static vs Auto-Tune ─────────────────────────────

const DIAMOND_WEEKS = ['Week 1', 'Week 2', 'Week 3', 'Week 4', 'Week 5', 'Week 6'];

const STATIC_DIAMOND = [250, 250, 250, 250, 250, 250];
const AUTOTUNE_DIAMOND = [250, 312, 278, 341, 289, 265];

function MiniSparkline({ data, color }: { data: number[]; color: string }) {
  const min = Math.min(...data);
  const max = Math.max(...data);
  const range = max - min || 1;
  const w = 120;
  const h = 36;
  const pts = data.map((v, i) => {
    const x = (i / (data.length - 1)) * w;
    const y = h - ((v - min) / range) * h;
    return `${x},${y}`;
  }).join(' ');
  return (
    <svg width={w} height={h} className="inline-block">
      <polyline
        points={pts}
        fill="none"
        stroke={color}
        strokeWidth="2"
        strokeLinejoin="round"
        strokeLinecap="round"
      />
    </svg>
  );
}

function ScenarioCard({
  icon: Icon,
  title,
  staticPrice,
  autotunePrice,
  staticEffect,
  autotuneEffect,
  explanation,
  badge,
  badgeColor,
}: {
  icon: React.ElementType;
  title: string;
  staticPrice: string;
  autotunePrice: string;
  staticEffect: string;
  autotuneEffect: string;
  explanation: string;
  badge?: string;
  badgeColor?: string;
}) {
  return (
    <div className="bg-gray-900/60 border border-gray-800 rounded-2xl p-6 flex flex-col gap-4 hover:border-gray-700 transition-colors">
      <div className="flex items-start justify-between">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-emerald-950/60 border border-emerald-800/50 flex items-center justify-center flex-shrink-0">
            <Icon className="w-4 h-4 text-emerald-400" />
          </div>
          <div>
            <h3 className="font-semibold text-white text-sm">{title}</h3>
            {badge && (
              <span className={`text-[10px] font-medium px-1.5 py-0.5 rounded-full ${badgeColor}`}>
                {badge}
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Static vs Auto-Tune prices */}
      <div className="grid grid-cols-2 gap-3">
        <div className="bg-gray-800/50 rounded-xl p-3">
          <p className="text-[10px] text-gray-500 uppercase tracking-wider mb-1">Static Shop</p>
          <p className="text-lg font-bold text-gray-400">{staticPrice}</p>
          <p className="text-xs text-gray-600 mt-1">{staticEffect}</p>
        </div>
        <div className="bg-emerald-950/30 rounded-xl p-3 border border-emerald-800/40">
          <p className="text-[10px] text-emerald-500 uppercase tracking-wider mb-1">Auto-Tune</p>
          <p className="text-lg font-bold text-emerald-400">{autotunePrice}</p>
          <p className="text-xs text-emerald-600/80 mt-1">{autotuneEffect}</p>
        </div>
      </div>

      <p className="text-xs text-gray-400 leading-relaxed">{explanation}</p>
    </div>
  );
}

export default function WhyAutoTunePage() {
  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">

      {/* ─── Hero ─────────────────────────────────────────────────────────── */}
      <section className="relative overflow-hidden border-b border-gray-800/50">
        {/* Background gradient */}
        <div className="absolute inset-0 bg-gradient-to-b from-emerald-950/20 via-gray-950 to-gray-950 pointer-events-none" />
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[600px] h-[300px] bg-emerald-500/5 rounded-full blur-3xl pointer-events-none" />

        <div className="relative max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-24 text-center">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-emerald-950/60 border border-emerald-800/50 text-emerald-400 text-xs font-medium mb-8">
            <Zap className="w-3 h-3" />
            Supply-and-demand pricing for Minecraft
          </div>

          <h1 className="text-4xl sm:text-5xl font-bold text-white mb-6 leading-tight">
            Static prices are a dead end.
            <br />
            <span className="text-emerald-400">Auto-Tune makes it real.</span>
          </h1>

          <p className="text-lg text-gray-400 max-w-2xl mx-auto mb-10 leading-relaxed">
            Every Minecraft economy plugin has a shop. Most of them have the same problem: prices
            never change. Players learn them in an hour, game them in a day, and the economy
            flatlines in a week. Auto-Tune fixes that.
          </p>

          <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
            <Link
              href="/install"
              className="inline-flex items-center gap-2 px-6 py-3 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded-xl transition-colors shadow-lg shadow-emerald-600/20"
            >
              Install in 5 minutes <ArrowRight className="w-4 h-4" />
            </Link>
            <Link
              href="/how-it-works"
              className="inline-flex items-center gap-2 px-6 py-3 border border-gray-700 hover:border-gray-600 text-gray-300 font-medium rounded-xl transition-colors"
            >
              How the engine works
            </Link>
          </div>
        </div>
      </section>

      {/* ─── The Problem ──────────────────────────────────────────────────── */}
      <section className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-20">
        <div className="text-center mb-14">
          <p className="text-xs text-amber-400 uppercase tracking-widest font-medium mb-2">The Problem</p>
          <h2 className="text-2xl sm:text-3xl font-bold text-white mb-4">Static shops create a broken economy</h2>
          <p className="text-gray-400 max-w-xl mx-auto text-sm leading-relaxed">
            When prices never move, the economy stops being interesting. Here&apos;s what happens on every server with a static shop.
          </p>
        </div>

        {/* The static shop problem — illustrated */}
        <div className="bg-gray-900/60 border border-gray-800 rounded-2xl overflow-hidden mb-10">
          <div className="grid sm:grid-cols-3 divide-y sm:divide-y-0 sm:divide-x divide-gray-800">
            {[
              {
                day: 'Day 1',
                title: 'Players learn the prices',
                body: 'An hour of play and everyone knows Diamond Pickaxe is $250. They\'ve done the math. The mystery is gone.',
                icon: '💡',
                iconBg: 'bg-amber-950/50',
                iconBorder: 'border-amber-800/50',
              },
              {
                day: 'Day 7',
                title: 'Exploiters find the margins',
                body: 'Someone buys Diamonds at $250, crafts Diamond Picks, and sells them back at $245. Profit with zero risk. The shop funds their empire.',
                icon: '⚠️',
                iconBg: 'bg-orange-950/50',
                iconBorder: 'border-orange-800/50',
              },
              {
                day: 'Day 30',
                title: 'The economy collapses',
                body: 'Smart players have millions. Everyone else quit. The shop is a money printer for the 10% who figured it out. Everyone else stopped caring.',
                icon: '💀',
                iconBg: 'bg-red-950/50',
                iconBorder: 'border-red-800/50',
              },
            ].map(({ day, title, body, icon, iconBg, iconBorder }) => (
              <div key={day} className="p-6 text-center">
                <div className={`w-10 h-10 rounded-xl ${iconBg} border ${iconBorder} flex items-center justify-center text-lg mx-auto mb-4`}>
                  {icon}
                </div>
                <p className="text-xs text-gray-500 uppercase tracking-wider mb-1">{day}</p>
                <h3 className="font-semibold text-white text-sm mb-2">{title}</h3>
                <p className="text-xs text-gray-400 leading-relaxed">{body}</p>
              </div>
            ))}
          </div>
        </div>

        <div className="bg-emerald-950/20 border border-emerald-800/30 rounded-2xl p-6 text-center">
          <p className="text-sm text-emerald-300 font-medium mb-1">
            Auto-Tune&apos;s fix: prices follow supply and demand.
          </p>
          <p className="text-xs text-emerald-500/80 leading-relaxed">
            When everyone sells Diamonds, the price drops. When everyone buys Diamonds, the price rises.
            The exploit stops working because the margin disappears. The economy stays alive.
          </p>
        </div>
      </section>

      {/* ─── Dynamic Scenarios ────────────────────────────────────────────── */}
      <section className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-20 border-t border-gray-800/40">
        <div className="text-center mb-14">
          <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">Auto-Tune in Action</p>
          <h2 className="text-2xl sm:text-3xl font-bold text-white mb-4">Three real scenarios, explained</h2>
          <p className="text-gray-400 max-w-xl mx-auto text-sm leading-relaxed">
            Watch how Auto-Tune prices adapt vs. what would happen with a static shop.
          </p>
        </div>

        <div className="grid sm:grid-cols-3 gap-4 mb-10">
          <ScenarioCard
            icon={TrendingUp}
            title="Diamond demand surge"
            staticPrice="$250"
            autotunePrice="$341 ↑"
            staticEffect="No change"
            autotuneEffect="+36% in 2 weeks"
            badge="Demand signal"
            badgeColor="bg-sky-950/60 text-sky-400 border border-sky-800/50"
            explanation="A guild starts mass-buying Diamonds for armor. Static shop: still $250. Auto-Tune: rises to $341 as 70% of trades are buys. Guild pays more but feels the market responding. New miners start logging in to profit from the surge."
          />
          <ScenarioCard
            icon={TrendingDown}
            title="Iron oversupply"
            staticPrice="$5"
            autotunePrice="$2.80 ↓"
            staticEffect="No change"
            autotuneEffect="−44% over 3 weeks"
            badge="Supply signal"
            badgeColor="bg-amber-950/60 text-amber-400 border border-amber-800/50"
            explanation="A farm produces 10,000 Iron Ingots daily. Static shop: price stays $5, farm drains server wealth. Auto-Tune: drops to $2.80. High-volume sellers learn to diversify to Gold/Netherite. Server wealth is protected. Iron price recovers as supply normalises."
          />
          <ScenarioCard
            icon={Users}
            title="New player onboarding"
            staticPrice="$250"
            autotunePrice="$230 → $265"
            staticEffect="Always $250"
            autotuneEffect="Price finds its level"
            badge="Natural discovery"
            badgeColor="bg-violet-950/60 text-violet-400 border border-violet-800/50"
            explanation="New player joins, sells their first Diamond. Static shop: $250, feels arbitrary. Auto-Tune: price reflects current market activity. They learned something real about the economy just by trading. They come back."
          />
        </div>

        {/* Price history sparklines */}
        <div className="bg-gray-900/60 border border-gray-800 rounded-2xl p-6">
          <p className="text-xs text-gray-500 uppercase tracking-wider mb-4">Diamond price over 6 weeks — same server conditions</p>
          <div className="grid sm:grid-cols-2 gap-6">
            <div>
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs font-medium text-gray-400">Static Shop</span>
                <span className="text-xs text-gray-600">Always $250</span>
              </div>
              <div className="bg-gray-800/60 rounded-xl p-3 flex items-end gap-4">
                <MiniSparkline data={STATIC_DIAMOND} color="#6b7280" />
                <div className="flex flex-col gap-0.5 text-[10px] text-gray-500">
                  {STATIC_DIAMOND.map((v, i) => <span key={i}>${v}</span>)}
                </div>
              </div>
              <p className="text-xs text-gray-600 mt-2">Flat. No market signal. No engagement.</p>
            </div>
            <div>
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs font-medium text-emerald-400">Auto-Tune</span>
                <span className="text-xs text-emerald-600">Supply + demand</span>
              </div>
              <div className="bg-emerald-950/20 rounded-xl p-3 flex items-end gap-4 border border-emerald-900/50">
                <MiniSparkline data={AUTOTUNE_DIAMOND} color="#34d399" />
                <div className="flex flex-col gap-0.5 text-[10px] text-emerald-500/80">
                  {AUTOTUNE_DIAMOND.map((v, i) => <span key={i}>${v}</span>)}
                </div>
              </div>
              <p className="text-xs text-emerald-600/80 mt-2">Market tells a story. Players react and learn.</p>
            </div>
          </div>
        </div>
      </section>

      {/* ─── The Economics ─────────────────────────────────────────────────── */}
      <section className="border-t border-gray-800/40 bg-gray-900/30">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-20">
          <div className="text-center mb-14">
            <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">The Economics</p>
            <h2 className="text-2xl sm:text-3xl font-bold text-white mb-4">
              Why supply-and-demand pricing works in games
            </h2>
            <p className="text-gray-400 max-w-xl mx-auto text-sm leading-relaxed">
              These concepts are used in real financial markets. Auto-Tune brings them to Minecraft — adapted for the mechanics of a game server.
            </p>
          </div>

          <div className="grid sm:grid-cols-2 gap-4 mb-8">
            {[
              {
                icon: BarChart2,
                title: 'Price discovery',
                body: 'Markets work because prices carry information. When Diamond is $341, that\'s a signal: "more people want it than supply it." Players act on that signal — mining increases, buyers wait. The price self-corrects. Static shops throw all that away.',
                accent: 'border-sky-800/50',
                iconBg: 'bg-sky-950/60',
                iconColor: 'text-sky-400',
              },
              {
                icon: TrendingUp,
                title: 'Spread as margin',
                body: 'Auto-Tune\'s buy/sell spread (e.g., $265 buy / $255 sell) creates a guaranteed server margin on every trade. The spread widens when the market is thin and tightens when it\'s active. Admins get a reliable income stream without tax commands.',
                accent: 'border-amber-800/50',
                iconBg: 'bg-amber-950/60',
                iconColor: 'text-amber-400',
              },
              {
                icon: Shield,
                title: 'Exploit resistance',
                body: 'With a static $250 Diamond, buying low and selling high is risk-free arbitrage. With Auto-Tune\'s moving price, the margin you arbitraged today is gone tomorrow. The engine makes price manipulation a losing strategy.',
                accent: 'border-emerald-800/50',
                iconBg: 'bg-emerald-950/60',
                iconColor: 'text-emerald-400',
              },
              {
                icon: Clock,
                title: 'The 5-minute tick',
                body: 'Auto-Tune re-evaluates every item every 5 minutes based on the last 7 days of trade data. Prices shift gradually — never jarring, always responsive. Players can watch the market move and make decisions. That\'s engagement.',
                accent: 'border-violet-800/50',
                iconBg: 'bg-violet-950/60',
                iconColor: 'text-violet-400',
              },
            ].map(({ icon: Icon, title, body, accent, iconBg, iconColor }) => (
              <div key={title} className={`bg-gray-900/60 border rounded-xl p-5 ${accent}`}>
                <div className={`w-8 h-8 rounded-lg ${iconBg} flex items-center justify-center mb-3`}>
                  <Icon className={`w-4 h-4 ${iconColor}`} />
                </div>
                <h3 className="font-semibold text-white text-sm mb-1.5">{title}</h3>
                <p className="text-xs text-gray-400 leading-relaxed">{body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ─── What players earn ─────────────────────────────────────────────── */}
      <section className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-20 border-t border-gray-800/40">
        <div className="text-center mb-14">
          <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">The Impact</p>
          <h2 className="text-2xl sm:text-3xl font-bold text-white mb-4">
            The economy your players actually live in
          </h2>
          <p className="text-gray-400 max-w-xl mx-auto text-sm leading-relaxed">
            Auto-Tune doesn&apos;t just change prices — it changes what players can do. The difference shows in the numbers that matter: GDP growth, volatility, and whether the smart player is enriching themselves or the server.
          </p>
        </div>

        {/* Simulation-grounded metric cards */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-10">
          {[
            {
              label: 'Economy GDP growth',
              control: '+0%',
              autotune: '+44%',
              note: '30-day horizon, 2MM+2GB+floor config',
              good: true,
            },
            {
              label: 'Volatility (CV)',
              control: '6.1%',
              autotune: '4.4%',
              note: '30-day — lower is more stable',
              good: true,
            },
            {
              label: 'Debt / GDP ratio',
              control: '8.31×',
              autotune: '7.50×',
              note: '30-day — deleverages over time',
              good: true,
            },
          ].map(({ label, control, autotune, note, good }) => (
            <div key={label} className="bg-gray-900/60 border border-gray-800 rounded-xl p-5">
              <p className="text-xs text-gray-500 uppercase tracking-wider mb-3">{label}</p>
              <div className="flex items-end gap-4 mb-2">
                <div className="flex-1">
                  <p className="text-xs text-gray-500 mb-0.5">Static shop</p>
                  <p className="text-xl font-bold font-mono text-gray-400">{control}</p>
                </div>
                <div className="text-gray-700">→</div>
                <div className="flex-1">
                  <p className="text-xs text-emerald-500 mb-0.5">Auto-Tune</p>
                  <p className={`text-xl font-bold font-mono ${good ? 'text-emerald-400' : 'text-red-400'}`}>{autotune}</p>
                </div>
              </div>
              <p className="text-xs text-gray-600">{note}</p>
            </div>
          ))}
        </div>

        {/* Player type qualitative table */}
        <div className="bg-gray-900/60 border border-gray-800 rounded-2xl overflow-hidden mb-8">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-800 bg-gray-900/80">
                  <th className="py-3.5 px-5 text-left text-xs font-medium text-gray-400 uppercase tracking-wider">Player Type</th>
                  <th className="py-3.5 px-4 text-center text-xs font-medium text-gray-400 uppercase tracking-wider">Static Shop</th>
                  <th className="py-3.5 px-4 text-center text-xs font-medium text-emerald-400 uppercase tracking-wider">Auto-Tune</th>
                  <th className="py-3.5 px-4 text-center text-xs font-medium text-gray-400 uppercase tracking-wider">Why</th>
                </tr>
              </thead>
              <tbody>
                {[
                  {
                    type: 'Farmer (high volume)',
                    static: 'Earns fixed price, floods market',
                    autotune: 'Sells into demand spikes, earns more',
                    reason: 'Price rises when demand outpaces supply — farmers time high-volume sells for peak demand',
                    color: 'text-emerald-400',
                  },
                  {
                    type: 'Smart trader',
                    static: 'Exploits static spread for easy profit',
                    autotune: 'Reads price signals, earns from real moves',
                    reason: 'Prices carry information — traders who understand supply/demand dynamics outperform those who don\'t',
                    color: 'text-emerald-400',
                  },
                  {
                    type: 'Static arbitrageur',
                    static: 'Near-risk-free profit, drains server',
                    autotune: 'Margin disappears as prices adapt',
                    reason: 'The core exploit — buy low, sell high to the same shop — stops working when the shop\'s price follows the trade',
                    color: 'text-red-400',
                  },
                  {
                    type: 'New player',
                    static: 'Arbitrary price, no market signal',
                    autotune: 'Price reflects current activity — learns by trading',
                    reason: 'Selling their first Diamond teaches them something real about supply and demand',
                    color: 'text-emerald-400',
                  },
                  {
                    type: 'Guild (combined)',
                    static: 'Bulk buying at fixed prices strains treasury',
                    autotune: 'Guild-wide buying signals economy — treasury adjusts',
                    reason: 'Market activity creates price signals that help guilds time large purchases',
                    color: 'text-emerald-400',
                  },
                ].map(({ type, static: s, autotune: a, reason, color }, i) => (
                  <tr key={type} className={`border-b border-gray-800/50 ${i % 2 === 0 ? 'bg-gray-900/30' : ''}`}>
                    <td className="py-3 px-5 text-gray-300 text-sm font-medium">{type}</td>
                    <td className="py-3 px-4 text-center text-gray-500 text-xs">{s}</td>
                    <td className={`py-3 px-4 text-center text-xs font-medium ${color}`}>{a}</td>
                    <td className="py-3 px-4 text-center text-gray-600 text-xs hidden sm:table-cell">{reason}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="px-5 py-3 bg-gray-900/40 border-t border-gray-800/50">
            <p className="text-xs text-gray-500">
              * Per-player earnings are representative examples. Economy metrics (GDP growth, volatility, D/G) are from Auto-Tune&apos;s market simulation lab: 2MM+2GB+floor config, 30-day horizon, 5-seed average. Standard archetype (3Cas+3Far+2Tra) — the recommended config for most servers.
            </p>
          </div>
        </div>

        <div className="bg-amber-950/20 border border-amber-800/30 rounded-xl p-4 text-center">
          <p className="text-sm text-amber-300 font-medium mb-1">The arbitrageur doesn&apos;t disappear — they adapt.</p>
          <p className="text-xs text-amber-500/80 leading-relaxed">
            Instead of free money from a static spread, the smart player starts watching price trends, buying before surges, and selling during gluts. They become a market participant instead of a parasite on the economy. That&apos;s the kind of player you want on your server.
          </p>
        </div>
      </section>

      {/* ─── Call to action ─────────────────────────────────────────────────── */}
      <section className="border-t border-gray-800/50 bg-gradient-to-b from-gray-900/50 to-gray-950">
        <div className="max-w-2xl mx-auto px-4 sm:px-6 lg:px-8 py-20 text-center">
          <h2 className="text-2xl sm:text-3xl font-bold text-white mb-4">
            Ready to run a real economy?
          </h2>
          <p className="text-gray-400 text-sm leading-relaxed mb-8 max-w-lg mx-auto">
            Auto-Tune installs in under 5 minutes. No database setup, no external services, no
            complicated configuration. Works out of the box with sensible defaults — then adapts to
            your server as players trade.
          </p>
          <div className="flex flex-col sm:flex-row items-center justify-center gap-3">
            <Link
              href="/install"
              className="inline-flex items-center gap-2 px-6 py-3 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded-xl transition-colors shadow-lg shadow-emerald-600/20"
            >
              Install Auto-Tune <ArrowRight className="w-4 h-4" />
            </Link>
            <Link
              href="/simulator"
              className="inline-flex items-center gap-2 px-6 py-3 border border-gray-700 hover:border-gray-600 text-gray-300 font-medium rounded-xl transition-colors"
            >
              Run a simulation
            </Link>
          </div>
          <p className="text-xs text-gray-600 mt-6">
            Free and open source. Paper 1.21.4+, Java 21.
          </p>
        </div>
      </section>

    </div>
  );
}
