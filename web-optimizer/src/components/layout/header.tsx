'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Github, BarChart2, Home, BookOpen, DollarSign, Server } from 'lucide-react';
import { cn } from '@/lib/utils';

const NAV = [
  { href: '/', label: 'Home', icon: Home },
  { href: '/how-it-works', label: 'How It Works', icon: BookOpen },
  { href: '/simulator', label: 'Simulator', icon: BarChart2 },
  { href: '/true-prices', label: 'True Prices', icon: DollarSign },
  { href: '/servers', label: 'Servers', icon: Server },
];

export function Header() {
  const pathname = usePathname();

  return (
    <header className="sticky top-0 z-50 border-b border-gray-800/60 bg-gray-950/80 backdrop-blur-md">
      <nav className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-14">
          <Link href="/" className="flex items-center gap-2 group">
            <div className="w-7 h-7 rounded-md bg-emerald-600/20 border border-emerald-600/40 flex items-center justify-center group-hover:bg-emerald-600/30 transition-colors">
              <DollarSign className="w-4 h-4 text-emerald-400" />
            </div>
            <span className="font-bold text-white tracking-tight">
              Auto<span className="text-emerald-400">-Tune</span>
            </span>
          </Link>

          <div className="flex items-center gap-1">
            {NAV.map(({ href, label, icon: Icon }) => {
              const active = pathname === href;
              return (
                <Link
                  key={href}
                  href={href}
                  className={cn(
                    'flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm transition-colors',
                    active
                      ? 'bg-emerald-600/15 text-emerald-400 border border-emerald-600/30'
                      : 'text-gray-400 hover:text-gray-200 hover:bg-gray-800/60',
                  )}
                >
                  <Icon className="w-3.5 h-3.5 shrink-0" />
                  <span className="hidden sm:inline">{label}</span>
                </Link>
              );
            })}
            <a
              href="https://github.com/noahbclarkson/Auto-Tune"
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm text-gray-400 hover:text-gray-200 hover:bg-gray-800/60 transition-colors ml-1"
            >
              <Github className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">GitHub</span>
            </a>
          </div>
        </div>
      </nav>
    </header>
  );
}
