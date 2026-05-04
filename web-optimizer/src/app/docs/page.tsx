import { Metadata } from 'next';
import Link from 'next/link';
import { ExternalLink, BookOpen, Wand2, Users, Code2, Zap, ArrowRight } from 'lucide-react';

export const metadata: Metadata = {
  title: 'Documentation | Auto-Tune',
  description:
    'Guides, references, and manuals for running Auto-Tune on your Minecraft server. Quickstart, admin guide, config reference, player docs, and developer API.',
  openGraph: {
    title: 'Documentation | Auto-Tune',
    description:
      'Complete documentation for Auto-Tune server admins, players, and developers.',
    images: [{ url: '/og-image.png', width: 1200, height: 630, alt: 'Auto-Tune Documentation' }],
  },
  twitter: {
    card: 'summary_large_image',
    images: ['/og-image.png'],
  },
};

const GITHUB_BASE = 'https://github.com/noahbclarkson/Auto-Tune/blob/rewrite-2/docs';

type DocCard = {
  title: string;
  description: string;
  audience: 'admin' | 'player' | 'developer' | 'all';
  readingTime: string;
  href: string;
  badge?: string;
};

const CARDS: DocCard[] = [
  // Admin guides — Core
  {
    title: 'Admin Quickstart',
    description:
      'The 5 decisions to make before launching. Archetype mix, loans, floor, events, and digest. Backed by 5-seed simulation evidence across thousands of runs.',
    audience: 'admin',
    readingTime: '8 min',
    href: `${GITHUB_BASE}/QUICKSTART.md`,
    badge: 'Start here',
  },
  {
    title: 'Server Admin Guide',
    description:
      'Full manual for running Auto-Tune: commands, events, loans, auction house, GUI navigation, economy monitoring, and recovery procedures.',
    audience: 'admin',
    readingTime: '20 min',
    href: `${GITHUB_BASE}/SERVER_ADMIN_GUIDE.md`,
    badge: 'Primary guide',
  },
  {
    title: 'Economy Concepts',
    description:
      'How the market engine actually works: GDP, Debt/GDP ratio, volatility, spreads, the Floor Paradox, and why archetype mix matters more than parameters.',
    audience: 'admin',
    readingTime: '10 min',
    href: `${GITHUB_BASE}/ECONOMY_CONCEPTS.md`,
  },
  {
    title: 'Config Guide',
    description:
      'Every config parameter documented: market engine, loans, GuildBuyer, circuit breaker, floor/ceiling, cleanup, web server, price reporting, and market events.',
    audience: 'admin',
    readingTime: '20 min',
    href: `${GITHUB_BASE}/CONFIG_GUIDE.md`,
  },
  {
    title: 'Ecosystem Analysis',
    description:
      'Definitive archetype recommendations from simulation lab: which player types to allow, which to restrict, and why. Newbie, InsiderTrader, VolumeTrader, AFKFarmer tested.',
    audience: 'admin',
    readingTime: '10 min',
    href: `${GITHUB_BASE}/ECOSYSTEM_ANALYSIS.md`,
  },
  // Migration
  {
    title: 'Migration Guide',
    description:
      'Everything that changed between the old Auto-Tune (main branch) and rewrite-2. Breaking changes, removed features, new requirements, config key differences.',
    audience: 'admin',
    readingTime: '12 min',
    href: `${GITHUB_BASE}/MIGRATION.md`,
  },
  {
    title: 'Auction House Guide',
    description:
      'Complete reference for the built-in P2P auction house: limit orders, order book, fill notifications, native /auction commands, depth chart reading, integrity monitoring, and thin-book/spoofing detection.',
    audience: 'admin',
    readingTime: '10 min',
    href: `${GITHUB_BASE}/AUCTION_HOUSE_GUIDE.md`,
  },
  // FAQ
  {
    title: 'FAQ',
    description:
      '30+ common admin questions answered directly: How fast do prices move? Why is Debt/GDP high? How do I prevent exploitation? Why did prices crash?',
    audience: 'admin',
    readingTime: '8 min',
    href: `${GITHUB_BASE}/FAQ.md`,
  },
  // Player
  {
    title: 'Player Quickstart',
    description:
      'What your players see: /shop, /sell, /loans, /compare commands. How prices change, how to read the market, and strategies for making money in the economy.',
    audience: 'player',
    readingTime: '5 min',
    href: `${GITHUB_BASE}/PLAYER_QUICKSTART.md`,
    badge: 'For players',
  },
  // Developer
  {
    title: 'Architecture Guide',
    description:
      'Plugin internals: MarketEngine pipeline, LoanManager, MarketEventService, AuctionMatchingEngine, WebServer, PriceReporter, database schema, and data flow.',
    audience: 'developer',
    readingTime: '15 min',
    href: `${GITHUB_BASE}/ARCHITECTURE.md`,
  },
  {
    title: 'Dashboard API',
    description:
      'Bundled Javalin web server (port 8989). REST + WebSocket endpoints for prices, economy health, GDP, loans, transactions, admin health, and item details.',
    audience: 'developer',
    readingTime: '12 min',
    href: `${GITHUB_BASE}/DASHBOARD_API.md`,
  },
  {
    title: 'Cross-Server API',
    description:
      'Rust API server endpoints for cross-server price aggregation. Server registration, price submission, true-price solving, exchange rates, and rate limits.',
    audience: 'developer',
    readingTime: '10 min',
    href: `${GITHUB_BASE}/API.md`,
  },
  {
    title: 'Security & Trust Model',
    description:
      'How Auto-Tune protects the cross-server price network from manipulation. Server key auth, outlier rejection in the solver, reputation weighting, anti-Sybil measures, and data freshness requirements.',
    audience: 'admin',
    readingTime: '8 min',
    href: `${GITHUB_BASE}/SECURITY.md`,
    badge: 'Trust',
  },
  {
    title: 'Contributing Guide',
    description:
      'How to build Auto-Tune locally: Java 21, Gradle, Rust toolchain, web builds, test commands, code standards, and PR checklist.',
    audience: 'developer',
    readingTime: '6 min',
    href: `${GITHUB_BASE}/CONTRIBUTING.md`,
  },
];

