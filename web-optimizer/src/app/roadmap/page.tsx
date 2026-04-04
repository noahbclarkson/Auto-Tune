import { Metadata } from 'next';
import Link from 'next/link';
import { ArrowLeft, CheckCircle2, Circle, Clock, Zap, Globe, BarChart2, Shield, Code2 } from 'lucide-react';

export const metadata: Metadata = {
  title: 'Roadmap — Auto-Tune',
  description: 'What\'s coming next for Auto-Tune',
};

const CATEGORIES = [
  {
    icon: Zap,
    color: 'emerald',
    label: 'Market Engine',
    items: [
      { status: 'done', text: 'Asymmetric spread pipeline (5-factor)' },
      { status: 'done', text: 'Player count scaling via tanh curve' },
      { status: 'done', text: 'Trend dampening and sector correlation' },
      { status: 'done', text: 'Market events (DEMAND_SURGE, SUPPLY_GLUT, INFLATION_BOOST, etc.)' },
      { status: 'done', text: 'Loan circuit breaker with tiered interest caps' },
      { status: 'done', text: 'TIER3 circuit breaker hysteresis — locks at 0% until D/G < 9.0× (92% fewer oscillations)' },
      { status: 'done', text: 'Market event boss bar announcements (in-game)' },
      { status: 'in-progress', text: 'Price anchoring from cross-server true prices' },
      { status: 'todo', text: 'Player archetype auto-tuning via ML on server metrics' },
    ],
  },
  {
    icon: Globe,
    color: 'sky',
    label: 'Cross-Server Ecosystem',
    items: [
      { status: 'done', text: 'Price submission API with 3σ outlier filtering' },
      { status: 'done', text: 'Weighted LS price solver with anchor items' },
      { status: 'done', text: 'Server registration and API key auth' },
      { status: 'done', text: 'Exchange rate computation (per-server vs true-price baseline)' },
      { status: 'done', text: 'Cross-server exchange rate plugin integration (ExchangeRateService)' },
      { status: 'in-progress', text: 'Per-server reputation weighting for submissions' },
      { status: 'todo', text: 'Server health leaderboard with voluntary reporting' },
    ],
  },
  {
    icon: BarChart2,
    color: 'amber',
    label: 'Analytics & UX',
    items: [
      { status: 'done', text: 'Bundled web dashboard (prices, trends, GDP, loans)' },
      { status: 'done', text: 'WebSocket live price updates' },
      { status: 'done', text: 'Parameter sweep tool with 840-config grid' },
      { status: 'done', text: 'Simulation result analyzer (CLI + web)' },
      { status: 'done', text: 'Market simulator with StabilityForecast' },
      { status: 'done', text: 'Player achievement badges and trading milestones' },
      { status: 'done', text: 'Market digest Discord webhook (admin alerts + weekly digest)' },
      { status: 'in-progress', text: 'web-optimizer public dashboard with live cross-server data' },
    ],
  },
  {
    icon: Shield,
    color: 'rose',
    label: 'Security & Operations',
    items: [
      { status: 'done', text: 'Config validation at startup (100+ rules)' },
      { status: 'done', text: 'API rate limiting (token-bucket per-IP)' },
      { status: 'done', text: 'Request body size limits' },
      { status: 'done', text: 'Outlier submission filtering (3σ statistical filter)' },
      { status: 'todo', text: 'Manual server key issuance portal (anti-Sybil)' },
      { status: 'todo', text: 'Flyway/Liquibase database migration system' },
      { status: 'todo', text: 'Integration test framework (MockBukkit)' },
    ],
  },
  {
    icon: Code2,
    color: 'violet',
    label: 'Developer Experience',
    items: [
      { status: 'done', text: 'Full documentation suite (ARCHITECTURE, CONFIG, API, ADMIN guides)' },
      { status: 'done', text: 'Conventional commits + PR checklist' },
      { status: 'done', text: 'Simulation regression suite (5 scenarios, 0.000% delta)' },
      { status: 'done', text: 'Shared test utilities (FakeEconomy, PluginAdapter)' },
      { status: 'todo', text: 'OpenAPI/Swagger for API server' },
      { status: 'todo', text: 'Automated engine sync tests (Java ↔ Rust ↔ TS)' },
      { status: 'todo', text: 'Plugin test coverage target: 60%+ for critical paths' },
    ],
  },
];

