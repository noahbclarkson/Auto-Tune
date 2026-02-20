'use client';

import Link from 'next/link';
import { Github, Calculator, Home, BookOpen } from 'lucide-react';

export function Header() {
  return (
    <header className="bg-gray-900 border-b border-gray-800 sticky top-0 z-50">
      <nav className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-14 sm:h-16">
          {/* Logo */}
          <Link href="/" className="flex items-center gap-2 text-emerald-500 font-bold text-lg sm:text-xl shrink-0">
            <Calculator className="w-5 h-5 sm:w-6 sm:h-6" />
            <span>Auto-Tune</span>
          </Link>
          
          {/* Nav links */}
          <div className="flex items-center gap-3 sm:gap-6">
            <Link
              href="/"
              className="flex items-center gap-1.5 text-gray-300 hover:text-emerald-500 transition-colors"
              title="Home"
            >
              <Home className="w-4 h-4" />
              <span className="hidden sm:inline text-sm">Home</span>
            </Link>
            <Link
              href="/how-it-works"
              className="flex items-center gap-1.5 text-gray-300 hover:text-emerald-500 transition-colors"
              title="How It Works"
            >
              <BookOpen className="w-4 h-4" />
              <span className="hidden sm:inline text-sm">How It Works</span>
            </Link>
            <Link
              href="/simulator"
              className="flex items-center gap-1.5 text-gray-300 hover:text-emerald-500 transition-colors"
              title="Simulator"
            >
              <Calculator className="w-4 h-4" />
              <span className="hidden sm:inline text-sm">Simulator</span>
            </Link>
            <a
              href="https://github.com/Aetheraudios/Auto-Tune"
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-1.5 text-gray-300 hover:text-emerald-500 transition-colors"
              title="GitHub"
            >
              <Github className="w-4 h-4" />
              <span className="hidden sm:inline text-sm">GitHub</span>
            </a>
          </div>
        </div>
      </nav>
    </header>
  );
}
