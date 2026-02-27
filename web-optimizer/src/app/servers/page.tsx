import { Header } from '@/components/layout/header';
import { Footer } from '@/components/layout/footer';
import { ServerCard } from '@/components/servers/server-card';
import { RegisterServerModal } from '@/components/servers/register-server-modal';
import { fetchServers, hasConfiguredApiUrl } from '@/lib/api-client';

export const metadata = {
  title: 'Servers | Auto-Tune',
  description: 'Registered Auto-Tune servers and their latest submissions',
};

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
            Track server availability and inspect the most recent price-submission activity.
          </p>
          <div className="mt-4">
            <RegisterServerModal />
          </div>
        </div>

        {servers.length > 0 ? (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {servers.map((server) => (
              <ServerCard key={server.id} server={server} />
            ))}
          </div>
        ) : (
          <div className="bg-gray-900/50 border border-gray-800/50 rounded-xl p-6 mb-8">
            <p className="text-gray-200 font-medium mb-2">No servers registered yet</p>
            <p className="text-gray-400 text-sm">
              {!hasConfiguredApiUrl
                ? 'Set NEXT_PUBLIC_API_URL to point at your api-server.'
                : 'Register a server and submit price data to populate this page.'}
            </p>
            {serversResult.error && <p className="text-amber-300 text-xs mt-3">{serversResult.error}</p>}
          </div>
        )}
      </main>
      <Footer />
    </div>
  );
}
