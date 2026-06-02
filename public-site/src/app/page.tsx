import Link from 'next/link';
import {
  ArrowRight,
  BarChart3,
  BookOpen,
  Database,
  Download,
  Github,
  Network,
  Server,
  Settings,
  ShieldCheck,
  Sliders,
  TerminalSquare,
} from 'lucide-react';
import { fetchGitHubStats } from '@/lib/github-stats';

const SHOP_PREVIEW =
  'https://github.com/noahbclarkson/Auto-Tune/blob/rewrite-2/.github/Auto-Tune-Shop.gif?raw=true';

const SYSTEM_PARTS = [
  {
    icon: Server,
    title: 'Paper Plugin',
    text: 'Runs the shop, commands, autosell, loans, treasury, auction house, and local market ticks.',
    href: '/install',
  },
  {
    icon: BarChart3,
    title: 'Bundled Dashboard',
    text: 'Served by the plugin on port 8989 for local prices, GDP, debt, loans, and transactions.',
    href: '/docs',
  },
  {
    icon: Network,
    title: 'Price API',
    text: 'Optional Rust service for registered servers, anonymized ratio submissions, and true prices.',
    href: '/api-docs',
  },
  {
    icon: Sliders,
    title: 'Simulation Lab',
    text: 'Rust and TypeScript tools for checking market behavior before a config reaches players.',
    href: '/simulator',
  },
];

const ADMIN_PATHS = [
  {
    icon: Download,
    title: 'Install the plugin',
    text: 'Build or download the shadow JAR, add Vault and an economy provider, then start Paper 1.21.4.',
    href: '/install',
    cta: 'Install guide',
  },
  {
    icon: Settings,
    title: 'Generate a starting config',
    text: 'Use the setup wizard to pick server size, goals, loan posture, and a first-pass YAML config.',
    href: '/setup',
    cta: 'Open setup',
  },
  {
    icon: TerminalSquare,
    title: 'Preview risky changes',
    text: 'Compare configs and run the simulator before changing live spreads, windows, or loan thresholds.',
    href: '/config-preview',
    cta: 'Preview config',
  },
];

function formatNumber(n: number): string {
  if (!Number.isFinite(n) || n <= 0) return 'Not fetched';
  if (n >= 1000) return `${(n / 1000).toFixed(n >= 10000 ? 0 : 1)}K`;
  return String(n);
}

