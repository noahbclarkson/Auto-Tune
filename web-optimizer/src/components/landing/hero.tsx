import Link from 'next/link';
import { ArrowRight, Play, TrendingUp, TrendingDown, Download, Github } from 'lucide-react';
import { QuickSimulator } from './quick-simulator';

/* ------------------------------------------------------------------ */
/* Fake sparkline data — upward-trending with realistic noise          */
/* ------------------------------------------------------------------ */
const SPARK_LINE =
  'M0,32 L12,34 L24,29 L36,31 L48,24 L60,27 L72,21 L84,25 L96,18 L108,15 L120,19 L132,12 L144,14 L156,7 L168,4 L180,0';
const SPARK_FILL =
  'M0,32 L12,34 L24,29 L36,31 L48,24 L60,27 L72,21 L84,25 L96,18 L108,15 L120,19 L132,12 L144,14 L156,7 L168,4 L180,0 L180,44 L0,44 Z';

const MOCK_ITEMS = [
  { name: 'Diamond',       base: 500,   buy: 547.50, sell: 459.00, change: +4.2,  up: true  },
  { name: 'Iron Ingot',    base: 50,    buy: 55.30,  sell: 46.40,  change: -1.3,  up: false },
  { name: 'Blaze Rod',     base: 75,    buy: 80.10,  sell: 69.50,  change: +2.1,  up: true  },
  { name: 'Netherite Ingot', base: 2500, buy: 2637.50, sell: 2362.50, change: +0.8, up: true },
];

/* Ticker strip items (duplicated for seamless loop) */
const TICKER = [...MOCK_ITEMS, ...MOCK_ITEMS];

