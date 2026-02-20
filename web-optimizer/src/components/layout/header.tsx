'use client';

import Link from 'next/link';
import { Github, Calculator, Home, BookOpen } from 'lucide-react';

export function Header() {
  return (
    <header className="bg-gray-900 border-b border-gray-800 sticky top-0 z-50">
      <nav className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          <Link href="/" className="flex items-center gap-2 text-emerald-500 font-bold text-xl">
            <Calculator className="w-6 h-6" />
            <span>Auto-Tune</span>
          </Link>
          
          <div className="flex items-center gap-6">
            <Link 
              href="/" 
              className="flex items-center gap-1.5 text-gray-300 hover:text-emerald-500 transition-colors"
            >
              <Home className="w-4 h-4" />
              <span>Home</span>
            </Link>
            <Link 
              href="/how-it-works" 
              className="flex items-center gap-1.5 text-gray-300 hover:text-emerald-500 transition-colors"
            >
              <BookOpen className="w-4 h-4" />
              <span>How It Works</span>
            </Link>
            <Link 
              href="/simulator" 
              className="flex items-center gap-1.5 text-gray-300 hover:text-emerald-500 transition-colors"
            >
              <Calculator className="w-4 h-4" />
              <span>Simulator</span>
            </Link>
            <a 
              href="https://github.com/Aetheraudios/Auto-Tune"
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-1.5 text-gray-300 hover:text-emerald-500 transition-colors"
            >
              <Github className="w-4 h-4" />
              <span>GitHub</span>
            </a>
          </div>
        </div>
      </nav>
    </header>
  );
}