const AUDIENCE_LABELS = {
  admin: { label: 'Server Admin', color: 'bg-emerald-950 text-emerald-400 border-emerald-800' },
  player: { label: 'Player', color: 'bg-sky-950 text-sky-400 border-sky-800' },
  developer: { label: 'Developer', color: 'bg-violet-950 text-violet-400 border-violet-800' },
  all: { label: 'All', color: 'bg-gray-800 text-gray-300 border-gray-700' },
};

const SECTION_LABELS = {
  admin: { title: 'For Server Admins', desc: 'Guides for installing, configuring, and running Auto-Tune on your server.' },
  player: { title: 'For Players', desc: 'How to use the economy, read prices, and make money.' },
  developer: { title: 'For Developers', desc: 'Architecture internals, API references, and contribution guide.' },
};

export default function DocsPage() {
  const byAudience = (a: DocCard['audience']) => CARDS.filter((c) => c.audience === a);

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      {/* Version disclaimer */}
      <div className="border-b border-amber-800/30 bg-amber-950/10">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-3 flex items-start sm:items-center gap-3">
          <div className="shrink-0 w-5 h-5 rounded bg-amber-600/20 border border-amber-600/30 flex items-center justify-center mt-0.5 sm:mt-0">
            <span className="text-amber-400 text-xs font-bold">!</span>
          </div>
          <p className="text-xs sm:text-sm text-amber-200/80 leading-relaxed">
            <span className="font-semibold text-amber-300">These docs track the rewrite-2 branch</span>
            {' '}&mdash; in-development version targeting Paper 1.21.4. All findings and defaults reflect rewrite-2.{' '}
            <a
              href="https://github.com/noahbclarkson/Auto-Tune/tree/rewrite-2/docs"
              target="_blank"
              rel="noopener noreferrer"
              className="underline underline-offset-2 text-amber-300 hover:text-amber-200"
            >
              Main branch docs
            </a>{' '}
            cover the legacy version.
          </p>
        </div>
      </div>

      {/* Hero */}
      <div className="border-b border-gray-800/60 bg-gray-950">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-16">
          <div className="flex items-center gap-3 mb-4">
            <BookOpen className="w-6 h-6 text-emerald-400" />
            <span className="text-sm font-medium text-emerald-400 uppercase tracking-wider">
              Documentation
            </span>
          </div>
          <h1 className="text-4xl font-bold text-white mb-4">
            Auto-Tune Docs
          </h1>
          <p className="text-lg text-gray-400 max-w-2xl leading-relaxed">
            Everything you need to run a great Auto-Tune economy. From first install to advanced
            tuning — backed by thousands of simulation runs across 5 seeds.
          </p>
          <div className="flex items-center gap-4 mt-6">
            <Link
              href="/setup"
              className="inline-flex items-center gap-2 px-4 py-2 bg-emerald-700 hover:bg-emerald-600 text-white rounded-lg text-sm font-medium transition-colors"
            >
              <Wand2 className="w-4 h-4" />
              Setup Wizard
            </Link>
            <Link
              href="/simulator"
              className="inline-flex items-center gap-2 px-4 py-2 border border-gray-700 hover:border-gray-600 text-gray-300 rounded-lg text-sm font-medium transition-colors"
            >
              <Zap className="w-4 h-4" />
              Simulator
            </Link>
          </div>
        </div>
      </div>

      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-12 space-y-16">
        {/* Server Admins */}
        <section>
          <div className="mb-6">
            <h2 className="text-xl font-bold text-white flex items-center gap-2">
              <Users className="w-5 h-5 text-emerald-400" />
              {SECTION_LABELS.admin.title}
            </h2>
            <p className="text-gray-400 text-sm mt-1">{SECTION_LABELS.admin.desc}</p>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            {byAudience('admin').map((card) => (
              <a
                key={card.href}
                href={card.href}
                target="_blank"
                rel="noopener noreferrer"
                className="group block bg-gray-900 border border-gray-800 rounded-xl p-5 hover:border-emerald-800/60 hover:bg-gray-900/80 transition-all"
              >
                <div className="flex items-start justify-between gap-3 mb-2">
                  <div className="flex items-center gap-2 flex-wrap">
                    <h3 className="font-semibold text-white group-hover:text-emerald-300 transition-colors">
                      {card.title}
                    </h3>
                    {card.badge && (
                      <span className="text-xs px-2 py-0.5 rounded-full bg-emerald-900/50 text-emerald-400 border border-emerald-800/50">
                        {card.badge}
                      </span>
                    )}
                  </div>
                  <ExternalLink className="w-4 h-4 text-gray-600 group-hover:text-emerald-400 transition-colors shrink-0 mt-0.5" />
                </div>
                <p className="text-sm text-gray-400 leading-relaxed">{card.description}</p>
                <div className="flex items-center gap-3 mt-3 pt-3 border-t border-gray-800/60">
                  <span className={`text-xs px-2 py-0.5 rounded-full border ${AUDIENCE_LABELS[card.audience].color}`}>
                    {AUDIENCE_LABELS[card.audience].label}
                  </span>
                  <span className="text-xs text-gray-600">{card.readingTime} read</span>
                </div>
              </a>
            ))}
          </div>
        </section>

        {/* Players */}
        <section>
          <div className="mb-6">
            <h2 className="text-xl font-bold text-white flex items-center gap-2">
              <Users className="w-5 h-5 text-sky-400" />
              {SECTION_LABELS.player.title}
            </h2>
            <p className="text-gray-400 text-sm mt-1">{SECTION_LABELS.player.desc}</p>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            {byAudience('player').map((card) => (
              <a
                key={card.href}
                href={card.href}
                target="_blank"
                rel="noopener noreferrer"
                className="group block bg-gray-900 border border-gray-800 rounded-xl p-5 hover:border-sky-800/60 hover:bg-gray-900/80 transition-all"
              >
                <div className="flex items-start justify-between gap-3 mb-2">
                  <div className="flex items-center gap-2 flex-wrap">
                    <h3 className="font-semibold text-white group-hover:text-sky-300 transition-colors">
                      {card.title}
                    </h3>
                    {card.badge && (
                      <span className="text-xs px-2 py-0.5 rounded-full bg-sky-900/50 text-sky-400 border border-sky-800/50">
                        {card.badge}
                      </span>
                    )}
                  </div>
                  <ExternalLink className="w-4 h-4 text-gray-600 group-hover:text-sky-400 transition-colors shrink-0 mt-0.5" />
                </div>
                <p className="text-sm text-gray-400 leading-relaxed">{card.description}</p>
                <div className="flex items-center gap-3 mt-3 pt-3 border-t border-gray-800/60">
                  <span className={`text-xs px-2 py-0.5 rounded-full border ${AUDIENCE_LABELS[card.audience].color}`}>
                    {AUDIENCE_LABELS[card.audience].label}
                  </span>
                  <span className="text-xs text-gray-600">{card.readingTime} read</span>
                </div>
              </a>
            ))}
          </div>
        </section>

        {/* Developers */}
        <section>
          <div className="mb-6">
            <h2 className="text-xl font-bold text-white flex items-center gap-2">
              <Code2 className="w-5 h-5 text-violet-400" />
              {SECTION_LABELS.developer.title}
            </h2>
            <p className="text-gray-400 text-sm mt-1">{SECTION_LABELS.developer.desc}</p>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            {byAudience('developer').map((card) => (
              <a
                key={card.href}
                href={card.href}
                target="_blank"
                rel="noopener noreferrer"
                className="group block bg-gray-900 border border-gray-800 rounded-xl p-5 hover:border-violet-800/60 hover:bg-gray-900/80 transition-all"
              >
                <div className="flex items-start justify-between gap-3 mb-2">
                  <div className="flex items-center gap-2 flex-wrap">
                    <h3 className="font-semibold text-white group-hover:text-violet-300 transition-colors">
                      {card.title}
                    </h3>
                    {card.badge && (
                      <span className="text-xs px-2 py-0.5 rounded-full bg-violet-900/50 text-violet-400 border border-violet-800/50">
                        {card.badge}
                      </span>
                    )}
                  </div>
                  <ExternalLink className="w-4 h-4 text-gray-600 group-hover:text-violet-400 transition-colors shrink-0 mt-0.5" />
                </div>
                <p className="text-sm text-gray-400 leading-relaxed">{card.description}</p>
                <div className="flex items-center gap-3 mt-3 pt-3 border-t border-gray-800/60">
                  <span className={`text-xs px-2 py-0.5 rounded-full border ${AUDIENCE_LABELS[card.audience].color}`}>
                    {AUDIENCE_LABELS[card.audience].label}
                  </span>
                  <span className="text-xs text-gray-600">{card.readingTime} read</span>
                </div>
              </a>
            ))}
          </div>
        </section>

        {/* Also see */}
        <section className="border-t border-gray-800/60 pt-10">
          <h2 className="text-lg font-bold text-white mb-4">Also on this site</h2>
          <div className="grid gap-3 sm:grid-cols-3">
            {[
              { href: '/changelog', label: 'Changelog', desc: 'Version history and release notes' },
              { href: '/roadmap', label: 'Roadmap', desc: 'Planned features and priorities' },
              { href: '/api-docs', label: 'API Reference', desc: 'Interactive API docs for cross-server server' },
              { href: '/auction', label: 'Auction House', desc: 'P2P order-book marketplace — order book demo, commands, integrity monitoring' },
            ].map(({ href, label, desc }) => (
              <Link
                key={href}
                href={href}
                className="flex items-start gap-3 p-4 bg-gray-900 border border-gray-800 rounded-lg hover:border-gray-700 transition-colors group"
              >
                <ArrowRight className="w-4 h-4 text-gray-600 group-hover:text-emerald-400 transition-colors mt-0.5 shrink-0" />
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
