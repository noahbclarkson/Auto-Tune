import Link from 'next/link';
import { ArrowRight, Play } from 'lucide-react';

export function Hero() {
  return (
    <section className="relative py-20 sm:py-32 overflow-hidden">
      {/* Background gradient */}
      <div className="absolute inset-0 bg-gradient-to-b from-emerald-950/20 to-transparent" />
      
      <div className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
        <h1 className="text-4xl sm:text-6xl font-bold text-white mb-6">
          <span className="text-emerald-500">Auto-Tune</span>
          <br />
          Adaptive Market Pricing for Minecraft
        </h1>
        
        <p className="text-xl text-gray-400 max-w-3xl mx-auto mb-10">
          A sophisticated market engine that dynamically adjusts prices based on supply, 
          demand, player activity, and market liquidity. Create balanced, player-driven 
          economies on your Paper server.
        </p>
        
        <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
          <Link
            href="/simulator"
            className="inline-flex items-center gap-2 px-6 py-3 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded-lg transition-colors shadow-lg shadow-emerald-600/25"
          >
            <Play className="w-5 h-5" />
            Try the Simulator
          </Link>
          <Link
            href="/how-it-works"
            className="inline-flex items-center gap-2 px-6 py-3 bg-gray-800 hover:bg-gray-700 text-gray-200 font-semibold rounded-lg transition-colors border border-gray-700"
          >
            <ArrowRight className="w-5 h-5" />
            How It Works
          </Link>
        </div>
      </div>
    </section>
  );
}
