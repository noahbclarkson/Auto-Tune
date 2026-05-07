import { Header } from '@/components/layout/header';
import { Footer } from '@/components/layout/footer';
import { Copy, Check, Key, Shield, Globe, AlertTriangle } from 'lucide-react';
import Link from 'next/link';

export const metadata = {
  title: 'API Reference | Auto-Tune',
  description:
    'HTTP API reference for Auto-Tune cross-server price submission and true-price aggregation. Server authentication, rate limits, and endpoint documentation.',
  openGraph: {
    title: 'API Reference | Auto-Tune',
    description: 'HTTP API reference for Auto-Tune cross-server price submission and true-price aggregation.',
    images: [{ url: '/og-image.png', width: 1200, height: 630 }],
  },
  twitter: { card: 'summary_large_image', images: ['/og-image.png'] },
};

function Endpoint({
  method,
  path,
  description,
  params,
  response,
  notes,
  accent = 'emerald',
}: {
  method: string;
  path: string;
  description: string;
  params?: { name: string; type: string; required: boolean; description: string }[];
  response?: { status: string; body: string };
  notes?: string[];
  accent?: string;
}) {
  const accentColors: Record<string, { method: string; badge: string; border: string; bg: string }> = {
    emerald: { method: 'text-emerald-400 bg-emerald-950 border-emerald-800/60', badge: 'bg-emerald-950 border border-emerald-800/60', border: 'border-emerald-800/30', bg: 'bg-emerald-950/10' },
    sky: { method: 'text-sky-400 bg-sky-950 border-sky-800/60', badge: 'bg-sky-950 border border-sky-800/60', border: 'border-sky-800/30', bg: 'bg-sky-950/10' },
    amber: { method: 'text-amber-400 bg-amber-950 border-amber-800/60', badge: 'bg-amber-950 border border-amber-800/60', border: 'border-amber-800/30', bg: 'bg-amber-950/10' },
    rose: { method: 'text-rose-400 bg-rose-950 border-rose-800/60', badge: 'bg-rose-950 border border-rose-800/60', border: 'border-rose-800/30', bg: 'bg-rose-950/10' },
  };
  const c = accentColors[accent];

  return (
    <div className={`rounded-xl border ${c.border} ${c.bg} overflow-hidden mb-4`}>
      {/* Method + path */}
      <div className="flex items-center gap-3 px-5 py-3 border-b border-gray-800/50 bg-gray-900/40">
        <span className={`px-2 py-0.5 rounded text-xs font-mono font-bold ${c.method}`}>{method}</span>
        <code className="text-sm font-mono text-gray-200">{path}</code>
      </div>

      <div className="p-5">
        <p className="text-sm text-gray-300 mb-4">{description}</p>

        {params && params.length > 0 && (
          <div className="mb-4">
            <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Parameters</p>
            <div className="rounded-lg border border-gray-800 bg-gray-950/60 overflow-hidden">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-800/50">
                    <th className="px-3 py-2 text-left text-xs font-medium text-gray-500">Name</th>
                    <th className="px-3 py-2 text-left text-xs font-medium text-gray-500">Type</th>
                    <th className="px-3 py-2 text-left text-xs font-medium text-gray-500">Description</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-800/40">
                  {params.map((p) => (
                    <tr key={p.name}>
                      <td className="px-3 py-2">
                        <code className="text-xs font-mono text-sky-300">{p.name}</code>
                        {!p.required && <span className="text-gray-600 text-xs ml-1">(optional)</span>}
                      </td>
                      <td className="px-3 py-2 text-xs text-gray-500 font-mono">{p.type}</td>
                      <td className="px-3 py-2 text-xs text-gray-400">{p.description}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {response && (
          <div className="mb-4">
            <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Response</p>
            <div className="rounded-lg border border-gray-800 bg-gray-950/60 p-4">
              <div className="flex items-center gap-2 mb-2">
                <span className="text-xs font-mono text-gray-500">{response.status}</span>
              </div>
              <pre className="text-xs font-mono text-gray-300 whitespace-pre-wrap overflow-x-auto">{response.body}</pre>
            </div>
          </div>
        )}

        {notes && notes.length > 0 && (
          <div className="space-y-1.5">
            {notes.map((note, i) => (
              <div key={i} className="flex items-start gap-2 text-xs text-gray-400">
                <span className="text-gray-600 mt-0.5">→</span>
                <span>{note}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function Section({ id, title, children }: { id: string; title: string; children: React.ReactNode }) {
  return (
    <section id={id} className="mb-12">
      <h2 className="text-xl font-bold text-white mb-1">{title}</h2>
      <div className="h-px bg-gray-800 mb-6" />
      {children}
    </section>
  );
}

function AuthBox() {
  return (
    <div className="rounded-xl border border-amber-800/40 bg-amber-950/20 p-4 mb-6">
      <div className="flex items-start gap-3">
        <Key className="w-4 h-4 text-amber-400 mt-0.5 shrink-0" />
        <div>
          <p className="text-sm font-semibold text-amber-300 mb-1">Server authentication required</p>
          <p className="text-xs text-gray-400 leading-relaxed">
            All write endpoints require an <code className="text-amber-300 font-mono">Authorization: Bearer &lt;api-key&gt;</code> header.
            Register your server through <code className="text-amber-300 font-mono">POST /api/servers/register</code> to get an API key.
            Keys are shown once at registration — store them in a password manager.
          </p>
        </div>
      </div>
    </div>
  );
}

export default function ApiDocsPage() {
  return (
    <div className="min-h-screen bg-gray-950 text-white">
      <Header />
      <main className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-12">

        {/* Page heading */}
        <div className="mb-10">
          <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">Reference</p>
          <h1 className="text-3xl sm:text-4xl font-bold text-white mb-3">API Documentation</h1>
          <p className="text-gray-400 text-sm sm:text-base leading-relaxed">
            Cross-server price submission and aggregation API for Auto-Tune. Servers opt-in to
            share anonymised price ratios — used to compute globally consistent starting prices.
            All data is server-to-server; no player data is ever transmitted.
          </p>
        </div>

        <AuthBox />

        {/* Navigation */}
        <div className="flex flex-wrap gap-3 text-sm mb-10 pb-6 border-b border-gray-800">
          {[
            { id: 'overview', label: 'Overview' },
            { id: 'authentication', label: 'Authentication' },
            { id: 'servers', label: 'Server Registry' },
            { id: 'prices', label: 'Price Submission' },
            { id: 'true-prices', label: 'True Prices' },
            { id: 'exchange-rates', label: 'Exchange Rates' },
            { id: 'errors', label: 'Error Codes' },
          ].map(({ id, label }) => (
            <a key={id} href={`#${id}`} className="text-gray-400 hover:text-emerald-400 transition-colors">
              {label}
            </a>
          ))}
        </div>

        <Section id="overview" title="Overview">
          <div className="grid sm:grid-cols-2 gap-4 mb-4">
            {[
              { label: 'Base URL', value: 'https://api.autotune.gg' },
              { label: 'Auth', value: 'Authorization: Bearer <api-key>' },
              { label: 'Format', value: 'JSON' },
              { label: 'Submit interval', value: 'Every 5 min by default' },
            ].map(({ label, value }) => (
              <div key={label} className="rounded-lg border border-gray-800 bg-gray-900/40 px-4 py-3">
                <p className="text-xs text-gray-500 uppercase tracking-wider mb-1">{label}</p>
                <p className="text-sm font-mono text-white">{value}</p>
              </div>
            ))}
          </div>
          <p className="text-sm text-gray-400 leading-relaxed">
            The API is entirely optional. A server running Auto-Tune without an API key will function
            identically — prices will still adjust based on local player activity. The API adds
            cross-server price intelligence.
          </p>
        </Section>

        <Section id="authentication" title="Authentication">
          <p className="text-sm text-gray-400 mb-4 leading-relaxed">
            Each registered server gets a unique API key. Include it as a header on all requests:
          </p>
          <div className="rounded-lg border border-gray-800 bg-gray-950/60 p-4 mb-4">
            <p className="text-xs text-gray-500 mb-2 font-semibold">Request header</p>
            <code className="text-sm font-mono text-sky-300">Authorization: Bearer at_srv_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx</code>
          </div>
          <div className="flex items-start gap-2 text-xs text-gray-400">
            <Shield className="w-3.5 h-3.5 text-emerald-400 mt-0.5 shrink-0" />
            <span>Keys are hashed on storage. The API stores only a SHA-256 hash — your raw key is returned once and cannot be recovered.</span>
          </div>
        </Section>

        <Section id="servers" title="Server Registry">
          <p className="text-sm text-gray-400 mb-4 leading-relaxed">
            Register your server to get an API key and appear on the{' '}
            <Link href="/servers" className="text-emerald-400 hover:underline">public servers page</Link>.
            Each server is identified by a unique UUID generated at registration.
          </p>

          <Endpoint
            method="POST"
            path="/api/servers/register"
            description="Register a new server and receive an API key."
            params={[
              { name: 'name', type: 'string', required: true, description: 'Display name for your server' },
            ]}
            response={{
              status: '201 Created',
              body: `{
  "server_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "api_key": "64_hex_chars"
}`,
            }}
            notes={[
              'The api_key is returned ONCE at registration. Store it securely — it cannot be recovered.',
              'Server name is shown publicly on /servers. Use anything you like.',
              'Rate limit: 10 registrations per minute per IP, with a burst of 10.',
            ]}
            accent="emerald"
          />

          <Endpoint
            method="GET"
            path="/api/servers"
            description="List all registered servers and their latest heartbeat timestamps."
            response={{
              status: '200 OK',
              body: `[
  {
    "id": "a1b2c3d4-...",
    "name": "My SMP Server",
    "player_count": 50,
    "created_at": "2026-03-28T12:00:00Z",
    "last_seen": "2026-03-28T23:45:00Z",
    "last_submission_at": "2026-03-28T23:40:00Z",
    "last_submission_item_count": 48,
    "plugin_version": "2.0.0"
  }
]`,
            }}
            accent="sky"
          />

          <Endpoint
            method="POST"
            path="/api/servers/:id/heartbeat"
            description="Send an authenticated heartbeat to keep server status and metadata fresh. Send every 5 minutes."
            params={[
              { name: 'player_count', type: 'integer', required: false, description: 'Current online player count' },
              { name: 'plugin_version', type: 'string', required: false, description: 'Auto-Tune plugin version, if available' },
            ]}
            response={{ status: '200 OK', body: '{ "ok": true, "server_id": "a1b2c3d4-...", "last_seen": "2026-03-28T23:45:00Z" }' }}
            notes={['Requires Authorization: Bearer <api-key>. The key must belong to the server UUID in the path.', 'If no heartbeat or price submission arrives recently, the server appears stale/offline in ecosystem views.']}
            accent="sky"
          />
        </Section>

        <Section id="prices" title="Price Submission">
          <p className="text-sm text-gray-400 mb-4 leading-relaxed">
            Submit anonymised price ratios periodically (default: every 5 minutes). Only the <em>relative</em>{' '}
            ratios are sent — e.g. "Diamond is 100× the price of Dirt" rather than absolute values.
            This means your server&apos;s base prices stay private.
          </p>

          <div className="rounded-xl border border-sky-800/40 bg-sky-950/10 p-4 mb-4">
            <div className="flex items-start gap-3">
              <Globe className="w-4 h-4 text-sky-400 mt-0.5 shrink-0" />
              <div>
                <p className="text-sm font-semibold text-sky-300 mb-1">What data is shared?</p>
                <p className="text-xs text-gray-400 leading-relaxed">
                  Only: server ID, item names, an item-to-item ratio matrix, and current player count.
                  No player names, no transaction amounts, no inventory data.
                </p>
              </div>
            </div>
          </div>

          <Endpoint
            method="POST"
            path="/api/servers/:id/prices"
            description="Submit this server's current item ratio matrix."
            params={[
              { name: 'item_names', type: 'string[]', required: true, description: 'Ordered item names for the matrix axes' },
              { name: 'ratio_matrix', type: 'number[][]', required: true, description: 'Square reciprocal matrix where matrix[i][j] = item_i price ÷ item_j price' },
              { name: 'player_count', type: 'integer', required: true, description: 'Current online player count for freshness/metadata' },
            ]}
            response={{
              status: '200 OK',
              body: `{
  "success": true,
  "items_processed": 3
}`,
            }}
            notes={[
              'The authenticated API key must match the server UUID in the path.',
              'The ratio matrix is validated for shape, positivity, reciprocal consistency, and transitivity before storage.',
              'Recomputation runs asynchronously after submission and uses only fresh submissions by default (STALE_THRESHOLD_HOURS=24).',
              'Rate limit: 6 submissions per minute per IP, with a burst of 6.',
            ]}
            accent="sky"
          />

          <div className="rounded-lg border border-gray-800 bg-gray-900/40 p-4 mb-4">
            <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Ratios format detail</p>
            <pre className="text-xs font-mono text-gray-300 whitespace-pre-wrap">{`"item_names": ["minecraft:dirt", "minecraft:cobblestone", "minecraft:diamond"],
"ratio_matrix": [
  [1.0,   0.5,  0.001],
  [2.0,   1.0,  0.002],
  [1000.0, 500.0, 1.0]
],
"player_count": 24`}</pre>
            <p className="text-xs text-gray-500 mt-2">
              Matrix values are plain price ratios, not log values. The solver converts ratios to log-space internally for outlier filtering and least-squares solving.
            </p>
          </div>
        </Section>

        <Section id="true-prices" title="True Prices">
          <p className="text-sm text-gray-400 mb-4 leading-relaxed">
            After multiple servers submit data, the API solves a constrained least-squares system
            over the ratio graph. The result is a globally consistent set of item prices anchored
            to a reference item (defaults to dirt = 0.10, configurable on the API server).
          </p>

          <Endpoint
            method="GET"
            path="/api/prices/true"
            description="Fetch the latest computed true prices across all items."
            response={{
              status: '200 OK',
              body: `{
  "prices": [
    {
      "item": "minecraft:diamond",
      "price": 250.0,
      "confidence": 0.92,
      "servers": 8,
      "anchored": true,
      "last_updated": "2026-03-28T23:40:00Z"
    }
  ],
  "last_updated": "2026-03-28T23:40:00Z"
}`,
            }}
            notes={[
              'Confidence reflects the variance across servers — items traded on many servers score higher.',
              'Prices are recomputed asynchronously after accepted submissions, using the latest fresh submission per server.',
              'New servers can seed their economy from these prices rather than starting from scratch.',
            ]}
            accent="emerald"
          />
        </Section>

        <Section id="exchange-rates" title="Exchange Rates">
          <p className="text-sm text-gray-400 mb-4 leading-relaxed">
            Each server&apos;s local prices are expressed as a multiplier relative to the true-price baseline.
            A rate of 1.30× means the server&apos;s economy runs 30% "hotter" than the global average.
            This is displayed publicly at{' '}
            <Link href="/exchange-rates" className="text-emerald-400 hover:underline">/exchange-rates</Link>.
          </p>

          <Endpoint
            method="GET"
            path="/api/servers/exchange-rates"
            description="Fetch per-server exchange rates vs the global true-price baseline."
            response={{
              status: '200 OK',
              body: `{
  "base": "true_prices",
  "rates": [
    {
      "server_id": "a1b2c3d4-...",
      "name": "My SMP Server",
      "rate": 1.23,
      "player_count": 34,
      "last_seen": "2026-03-28T23:45:00Z"
    }
  ]
}`,
            }}
            accent="amber"
          />
        </Section>

        <Section id="errors" title="Error Codes">
          <p className="text-sm text-gray-400 mb-4 leading-relaxed">
            All errors return a JSON body with an <code className="text-sky-300 font-mono">error</code> field.
            HTTP status codes follow REST conventions; there is no separate machine-readable error code field yet.
          </p>

          <div className="rounded-xl border border-gray-800 bg-gray-900/40 overflow-hidden mb-4">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-800/50">
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500">Status</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500">Code</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500">Meaning</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-800/40">
                {[
                  { status: '400', code: 'INVALID_REQUEST', meaning: 'Malformed JSON or missing required fields' },
                  { status: '401', code: 'UNAUTHORIZED', meaning: 'Missing or invalid Authorization header (expected: Bearer <key>)' },
                  { status: '403', code: 'FORBIDDEN', meaning: 'Valid key but action not permitted (e.g. wrong server ID in path)' },
                  { status: '422', code: 'UNPROCESSABLE', meaning: 'Valid JSON but failed validation (e.g. negative ratio value)' },
                  { status: '429', code: 'RATE_LIMITED', meaning: 'Too many submissions. Check Retry-After header.' },
                  { status: '500', code: 'INTERNAL_ERROR', meaning: 'API server error. Check status page.' },
                ].map(({ status, code, meaning }) => (
                  <tr key={code}>
                    <td className="px-4 py-3 font-mono text-xs text-sky-300">{status}</td>
                    <td className="px-4 py-3 font-mono text-xs text-amber-300">{code}</td>
                    <td className="px-4 py-3 text-xs text-gray-400">{meaning}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="flex items-start gap-2 text-xs text-gray-400">
            <AlertTriangle className="w-3.5 h-3.5 text-amber-400 mt-0.5 shrink-0" />
            <span>
              <strong className="text-gray-300">Rate limits:</strong> Price submission: 6 req/min per IP. Server registration: 10 req/min per IP.
              True prices / exchange rates are public reads and do not require an API key.
            </span>
          </div>
        </Section>

        {/* Bottom nav */}
        <div className="mt-8 pt-6 border-t border-gray-800 flex flex-wrap justify-between gap-4 text-sm">
          <Link href="/true-prices" className="text-gray-400 hover:text-emerald-400 transition-colors">
            ← True Prices
          </Link>
          <Link href="/servers" className="text-gray-400 hover:text-emerald-400 transition-colors">
            Register a server →
          </Link>
        </div>
      </main>
      <Footer />
    </div>
  );
}
