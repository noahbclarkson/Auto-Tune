'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, Clock, Globe, RefreshCw, Shield, TrendingUp } from 'lucide-react';
import { fetchServers, hasConfiguredApiUrl, type ManagedServer } from '@/lib/api-client';
import { RegisterServerModal } from '@/components/servers/register-server-modal';
import { ServerCard } from '@/components/servers/server-card';

type LoadState =
  | { status: 'loading'; servers: ManagedServer[]; error: null }
  | { status: 'ready'; servers: ManagedServer[]; error: null }
  | { status: 'error'; servers: ManagedServer[]; error: string };

function NetworkStat({ icon: Icon, label, value }: { icon: typeof Globe; label: string; value: string }) {
  return (
    <div className="flex items-center gap-3 rounded-lg border border-gray-800/60 bg-gray-900/45 px-4 py-3">
      <Icon className="h-5 w-5 shrink-0 text-emerald-400" />
      <div>
        <p className="text-xs uppercase tracking-wide text-gray-500">{label}</p>
        <p className="text-sm font-semibold text-white">{value}</p>
      </div>
    </div>
  );
}

function OfflineState({ error, onRetry }: { error: string; onRetry: () => void }) {
  return (
    <div className="rounded-xl border border-amber-800/40 bg-amber-950/15 p-6">
      <div className="flex items-start gap-3">
        <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-amber-400" />
        <div>
          <h2 className="text-base font-semibold text-amber-200">Server registry unavailable</h2>
          <p className="mt-2 max-w-2xl text-sm leading-relaxed text-amber-100/75">
            The public site can still explain the network, but the live registry needs the Rust
            price API. No sample servers are shown here because they are easy to mistake for real
            network activity.
          </p>
          <p className="mt-2 text-xs text-amber-200/70">
            Current error: <span className="font-mono">{error}</span>
          </p>
          {!hasConfiguredApiUrl && (
            <p className="mt-2 text-xs text-amber-200/70">
              Set <code className="rounded bg-amber-950/60 px-1 py-0.5">NEXT_PUBLIC_API_URL</code>
              for deployed builds.
            </p>
          )}
          <div className="mt-4 flex flex-wrap gap-3">
            <button
              type="button"
              onClick={onRetry}
              className="inline-flex items-center gap-2 rounded-lg border border-amber-700/50 bg-amber-900/20 px-4 py-2 text-sm font-medium text-amber-200 transition-colors hover:bg-amber-900/35"
            >
              <RefreshCw className="h-4 w-4" />
              Retry
            </button>
            <Link
              href="/api-docs"
              className="inline-flex items-center rounded-lg border border-gray-700 px-4 py-2 text-sm font-medium text-gray-300 transition-colors hover:border-gray-600 hover:text-white"
            >
              API docs
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}

function EmptyState() {
  return (
    <div className="rounded-xl border border-gray-800 bg-gray-900/45 p-6">
      <h2 className="text-base font-semibold text-white">No registered servers yet</h2>
      <p className="mt-2 max-w-2xl text-sm leading-relaxed text-gray-400">
        The API is reachable, but no server has registered with this price network. Registering
        returns an API key that the plugin can use for heartbeats and price submissions.
      </p>
      <div className="mt-4 flex flex-wrap gap-3">
        <RegisterServerModal />
        <Link
          href="/trust"
          className="inline-flex items-center rounded-lg border border-gray-700 px-4 py-2 text-sm font-medium text-gray-300 transition-colors hover:border-gray-600 hover:text-white"
        >
          Review safeguards
        </Link>
      </div>
    </div>
  );
}

export function ServersPageClient() {
  const [state, setState] = useState<LoadState>({ status: 'loading', servers: [], error: null });

  async function load() {
    setState({ status: 'loading', servers: [], error: null });
    const result = await fetchServers();
    if (result.error || !result.data) {
      setState({ status: 'error', servers: [], error: result.error ?? 'No response from API' });
      return;
    }
    setState({ status: 'ready', servers: result.data, error: null });
  }

  useEffect(() => {
    load();
  }, []);

  const stats = useMemo(() => {
    const servers = state.servers;
    const itemCount = servers.reduce((acc, server) => acc + (server.last_submission_item_count ?? 0), 0);
    const lastSeenMs = servers
      .map((server) => new Date(server.last_seen).getTime())
      .filter((value) => Number.isFinite(value));

    return {
      servers: servers.length,
      items: itemCount,
      lastSeen: lastSeenMs.length ? new Date(Math.max(...lastSeenMs)).toLocaleString() : 'No heartbeat',
    };
  }, [state.servers]);

  return (
    <main className="mx-auto max-w-6xl px-4 py-12 sm:px-6 lg:px-8">
      <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="mb-2 text-xs font-medium uppercase tracking-widest text-emerald-400">Price Network</p>
          <h1 className="mb-3 text-3xl font-bold text-white sm:text-4xl">Registered Servers</h1>
          <p className="max-w-3xl text-sm leading-relaxed text-gray-400 sm:text-base">
            Servers opt in by registering with the Rust API and configuring the plugin price
            reporter. The network stores server-level heartbeats and item ratio submissions, not
            player identities.
          </p>
        </div>
        <RegisterServerModal />
      </div>

      {state.status === 'loading' && (
        <div className="flex items-center justify-center rounded-xl border border-gray-800 bg-gray-900/40 py-16">
          <RefreshCw className="h-6 w-6 animate-spin text-gray-600" />
        </div>
      )}

      {state.status === 'error' && <OfflineState error={state.error} onRetry={load} />}

      {state.status === 'ready' && state.servers.length === 0 && <EmptyState />}

      {state.status === 'ready' && state.servers.length > 0 && (
        <>
          <div className="mb-8 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <NetworkStat icon={Globe} label="Registered servers" value={String(stats.servers)} />
            <NetworkStat icon={TrendingUp} label="Submitted items" value={String(stats.items)} />
            <NetworkStat icon={Shield} label="Player data" value="Not collected" />
            <NetworkStat icon={Clock} label="Latest heartbeat" value={stats.lastSeen} />
          </div>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            {state.servers.map((server) => (
              <ServerCard key={server.id} server={server} />
            ))}
          </div>
        </>
      )}

      <section className="mt-10 rounded-xl border border-gray-800 bg-gray-900/35 p-5">
        <h2 className="text-base font-semibold text-white">How a server joins</h2>
        <div className="mt-4 grid gap-4 text-sm text-gray-400 md:grid-cols-3">
          <div>
            <p className="mb-1 font-medium text-gray-200">1. Register</p>
            <p>Generate a server ID and API key from this site or a self-hosted API instance.</p>
          </div>
          <div>
            <p className="mb-1 font-medium text-gray-200">2. Configure plugin</p>
            <p>Set the price reporter URL, server ID, API key, and report interval in config.yml.</p>
          </div>
          <div>
            <p className="mb-1 font-medium text-gray-200">3. Submit ratios</p>
            <p>The plugin sends anonymized item ratios and heartbeats on its normal schedule.</p>
          </div>
        </div>
      </section>
    </main>
  );
}
