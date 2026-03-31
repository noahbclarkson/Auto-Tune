import { Header } from '@/components/layout/header';
import { Footer } from '@/components/layout/footer';
import { ServerCard } from '@/components/servers/server-card';
import { RegisterServerModal } from '@/components/servers/register-server-modal';
import { fetchServers } from '@/lib/api-client';
import { Globe, Clock, TrendingUp, Shield } from 'lucide-react';

export const metadata = {
  title: 'Servers | Auto-Tune',
  description: 'Registered Auto-Tune servers and their latest submissions',
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

function ServerPreviewCard() {
  return (
    <div className="bg-gray-800/30 border border-gray-700/40 rounded-xl p-5 opacity-60">
      <div className="flex items-start justify-between gap-3 mb-3">
        <div>
          <h3 className="text-lg font-semibold text-white">SMP Alpha</h3>
          <p className="text-xs text-gray-500">Server ID: preview</p>
        </div>
        <span className="text-xs px-2.5 py-1 rounded-full border text-emerald-300 bg-emerald-500/10 border-emerald-500/30">Online</span>
      </div>
      <div className="grid grid-cols-2 gap-3 text-sm">
        <div className="bg-gray-950/40 rounded-lg p-3 border border-gray-800/40">
          <p className="text-gray-500 text-xs uppercase tracking-wide mb-1">Players</p>
          <p className="text-gray-100 font-medium">24</p>
        </div>
        <div className="bg-gray-950/40 rounded-lg p-3 border border-gray-800/40">
          <p className="text-gray-500 text-xs uppercase tracking-wide mb-1">Items tracked</p>
          <p className="text-gray-100 font-medium">312</p>
        </div>
      </div>
      <div className="mt-3 text-xs text-gray-400 space-y-1">
        <p>Last seen: just now</p>
        <p>Last submission: 18 minutes ago</p>
      </div>
    </div>
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
            <div className="bg-gray-900/50 border border-gray-800/50 rounded-xl p-6 mb-6">
              <p className="text-gray-200 font-semibold mb-1">No servers registered yet</p>
              <p className="text-gray-400 text-sm">
                Be the first to join the Auto-Tune network. Registration is free and takes 30 seconds — you&apos;ll get an API key to configure your server.
              </p>
              {serversResult.error && (
                <p className="text-amber-300 text-xs mt-3">
                  Could not connect to the API server: {serversResult.error}. Make sure <code className="font-mono text-amber-200">NEXT_PUBLIC_API_URL</code> is pointing at a running API server.
                </p>
              )}
            </div>

            {/* Preview card */}
            <div>
              <p className="text-xs text-gray-500 uppercase tracking-wide mb-3">What servers look like</p>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 max-w-2xl">
                <ServerPreviewCard />
              </div>
            </div>

            {/* Benefits */}
            <div className="mt-8 grid grid-cols-1 sm:grid-cols-3 gap-4 max-w-3xl">
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
