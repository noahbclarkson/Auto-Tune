'use client';

import { useState } from 'react';
import Link from 'next/link';
import { ArrowLeft, Copy, Check, ExternalLink } from 'lucide-react';

type BadgeStyle = 'compact' | 'standard' | 'detailed';
type HealthStatus = 'healthy' | 'moderate' | 'unhealthy';

function HealthBadgePreview({
  serverName,
  style,
  status,
}: {
  serverName: string;
  style: BadgeStyle;
  status: HealthStatus;
}) {
  const colors = {
    healthy: { bg: 'bg-emerald-600', text: 'text-emerald-400', border: 'border-emerald-600', dot: 'bg-emerald-400' },
    moderate: { bg: 'bg-amber-600', text: 'text-amber-400', border: 'border-amber-600', dot: 'bg-amber-400' },
    unhealthy: { bg: 'bg-red-600', text: 'text-red-400', border: 'border-red-600', dot: 'bg-red-400' },
  };
  const c = colors[status];

  if (style === 'compact') {
    return (
      <div className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full border ${c.border} bg-gray-900/80 text-xs font-mono`}>
        <span className={`w-1.5 h-1.5 rounded-full ${c.dot} animate-pulse-slow`} />
        <span className={`${c.text} font-semibold`}>
          {serverName || 'My Server'}
        </span>
        <span className="text-gray-400">·</span>
        <span className={`${c.text}`}>
          {status === 'healthy' ? 'Healthy' : status === 'moderate' ? 'Moderate' : 'Unhealthy'}
        </span>
      </div>
    );
  }

  if (style === 'standard') {
    return (
      <div className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-lg border ${c.border} bg-gray-900/80 text-xs font-mono`}>
        <span className={`w-2 h-2 rounded-full ${c.dot} animate-pulse-slow`} />
        <span className="text-gray-300 font-semibold">{serverName || 'My Server'}</span>
        <span className="text-gray-500">—</span>
        <span className={c.text}>
          {status === 'healthy' ? '✓ Economy Healthy' : status === 'moderate' ? '⚠ Economy Moderate' : '✕ Economy Unhealthy'}
        </span>
        <ExternalLink className="w-3 h-3 text-gray-500 ml-1" />
      </div>
    );
  }

  // detailed
  return (
    <div className={`max-w-xs rounded-xl border ${c.border} bg-gray-900/90 p-3 font-mono text-xs`}>
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2">
          <span className={`w-2 h-2 rounded-full ${c.dot} animate-pulse-slow`} />
          <span className="text-white font-bold">{serverName || 'My Server'}</span>
        </div>
        <span className={`${c.text} font-semibold`}>
          {status === 'healthy' ? 'Healthy' : status === 'moderate' ? 'Moderate' : 'Unhealthy'}
        </span>
      </div>
      <div className="space-y-1 text-gray-400">
        <div className="flex justify-between"><span>GDP</span><span className="text-white">$1.24M</span></div>
        <div className="flex justify-between"><span>D/G</span><span className="text-white">0.74×</span></div>
        <div className="flex justify-between"><span>Buy %</span><span className="text-white">71.4%</span></div>
      </div>
      <div className={`mt-2 pt-2 border-t ${c.border} text-[10px] ${c.text} text-center`}>
        Powered by Auto-Tune
      </div>
    </div>
  );
}

function buildSnippet(serverName: string, dashboardUrl: string, style: BadgeStyle): string {
  if (!serverName) return '<!-- Enter a server name to generate the badge -->';

  const encoded = encodeURIComponent(serverName);
  const badgeStyle = style === 'compact' ? 'compact' : style === 'standard' ? 'standard' : 'detailed';

  return `<!-- Auto-Tune Economy Health Badge -->
<a href="${dashboardUrl || 'https://autotune.live'}"
   target="_blank"
   style="display:inline-flex;align-items:center;gap:6px;padding:4px 10px;border-radius:6px;border:1px solid ${badgeStyle === 'compact' ? '#10b981' : badgeStyle === 'standard' ? '#10b981' : '#10b981'}22;background:#0f172a;font-family:monospace;font-size:11px;color:#10b981;text-decoration:none;">
  <span style="width:6px;height:6px;border-radius:50%;background:#10b981;animation:pulse 2s infinite;"></span>
  <span style="color:#e2e8f0;font-weight:600;">${serverName}</span>
  <span style="color:#64748b;">—</span>
  <span>✓ Economy Healthy</span>
</a>
<style>@keyframes pulse{0%,100%{opacity:1}50%{opacity:.4}}</style>`;
}

