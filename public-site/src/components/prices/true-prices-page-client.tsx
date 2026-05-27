'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { AlertTriangle, Database, RefreshCw } from 'lucide-react';
import { PriceCalculator } from '@/components/prices/price-calculator';
import { TruePricesLive } from '@/components/prices/true-prices-live';
import { fetchTruePrices, hasConfiguredApiUrl, type TruePricesResponse } from '@/lib/api-client';

type LoadState =
  | { status: 'loading'; data: null; error: null }
  | { status: 'ready'; data: TruePricesResponse; error: null }
  | { status: 'empty'; data: TruePricesResponse | null; error: null }
  | { status: 'error'; data: null; error: string };

function OfflineState({ error, onRetry }: { error: string; onRetry: () => void }) {
  return (
    <div className="rounded-xl border border-amber-800/40 bg-amber-950/15 p-6">
      <div className="flex items-start gap-3">
        <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-amber-400" />
        <div>
          <h2 className="text-base font-semibold text-amber-200">Price API unavailable</h2>
          <p className="mt-2 max-w-2xl text-sm leading-relaxed text-amber-100/75">
            This page only shows live true prices when the Rust price API is reachable. No
            simulated table is shown here because admins should be able to tell immediately
            whether network data is live.
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
      <div className="flex items-start gap-3">
        <Database className="mt-0.5 h-5 w-5 shrink-0 text-gray-500" />
        <div>
          <h2 className="text-base font-semibold text-white">No solved prices yet</h2>
          <p className="mt-2 max-w-2xl text-sm leading-relaxed text-gray-400">
            The API is reachable, but it has not produced a true-price snapshot. Prices appear
            after registered servers submit ratio matrices and the solver has enough coverage.
          </p>
          <div className="mt-4 flex flex-wrap gap-3">
            <Link
              href="/servers"
              className="inline-flex items-center rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-emerald-500"
            >
              View servers
            </Link>
            <Link
              href="/trust"
              className="inline-flex items-center rounded-lg border border-gray-700 px-4 py-2 text-sm font-medium text-gray-300 transition-colors hover:border-gray-600 hover:text-white"
            >
              Trust model
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}

export function TruePricesPageClient() {
  const [state, setState] = useState<LoadState>({ status: 'loading', data: null, error: null });

  async function load() {
    setState({ status: 'loading', data: null, error: null });
    const result = await fetchTruePrices();

    if (result.error || !result.data) {
      setState({ status: 'error', data: null, error: result.error ?? 'No response from API' });
      return;
    }

    if (result.data.prices.length === 0) {
      setState({ status: 'empty', data: result.data, error: null });
      return;
    }

    setState({ status: 'ready', data: result.data, error: null });
  }

  useEffect(() => {
    load();
  }, []);

  return (
    <main className="mx-auto max-w-5xl px-4 py-12 sm:px-6 lg:px-8">
      <div className="mb-8">
        <p className="mb-2 text-xs font-medium uppercase tracking-widest text-emerald-400">Network Data</p>
        <h1 className="mb-3 text-3xl font-bold text-white sm:text-4xl">True Prices</h1>
        <p className="max-w-3xl text-sm leading-relaxed text-gray-400 sm:text-base">
          Auto-Tune combines opt-in server ratio matrices and solves a best-fit item value graph.
          These values are network references, not replacements for your local server economy.
        </p>
      </div>

      <div className="mb-8 rounded-xl border border-gray-800 bg-gray-900/45 p-5">
        <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-emerald-400">
          What this page can tell you
        </h2>
        <p className="text-sm leading-relaxed text-gray-300">
          The solver compares item-to-item ratios, filters weak coverage, and labels confidence.
          Use it to seed new servers or sanity-check local prices. Use the bundled plugin dashboard
          for your actual live economy.
        </p>
        <Link href="/trust" className="mt-3 inline-flex text-sm font-medium text-emerald-400 hover:text-emerald-300">
          Read the trust model
        </Link>
      </div>

      {state.status === 'loading' && (
        <div className="flex items-center justify-center rounded-xl border border-gray-800 bg-gray-900/40 py-16">
          <RefreshCw className="h-6 w-6 animate-spin text-gray-600" />
        </div>
      )}

      {state.status === 'error' && <OfflineState error={state.error} onRetry={load} />}
      {state.status === 'empty' && <EmptyState />}

      {state.status === 'ready' && (
        <>
          <TruePricesLive prices={state.data.prices} />
          {state.data.last_updated && (
            <p className="mb-8 text-xs text-gray-500">
              Last solver run: {new Date(state.data.last_updated).toLocaleString()}
            </p>
          )}
        </>
      )}

      <div className="mt-8">
        <PriceCalculator />
      </div>
    </main>
  );
}