const STATUS_CONFIG = {
  done: {
    icon: CheckCircle2,
    label: 'Done',
    textClass: 'text-emerald-400',
    bgClass: 'bg-emerald-950/40 border-emerald-800/40',
    iconClass: 'text-emerald-400',
  },
  'in-progress': {
    icon: Clock,
    label: 'In Progress',
    textClass: 'text-sky-400',
    bgClass: 'bg-sky-950/40 border-sky-800/40',
    iconClass: 'text-sky-400',
  },
  todo: {
    icon: Circle,
    label: 'Planned',
    textClass: 'text-gray-500',
    bgClass: 'bg-gray-900/50 border-gray-800',
    iconClass: 'text-gray-500',
  },
};

const COLOR_MAP: Record<string, string> = {
  emerald: 'text-emerald-400',
  sky: 'text-sky-400',
  amber: 'text-amber-400',
  rose: 'text-rose-400',
  violet: 'text-violet-400',
};

export default function RoadmapPage() {
  return (
    <div className="min-h-screen">
      {/* Header */}
      <div className="border-b border-gray-800/60 bg-gray-950/60">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 py-8">
          <Link
            href="/"
            className="inline-flex items-center gap-1.5 text-sm text-gray-400 hover:text-gray-200 mb-6 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to home
          </Link>
          <div className="flex items-start gap-4">
            <div className="w-12 h-12 rounded-xl bg-emerald-950/60 border border-emerald-800/40 flex items-center justify-center shrink-0 mt-0.5">
              <Zap className="w-6 h-6 text-emerald-400" />
            </div>
            <div>
              <h1 className="text-3xl font-bold text-white mb-2">Auto-Tune Roadmap</h1>
              <p className="text-gray-400 leading-relaxed">
                Where Auto-Tune is headed. Features are organized by category.
                Items marked &ldquo;In Progress&rdquo; are actively being developed.
                All timelines are approximate — the plugin is maintained by a small team.
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Status legend */}
      <div className="max-w-4xl mx-auto px-4 sm:px-6 py-6">
        <div className="flex flex-wrap gap-4 text-xs">
          {Object.entries(STATUS_CONFIG).map(([key, cfg]) => {
            const Icon = cfg.icon;
            return (
              <div key={key} className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full border ${cfg.bgClass}`}>
                <Icon className={`w-3.5 h-3.5 ${cfg.iconClass}`} />
                <span className={cfg.textClass}>{cfg.label}</span>
              </div>
            );
          })}
        </div>
      </div>

      {/* Categories */}
      <div className="max-w-4xl mx-auto px-4 sm:px-6 pb-16 space-y-8">
        {CATEGORIES.map(({ icon: Icon, color, label, items }) => (
          <div key={label} className="rounded-2xl border border-gray-800 bg-gray-900/40 overflow-hidden">
            {/* Category header */}
            <div className="flex items-center gap-3 px-5 py-4 border-b border-gray-800/60 bg-gray-950/40">
              <div className={`w-8 h-8 rounded-lg flex items-center justify-center bg-gray-900/80 border border-gray-800 ${COLOR_MAP[color].replace('text-', 'text-')}`}>
                <Icon className={`w-4 h-4 ${COLOR_MAP[color]}`} />
              </div>
              <h2 className="text-base font-semibold text-white">{label}</h2>
            </div>

            {/* Items */}
            <div className="divide-y divide-gray-800/50">
              {items.map(({ status, text }) => {
                const cfg = STATUS_CONFIG[status as keyof typeof STATUS_CONFIG];
                const StatusIcon = cfg.icon;
                return (
                  <div key={text} className="flex items-start gap-3 px-5 py-3.5 hover:bg-gray-800/20 transition-colors">
                    <StatusIcon className={`w-4 h-4 ${cfg.iconClass} mt-0.5 shrink-0`} />
                    <span className={`text-sm ${status === 'done' ? 'text-gray-400' : status === 'in-progress' ? 'text-gray-200' : 'text-gray-500'}`}>
                      {text}
                    </span>
                    <span className={`ml-auto text-xs px-2 py-0.5 rounded-full border shrink-0 ${cfg.bgClass} ${cfg.textClass}`}>
                      {cfg.label}
                    </span>
                  </div>
                );
              })}
            </div>
          </div>
        ))}

        {/* Contribute CTA */}
        <div className="rounded-2xl border border-emerald-800/40 bg-emerald-950/20 p-6 text-center">
          <p className="text-gray-300 text-sm mb-4">
            Want a feature? Open a GitHub issue or pull request.
            Auto-Tune is open source and contributions are welcome.
          </p>
          <a
            href="https://github.com/noahbclarkson/Auto-Tune/issues"
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-2 px-5 py-2.5 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded-lg transition-colors text-sm"
          >
            Open an Issue
          </a>
        </div>
      </div>
    </div>
  );
}
