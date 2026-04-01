'use client';

import { useState } from 'react';
import { WifiOff, ChevronDown, ChevronUp, RefreshCw, X } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';

interface ApiErrorBannerProps {
  /** Custom message to display (optional — defaults to generic) */
  message?: string;
  /** Whether to show the banner even when dismissed (e.g., persistent error) */
  persistent?: boolean;
  /** Called when user clicks retry */
  onRetry?: () => void;
  /** The API base URL being used (to show in troubleshooting) */
  apiBase?: string;
}

export function ApiErrorBanner({ message, persistent = false, onRetry, apiBase }: ApiErrorBannerProps) {
  const [dismissed, setDismissed] = useState(false);
  const [detailsOpen, setDetailsOpen] = useState(false);

  // Must come after all hooks
  if (dismissed && !persistent) return null;

  const isLocal = !apiBase || apiBase === '' || apiBase.includes('localhost');

  return (
    <Card className="border-amber-700/40 bg-amber-950/20">
      <CardContent className="p-4">
        {/* Top row */}
        <div className="flex items-start gap-3">
          <div className="shrink-0 mt-0.5">
            <WifiOff className="h-5 w-5 text-amber-400" />
          </div>
          <div className="flex-1 min-w-0">
            <div className="flex items-start justify-between gap-2">
              <p className="text-sm font-semibold text-amber-200">
                {message ?? 'Unable to connect to the server'}
              </p>
              <div className="flex items-center gap-1 shrink-0">
                {onRetry && (
                  <button
                    onClick={onRetry}
                    className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-amber-500/20 hover:bg-amber-500/30 border border-amber-500/30 text-amber-300 text-xs font-medium transition-colors"
                  >
                    <RefreshCw className="h-3 w-3" />
                    Retry
                  </button>
                )}
                {!persistent && (
                  <button
                    onClick={() => setDismissed(true)}
                    className="p-1 rounded text-amber-500/40 hover:text-amber-400/70 transition-colors"
                    aria-label="Dismiss"
                  >
                    <X className="h-4 w-4" />
                  </button>
                )}
              </div>
            </div>
            <p className="text-xs text-amber-300/70 mt-0.5">
              The web dashboard can&apos;t reach the server. Your trades in-game are unaffected.
            </p>
          </div>
        </div>

        {/* Troubleshooting */}
        <button
          onClick={() => setDetailsOpen((v) => !v)}
          className="flex items-center gap-1.5 mt-2.5 text-xs text-amber-400/70 hover:text-amber-300 transition-colors"
        >
          {detailsOpen ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
          {detailsOpen ? 'Hide' : 'Show'} troubleshooting steps
        </button>

        {detailsOpen && (
          <div className="mt-3 space-y-2 text-xs text-amber-200/80">
            <ol className="space-y-2 ml-1">
              <li className="flex gap-2">
                <span className="font-semibold text-amber-400 shrink-0">1.</span>
                <span>
                  <strong className="text-amber-300">Confirm the server is running.</strong> You need to be online or have the server&apos;s web dashboard port ({isLocal ? '8989' : 'the server port'}) accessible.
                </span>
              </li>
              <li className="flex gap-2">
                <span className="font-semibold text-amber-400 shrink-0">2.</span>
                <span>
                  <strong className="text-amber-300">Check the web-server config.</strong> In{' '}
                  <code className="font-mono text-amber-200 bg-amber-950/50 px-1 py-0.5 rounded">config.yml</code>,
                  verify{' '}
                  <code className="font-mono text-amber-200 bg-amber-950/50 px-1 py-0.5 rounded">web-server.enabled: true</code>{' '}
                  and{' '}
                  <code className="font-mono text-amber-200 bg-amber-950/50 px-1 py-0.5 rounded">web-server.port: 8989</code>.
                </span>
              </li>
              <li className="flex gap-2">
                <span className="font-semibold text-amber-400 shrink-0">3.</span>
                <span>
                  <strong className="text-amber-300">Check the address.</strong> This dashboard is bundled with your server and connects to{' '}
                  <code className="font-mono text-amber-200 bg-amber-950/50 px-1 py-0.5 rounded">
                    {isLocal
                      ? 'http://localhost:8989'
                      : apiBase}
                  </code>
                  .
                  {isLocal
                    ? ' Make sure you&apos;re viewing the dashboard on the same machine as the server, or use a reverse proxy.'
                    : ' Make sure the server&apos;s port is open and not blocked by a firewall.'}
                </span>
              </li>
              <li className="flex gap-2">
                <span className="font-semibold text-amber-400 shrink-0">4.</span>
                <span>
                  <strong className="text-amber-300">Check the server console.</strong> Look for{' '}
                  <code className="font-mono text-amber-200 bg-amber-950/50 px-1 py-0.5 rounded">Auto-Tune web server started on port 8989</code>{' '}
                  at startup.
                </span>
              </li>
              <li className="flex gap-2">
                <span className="font-semibold text-amber-400 shrink-0">5.</span>
                <span>
                  <strong className="text-amber-300">Restart if needed.</strong> Run{' '}
                  <code className="font-mono text-amber-200 bg-amber-950/50 px-1 py-0.5 rounded">/at admin reload</code>{' '}
                  in-game to reload the web server config without a full server restart.
                </span>
              </li>
            </ol>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
