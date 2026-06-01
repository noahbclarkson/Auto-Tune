'use client';
import { useState } from 'react';
import { Metadata } from 'next';
import Link from 'next/link';
import { Gavel, TrendingUp, TrendingDown, Shield, Clock, ArrowRight, CheckCircle, BarChart2, AlertTriangle, Eye, Users } from 'lucide-react';
import { DepthChart } from '@/components/auction/depth-chart';

// ─── Mock auction data ────────────────────────────────────────────────────────

const MOCK_ORDERS = [
  { side: 'BUY', mat: 'DIAMOND', price: 305, qty: 12, filled: 0, status: 'active' },
  { side: 'SELL', mat: 'DIAMOND', price: 318, qty: 5, filled: 0, status: 'active' },
  { side: 'BUY', mat: 'DIAMOND', price: 298, qty: 22, filled: 0, status: 'active' },
  { side: 'SELL', mat: 'DIAMOND', price: 325, qty: 8, filled: 0, status: 'active' },
  { side: 'BUY', mat: 'EMERALD', price: 140, qty: 50, filled: 30, status: 'partial' },
  { side: 'SELL', mat: 'EMERALD', price: 155, qty: 20, filled: 0, status: 'active' },
  { side: 'BUY', mat: 'IRON', price: 10, qty: 200, filled: 0, status: 'active' },
  { side: 'SELL', mat: 'GOLD', price: 21, qty: 64, filled: 12, status: 'partial' },
  { side: 'BUY', mat: 'GOLD', price: 19, qty: 100, filled: 0, status: 'active' },
  { side: 'SELL', mat: 'NETHERITE', price: 2200, qty: 3, filled: 0, status: 'active' },
];

const MOCK_BIDS = [
  { price: 305, qty: 12 },
  { price: 298, qty: 34 },
  { price: 290, qty: 52 },
  { price: 285, qty: 68 },
  { price: 280, qty: 79 },
  { price: 270, qty: 88 },
];

const MOCK_ASKS = [
  { price: 318, qty: 8 },
  { price: 325, qty: 18 },
  { price: 330, qty: 30 },
  { price: 340, qty: 46 },
  { price: 355, qty: 60 },
  { price: 370, qty: 75 },
];

const MOCK_FILLS = [
  { mat: 'DIAMOND', side: 'BUY', price: 305, qty: 8, time: '2m ago' },
  { mat: 'EMERALD', side: 'SELL', price: 140, qty: 30, time: '5m ago' },
  { mat: 'GOLD', side: 'BUY', price: 19, qty: 20, time: '12m ago' },
  { mat: 'IRON', side: 'BUY', price: 10, qty: 64, time: '18m ago' },
  { mat: 'DIAMOND', side: 'SELL', price: 318, qty: 3, time: '31m ago' },
  { mat: 'COPPER', side: 'BUY', price: 4, qty: 200, time: '1h ago' },
  { mat: 'LAPIS', side: 'SELL', price: 10, qty: 50, time: '2h ago' },
  { mat: 'COAL', side: 'BUY', price: 3, qty: 300, time: '3h ago' },
];

const COMMANDS = [
  { cmd: '/auction browse', desc: 'View live buy/sell order book with depth' },
  { cmd: '/auction sell <price> <qty>', desc: 'Place a sell limit order (your item in hand)' },
  { cmd: '/auction buy <material> <price> <qty>', desc: 'Place a buy order; fills automatically when it crosses an ask' },
  { cmd: '/auction my', desc: 'View your active orders and fill history' },
  { cmd: '/auction watch <id>', desc: 'Get in-game alert when an order fills' },
  { cmd: '/auction cancel <id>', desc: 'Cancel one of your active orders' },
  { cmd: '/auction info <id>', desc: 'Inspect any order in detail' },
  { cmd: '/auction reclaim', desc: 'Reclaim expired items and pending deliveries' },
];

const INTEGRITY_ITEMS = [
  { label: 'Cancellation churn monitoring', desc: 'Admins see abnormal cancel rates that may indicate spoofing or wash trading.', icon: AlertTriangle, color: 'text-amber-400' },
  { label: 'Self-trade detection', desc: 'Self-trade fills are flagged — same player on both sides of a trade is not real liquidity.', icon: Users, color: 'text-rose-400' },
  { label: 'Thin book warnings', desc: 'Materials with low open interest are flagged so admins know when a large order could move the market.', icon: BarChart2, color: 'text-amber-400' },
  { label: 'Large sell wall alerts', desc: 'Unusual sell walls are surfaced — a whale dumping a material can signal intentional manipulation.', icon: AlertTriangle, color: 'text-rose-400' },
];

// ─── Order book component ───────────────────────────────────────────────────