export default async function Home() {
  const stats = await fetchGitHubStats();

  return (
    <div>
      <section className="border-b border-gray-800/70 bg-gray-950">
        <div className="mx-auto grid max-w-7xl gap-10 px-4 py-14 sm:px-6 lg:grid-cols-[1.05fr_0.95fr] lg:px-8 lg:py-20">
          <div>
            <div className="mb-5 inline-flex items-center gap-2 rounded-md border border-emerald-800/50 bg-emerald-950/35 px-3 py-1.5 text-xs font-medium text-emerald-300">
              <ShieldCheck className="h-3.5 w-3.5" />
              Adaptive market pricing for Paper servers
            </div>
            <h1 className="max-w-3xl text-4xl font-bold leading-tight text-white sm:text-5xl">
              Replace static shop prices with a market that reacts to player trades.
            </h1>
            <p className="mt-5 max-w-2xl text-base leading-relaxed text-gray-400 sm:text-lg">
              Auto-Tune watches buy and sell activity, recalculates item prices and bid-ask
              spreads, and gives admins a local dashboard for the economy their players are
              actually using. The cross-server price network is optional.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Link
                href="/install"
                className="inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-emerald-500"
              >
                Install Auto-Tune
                <ArrowRight className="h-4 w-4" />
              </Link>
              <Link
                href="/simulator"
                className="inline-flex items-center gap-2 rounded-lg border border-gray-700 px-5 py-2.5 text-sm font-medium text-gray-300 transition-colors hover:border-gray-600 hover:text-white"
              >
                Run simulator
              </Link>
              <a
                href="https://github.com/noahbclarkson/Auto-Tune"
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-2 rounded-lg px-5 py-2.5 text-sm font-medium text-gray-500 transition-colors hover:text-gray-200"
              >
                <Github className="h-4 w-4" />
                GitHub
              </a>
            </div>
            <div className="mt-9 grid gap-3 text-sm sm:grid-cols-3">
              <div className="rounded-lg border border-gray-800 bg-gray-900/45 p-3">
                <p className="text-xs uppercase tracking-wide text-gray-500">Market tick</p>
                <p className="mt-1 font-mono text-white">5 minutes</p>
              </div>
              <div className="rounded-lg border border-gray-800 bg-gray-900/45 p-3">
                <p className="text-xs uppercase tracking-wide text-gray-500">GitHub stars</p>
                <p className="mt-1 font-mono text-white">{formatNumber(stats.stars)}</p>
              </div>
              <div className="rounded-lg border border-gray-800 bg-gray-900/45 p-3">
                <p className="text-xs uppercase tracking-wide text-gray-500">License</p>
                <p className="mt-1 font-mono text-white">MIT</p>
              </div>
            </div>
          </div>

          <div className="overflow-hidden rounded-lg border border-gray-800 bg-gray-900/50">
            <div className="border-b border-gray-800 px-4 py-3">
              <p className="text-xs font-medium uppercase tracking-widest text-gray-500">In-game shop preview</p>
            </div>
            <img
              src={SHOP_PREVIEW}
              alt="Auto-Tune in-game shop GUI"
              className="aspect-[4/3] w-full object-cover object-left-top"
            />
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 py-14 sm:px-6 lg:px-8">
        <div className="mb-8 max-w-2xl">
          <p className="mb-2 text-xs font-medium uppercase tracking-widest text-emerald-400">Project Structure</p>
          <h2 className="text-2xl font-bold text-white">Four pieces, separate responsibilities</h2>
          <p className="mt-3 text-sm leading-relaxed text-gray-400">
            The public site should not blur the plugin, dashboard, API, and simulation tools.
            Each has a specific role in the repo and in an admin workflow.
          </p>
        </div>
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          {SYSTEM_PARTS.map(({ icon: Icon, title, text, href }) => (
            <Link
              key={title}
              href={href}
              className="group rounded-lg border border-gray-800 bg-gray-900/45 p-5 transition-colors hover:border-gray-700 hover:bg-gray-900/70"
            >
              <Icon className="mb-4 h-5 w-5 text-emerald-400" />
              <h3 className="text-sm font-semibold text-white">{title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-gray-400">{text}</p>
              <span className="mt-4 inline-flex items-center gap-1 text-sm text-gray-500 group-hover:text-emerald-300">
                Open
                <ArrowRight className="h-3.5 w-3.5" />
              </span>
            </Link>
          ))}
        </div>
      </section>

      <section className="border-y border-gray-800/70 bg-gray-900/25">
        <div className="mx-auto max-w-7xl px-4 py-14 sm:px-6 lg:px-8">
          <div className="grid gap-8 lg:grid-cols-[0.8fr_1.2fr]">
            <div>
              <p className="mb-2 text-xs font-medium uppercase tracking-widest text-emerald-400">Admin Workflow</p>
              <h2 className="text-2xl font-bold text-white">From install to production tuning</h2>
              <p className="mt-3 text-sm leading-relaxed text-gray-400">
                Auto-Tune should feel practical from the first visit. These are the paths that
                matter before a server owner trusts it with a live economy.
              </p>
            </div>
            <div className="grid gap-4 md:grid-cols-3">
              {ADMIN_PATHS.map(({ icon: Icon, title, text, href, cta }) => (
                <Link
                  key={title}
                  href={href}
                  className="rounded-lg border border-gray-800 bg-gray-950/55 p-5 transition-colors hover:border-gray-700"
                >
                  <Icon className="mb-4 h-5 w-5 text-emerald-400" />
                  <h3 className="text-sm font-semibold text-white">{title}</h3>
                  <p className="mt-2 min-h-20 text-sm leading-relaxed text-gray-400">{text}</p>
                  <span className="mt-4 inline-flex text-sm font-medium text-emerald-400">{cta}</span>
                </Link>
              ))}
            </div>
          </div>
        </div>
      </section>

      <section className="mx-auto grid max-w-7xl gap-5 px-4 py-14 sm:px-6 lg:grid-cols-3 lg:px-8">
        <div className="rounded-lg border border-gray-800 bg-gray-900/45 p-5">
          <BookOpen className="mb-4 h-5 w-5 text-sky-400" />
          <h2 className="text-base font-semibold text-white">Read the actual docs</h2>
          <p className="mt-2 text-sm leading-relaxed text-gray-400">
            The docs index links to the rewrite-2 admin guide, config reference, architecture,
            dashboard API, and player quickstart.
          </p>
          <Link href="/docs" className="mt-4 inline-flex text-sm font-medium text-emerald-400">
            Open docs
          </Link>
        </div>
        <div className="rounded-lg border border-gray-800 bg-gray-900/45 p-5">
          <Database className="mb-4 h-5 w-5 text-amber-400" />
          <h2 className="text-base font-semibold text-white">Network data is optional</h2>
          <p className="mt-2 text-sm leading-relaxed text-gray-400">
            The plugin works without the public price API. True prices and exchange rates only
            appear when the API is deployed and servers opt in.
          </p>
          <Link href="/true-prices" className="mt-4 inline-flex text-sm font-medium text-emerald-400">
            Check true prices
          </Link>
        </div>
        <div className="rounded-lg border border-gray-800 bg-gray-900/45 p-5">
          <ShieldCheck className="mb-4 h-5 w-5 text-emerald-400" />
          <h2 className="text-base font-semibold text-white">No fake live activity</h2>
          <p className="mt-2 text-sm leading-relaxed text-gray-400">
            Public network pages now show either live API data, an empty registry, or an explicit
            offline state. Simulations stay in the lab.
          </p>
          <Link href="/servers" className="mt-4 inline-flex text-sm font-medium text-emerald-400">
            View registry
          </Link>
        </div>
      </section>
    </div>
  );
}
