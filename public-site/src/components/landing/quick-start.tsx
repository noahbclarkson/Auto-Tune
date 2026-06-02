import Link from 'next/link';
import { ArrowRight, Download, Terminal, Zap, BookOpen, Package, Users, TrendingUp, Shield } from 'lucide-react';

const INSTALL_STEPS = [
  {
    num: '1',
    title: 'Drop the JAR into plugins/',
    desc: 'Download the latest release from GitHub. Drop autotune-*.jar into your Paper 1.21.4 server\'s plugins/ folder and restart.',
    icon: Download,
    accent: 'emerald',
  },
  {
    num: '2',
    title: 'Configure base prices',
    desc: 'Edit config.yml with your server\'s base prices. Prices can be anything — Auto-Tune adjusts from wherever you start.',
    icon: Terminal,
    accent: 'sky',
  },
  {
    num: '3',
    title: 'Players start trading',
    desc: 'Auto-Tune ticks every 5 minutes. Prices begin shifting based on actual player activity immediately.',
    icon: Zap,
    accent: 'amber',
  },
];

const PLAYER_COMMANDS = [
  { cmd: '/shop', desc: 'Browse and search all items' },
  { cmd: '/sell', desc: 'Sell items from inventory' },
  { cmd: '/autosell', desc: 'Toggle auto-sell on pickup' },
  { cmd: '/loan', desc: 'Take and repay loans' },
  { cmd: '/auction', desc: 'Browse and trade in auction house' },
  { cmd: '/shop history <item>', desc: 'View price history chart' },
];

const ADMIN_COMMANDS = [
  { cmd: '/at admin freeze <item>', desc: 'Freeze an item\'s price' },
  { cmd: '/at admin price setmax', desc: 'Set per-item price ceiling' },
  { cmd: '/at admin transactions', desc: 'Audit recent transactions' },
  { cmd: '/autotune treasury', desc: 'View server treasury balance' },
  { cmd: '/autotune alert add', desc: 'Set price change alerts' },
  { cmd: '/at admin item reset', desc: 'Reset per-item overrides' },
];

const accentColors: Record<string, { num: string; border: string; bg: string; icon: string; code: string }> = {
  emerald: {
    num: 'text-emerald-600',
    border: 'border-emerald-800/50',
    bg: 'bg-emerald-950/20',
    icon: 'bg-emerald-950/40 border-emerald-800/40 text-emerald-400',
    code: 'text-emerald-300',
  },
  sky: {
    num: 'text-sky-600',
    border: 'border-sky-800/50',
    bg: 'bg-sky-950/20',
    icon: 'bg-sky-950/40 border-sky-800/40 text-sky-400',
    code: 'text-sky-300',
  },
  amber: {
    num: 'text-amber-600',
    border: 'border-amber-800/50',
    bg: 'bg-amber-950/20',
    icon: 'bg-amber-950/40 border-amber-800/40 text-amber-400',
    code: 'text-amber-300',
  },
};

