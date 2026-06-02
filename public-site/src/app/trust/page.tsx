import { Metadata } from 'next';
import Link from 'next/link';
import { Shield, CheckCircle, AlertTriangle, Clock, Users, Database, Server, Zap } from 'lucide-react';

export const metadata: Metadata = {
  title: 'Trust & Governance | Auto-Tune',
  description:
    "How Auto-Tune's cross-server network protects price data from manipulation, bad actors, and stale submissions.",
  openGraph: {
    title: 'Trust & Governance | Auto-Tune',
    description: "Security model for Auto-Tune opt-in cross-server price network.",
    images: [{ url: '/og-image.png', width: 1200, height: 630 }],
  },
  twitter: { card: 'summary_large_image', images: ['/og-image.png'] },
};

const PRINCIPLES = [
  {
    icon: Shield,
    label: 'Server keys — one key, one server',
    body:
      'Every registered server receives a unique 32-byte API key shown exactly once at registration. The API server stores only a SHA-256 hash — plaintext keys cannot be recovered if lost. All writer endpoints enforce server-ID/path auth: submissions are rejected with 403 if the key does not match the claimed server ID.',
  },
  {
    icon: Database,
    label: 'No player data leaves your server',
    body:
      'The API server stores aggregated ratio matrices and metadata only. No player UUIDs, names, balances, or individual transaction data is ever transmitted. A ratio matrix is a table of relative prices (e.g., DIAMOND/IRON_INGOT = 9.6) — no player identity can be extracted from it.',
  },
  {
    icon: Zap,
    label: 'Exchange-rate decisions stay local',
    body:
      'The API computes true prices; your plugin decides how to use them. Auto-Tune never pushes exchange-rate changes to your server. Your local economy remains fully under your control — the API is advisory, not authoritative.',
  },
];

const SAFEGUARDS: Array<{
  icon: typeof Shield;
  label: string;
  items: string[];
  status: 'live' | 'partial' | 'planned';
  statusNote: string;
}> = [
  {
    icon: Users,
    label: 'Anti-Sybil',
    items: [
      'Per-IP registration rate limits (10 req/min, burst 10)',
      'One API key bound to one server ID',
      'Matrix validation rejects contradictory data before it reaches the solver',
      'Outlier filtering rejects statistically extreme ratios when honest peers exist',
      'Registration approval / invite flow (planned before public launch)',
    ],
    status: 'partial',
    statusNote: 'Rate limits and matrix validation are live. Invite flow is planned.',
  },
  {
    icon: Clock,
    label: 'Freshness filtering',
    items: [
      'Submissions older than 24 hours are excluded from recomputation',
      'Servers that go offline stop influencing prices automatically',
      'Last-seen timestamps visible per server on the /servers page',
    ],
    status: 'live',
    statusNote: '24-hour default, configurable via STALE_THRESHOLD_HOURS env var.',
  },
  {
    icon: AlertTriangle,
    label: 'Outlier filtering',
    items: [
      'Ratios collected in log-space per item pair',
      'Observations beyond 3σ from pair consensus are replaced with "unknown"',
      'Bridge inference fills gaps from neighbouring valid ratios',
      'Outlier detection requires multiple independent servers — low-sample states are surfaced as low-confidence',
    ],
    status: 'live',
    statusNote: 'Needs 3+ servers for effective filtering. Low-sample states are labeled transparently.',
  },
  {
    icon: CheckCircle,
    label: 'Solver integrity',
    items: [
      'Constrained least-squares price solving on validated graph',
      'Self-reported player count weighted (capped minimum of 1)',
      'Age/reputation weighting planned — new servers start lower trust',
      'No price can move arbitrarily: all moves constrained by graph structure',
    ],
    status: 'partial',
    statusNote: 'Player-count weighting live. Age/reputation weighting planned.',
  },
];

function SafeguardCard({
  icon: Icon,
  label,
  items,
  status,
  statusNote,
}: {
  icon: typeof Shield;
  label: string;
  items: string[];
  status: 'live' | 'partial' | 'planned';
  statusNote: string;
}) {
  const statusConfig = {
    live: { label: 'Live', className: 'text-emerald-400 bg-emerald-500/10 border-emerald-500/30' },
    partial: { label: 'Partially live', className: 'text-amber-400 bg-amber-500/10 border-amber-500/30' },
    planned: { label: 'Planned', className: 'text-gray-400 bg-gray-500/10 border-gray-500/30' },
  };
  const s = statusConfig[status];
  return (
    <div className="bg-gray-900/50 border border-gray-800/50 rounded-xl p-5">
      <div className="flex items-start gap-3 mb-3">
        <Icon className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <h3 className="text-sm font-semibold text-white">{label}</h3>
            <span
              className={`text-xs px-2 py-0.5 rounded-full border font-medium ${s.className}`}
            >
              {s.label}
            </span>
          </div>
          <p className="text-xs text-gray-500 mt-0.5">{statusNote}</p>
        </div>
      </div>
      <ul className="space-y-1.5">
        {items.map((item) => (
          <li key={item} className="flex items-start gap-2 text-xs text-gray-300">
            <span className="text-emerald-500 mt-0.5 shrink-0">•</span>
            {item}
          </li>
        ))}
      </ul>
    </div>
  );
}

