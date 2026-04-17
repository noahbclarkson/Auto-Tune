import Link from 'next/link';
import { Globe, ArrowRight, Server, TrendingUp } from 'lucide-react';

/* ------------------------------------------------------------------ */
/* Cross-server network effect banner                                    */
/* ------------------------------------------------------------------ */

export function CrossServerBanner() {
  return (
    <section className="border-y border-gray-800/50 bg-gradient-to-r from-emerald-950/30 via-gray-950 to-emerald-950/30 py-8">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex flex-col sm:flex-row items-start sm:items-center gap-6">

          {/* Left — copy */}
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 mb-2">
              <Globe className="w-4 h-4 text-emerald-400" />
              <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium">Cross-Server Network</p>
            </div>
            <h2 className="text-lg font-semibold text-white mb-1">
              Prices that get smarter with every server
            </h2>
            <p className="text-sm text-gray-400 leading-relaxed max-w-xl">
              Auto-Tune servers opt-in to share anonymous price ratios. The API aggregates data
              across all servers to compute &ldquo;true&rdquo; prices — then seeds every new server
              with those prices. More servers = sharper price signals for everyone.
            </p>
          </div>

          {/* Right — 3-step flow */}
          <div className="shrink-0 flex items-center gap-3">
            <NetworkStep icon={Server} label="Your server submits" detail="Anonymous ratios, not prices" />
            <ArrowRight className="w-4 h-4 text-gray-600 shrink-0" />
            <NetworkStep icon={Globe} label="API aggregates" detail="Cross-server true prices" />
            <ArrowRight className="w-4 h-4 text-gray-600 shrink-0" />
            <NetworkStep icon={TrendingUp} label="Every new server benefits" detail="Starts smarter" />
          </div>
        </div>

        <div className="mt-4 pt-4 border-t border-gray-800/50 flex flex-wrap items-center gap-4 text-xs text-gray-500">
          <span>
            See the live aggregated data:{' '}
            <Link href="/true-prices" className="text-emerald-400 hover:underline">True Prices</Link>
            {' '}·{' '}
            <Link href="/servers" className="text-emerald-400 hover:underline">Server Explorer</Link>
          </span>
          <span className="text-gray-700">·</span>
          <span>Opt-in only. No player data shared. Your economy stays yours.</span>
        </div>
      </div>
    </section>
  );
}

function NetworkStep({ icon: Icon, label, detail }: { icon: React.ElementType; label: string; detail: string }) {
  return (
    <div className="flex flex-col items-center gap-1 text-center min-w-[80px]">
      <div className="w-8 h-8 rounded-lg bg-emerald-950/60 border border-emerald-800/50 flex items-center justify-center">
        <Icon className="w-4 h-4 text-emerald-400" />
      </div>
      <p className="text-xs font-medium text-gray-300 leading-tight">{label}</p>
      <p className="text-[10px] text-gray-600 leading-tight">{detail}</p>
    </div>
  );
}
