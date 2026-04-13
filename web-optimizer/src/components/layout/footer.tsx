import Link from 'next/link';
import { TrendingUp, Github, ExternalLink } from 'lucide-react';

export function Footer() {
  return (
    <footer className="border-t border-gray-800/60 bg-gray-950 py-10">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex flex-col md:flex-row items-start justify-between gap-8">
          {/* Brand */}
          <div>
            <div className="flex items-center gap-2 mb-2">
              <div className="w-6 h-6 rounded bg-emerald-600/20 border border-emerald-600/30 flex items-center justify-center">
                <TrendingUp className="w-3.5 h-3.5 text-emerald-400" />
              </div>
              <span className="font-bold text-white text-sm">
                Auto<span className="text-emerald-400">-Tune</span>
              </span>
            </div>
            <p className="text-xs text-gray-500 max-w-xs leading-relaxed">
              Adaptive market pricing engine for Minecraft Paper servers.
              Supply, demand, and player activity drive the economy.
            </p>
          </div>

          {/* Links */}
          <div className="flex flex-col gap-2 text-sm">
            <p className="text-xs font-medium text-gray-400 uppercase tracking-wider mb-1">Links</p>
            <a
              href="https://github.com/noahbclarkson/Auto-Tune"
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-1.5 text-gray-500 hover:text-emerald-400 transition-colors"
            >
              <Github className="w-3.5 h-3.5" />
              GitHub
            </a>
            <Link href="/simulator" className="flex items-center gap-1.5 text-gray-500 hover:text-emerald-400 transition-colors">
              <ExternalLink className="w-3.5 h-3.5" />
              Simulator
            </Link>
            <Link href="/how-it-works" className="flex items-center gap-1.5 text-gray-500 hover:text-emerald-400 transition-colors">
              <ExternalLink className="w-3.5 h-3.5" />
              How It Works
            </Link>
            <Link href="/install" className="flex items-center gap-1.5 text-gray-500 hover:text-emerald-400 transition-colors">
              <ExternalLink className="w-3.5 h-3.5" />
              Install Guide
            </Link>
            <Link href="/setup" className="flex items-center gap-1.5 text-gray-500 hover:text-emerald-400 transition-colors">
              <ExternalLink className="w-3.5 h-3.5" />
              Setup Wizard
            </Link>
            <Link href="/docs" className="flex items-center gap-1.5 text-gray-500 hover:text-emerald-400 transition-colors">
              <ExternalLink className="w-3.5 h-3.5" />
              Docs
            </Link>
            <Link href="/changelog" className="flex items-center gap-1.5 text-gray-500 hover:text-emerald-400 transition-colors">
              <ExternalLink className="w-3.5 h-3.5" />
              Changelog
            </Link>
            <a
              href="https://github.com/noahbclarkson/Auto-Tune/blob/rewrite-2/docs/FAQ.md"
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-1.5 text-gray-500 hover:text-emerald-400 transition-colors"
            >
              <ExternalLink className="w-3.5 h-3.5" />
              FAQ
            </a>
          </div>
        </div>

        <div className="mt-8 pt-6 border-t border-gray-800/40 flex items-center justify-between">
          <p className="text-xs text-gray-600">© 2026 Auto-Tune. MIT License.</p>
          <p className="text-xs text-gray-600">Paper 1.21.4 · Java 21</p>
        </div>
      </div>
    </footer>
  );
}