export default function TrustPage() {
  return (
    <div className="min-h-screen bg-gray-950 text-white">
      <main className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        {/* Hero */}
        <div className="mb-10">
          <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">
            Trust & Security
          </p>
          <h1 className="text-3xl sm:text-4xl font-bold text-white mb-3">
            Built to be trustworthy, not just technically sound
          </h1>
          <p className="text-gray-400 max-w-3xl text-sm sm:text-base leading-relaxed">
            Auto-Tune's cross-server network aggregates price data from independent Minecraft
            servers. That data is only valuable if you can trust it. This page explains exactly
            what protections are in place, what is still being hardened, and what the exchange-rate
            model intentionally does not do.
          </p>
        </div>

        {/* Core principles */}
        <div className="bg-gray-900/50 border border-gray-800/50 rounded-xl p-6 mb-8">
          <h2 className="text-sm font-semibold text-emerald-400 uppercase tracking-wide mb-4">
            Core Principles
          </h2>
          <div className="grid sm:grid-cols-1 md:grid-cols-3 gap-5">
            {PRINCIPLES.map((p) => (
              <div key={p.label}>
                <div className="flex items-center gap-2 mb-2">
                  <p.icon className="w-4 h-4 text-emerald-400 shrink-0" />
                  <h3 className="text-xs font-semibold text-gray-200 uppercase tracking-wide">
                    {p.label}
                  </h3>
                </div>
                <p className="text-xs text-gray-400 leading-relaxed">{p.body}</p>
              </div>
            ))}
          </div>
        </div>

        {/* Safeguards */}
        <h2 className="text-lg font-semibold text-white mb-4">Active Safeguards</h2>
        <div className="grid sm:grid-cols-2 gap-4 mb-10">
          {SAFEGUARDS.map((s) => (
            <SafeguardCard key={s.label} {...s} />
          ))}
        </div>

        {/* Data boundary */}
        <div className="bg-gray-900/50 border border-gray-800/50 rounded-xl p-6 mb-8">
          <h2 className="text-sm font-semibold text-emerald-400 uppercase tracking-wide mb-4">
            What the API server stores
          </h2>
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b border-gray-800">
                  <th className="text-left text-gray-500 font-medium pb-2 pr-4">Data</th>
                  <th className="text-left text-gray-500 font-medium pb-2">Stored?</th>
                </tr>
              </thead>
              <tbody className="text-gray-300">
                {[
                  ['Player UUIDs or names', 'Never'],
                  ['Balances', 'Never'],
                  ['Individual transactions', 'Never'],
                  ['Chat or social data', 'Never'],
                  ['Server name and ID', 'Yes'],
                  ['Reported player count', 'Yes'],
                  ['Aggregated ratio matrix', 'Yes'],
                  ['True prices and history', 'Yes'],
                  ['Last submission timestamps', 'Yes'],
                ].map(([data, stored]) => (
                  <tr key={data} className="border-b border-gray-800/50 last:border-0">
                    <td className="py-2 pr-4 text-gray-300">{data}</td>
                    <td
                      className={`py-2 font-medium ${
                        stored === 'Never'
                          ? 'text-red-400'
                          : stored === 'Yes'
                          ? 'text-emerald-400'
                          : 'text-gray-400'
                      }`}
                    >
                      {stored}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        {/* Honest caveat */}
        <div className="border border-amber-500/20 bg-amber-500/5 rounded-xl p-5 mb-8">
          <div className="flex items-start gap-3">
            <AlertTriangle className="w-5 h-5 text-amber-400 shrink-0 mt-0.5" />
            <div>
              <h3 className="text-sm font-semibold text-amber-400 mb-1">
                What this page does not claim
              </h3>
              <p className="text-xs text-gray-300 leading-relaxed">
                With fewer than 3–4 participating servers, outlier filtering has limited
                effectiveness and the network should be treated as low-confidence. Registration
                approval, key revocation, and age/reputation weighting are not yet implemented —
                these are on the roadmap before the network is marketed as production-grade public
                infrastructure. Auto-Tune is a strong technical foundation; governance hardening
                is the next layer.
              </p>
            </div>
          </div>
        </div>

        {/* Roadmap CTA */}
        <div className="flex flex-col sm:flex-row gap-4 justify-center">
          <Link
            href="/true-prices"
            className="text-sm px-5 py-2.5 bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg font-medium text-center transition-colors"
          >
            View True Prices
          </Link>
          <Link
            href="/servers"
            className="text-sm px-5 py-2.5 bg-gray-800 hover:bg-gray-700 text-gray-200 rounded-lg font-medium text-center transition-colors"
          >
            See Registered Servers
          </Link>
          <Link
            href="/roadmap"
            className="text-sm px-5 py-2.5 bg-gray-800 hover:bg-gray-700 text-gray-200 rounded-lg font-medium text-center transition-colors"
          >
            View Roadmap
          </Link>
        </div>
      </main>
    </div>
  );
}