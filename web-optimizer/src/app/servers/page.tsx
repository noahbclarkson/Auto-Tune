import Link from 'next/link';
import { Header } from '@/components/layout/header';
import { Footer } from '@/components/layout/footer';
import { ServerCard } from '@/components/servers/server-card';
import { RegisterServerModal } from '@/components/servers/register-server-modal';
import { fetchServers } from '@/lib/api-client';
import { Globe, Clock, TrendingUp, Shield, TrendingDown, CheckCircle, AlertTriangle, ExternalLink } from 'lucide-react';

export const metadata = {
  title: 'Servers | Auto-Tune',
  description: 'Registered Auto-Tune servers and their latest submissions',
  openGraph: {
    title: 'Servers | Auto-Tune',
    description: 'Registered Auto-Tune servers and their latest submissions',
    images: [{ url: '/og-image.png', width: 1200, height: 630 }],
  },
  twitter: { card: 'summary_large_image', images: ['/og-image.png'] },
};

function NetworkStat({ icon: Icon, label, value }: { icon: typeof Globe; label: string; value: string }) {
  return (
    <div className="flex items-center gap-3 bg-gray-900/40 border border-gray-800/40 rounded-lg px-4 py-3">
      <Icon className="w-5 h-5 text-emerald-400 shrink-0" />
      <div>
        <p className="text-xs text-gray-500 uppercase tracking-wide">{label}</p>
        <p className="text-sm font-semibold text-white">{value}</p>
      </div>
    </div>
  );
}

// ─── Mock server showcase (shown when no real servers registered) ──────────────

interface MockServer {
  name: string;
  id: string;
  status: { label: string; className: string };
  players: number;
  items: number;
  lastSeen: string;
  lastSubmission: string;
  healthScore: number;
  healthLabel: string;
  healthClass: string;
  topMover: { item: string; change: string; up: boolean };
  serverType: string;
}

const MOCK_SERVERS: MockServer[] = [
  {
    name: 'Cobblestone SMP',
    id: 'cobble-smp-7f3a',
    status: { label: 'Online', className: 'text-emerald-300 bg-emerald-500/10 border-emerald-500/30' },
    players: 31,
    items: 412,
    lastSeen: new Date(Date.now() - 2 * 60 * 1000).toISOString(),
    lastSubmission: new Date(Date.now() - 28 * 60 * 1000).toISOString(),
    healthScore: 84,
    healthLabel: 'Healthy',
    healthClass: 'text-emerald-400',
    topMover: { item: 'ANCIENT_DEBRIS', change: '+4.2%', up: true },
    serverType: 'Survival SMP',
  },
  {
    name: 'HermitCraft Clone',
    id: 'hermit-vaultex-9c1e',
    status: { label: 'Online', className: 'text-emerald-300 bg-emerald-500/10 border-emerald-500/30' },
    players: 18,
    items: 389,
    lastSeen: new Date(Date.now() - 4 * 60 * 1000).toISOString(),
    lastSubmission: new Date(Date.now() - 32 * 60 * 1000).toISOString(),
    healthScore: 71,
    healthLabel: 'Elevated Debt',
    healthClass: 'text-amber-400',
    topMover: { item: 'EMERALD', change: '-2.8%', up: false },
    serverType: 'Whitelisted SMP',
  },
  {
    name: 'EndWars PvP',
    id: 'endwars-pvp-2b8d',
    status: { label: 'Idle', className: 'text-amber-300 bg-amber-500/10 border-amber-500/30' },
    players: 7,
    items: 156,
    lastSeen: new Date(Date.now() - 45 * 60 * 1000).toISOString(),
    lastSubmission: new Date(Date.now() - 58 * 60 * 1000).toISOString(),
    healthScore: 62,
    healthLabel: 'Imbalanced',
    healthClass: 'text-amber-400',
    topMover: { item: 'DIAMOND', change: '+1.1%', up: true },
    serverType: 'PvP / Faction',
  },
];