function MarketMockPanel() {
  return (
    <div className="relative rounded-2xl border border-gray-700/60 bg-gray-900/70 backdrop-blur-sm overflow-hidden glow-emerald">
      {/* Header bar */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-800">
        <div className="flex items-center gap-2">
          <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse-slow" />
          <span className="text-xs font-medium text-gray-300 tracking-wide uppercase">Live Market</span>
        </div>
        <span className="text-xs text-gray-500 font-mono">Paper 1.21.4</span>
      </div>

      {/* Sparkline hero */}
      <div className="px-5 pt-4 pb-2">
        <div className="flex items-start justify-between mb-3">
          <div>
            <p className="text-xs text-gray-500 uppercase tracking-wider mb-0.5">Diamond ◆</p>
            <p className="text-2xl font-bold text-white font-mono">$500.00</p>
          </div>
          <div className="text-right">
            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-emerald-950/60 border border-emerald-800/60 text-emerald-400 text-xs font-mono">
              ▲ +4.2%
            </span>
          </div>
        </div>
        {/* Sparkline */}
        <svg viewBox="0 0 180 44" className="w-full h-12" preserveAspectRatio="none">
          <defs>
            <linearGradient id="sparkFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#10b981" stopOpacity="0.25" />
              <stop offset="100%" stopColor="#10b981" stopOpacity="0" />
            </linearGradient>
          </defs>
          <path d={SPARK_FILL} fill="url(#sparkFill)" />
          <path d={SPARK_LINE} fill="none" stroke="#10b981" strokeWidth="1.5" />
        </svg>
      </div>

      {/* Bid / Ask */}
      <div className="grid grid-cols-2 gap-px bg-gray-800/40 border-t border-gray-800">
        <div className="px-4 py-3 bg-gray-900/50">
          <p className="text-xs text-gray-500 mb-1">BUY (ask)</p>
          <p className="text-lg font-bold text-emerald-400 font-mono">$547.50</p>
          <p className="text-xs text-gray-600 font-mono">+9.5%</p>
        </div>
        <div className="px-4 py-3 bg-gray-900/50">
          <p className="text-xs text-gray-500 mb-1">SELL (bid)</p>
          <p className="text-lg font-bold text-rose-400 font-mono">$459.00</p>
          <p className="text-xs text-gray-600 font-mono">-8.2%</p>
        </div>
      </div>

      {/* Market table */}
      <div className="border-t border-gray-800">
        {MOCK_ITEMS.map((item) => (
          <div key={item.name} className="flex items-center justify-between px-4 py-2.5 border-b border-gray-800/50 last:border-0 hover:bg-gray-800/30 transition-colors">
            <span className="text-sm text-gray-300 w-32 truncate">{item.name}</span>
            <div className="flex items-center gap-4 text-xs font-mono">
              <span className="text-emerald-400">${item.buy.toFixed(2)}</span>
              <span className="text-rose-400">${item.sell.toFixed(2)}</span>
              <span className={item.up ? 'text-emerald-400 w-14 text-right' : 'text-rose-400 w-14 text-right'}>
                {item.up ? '▲' : '▼'} {Math.abs(item.change)}%
              </span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export function Hero() {
  return (
    <section className="relative overflow-hidden py-16 sm:py-24 bg-grid">
      {/* Gradient orbs */}
      <div className="absolute -top-32 -left-32 w-96 h-96 bg-emerald-600/8 rounded-full blur-3xl pointer-events-none" />
      <div className="absolute top-1/2 -right-48 w-[500px] h-[500px] bg-emerald-900/12 rounded-full blur-3xl pointer-events-none" />

      <div className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid lg:grid-cols-2 gap-12 lg:gap-16 items-center">

          {/* Left — copy */}
          <div className="animate-slide-up">
            <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full border border-emerald-800/60 bg-emerald-950/40 text-emerald-400 text-xs font-medium mb-6">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse-slow" />
              Paper 1.21.4 · Java 21 · Open Source
            </div>

            <h1 className="text-4xl sm:text-5xl font-bold text-white leading-tight tracking-tight mb-5">
              Adaptive Market<br />
              Pricing for<br />
              <span className="text-emerald-400">Minecraft</span>
            </h1>

            <p className="text-gray-400 text-lg leading-relaxed mb-8 max-w-lg">
              A multi-factor pricing engine that adjusts buy/sell spreads in real-time
              based on player count, trade volume, and supply–demand balance.
              No more static prices.
            </p>

            <div className="flex flex-wrap gap-3 mb-10">
              <Link
                href="https://github.com/noahbclarkson/Auto-Tune/releases"
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-2 px-5 py-2.5 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded-lg transition-colors shadow-lg shadow-emerald-600/20 text-sm"
              >
                <Download className="w-4 h-4" />
                Download Auto-Tune
              </Link>
              <Link
                href="/simulator"
                className="inline-flex items-center gap-2 px-5 py-2.5 bg-gray-800 hover:bg-gray-750 text-gray-200 font-medium rounded-lg transition-colors border border-gray-700 text-sm"
              >
                <Play className="w-4 h-4" />
                Try the Simulator
              </Link>
              <Link
                href="/how-it-works"
                className="inline-flex items-center gap-2 px-5 py-2.5 bg-gray-800 hover:bg-gray-750 text-gray-200 font-medium rounded-lg transition-colors border border-gray-700 text-sm"
              >
                <ArrowRight className="w-4 h-4" />
                How It Works
              </Link>
              <Link
                href="https://github.com/noahbclarkson/Auto-Tune"
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-2 px-5 py-2.5 text-gray-500 hover:text-gray-300 font-medium text-sm"
              >
                <Github className="w-4 h-4" />
                GitHub
              </Link>
            </div>

            {/* Stats strip */}
            <div className="flex flex-wrap gap-6 text-sm">
              {[
                { label: 'Market Tick', value: '5 min' },
                { label: 'Spread Factors', value: '5 layers' },
                { label: 'Price Floor', value: '$0.01' },
                { label: 'SQLite / MariaDB', value: 'supported' },
              ].map(({ label, value }) => (
                <div key={label}>
                  <p className="text-gray-500 text-xs uppercase tracking-wider mb-0.5">{label}</p>
                  <p className="text-white font-mono font-semibold">{value}</p>
                </div>
              ))}
            </div>

            {/* Inline quick simulator */}
            <div className="mt-8">
              <QuickSimulator embedded />
            </div>
          </div>

          {/* Right — mock market panel */}
          <div className="lg:pl-4 animate-fade-in">
            <MarketMockPanel />
          </div>
        </div>
      </div>

      {/* Scrolling ticker strip */}
      <div className="mt-14 border-y border-gray-800/50 bg-gray-900/30 py-2 overflow-hidden">
        <div className="flex animate-ticker whitespace-nowrap">
          {TICKER.map((item, i) => (
            <span key={i} className="inline-flex items-center gap-3 px-6 text-xs font-mono text-gray-400 border-r border-gray-800">
              <span className="text-gray-500">{item.name}</span>
              <span className="text-emerald-400">${item.buy.toFixed(2)}</span>
              <span className="text-rose-400">${item.sell.toFixed(2)}</span>
              <span className={item.up ? 'text-emerald-400' : 'text-rose-400'}>
                {item.up ? <TrendingUp className="w-3 h-3 inline" /> : <TrendingDown className="w-3 h-3 inline" />}
                {' '}{item.up ? '+' : ''}{item.change}%
              </span>
            </span>
          ))}
        </div>
      </div>
    </section>
  );
}