export default function HealthBadgePage() {
  const [serverName, setServerName] = useState('Diamond SMP');
  const [dashboardUrl, setDashboardUrl] = useState('https://play.diamondsmp.net:8989');
  const [badgeStyle, setBadgeStyle] = useState<BadgeStyle>('standard');
  const [copied, setCopied] = useState(false);

  const snippet = buildSnippet(serverName, dashboardUrl, badgeStyle);

  const copy = () => {
    navigator.clipboard.writeText(snippet).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  return (
    <div className="min-h-screen bg-gray-950 text-white">
      {/* Header */}
      <div className="border-b border-gray-800">
        <div className="max-w-4xl mx-auto px-4 py-4 flex items-center justify-between">
          <div>
            <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-0.5">Free Tool</p>
            <h1 className="text-xl font-bold">Economy Health Badge</h1>
          </div>
          <Link href="/" className="flex items-center gap-1.5 text-sm text-gray-400 hover:text-white transition-colors">
            <ArrowLeft className="w-4 h-4" />
            Back to Auto-Tune
          </Link>
        </div>
      </div>

      <div className="max-w-4xl mx-auto px-4 py-10">
        <div className="grid lg:grid-cols-2 gap-10">

          {/* Left — form */}
          <div>
            <p className="text-gray-400 text-sm leading-relaxed mb-8">
              Generate an embeddable HTML badge showing your server&apos;s Auto-Tune economy health.
              Drop it on your forum, website, or Discord sidebar. No JavaScript required — it&apos;s
              a self-contained static snippet.
            </p>

            <div className="space-y-6">
              <div>
                <label className="block text-sm font-medium text-gray-300 mb-2">
                  Server Name <span className="text-red-400">*</span>
                </label>
                <input
                  type="text"
                  value={serverName}
                  onChange={e => setServerName(e.target.value)}
                  placeholder="Diamond SMP"
                  className="w-full px-3 py-2 rounded-lg bg-gray-900 border border-gray-700 text-white text-sm placeholder-gray-600 focus:outline-none focus:border-emerald-600 transition-colors"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-300 mb-2">
                  Dashboard URL <span className="text-gray-500 font-normal">(optional)</span>
                </label>
                <input
                  type="text"
                  value={dashboardUrl}
                  onChange={e => setDashboardUrl(e.target.value)}
                  placeholder="https://play.myserver.net:8989"
                  className="w-full px-3 py-2 rounded-lg bg-gray-900 border border-gray-700 text-white text-sm placeholder-gray-600 focus:outline-none focus:border-emerald-600 transition-colors"
                />
                <p className="text-xs text-gray-500 mt-1.5">Link the badge to your server&apos;s Auto-Tune dashboard. Leave blank to link to autotune.live.</p>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-300 mb-2">Badge Style</label>
                <div className="grid grid-cols-3 gap-2">
                  {(['compact', 'standard', 'detailed'] as BadgeStyle[]).map(s => (
                    <button
                      key={s}
                      onClick={() => setBadgeStyle(s)}
                      className={`px-3 py-2 rounded-lg border text-xs font-medium transition-all ${
                        badgeStyle === s
                          ? 'border-emerald-600 bg-emerald-950/40 text-emerald-400'
                          : 'border-gray-700 bg-gray-900 text-gray-400 hover:border-gray-600'
                      }`}
                    >
                      {s.charAt(0).toUpperCase() + s.slice(1)}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            {/* Snippet output */}
            <div className="mt-8">
              <div className="flex items-center justify-between mb-2">
                <label className="text-sm font-medium text-gray-300">Embed Code</label>
                <button
                  onClick={copy}
                  className="flex items-center gap-1.5 text-xs text-gray-400 hover:text-emerald-400 transition-colors"
                >
                  {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                  {copied ? 'Copied!' : 'Copy snippet'}
                </button>
              </div>
              <textarea
                readOnly
                value={snippet}
                rows={8}
                className="w-full px-3 py-2 rounded-lg bg-gray-950 border border-gray-800 text-gray-300 text-xs font-mono leading-relaxed resize-none focus:outline-none"
              />
            </div>

            {/* Instructions */}
            <div className="mt-6 p-4 rounded-lg bg-gray-900/60 border border-gray-800 text-xs text-gray-400 leading-relaxed">
              <p className="font-medium text-gray-300 mb-2">How to use</p>
              <ol className="list-decimal list-inside space-y-1">
                <li>Enter your server name above</li>
                <li>Optionally add your Auto-Tune dashboard URL</li>
                <li>Pick a badge style</li>
                <li>Copy the HTML and paste it into your forum/website</li>
              </ol>
              <p className="mt-2">The badge is self-contained — no JavaScript, no external dependencies, no API calls. Update it manually or script it from your server.</p>
            </div>
          </div>

          {/* Right — preview */}
          <div>
            <p className="text-sm font-medium text-gray-300 mb-4">Live Preview</p>

            <div className="space-y-6">
              {/* On dark background */}
              <div>
                <p className="text-xs text-gray-500 uppercase tracking-wider mb-3">On dark background</p>
                <div className="bg-gray-900 rounded-xl p-6 flex items-center justify-center border border-gray-800">
                  <HealthBadgePreview serverName={serverName} style={badgeStyle} status="healthy" />
                </div>
              </div>

              {/* On light background */}
              <div>
                <p className="text-xs text-gray-500 uppercase tracking-wider mb-3">On light background</p>
                <div className="bg-gray-100 rounded-xl p-6 flex items-center justify-center border border-gray-200">
                  <HealthBadgePreview serverName={serverName} style={badgeStyle} status="healthy" />
                </div>
              </div>

              {/* All three health states */}
              <div>
                <p className="text-xs text-gray-500 uppercase tracking-wider mb-3">All health states</p>
                <div className="bg-gray-900 rounded-xl p-6 space-y-3 border border-gray-800">
                  {(['healthy', 'moderate', 'unhealthy'] as HealthStatus[]).map(status => (
                    <div key={status} className="flex items-center gap-3">
                      <span className="text-xs text-gray-500 w-16 capitalize">{status}</span>
                      <HealthBadgePreview serverName={serverName} style={badgeStyle} status={status} />
                    </div>
                  ))}
                </div>
              </div>

              {/* Dashboard URL info */}
              <div className="p-4 rounded-lg bg-emerald-950/30 border border-emerald-900/50 text-xs text-emerald-300/80 leading-relaxed">
                <p className="font-medium text-emerald-300 mb-1">Want live data?</p>
                <p>
                  The static badge works without any backend. For a live badge that automatically updates
                  from your server&apos;s Auto-Tune dashboard, your server must expose the{' '}
                  <code className="text-emerald-200">/api/admin/health</code> endpoint publicly, and you
                  can add a small JavaScript fetch to refresh the badge periodically.
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
