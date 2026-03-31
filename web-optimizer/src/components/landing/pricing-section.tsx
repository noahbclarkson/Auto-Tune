import Link from 'next/link';
import { Github, Heart, Shield, Zap } from 'lucide-react';

export function PricingSection() {
  return (
    <section className="py-20 border-t border-gray-800/60">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="text-center mb-12">
          <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">Pricing</p>
          <h2 className="text-2xl sm:text-3xl font-bold text-white mb-3">
            Free and open source. Forever.
          </h2>
          <p className="text-gray-400 max-w-lg mx-auto text-sm leading-relaxed">
            Auto-Tune is and always will be free. No paywalls, no premium tiers, no &quot;enterprise&quot;
            pricing. The full feature set is available to every server, no matter the size.
          </p>
        </div>

        {/* Single free tier */}
        <div className="max-w-md mx-auto">
          <div className="rounded-2xl border border-emerald-800/50 bg-gradient-to-b from-emerald-950/30 to-gray-900/60 p-8 text-center">
            <div className="inline-flex items-center justify-center w-12 h-12 rounded-full bg-emerald-600/20 border border-emerald-600/30 mb-5">
              <span className="text-xl">$0</span>
            </div>
            <h3 className="text-lg font-bold text-white mb-1">Free Forever</h3>
            <p className="text-sm text-gray-400 mb-6">All features. All platforms. No catch.</p>

            <ul className="space-y-3 text-sm text-left mb-8">
              {[
                { icon: Zap, text: 'Full market engine with supply-and-demand pricing', color: 'text-emerald-400' },
                { icon: Shield, text: 'Auction house, loans, autosell, treasury', color: 'text-emerald-400' },
                { icon: Heart, text: 'All future updates included at no extra cost', color: 'text-emerald-400' },
                { icon: Github, text: 'Source code on GitHub — audit it yourself', color: 'text-emerald-400' },
              ].map(({ icon: Icon, text, color }) => (
                <li key={text} className="flex items-start gap-3">
                  <Icon className={`w-4 h-4 ${color} shrink-0 mt-0.5`} />
                  <span className="text-gray-300">{text}</span>
                </li>
              ))}
            </ul>

            <Link
              href="/install"
              className="block w-full py-3 px-6 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded-lg transition-colors shadow-lg shadow-emerald-600/20 text-sm"
            >
              Get started — it&apos;s free
            </Link>
          </div>
        </div>

        {/* Why free */}
        <div className="mt-8 text-center">
          <p className="text-xs text-gray-600 leading-relaxed max-w-md mx-auto">
            Auto-Tune is built by a small team who believe every Minecraft server deserves a real economy.
            If you find it useful, star us on GitHub — that helps more than money.
          </p>
          <div className="mt-4 flex items-center justify-center gap-4">
            <a
              href="https://github.com/noahbclarkson/Auto-Tune"
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 text-xs text-gray-500 hover:text-gray-300 transition-colors"
            >
              <Github className="w-3.5 h-3.5" />
              github.com/noahbclarkson/Auto-Tune
            </a>
          </div>
        </div>
      </div>
    </section>
  );
}