function HealthBadge({ score, label, cls }: { score: number; label: string; cls: string }) {
  return (
    <div className="flex items-center gap-1.5">
      <span className={`text-xs font-semibold ${cls}`}>{score}</span>
      <span className="text-xs text-gray-600">/100</span>
      <span className={`text-xs ${cls}`}>{label}</span>
    </div>
  );
}

function MockServerCard({ server, index }: { server: MockServer; index: number }) {
  const healthColor = server.healthScore >= 75 ? 'text-emerald-400' : server.healthScore >= 45 ? 'text-amber-400' : 'text-red-400';
  const healthBg = server.healthScore >= 75 ? 'bg-emerald-500/10 border-emerald-500/30' : server.healthScore >= 45 ? 'bg-amber-500/10 border-amber-500/30' : 'bg-red-500/10 border-red-500/30';
  const moverColor = server.topMover.up ? 'text-emerald-400' : 'text-rose-400';
  const MoverIcon = server.topMover.up ? TrendingUp : TrendingDown;

  return (
    <article className="bg-gray-900/50 border border-gray-800/50 rounded-xl p-5 hover:border-gray-700/60 transition-colors">
      <div className="flex items-start justify-between gap-3 mb-3">
        <div>
          <div className="flex items-center gap-2 mb-0.5">
            <h3 className="text-base font-semibold text-white">{server.name}</h3>
            <span className="text-[10px] text-gray-600 font-mono">#{index + 1}</span>
          </div>
          <p className="text-xs text-gray-500">{server.serverType} · ID: {server.id}</p>
        </div>
        <span className={`text-xs px-2 py-1 rounded-full border ${server.status.className}`}>{server.status.label}</span>
      </div>

      {/* Stats row */}
      <div className="grid grid-cols-3 gap-2 mb-3">
        <div className="bg-gray-950/40 rounded-lg p-2.5 border border-gray-800/40 text-center">
          <p className="text-gray-500 text-[10px] uppercase tracking-wide mb-0.5">Players</p>
          <p className="text-gray-100 font-semibold text-sm">{server.players}</p>
        </div>
        <div className="bg-gray-950/40 rounded-lg p-2.5 border border-gray-800/40 text-center">
          <p className="text-gray-500 text-[10px] uppercase tracking-wide mb-0.5">Items</p>
          <p className="text-gray-100 font-semibold text-sm">{server.items}</p>
        </div>
        <div className="bg-gray-950/40 rounded-lg p-2.5 border border-gray-800/40 text-center">
          <p className="text-gray-500 text-[10px] uppercase tracking-wide mb-0.5">Health</p>
          <p className={`font-semibold text-sm ${healthColor}`}>{server.healthScore}</p>
        </div>
      </div>

      {/* Top mover + last submission */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1.5">
          <MoverIcon className={`w-3.5 h-3.5 ${moverColor}`} />
          <span className="text-xs text-gray-400">
            <span className="text-gray-500">Top: </span>
            <span className="font-mono text-gray-300">{server.topMover.item}</span>
            <span className={`ml-1 ${moverColor}`}>{server.topMover.change}</span>
          </span>
        </div>
        <span className="text-[10px] text-gray-600">
          Submit: {new Date(server.lastSubmission).toLocaleTimeString()}
        </span>
      </div>
    </article>
  );
}

export default async function ServersPage() {
  const serversResult = await fetchServers();
  const servers = serversResult.data ?? [];

  return (
    <div className="min-h-screen bg-gray-950 text-white">
      <Header />
      <main className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <div className="mb-8">
          <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">Network</p>
          <h1 className="text-3xl sm:text-4xl font-bold text-white mb-3">Servers</h1>
          <p className="text-gray-400 max-w-3xl text-sm sm:text-base leading-relaxed">
            Every registered server submits anonymised price ratios every 30 minutes. Combined, they build a shared picture of item values across the Auto-Tune network.
          </p>
          <div className="mt-4">
            <RegisterServerModal />
          </div>
        </div>

        {/* Network stats */}
        {servers.length > 0 && (
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-8">
            <NetworkStat icon={Globe} label="Active servers" value={String(servers.length)} />
            <NetworkStat icon={TrendingUp} label="Total items" value={String(servers.reduce((acc, s) => acc + (s.last_submission_item_count ?? 0), 0))} />
            <NetworkStat icon={Shield} label="Anonymised" value="100%" />
            <NetworkStat icon={Clock} label="Last update" value={servers.length > 0 ? new Date(Math.max(...servers.map(s => new Date(s.last_seen).getTime()))).toLocaleTimeString() : '—'} />
          </div>
        )}

        {servers.length > 0 ? (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {servers.map((server) => (
              <ServerCard key={server.id} server={server} />
            ))}
          </div>
        ) : (
          <>
            {/* API server error notice */}
            {serversResult.error && (
              <div className="bg-amber-500/5 border border-amber-500/20 rounded-xl p-4 mb-8">
                <p className="text-amber-400 text-sm font-medium mb-1 flex items-center gap-2">
                  <AlertTriangle className="w-4 h-4" />
                  API server not connected
                </p>
                <p className="text-amber-300 text-xs">
                  Could not reach the Auto-Tune API server. Cross-server features are unavailable.{' '}
                  <code className="font-mono text-amber-200">NEXT_PUBLIC_API_URL</code> may not be set or the server is offline.
                </p>
              </div>
            )}

            {/* Mock network showcase */}
            <div className="mb-8">
              <div className="flex items-center justify-between mb-4">
                <div className="flex items-center gap-2">
                  <p className="text-xs text-gray-500 uppercase tracking-wide">The Auto-Tune network</p>
                  <span className="text-[10px] px-2 py-0.5 rounded-full bg-amber-500/10 border border-amber-500/30 text-amber-400 font-medium">Demo preview</span>
                </div>
                <span className="text-xs text-gray-600">{MOCK_SERVERS.length} servers · awaiting live data</span>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                {MOCK_SERVERS.map((server, i) => (
                  <MockServerCard key={server.id} server={server} index={i} />
                ))}
              </div>
            </div>

            {/* Aggregate network stats (mock) */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-8">
              <NetworkStat icon={Globe} label="Active servers" value="—" />
              <NetworkStat icon={TrendingUp} label="Total items" value="957" />
              <NetworkStat icon={Shield} label="Anonymised" value="100%" />
              <NetworkStat icon={Clock} label="Avg submission" value="~30 min" />
            </div>

            {/* CTA */}
            <div className="bg-gray-900/50 border border-gray-800/50 rounded-xl p-6 mb-8">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                  <p className="text-gray-200 font-semibold mb-1">Add your server to the network</p>
                  <p className="text-gray-400 text-sm">
                    Free to join. Takes 30 seconds to get an API key. Your server submits anonymised ratios every 30 minutes.
                  </p>
                </div>
                <RegisterServerModal />
              </div>
            </div>

            {/* Benefits */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              {[
                {
                  icon: TrendingUp,
                  title: 'Better prices from day one',
                  desc: 'New servers seed from aggregated network data instead of starting with blank prices.',
                },
                {
                  icon: Globe,
                  title: 'Cross-server consistency',
                  desc: 'The same item has the same value across servers, making trading between communities fair.',
                },
                {
                  icon: Shield,
                  title: 'Privacy-first',
                  desc: 'Only anonymised ratio matrices are shared — no player data, no item names, no economy values.',
                },
              ].map(({ icon: Icon, title, desc }) => (
                <div key={title} className="bg-gray-900/30 border border-gray-800/40 rounded-xl p-4">
                  <Icon className="w-5 h-5 text-emerald-400 mb-2" />
                  <h3 className="text-sm font-semibold text-white mb-1">{title}</h3>
                  <p className="text-xs text-gray-400 leading-relaxed">{desc}</p>
                </div>
              ))}
            </div>
          </>
        )}
      </main>
      <Footer />
    </div>
  );
}