function OrderBook() {
  const [filter, setFilter] = useState('ALL');
  const materials = ['ALL', 'DIAMOND', 'EMERALD', 'IRON', 'GOLD', 'NETHERITE'];
  const filtered = filter === 'ALL' ? MOCK_ORDERS : MOCK_ORDERS.filter(o => o.mat === filter);

  return (
    <div>
      <div className="flex gap-2 mb-4 flex-wrap">
        {materials.map(m => (
          <button
            key={m}
            onClick={() => setFilter(m)}
            className={`px-3 py-1 rounded text-xs font-mono transition-colors ${
              filter === m
                ? 'bg-emerald-600 text-white'
                : 'bg-gray-800 text-gray-400 hover:bg-gray-700'
            }`}
          >
            {m}
          </button>
        ))}
      </div>
      <div className="rounded-xl border border-gray-700 overflow-hidden bg-gray-900">
        <div className="flex items-center gap-1.5 px-4 py-2.5 bg-gray-800 border-b border-gray-700">
          <span className="w-3 h-3 rounded-full bg-red-500/70" />
          <span className="w-3 h-3 rounded-full bg-amber-500/70" />
          <span className="w-3 h-3 rounded-full bg-green-500/70" />
          <span className="ml-3 text-xs text-gray-400 font-mono">/auction — Order Book Demo</span>
          <span className="ml-auto flex items-center gap-1.5 text-[10px] text-amber-400">
            <span className="w-1.5 h-1.5 rounded-full bg-amber-400" />
            Demo
          </span>
        </div>
        <div className="p-3 space-y-1">
          <div className="flex items-center justify-between text-[9px] text-gray-500 uppercase tracking-wider mb-1 px-1">
            <span>Side</span>
            <span>Material</span>
            <span>Bid</span>
            <span>Ask</span>
            <span>Qty</span>
            <span>Filled</span>
          </div>
          {filtered.map((o, i) => (
            <div key={i} className="flex items-center justify-between px-2 py-1.5 rounded bg-gray-800/40 border border-gray-700/30 text-[10px] font-mono">
              <span className={`font-bold ${o.side === 'BUY' ? 'text-emerald-400' : 'text-rose-400'}`}>
                {o.side === 'BUY' ? '▲ BUY' : '▼ SELL'}
              </span>
              <span className="text-gray-300">{o.mat}</span>
              <span className="text-emerald-400">${o.price}</span>
              <span className="text-rose-300">${o.price + (o.side === 'BUY' ? 13 : -13)}</span>
              <span className="text-gray-400">{o.qty}</span>
              {o.filled > 0 ? (
                <span className="text-emerald-400">✓{o.filled}/{o.qty}</span>
              ) : (
                <span className="text-gray-600">—</span>
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

// ─── Recent fills component ──────────────────────────────────────────────────

function RecentFills() {
  return (
    <div className="rounded-xl border border-gray-700 overflow-hidden bg-gray-900">
      <div className="px-4 py-3 bg-gray-800 border-b border-gray-700">
        <p className="text-xs text-gray-400 font-medium">Recent Fills — Demo</p>
      </div>
      <div className="p-3 space-y-1">
        {MOCK_FILLS.map((f, i) => (
          <div key={i} className="flex items-center justify-between px-2 py-1.5 rounded bg-gray-800/30 border border-gray-800/40 text-[10px] font-mono">
            <div className="flex items-center gap-2">
              <span className={`font-bold ${f.side === 'BUY' ? 'text-emerald-400' : 'text-rose-400'}`}>
                {f.side === 'BUY' ? '▲' : '▼'}
              </span>
              <span className="text-gray-300">{f.mat}</span>
            </div>
            <span className={f.side === 'BUY' ? 'text-emerald-400' : 'text-rose-300'}>${f.price}</span>
            <span className="text-gray-400">×{f.qty}</span>
            <span className="text-gray-500">{f.time}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

// ─── Main Page ───────────────────────────────────────────────────────────────

export default function AuctionPage() {
  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      {/* Hero */}
      <section className="relative overflow-hidden border-b border-gray-800/50">
        <div className="absolute inset-0 bg-gradient-to-b from-rose-950/20 via-gray-950 to-gray-950 pointer-events-none" />
        <div className="relative max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-20 text-center">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-rose-950/60 border border-rose-800/50 text-rose-400 text-xs font-medium mb-8">
            <Gavel className="w-3 h-3" />
            P2P Auction House
          </div>
          <h1 className="text-4xl sm:text-5xl font-bold text-white mb-6 leading-tight">
            The market that never stops.
            <br />
            <span className="text-rose-400">Built into Auto-Tune.</span>
          </h1>
          <p className="text-lg text-gray-400 max-w-2xl mx-auto mb-10 leading-relaxed">
            A real order-book auction house — limit orders, live bid/ask ladder, fill notifications, and a depth chart. Every install comes with it. No separate plugin needed.
          </p>
          <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
            <Link
              href="/install"
              className="inline-flex items-center gap-2 px-6 py-3 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded-xl transition-colors shadow-lg shadow-emerald-600/20"
            >
              Install Auto-Tune <ArrowRight className="w-4 h-4" />
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

      {/* Order Book Demo */}
      <section className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-16 border-t border-gray-800/40">
        <div className="text-center mb-10">
          <p className="text-xs text-amber-400 uppercase tracking-widest font-medium mb-2">Order Book Demo</p>
          <h2 className="text-2xl sm:text-3xl font-bold text-white mb-4">The order book, in action</h2>
          <p className="text-gray-400 max-w-xl mx-auto text-sm leading-relaxed">
            Players place limit orders. When a buy order meets a sell order, the trade executes instantly. No admin mediation, no waiting.
          </p>
        </div>
        <div className="grid lg:grid-cols-2 gap-6">
          <OrderBook />
          <RecentFills />
        </div>
      </section>

      {/* Depth Chart */}
      <section className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-16 border-t border-gray-800/40 bg-gray-900/30">
        <div className="text-center mb-8">
          <p className="text-xs text-sky-400 uppercase tracking-widest font-medium mb-2">Market Depth</p>
          <h2 className="text-xl sm:text-2xl font-bold text-white mb-3">See where the market is thin</h2>
          <p className="text-gray-400 max-w-lg mx-auto text-sm leading-relaxed">
            The depth chart shows cumulative bid/ask quantities at each price level. Green area = buy walls; Rose area = sell walls. Where the two areas meet is the natural equilibrium.
          </p>
        </div>
        <div className="max-w-2xl mx-auto">
          <DepthChart bids={MOCK_BIDS} asks={MOCK_ASKS} />
        </div>
        <div className="mt-6 max-w-2xl mx-auto grid sm:grid-cols-3 gap-3 text-xs text-gray-500">
          <div className="bg-gray-900/60 border border-gray-800 rounded-xl p-3">
            <p className="text-emerald-400 font-medium mb-1">Bid walls</p>
            <p>Green area shows how much buying support exists at each price. Larger green = stronger buy support.</p>
          </div>
          <div className="bg-gray-900/60 border border-gray-800 rounded-xl p-3">
            <p className="text-rose-400 font-medium mb-1">Ask walls</p>
            <p>Rose area shows sell pressure. A tall rose spike means a large seller could move the price down.</p>
          </div>
          <div className="bg-gray-900/60 border border-gray-800 rounded-xl p-3">
            <p className="text-sky-400 font-medium mb-1">Thin books</p>
            <p>Small total area = thin book. Admins see a warning when a material has very low open interest.</p>
          </div>
        </div>
      </section>

      {/* How it works */}
      <section className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-16 border-t border-gray-800/40 bg-gray-900/30">
        <div className="text-center mb-10">
          <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">How It Works</p>
          <h2 className="text-2xl sm:text-3xl font-bold text-white mb-4">Three things that make it different</h2>
        </div>
        <div className="grid sm:grid-cols-3 gap-6">
          {[
            {
              icon: TrendingUp,
              title: 'You set the price',
              body: 'With /shop, the server sets the price. With /auction, you set it. Post a sell at $310 and wait. Post a buy at $295 and wait. The market decides when you get filled — and you get a better price than /shop for your patience.',
              color: 'emerald',
            },
            {
              icon: Eye,
              title: 'You see the depth',
              body: 'The order book shows every bid and ask. Players who read it know where the market is thin, where there is support, and where a large order could move the price. That information is available to everyone — not just insiders.',
              color: 'sky',
            },
            {
              icon: Clock,
              title: '72-hour orders',
              body: 'Orders expire after 72 hours by default. Funds and items are returned automatically. Players can set it and come back later. No stale orders sitting in the book indefinitely.',
              color: 'amber',
            },
          ].map(({ icon: Icon, title, body, color }) => (
            <div key={title} className={`bg-gray-900/60 border border-${color}-800/50 rounded-xl p-5`}>
              <div className={`w-9 h-9 rounded-lg bg-${color}-950/60 border border-${color}-800/50 flex items-center justify-center mb-3`}>
                <Icon className={`w-4 h-4 text-${color}-400`} />
              </div>
              <h3 className="font-semibold text-white text-sm mb-1.5">{title}</h3>
              <p className="text-xs text-gray-400 leading-relaxed">{body}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Auction Commands */}
      <section className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-16 border-t border-gray-800/40">
        <div className="text-center mb-10">
          <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">Player Commands</p>
          <h2 className="text-2xl sm:text-3xl font-bold text-white mb-4">Everything players can do</h2>
        </div>
        <div className="grid sm:grid-cols-2 gap-3">
          {COMMANDS.map(({ cmd, desc }) => (
            <div key={cmd} className="bg-gray-900/60 border border-gray-800 rounded-xl p-4 flex items-start gap-3">
              <code className="text-sm font-mono text-emerald-400 shrink-0">{cmd}</code>
              <span className="text-xs text-gray-400 leading-relaxed">{desc}</span>
            </div>
          ))}
        </div>
        <div className="mt-8 bg-emerald-950/20 border border-emerald-800/30 rounded-xl p-5">
          <h3 className="text-sm font-semibold text-emerald-400 mb-2 flex items-center gap-2">
            <CheckCircle className="w-4 h-4" />
            Native Watch Notifications
          </h3>
          <p className="text-xs text-gray-400 leading-relaxed">
            Players run <code className="text-emerald-400 font-mono">/auction watch &lt;order-id&gt;</code> to get an in-game alert when that order fills — even while offline. Watch state persists in the database. No browser required.
          </p>
        </div>
      </section>

      {/* Admin Integrity Monitoring */}
      <section className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-16 border-t border-gray-800/40 bg-gray-900/30">
        <div className="text-center mb-10">
          <p className="text-xs text-amber-400 uppercase tracking-widest font-medium mb-2">For Server Admins</p>
          <h2 className="text-2xl sm:text-3xl font-bold text-white mb-4">Integrity monitoring built in</h2>
          <p className="text-gray-400 max-w-xl mx-auto text-sm leading-relaxed">
            The auction has a full admin audit surface. Spot manipulation before it becomes a problem.
          </p>
        </div>
        <div className="grid sm:grid-cols-2 gap-4 mb-8">
          {INTEGRITY_ITEMS.map(({ label, desc, icon: Icon, color }) => (
            <div key={label} className="bg-gray-900/60 border border-gray-800 rounded-xl p-4 flex items-start gap-3">
              <div className={`w-8 h-8 rounded-lg bg-gray-800 border border-gray-700 flex items-center justify-center shrink-0 mt-0.5`}>
                <Icon className={`w-4 h-4 ${color}`} />
              </div>
              <div>
                <p className="text-sm font-medium text-white mb-0.5">{label}</p>
                <p className="text-xs text-gray-500 leading-relaxed">{desc}</p>
              </div>
            </div>
          ))}
        </div>
        <div className="bg-gray-800/40 border border-gray-700 rounded-xl p-4">
          <p className="text-xs text-gray-400 leading-relaxed">
            Access via <code className="text-emerald-400 font-mono">/at admin auction</code> or the bundled web dashboard at <code className="text-sky-400 font-mono">/admin</code>. Includes 7-day cancellation churn, fill/cancel/expire rates, self-trade fills, thin book materials, and large sell-wall warnings.
          </p>
        </div>
      </section>

      {/* Integrates with shop prices */}
      <section className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-16 border-t border-gray-800/40">
        <div className="bg-gray-900/60 border border-gray-800 rounded-2xl p-6 text-center">
          <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-3">How it connects to the engine</p>
          <h2 className="text-xl font-bold text-white mb-3">Auction fills sharpen every price</h2>
          <p className="text-sm text-gray-400 max-w-lg mx-auto leading-relaxed">
            Every auction fill is recorded as a trade in the market history. Those fills flow into the price engine — so the /shop spread tightens for materials with active auction markets, and the auction book always reflects the live /shop price.
          </p>
          <div className="flex justify-center mt-5">
            <Link
              href="/how-it-works"
              className="inline-flex items-center gap-2 text-sm text-emerald-400 hover:text-emerald-300 transition-colors"
            >
              See how the price engine works <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-16 border-t border-gray-800/40">
        <div className="text-center">
          <Gavel className="w-10 h-10 text-rose-400 mx-auto mb-4" />
          <h2 className="text-2xl font-bold text-white mb-3">Ready to run a real market?</h2>
          <p className="text-gray-400 text-sm mb-8 max-w-lg mx-auto">
            Auto-Tune installs in minutes. Every server gets the auction house, the web dashboard, and the price engine — together.
          </p>
          <Link
            href="/install"
            className="inline-flex items-center gap-2 px-6 py-3 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded-xl transition-colors shadow-lg shadow-emerald-600/20"
          >
            Install in 5 minutes <ArrowRight className="w-4 h-4" />
          </Link>
        </div>
      </section>
    </div>
  );
}