export function QuickStart() {
  return (
    <section className="py-20">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">

        {/* Header */}
        <div className="text-center max-w-2xl mx-auto mb-14">
          <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">Get Started</p>
          <h2 className="text-2xl sm:text-3xl font-bold text-white mb-4">
            From zero to live economy in 3 steps
          </h2>
          <p className="text-gray-400 leading-relaxed">
            Auto-Tune installs in minutes. No database setup, no external services required —
            SQLite runs out of the box, or connect MariaDB for production scale.
          </p>
        </div>

        {/* Install steps */}
        <div className="grid md:grid-cols-3 gap-5 mb-16">
          {INSTALL_STEPS.map((step, i) => {
            const c = accentColors[step.accent];
            return (
              <div
                key={step.num}
                className={`relative rounded-2xl border ${c.border} ${c.bg} p-6 hover:-translate-y-0.5 transition-transform`}
              >
                {/* Step connector arrow */}
                {i < INSTALL_STEPS.length - 1 && (
                  <div className="hidden md:block absolute -right-5 top-1/2 -translate-y-1/2 z-10">
                    <div className="w-10 h-px bg-gray-700 relative">
                      <ArrowRight className="w-3 h-3 text-gray-600 absolute top-1/2 -translate-y-1/2 right-0" />
                    </div>
                  </div>
                )}

                <div className="flex items-start gap-4">
                  <div className={`w-10 h-10 rounded-xl border ${c.icon} flex items-center justify-center shrink-0`}>
                    <step.icon className="w-5 h-5" />
                  </div>
                  <div>
                    <div className="flex items-center gap-2 mb-1">
                      <span className={`text-xs font-mono font-bold ${c.num}`}>STEP {step.num}</span>
                    </div>
                    <h3 className="text-base font-semibold text-white mb-2">{step.title}</h3>
                    <p className="text-sm text-gray-400 leading-relaxed">{step.desc}</p>
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        {/* Requirements + links */}
        <div className="rounded-2xl border border-gray-800 bg-gray-900/60 p-5 mb-14">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div className="flex flex-wrap items-center gap-3">
              {[
                { icon: Package, label: 'Paper 1.21.4+' },
                { icon: Terminal, label: 'Java 21' },
                { icon: Shield, label: 'Vault + Economy provider' },
                { icon: TrendingUp, label: 'SQLite (built-in) or MariaDB' },
              ].map(({ icon: Icon, label }) => (
                <div key={label} className="flex items-center gap-2 text-xs text-gray-400">
                  <Icon className="w-3.5 h-3.5 text-gray-500" />
                  <span>{label}</span>
                </div>
              ))}
            </div>
            <div className="flex items-center gap-3">
              <Link
                href="https://github.com/noahbclarkson/Auto-Tune"
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-gray-800 border border-gray-700 text-gray-300 text-xs hover:border-emerald-700 hover:text-emerald-400 transition-colors"
              >
                <Download className="w-3.5 h-3.5" />
                Download JAR
              </Link>
              <Link
                href="/how-it-works"
                className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-emerald-600/20 border border-emerald-700/50 text-emerald-400 text-xs hover:bg-emerald-600/30 transition-colors"
              >
                <BookOpen className="w-3.5 h-3.5" />
                Read Docs
              </Link>
            </div>
          </div>
        </div>

        {/* Commands */}
        <div className="grid lg:grid-cols-2 gap-6">
          {/* Player commands */}
          <div className="rounded-2xl border border-gray-800 bg-gray-900/50 overflow-hidden">
            <div className="px-5 py-4 border-b border-gray-800 flex items-center gap-2.5">
              <Users className="w-4 h-4 text-emerald-400" />
              <h3 className="text-sm font-semibold text-white">Player Commands</h3>
              <span className="ml-auto text-xs text-gray-500 font-mono">for your players</span>
            </div>
            <div className="divide-y divide-gray-800/50">
              {PLAYER_COMMANDS.map(({ cmd, desc }) => (
                <div key={cmd} className="flex items-center gap-4 px-5 py-3 hover:bg-gray-800/30 transition-colors">
                  <code className="text-xs font-mono text-sky-300 bg-gray-950 border border-gray-800 px-2 py-1 rounded shrink-0">
                    {cmd}
                  </code>
                  <span className="text-sm text-gray-400">{desc}</span>
                </div>
              ))}
            </div>
          </div>

          {/* Admin commands */}
          <div className="rounded-2xl border border-gray-800 bg-gray-900/50 overflow-hidden">
            <div className="px-5 py-4 border-b border-gray-800 flex items-center gap-2.5">
              <Shield className="w-4 h-4 text-amber-400" />
              <h3 className="text-sm font-semibold text-white">Admin Commands</h3>
              <span className="ml-auto text-xs text-gray-500 font-mono">requires op</span>
            </div>
            <div className="divide-y divide-gray-800/50">
              {ADMIN_COMMANDS.map(({ cmd, desc }) => (
                <div key={cmd} className="flex items-center gap-4 px-5 py-3 hover:bg-gray-800/30 transition-colors">
                  <code className="text-xs font-mono text-amber-300 bg-gray-950 border border-gray-800 px-2 py-1 rounded shrink-0">
                    {cmd}
                  </code>
                  <span className="text-sm text-gray-400">{desc}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Bottom CTA */}
        <div className="text-center mt-12">
          <p className="text-sm text-gray-500 mb-4">
            Full configuration guide covers spreads, loans, autosell, enchantment pricing, and more.
          </p>
          <div className="flex flex-wrap justify-center gap-3">
            <Link
              href="/simulator"
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded-lg transition-colors shadow-lg shadow-emerald-600/20 text-sm"
            >
              <TrendingUp className="w-4 h-4" />
              Try the Simulator
            </Link>
            <Link
              href="https://github.com/noahbclarkson/Auto-Tune"
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-gray-800 hover:bg-gray-700 text-gray-200 font-medium rounded-lg transition-colors border border-gray-700 text-sm"
            >
              View on GitHub
              <ArrowRight className="w-4 h-4" />
            </Link>
          </div>
        </div>
      </div>
    </section>
  );
}
