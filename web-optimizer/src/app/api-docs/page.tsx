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
            All write endpoints require an <code className="text-amber-300 font-mono">X-API-Key</code> header.
            Register your server at <Link href="/servers" className="text-amber-300 hover:underline">/servers</Link> to get an API key.
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
              { label: 'Base URL', value: 'https://api.autotune.dev/v1' },
              { label: 'Auth', value: 'X-API-Key header' },
              { label: 'Format', value: 'JSON' },
              { label: 'Submit interval', value: 'Every 5 min (1× per tick)' },
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
            <code className="text-sm font-mono text-sky-300">X-API-Key: at_srv_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx</code>
          </div>
          <div className="flex items-start gap-2 text-xs text-gray-400">
            <Shield className="w-3.5 h-3.5 text-emerald-400 mt-0.5 shrink-0" />
            <span>Keys are hashed on storage. The API only stores a bcrypt hash — your raw key is never stored server-side and cannot be recovered.</span>
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
            path="/v1/servers"
            description="Register a new server and receive an API key."
            params={[
              { name: 'name', type: 'string', required: true, description: 'Display name for your server (shown on /servers page)' },
              { name: 'player_count', type: 'integer', required: true, description: 'Approximate player capacity (used for weighting submissions)' },
            ]}
            response={{
              status: '201 Created',
              body: `{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "api_key": "at_srv_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "name": "My SMP Server",
  "player_count": 50,
  "registered_at": "2026-03-28T12:00:00Z"
}`,
            }}
            notes={[
              'The api_key is returned ONCE at registration. Store it securely — it cannot be recovered.',
              'Server name is shown publicly on /servers. Use anything you like.',
              'Rate limit: 1 registration per IP per hour.',
            ]}
            accent="emerald"
          />

          <Endpoint
            method="GET"
            path="/v1/servers"
            description="List all registered servers and their latest heartbeat timestamps."
            response={{
              status: '200 OK',
              body: `{
  "servers": [
    {
      "id": "a1b2c3d4-...",
      "name": "My SMP Server",
      "player_count": 50,
      "last_seen": "2026-03-28T23:45:00Z",
      "submission_count": 1247
    }
  ],
  "total": 12
}`,
            }}
            accent="sky"
          />

          <Endpoint
            method="POST"
            path="/v1/servers/:id/heartbeat"
            description="Send a heartbeat to keep your server's status as Online on the /servers page. Send every 5 minutes (once per tick)."
            params={[
              { name: 'player_count', type: 'integer', required: false, description: 'Current online player count (used for server health monitoring)' },
            ]}
            response={{ status: '200 OK', body: '{ "ok": true }' }}
            notes={['If no heartbeat is received within 1 hour, the server is marked as Offline.', 'No API key required for heartbeat — uses the server UUID in the URL path.']}
            accent="sky"
          />
        </Section>

        <Section id="prices" title="Price Submission">
          <p className="text-sm text-gray-400 mb-4 leading-relaxed">
            Submit anonymised price ratios once per tick (every 5 minutes). Only the <em>relative</em>{' '}
            ratios are sent — e.g. "Diamond is 100× the price of Iron Ingot" rather than absolute values.
            This means your server&apos;s base prices stay private.
          </p>

          <div className="rounded-xl border border-sky-800/40 bg-sky-950/10 p-4 mb-4">
            <div className="flex items-start gap-3">
              <Globe className="w-4 h-4 text-sky-400 mt-0.5 shrink-0" />
              <div>
                <p className="text-sm font-semibold text-sky-300 mb-1">What data is shared?</p>
                <p className="text-xs text-gray-400 leading-relaxed">
                  Only: server ID, item pairs with their base price ratios, tick timestamp, trade volume.
                  No player names, no transaction amounts, no inventory data.
                </p>
              </div>
            </div>
          </div>

          <Endpoint
            method="POST"
            path="/v1/prices"
            description="Submit a batch of price ratios for this server's current tick."
            params={[
              { name: 'server_id', type: 'string (UUID)', required: true, description: 'Your server UUID from registration' },
              { name: 'tick', type: 'integer', required: true, description: 'The current market tick number (auto-incremented by the plugin)' },
              { name: 'ratios', type: 'array', required: true, description: 'Array of { base_item, target_item, ratio, volume } objects — see format below' },
              { name: 'total_volume', type: 'number', required: true, description: 'Total trade volume for this tick across all items' },
            ]}
            response={{
              status: '201 Created',
              body: `{
  "submission_id": "sub_abc123",
  "ratios_received": 48,
  "outliers_filtered": 0,
  "next_tick": 8924
}`,
            }}
            notes={[
              'Submit exactly once per tick. Duplicate submissions within the same tick window are deduplicated.',
              'Minimum 5 servers must be active before outlier filtering activates (3σ log-space detection).',
              'Submission is fire-and-forget: the API queues it for processing and returns immediately.',
              'If your server misses a tick, it\'s fine — the solver handles gaps gracefully.',
            ]}
            accent="sky"
          />

          <div className="rounded-lg border border-gray-800 bg-gray-900/40 p-4 mb-4">
            <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Ratios format detail</p>
            <pre className="text-xs font-mono text-gray-300 whitespace-pre-wrap">{`"ratios": [
  { "base_item": "minecraft:diamond",  "target_item": "minecraft:iron_ingot", "ratio": 10.0,  "volume": 250 },
  { "base_item": "minecraft:diamond",  "target_item": "minecraft:gold_ingot", "ratio": 5.0,   "volume": 80 },
  { "base_item": "minecraft:iron_ingot","target_item": "minecraft:coal",      "ratio": 0.5,   "volume": 1200 }
]`}</pre>
            <p className="text-xs text-gray-500 mt-2">
              Ratio = base_item price ÷ target_item price. Ratios should be expressed in log-space by the plugin before submission to reduce outlier impact.
            </p>
          </div>
        </Section>

        <Section id="true-prices" title="True Prices">
          <p className="text-sm text-gray-400 mb-4 leading-relaxed">
            After multiple servers submit data, the API solves a constrained least-squares system
            over the ratio graph. The result is a globally consistent set of item prices anchored
            to a reference item (defaults to iron_ingot = 1.0).
          </p>

          <Endpoint
            method="GET"
            path="/v1/true-prices"
            description="Fetch the latest computed true prices across all items."
            response={{
              status: '200 OK',
              body: `{
  "prices": [
    { "item": "minecraft:diamond",       "price": 250.0,  "confidence": 0.92 },
    { "item": "minecraft:iron_ingot",    "price": 1.0,    "confidence": 1.0  },
    { "item": "minecraft:gold_ingot",   "price": 5.0,    "confidence": 0.87 }
  ],
  "anchored_to": "minecraft:iron_ingot",
  "solver_iterations": 23,
  "total_servers": 8,
  "total_submissions": 1847,
  "outliers_filtered": 12,
  "last_updated": "2026-03-28T23:40:00Z"
}`,
            }}
            notes={[
              'Confidence reflects the variance across servers — items traded on many servers score higher.',
              'Prices are updated every 30 minutes. The web dashboard at /true-prices shows the live data.',
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
            path="/v1/exchange-rates"
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
            HTTP status codes follow REST conventions.
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
                  { status: '401', code: 'UNAUTHORIZED', meaning: 'Missing or invalid X-API-Key' },
                  { status: '403', code: 'FORBIDDEN', meaning: 'Valid key but action not permitted (e.g. wrong server ID in path)' },
                  { status: '422', code: 'UNPROCESSABLE', meaning: 'Valid JSON but failed validation (e.g. negative ratio value)' },
                  { status: '429', code: 'RATE_LIMITED', meaning: 'Too many submissions. Check Retry-After header.' },
                  { status: '500', code: 'INTERNAL_ERROR', meaning: 'API server error. Check status page.' },
                  { status: '503', code: 'UNAVAILABLE', meaning: 'Solver is down or still initialising (try again in 5 min)' },
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
              <strong className="text-gray-300">Rate limits:</strong> Submit endpoint: 1 req/tick per server. Registry: 1/IP/hour.
              True prices / exchange rates: 60 req/min per IP (public, no key required).
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
