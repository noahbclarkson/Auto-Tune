'use client';

import { useState } from 'react';
import Link from 'next/link';
import { Header } from '@/components/layout/header';
import { Footer } from '@/components/layout/footer';
import {
  Shield,
  TrendingUp,
  TrendingDown,
  Activity,
  Zap,
  Clock,
  Database,
  Settings,
  RefreshCw,
  List,
  ArrowRight,
  ChevronDown,
  ChevronUp,
  BookOpen,
  BarChart2,
  AlertTriangle,
  CheckCircle2,
  Search,
  DollarSign,
  Users,
  Calendar,
  Server,
  Bell,
  Layers,
} from 'lucide-react';

export default function AdminPage() {
  const [searchQuery, setSearchQuery] = useState('');
  const [openCategories, setOpenCategories] = useState<Set<string>>(new Set(['health', 'prices', 'events']));

  const toggleCategory = (id: string) => {
    setOpenCategories(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const commandCategories = [
    {
      id: 'health',
      label: 'Economy Health',
      icon: Activity,
      color: 'text-emerald-400',
      bgColor: 'bg-emerald-500/10',
      borderColor: 'border-emerald-500/20',
      description: 'Monitor your server\'s economic health in real time.',
      commands: [
        {
          cmd: '/at admin health',
          desc: 'Live economy snapshot — GDP, debt, D/G ratio, buy ratio, active players, active loans.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin stats',
          desc: 'Total GDP, total debt, D/G ratio, market velocity, top volatile items, top undersold items.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin info',
          desc: 'Plugin version, config path, database size, web server status, price reporter status.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin trend [days]',
          desc: 'Price trend chart for all items over N days (default 7). Shows direction and velocity.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin history [limit]',
          desc: 'Historical economy snapshots — GDP, debt, D/G, volume, loan count per snapshot. Color-coded D/G. Defaults to last 10.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin advice',
          desc: 'Automated economy诊断 + actionable recommendations. Tells you what to fix and why.',
          permission: 'autotune.admin',
        },
      ],
    },
    {
      id: 'prices',
      label: 'Price Management',
      icon: DollarSign,
      color: 'text-amber-400',
      bgColor: 'bg-amber-500/10',
      borderColor: 'border-amber-500/20',
      description: 'Inspect, override, and reset item prices.',
      commands: [
        {
          cmd: '/at admin price set <material> <price> [hours]',
          desc: 'Override an item\'s price for a set duration (default: indefinitely). Useful for events or corrections.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin price list',
          desc: 'List all currently active price overrides with remaining time.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin price remove <material>',
          desc: 'Remove a price override — item returns to engine-discovered price immediately.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin prices export [filename]',
          desc: 'Export all current buy/sell prices to a file for backup or inspection.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin prices import <filename>',
          desc: 'Bulk-import prices from a previously exported file.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin prices reset <material>',
          desc: 'Reset a single item to its shops.yml base price, clear history, evict from engine cache.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin prices reset all',
          desc: 'Reset every item to base prices. Clears all history and cache. Use with caution.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin reseed-prices',
          desc: 'Re-initialize all prices from shops.yml without clearing history.',
          permission: 'autotune.admin',
        },
      ],
    },
    {
      id: 'items',
      label: 'Item Overrides',
      icon: Layers,
      color: 'text-cyan-400',
      bgColor: 'bg-cyan-500/10',
      borderColor: 'border-cyan-500/20',
      description: 'Per-item price limits and spread controls.',
      commands: [
        {
          cmd: '/at admin item spread <material> <value>',
          desc: 'Set a permanent spread override for a specific item (e.g., 0.05 = 5% half-spread).',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin item maxchange <material> <value>',
          desc: 'Set the max price change per tick for an item (e.g., 0.02 = 2% per 5-minute tick).',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin item floor <material> <value>',
          desc: 'Set a price floor — item cannot trade below this value. Sellers are protected.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin item ceiling <material> <value>',
          desc: 'Set a price ceiling — item cannot trade above this value.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin item tier <material> <tier>',
          desc: 'Assign a pricing tier (NORMAL, LUXURY, ESSENTIAL, BULK) to an item. Affects spread sensitivity.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin item info <material>',
          desc: 'Full item diagnostic: base price, current price, spread, trend, floor, ceiling, override status.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin item freeze <material>',
          desc: 'Freeze price discovery for an item — spread still computes, price stays locked. WARNING: wide spreads on frozen items.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin item unfreeze <material>',
          desc: 'Unfreeze an item — price discovery resumes from current price.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin item reset <material>',
          desc: 'Clear all per-item overrides (spread, maxchange, floor, ceiling, tier, freeze) and return to config defaults.',
          permission: 'autotune.admin',
        },
      ],
    },
    {
      id: 'market',
      label: 'Market Control',
      icon: Activity,
      color: 'text-violet-400',
      bgColor: 'bg-violet-500/10',
      borderColor: 'border-violet-500/20',
      description: 'Pause and resume the market engine.',
      commands: [
        {
          cmd: '/at admin market',
          desc: 'Show current market state (running/frozen) and freeze toggle.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin market freeze',
          desc: 'Emergency freeze — stops all price updates. Loans still work. Use for recovery scenarios.',
          permission: 'autotune.admin',
        },
      ],
    },
    {
      id: 'events',
      label: 'Market Events',
      icon: Zap,
      color: 'text-rose-400',
      bgColor: 'bg-rose-500/10',
      borderColor: 'border-rose-500/20',
      description: 'Trigger and schedule in-game economic events.',
      commands: [
        {
          cmd: '/at event list',
          desc: 'List all scheduled and active events with type, multiplier, time remaining.',
          permission: 'autotune.event',
        },
        {
          cmd: '/at event create <type> <materials> <multiplier> <duration>',
          desc: 'Create a new economic event. Types: DEMAND_SURGE, SUPPLY_GLUT, INFLATION_BOOST, GOLD_RUSH.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at event cancel <id>',
          desc: 'Cancel a scheduled or active event by ID.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at event templates',
          desc: 'List configured event templates from config.yml.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at event invoke <name>',
          desc: 'Trigger a named event template immediately (no schedule).',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at event schedule <type> <materials> <multiplier> <duration> <start-minutes>',
          desc: 'Schedule a future event — fires automatically at the specified time.',
          permission: 'autotune.admin',
        },
      ],
    },
    {
      id: 'recovery',
      label: 'Economy Recovery',
      icon: Shield,
      color: 'text-red-400',
      bgColor: 'bg-red-500/10',
      borderColor: 'border-red-500/20',
      description: 'Emergency controls when the economy is in distress.',
      commands: [
        {
          cmd: '/at admin recovery start',
          desc: 'Manually lock the circuit breaker — pauses all loan interest and new loan issuance. Emergency brake.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin recovery stop',
          desc: 'Release the circuit breaker lock — resume interest and loan issuance.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin audit',
          desc: 'Run economy audit — identifies largest debt holders, price anomalies, volatile items.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin auditlog [limit]',
          desc: 'Recent admin actions (price changes, overrides, event triggers) with timestamps.',
          permission: 'autotune.admin',
        },
      ],
    },
    {
      id: 'players',
      label: 'Player & Transaction Data',
      icon: Users,
      color: 'text-blue-400',
      bgColor: 'bg-blue-500/10',
      borderColor: 'border-blue-500/20',
      description: 'Inspect player activity and transaction history.',
      commands: [
        {
          cmd: '/at admin transactions [player]',
          desc: 'View recent transactions, optionally filtered to a specific player.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin transaction-min',
          desc: 'Show current minimum transaction thresholds (quantity and value minimums).',
          permission: 'autotune.admin',
        },
        {
          cmd: '/compare [player1] [player2]',
          desc: 'Compare two players\' trading stats — trades, volume, net position, badges, member since.',
          permission: 'autotune.compare',
        },
      ],
    },
    {
      id: 'leaderboard',
      label: 'Economy Leaderboard',
      icon: BarChart2,
      color: 'text-amber-400',
      bgColor: 'bg-amber-500/10',
      borderColor: 'border-amber-500/20',
      description: 'Top traders by volume and top loan holders by debt.',
      commands: [
        {
          cmd: '/at admin top trades [day|week|month] [limit]',
          desc: 'Top traders by trading volume for a period. Default: week, limit 10.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin top loans [limit]',
          desc: 'Top active loan holders ranked by outstanding balance. Shows loan count and % of total debt.',
          permission: 'autotune.admin',
        },
      ],
    },
    {
      id: 'config',
      label: 'Configuration',
      icon: Settings,
      color: 'text-slate-400',
      bgColor: 'bg-slate-500/10',
      borderColor: 'border-slate-500/20',
      description: 'Reload, monitor, and audit configuration.',
      commands: [
        {
          cmd: '/at admin reload',
          desc: 'Reload config.yml and plugin settings without restarting the server.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin digest',
          desc: 'Send the market digest broadcast to all online players immediately.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin digest config',
          desc: 'Show current digest configuration — enabled sections, interval, webhook URL.',
          permission: 'autotune.admin',
        },
        {
          cmd: '/at admin exchange',
          desc: 'Show current exchange rate settings and cross-server rate configuration.',
          permission: 'autotune.admin',
        },
      ],
    },
  ];

  const filteredCategories = commandCategories.map(cat => ({
    ...cat,
    commands: cat.commands.filter(c =>
      c.cmd.toLowerCase().includes(searchQuery.toLowerCase()) ||
      c.desc.toLowerCase().includes(searchQuery.toLowerCase())
    ),
  })).filter(cat => cat.commands.length > 0);

  return (
    <div className="min-h-screen bg-[#050f0a]">
      <Header />

      {/* Hero */}
      <section className="relative overflow-hidden border-b border-emerald-900/30">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_80%_50%_at_50%_-20%,rgba(16,185,129,0.12),transparent)]" />
        <div className="relative max-w-6xl mx-auto px-6 py-20 text-center">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs font-mono mb-6">
            <Server className="w-3 h-3" />
            21 commands — plugin v2.0.0
          </div>
          <h1 className="text-4xl md:text-5xl font-bold text-white mb-4">
            Admin Command Reference
          </h1>
          <p className="text-lg text-slate-400 max-w-2xl mx-auto mb-8">
            Everything you can do as an Auto-Tune admin on your server.
            From live health monitoring to emergency recovery mode.
          </p>
          <div className="flex flex-wrap justify-center gap-3">
            <Link href="/findings" className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-sm hover:bg-emerald-500/20 transition-colors">
              <BarChart2 className="w-4 h-4" />
              Simulation Findings
            </Link>
            <Link href="/economy" className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-slate-800 border border-slate-700 text-slate-300 text-sm hover:bg-slate-700 transition-colors">
              <Activity className="w-4 h-4" />
              Economy Explained
            </Link>
            <Link href="/health-badge" className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-slate-800 border border-slate-700 text-slate-300 text-sm hover:bg-slate-700 transition-colors">
              <Shield className="w-4 h-4" />
              Embed Health Badge
            </Link>
          </div>
        </div>
      </section>

      <main className="max-w-6xl mx-auto px-6 py-12">
        {/* Search */}
        <div className="relative mb-10">
          <Search className="absolute left-4 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
          <input
            type="text"
            placeholder="Search commands..."
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            className="w-full pl-12 pr-4 py-3 bg-slate-900/60 border border-slate-700/50 rounded-xl text-white placeholder-slate-500 focus:outline-none focus:border-emerald-500/50 focus:ring-1 focus:ring-emerald-500/20 text-sm"
          />
        </div>

        {/* D/G Explainers */}
        <div className="grid md:grid-cols-2 gap-4 mb-12">
          <div className="p-5 rounded-xl bg-slate-900/60 border border-slate-800">
            <div className="flex items-center gap-3 mb-3">
              <div className="w-8 h-8 rounded-lg bg-emerald-500/15 flex items-center justify-center">
                <CheckCircle2 className="w-4 h-4 text-emerald-400" />
              </div>
              <h3 className="text-white font-semibold">Debt/GDP Ratio (D/G)</h3>
            </div>
            <p className="text-sm text-slate-400 leading-relaxed">
              Total active economy debt divided by server GDP. The circuit breaker fires at <strong className="text-emerald-400">30×</strong> (TIER3) — all loan interest pauses until D/G drops below <strong className="text-emerald-400">15×</strong>. Healthy servers run 3–8×. Above 15× means the circuit is actively containing the economy.
            </p>
            <Link href="/economy" className="inline-flex items-center gap-1 text-xs text-emerald-500 mt-3 hover:text-emerald-400">
              Learn how the circuit breaker works <ArrowRight className="w-3 h-3" />
            </Link>
          </div>
          <div className="p-5 rounded-xl bg-slate-900/60 border border-slate-800">
            <div className="flex items-center gap-3 mb-3">
              <div className="w-8 h-8 rounded-lg bg-amber-500/15 flex items-center justify-center">
                <AlertTriangle className="w-4 h-4 text-amber-400" />
              </div>
              <h3 className="text-white font-semibold">When D/G Spikes</h3>
            </div>
            <p className="text-sm text-slate-400 leading-relaxed">
              A D/G spike means players collectively borrowed more than the economy can support. The circuit breaker is a <em>governor</em>, not a cure — it prevents collapse but doesn&apos;t fix underlying debt. Use <code className="text-amber-400 text-xs bg-slate-800 px-1 rounded">/at admin recovery start</code> to manually lock the circuit if needed.
            </p>
            <Link href="/findings" className="inline-flex items-center gap-1 text-xs text-amber-500 mt-3 hover:text-amber-400">
              See 90-day simulation data <ArrowRight className="w-3 h-3" />
            </Link>
          </div>
        </div>

        {/* Command Categories */}
        <div className="space-y-3">
          {filteredCategories.map(cat => {
            const Icon = cat.icon;
            const isOpen = openCategories.has(cat.id);
            return (
              <div key={cat.id} className={`rounded-xl border ${cat.borderColor} ${cat.bgColor} overflow-hidden`}>
                <button
                  onClick={() => toggleCategory(cat.id)}
                  className="w-full flex items-center gap-3 px-5 py-4 text-left hover:bg-white/5 transition-colors"
                >
                  <div className={`w-8 h-8 rounded-lg ${cat.bgColor} flex items-center justify-center flex-shrink-0`}>
                    <Icon className={`w-4 h-4 ${cat.color}`} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-white font-semibold text-sm">{cat.label}</span>
                      <span className={`text-xs px-2 py-0.5 rounded-full ${cat.bgColor} ${cat.color} font-mono`}>
                        {cat.commands.length} commands
                      </span>
                    </div>
                    <p className="text-xs text-slate-500 mt-0.5 truncate">{cat.description}</p>
                  </div>
                  {isOpen
                    ? <ChevronUp className="w-4 h-4 text-slate-500 flex-shrink-0" />
                    : <ChevronDown className="w-4 h-4 text-slate-500 flex-shrink-0" />}
                </button>

                {isOpen && (
                  <div className="border-t border-white/5">
                    <table className="w-full">
                      <thead>
                        <tr className="border-b border-white/5">
                          <th className="px-5 py-2 text-left text-xs font-medium text-slate-500 uppercase tracking-wider w-1/2">Command</th>
                          <th className="px-5 py-2 text-left text-xs font-medium text-slate-500 uppercase tracking-wider">Description</th>
                        </tr>
                      </thead>
                      <tbody>
                        {cat.commands.map((cmd, i) => (
                          <tr key={i} className={`border-b border-white/5 last:border-0 ${i % 2 === 0 ? '' : 'bg-white/2'}`}>
                            <td className="px-5 py-3">
                              <code className="text-xs font-mono text-emerald-400 bg-emerald-500/10 px-2 py-1 rounded">{cmd.cmd}</code>
                            </td>
                            <td className="px-5 py-3">
                              <span className="text-sm text-slate-300">{cmd.desc}</span>
                              <span className="ml-2 text-xs text-slate-600 font-mono">({cmd.permission})</span>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* Bottom CTA */}
        <div className="mt-12 p-6 rounded-xl bg-slate-900/60 border border-slate-800 flex flex-col md:flex-row items-start md:items-center gap-4">
          <div className="flex-1">
            <h3 className="text-white font-semibold mb-1">Want to see how the engine behaves before you deploy?</h3>
            <p className="text-sm text-slate-400">Run any config through 14–90 days of simulated player activity. 840-config parameter sweep available.</p>
          </div>
          <div className="flex gap-3 flex-shrink-0">
            <Link href="/simulator" className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-emerald-600 text-white text-sm hover:bg-emerald-500 transition-colors">
              <Activity className="w-4 h-4" />
              Run Simulator
            </Link>
            <Link href="/config-preview" className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-slate-800 border border-slate-700 text-slate-300 text-sm hover:bg-slate-700 transition-colors">
              <RefreshCw className="w-4 h-4" />
              Config Diff Tool
            </Link>
          </div>
        </div>
      </main>

      <Footer />
    </div>
  );
}