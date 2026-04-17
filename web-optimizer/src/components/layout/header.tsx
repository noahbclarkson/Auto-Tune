'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  Github, BarChart2, Home, BookOpen, DollarSign, Server,
  TrendingUp, Grid, FlaskConical, Code2, Map, Download,
  Sliders, GitCommit, Wand2, Tag, Wifi, ChevronDown,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { ThemeToggle } from '@/components/landing/theme-toggle';
import { useState } from 'react';

/* -------------------------------------------------------------------- */
/*  Nav groups — organized into 4 logical sections                        */
/* -------------------------------------------------------------------- */
const NAV_SECTIONS: { label: string; items: { href: string; label: string; icon: React.ElementType }[] }[] = [
  {
    label: 'Install',
    items: [
      { href: '/setup',       label: 'Setup',    icon: Wand2 },
      { href: '/install',      label: 'Install',  icon: Download },
    ],
  },
  {
    label: 'Learn',
    items: [
      { href: '/docs',           label: 'Docs',          icon: BookOpen },
      { href: '/how-it-works',   label: 'How It Works',  icon: BookOpen },
      { href: '/why-auto-tune',  label: 'Why Auto-Tune',  icon: TrendingUp },
      { href: '/findings',       label: 'Findings',       icon: FlaskConical },
    ],
  },
  {
    label: 'Tools',
    items: [
      { href: '/simulator',           label: 'Simulator',        icon: BarChart2 },
      { href: '/config-playground',   label: 'Config',           icon: Sliders },
      { href: '/sweep-results',       label: 'Sweep',            icon: Grid },
      { href: '/simulation-results',  label: 'Sim Results',      icon: FlaskConical },
      { href: '/health-badge',        label: 'Badge',            icon: Tag },
    ],
  },
  {
    label: 'Community',
    items: [
      { href: '/true-prices',    label: 'True Prices',  icon: Wifi },
      { href: '/exchange-rates', label: 'Rates',        icon: TrendingUp },
      { href: '/servers',        label: 'Servers',      icon: Server },
      { href: '/roadmap',        label: 'Roadmap',      icon: Map },
      { href: '/changelog',      label: 'Changelog',    icon: GitCommit },
      { href: '/api-docs',       label: 'API',          icon: Code2 },
    ],
  },
];

const NAV_HOME: { href: string; label: string; icon: React.ElementType } = {
  href: '/',
  label: 'Home',
  icon: Home,
};

export function Header() {
  const pathname = usePathname();
  const [mobileOpen, setMobileOpen] = useState(false);

  const isActive = (href: string) => pathname === href;

  /* ------------------------------------------------------------------ */
  /* Desktop nav — flat row with section labels above each group          */
  /* ------------------------------------------------------------------ */
  const DesktopNav = () => (
    <div className="hidden xl:flex items-center gap-0">
      {/* Home */}
      <Link
        href={NAV_HOME.href}
        className={cn(
          'flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm transition-colors mr-1',
          isActive(NAV_HOME.href)
            ? 'bg-emerald-600/15 text-emerald-400 border border-emerald-600/30'
            : 'text-gray-400 hover:text-gray-200 hover:bg-gray-800/60',
        )}
      >
        <NAV_HOME.icon className="w-3.5 h-3.5 shrink-0" />
        <span>{NAV_HOME.label}</span>
      </Link>

      {/* Divider */}
      <div className="w-px h-5 bg-gray-800 mx-1" />

      {NAV_SECTIONS.map((section, si) => (
        <div key={section.label} className="flex items-center">
          {/* Section label — only show after first section */}
          {si > 0 && (
            <>
              <div className="w-px h-5 bg-gray-800 mx-1" />
            </>
          )}

          <div className="flex items-center gap-0">
            {section.items.map(({ href, label, icon: Icon }) => (
              <Link
                key={href}
                href={href}
                className={cn(
                  'flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm transition-colors',
                  isActive(href)
                    ? 'bg-emerald-600/15 text-emerald-400 border border-emerald-600/30'
                    : 'text-gray-400 hover:text-gray-200 hover:bg-gray-800/60',
                )}
              >
                <Icon className="w-3.5 h-3.5 shrink-0" />
                <span>{label}</span>
              </Link>
            ))}
          </div>
        </div>
      ))}
    </div>
  );

  /* ------------------------------------------------------------------ */
  /* Mobile / tablet nav — hamburger + dropdown sections                  */
  /* ------------------------------------------------------------------ */
  const MobileNav = () => (
    <div className="xl:hidden">
      <button
        onClick={() => setMobileOpen(!mobileOpen)}
        className="flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm text-gray-400 hover:text-gray-200 hover:bg-gray-800/60 transition-colors border border-gray-800"
      >
        {mobileOpen ? '✕' : '☰'} Menu
      </button>

      {mobileOpen && (
        <div className="absolute top-14 left-0 right-0 z-50 bg-gray-950 border-b border-gray-800 px-4 py-4 space-y-5 shadow-xl">
          {/* Home */}
          <Link
            href={NAV_HOME.href}
            onClick={() => setMobileOpen(false)}
            className={cn(
              'flex items-center gap-2 px-3 py-2 rounded-md text-sm transition-colors',
              isActive(NAV_HOME.href)
                ? 'bg-emerald-600/15 text-emerald-400'
                : 'text-gray-300 hover:bg-gray-800/60',
            )}
          >
            <NAV_HOME.icon className="w-4 h-4" />
            {NAV_HOME.label}
          </Link>

          {NAV_SECTIONS.map((section) => (
            <div key={section.label}>
              <p className="px-3 mb-1 text-xs font-semibold text-gray-600 uppercase tracking-widest">
                {section.label}
              </p>
              <div className="space-y-0.5">
                {section.items.map(({ href, label, icon: Icon }) => (
                  <Link
                    key={href}
                    href={href}
                    onClick={() => setMobileOpen(false)}
                    className={cn(
                      'flex items-center gap-2 px-3 py-2 rounded-md text-sm transition-colors',
                      isActive(href)
                        ? 'bg-emerald-600/15 text-emerald-400'
                        : 'text-gray-300 hover:bg-gray-800/60',
                    )}
                  >
                    <Icon className="w-4 h-4 shrink-0" />
                    {label}
                  </Link>
                ))}
              </div>
            </div>
          ))}

          <a
            href="https://github.com/noahbclarkson/Auto-Tune"
            target="_blank"
            rel="noopener noreferrer"
            className="flex items-center gap-2 px-3 py-2 rounded-md text-sm text-gray-400 hover:bg-gray-800/60"
          >
            <Github className="w-4 h-4" />
            GitHub
          </a>
        </div>
      )}
    </div>
  );

  return (
    <header className="sticky top-0 z-50 border-b border-gray-800/60 bg-gray-950/80 backdrop-blur-md">
      <nav className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-14">
          {/* Logo */}
          <Link href="/" className="flex items-center gap-2 group">
            <div className="w-7 h-7 rounded-md bg-emerald-600/20 border border-emerald-600/40 flex items-center justify-center group-hover:bg-emerald-600/30 transition-colors">
              <DollarSign className="w-4 h-4 text-emerald-400" />
            </div>
            <span className="font-bold text-white tracking-tight">
              Auto<span className="text-emerald-400">-Tune</span>
            </span>
          </Link>

          {/* Nav — desktop vs mobile */}
          <div className="flex items-center gap-2">
            <DesktopNav />
            <MobileNav />
            <ThemeToggle />
            <a
              href="https://github.com/noahbclarkson/Auto-Tune"
              target="_blank"
              rel="noopener noreferrer"
              className="hidden xl:flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm text-gray-400 hover:text-gray-200 hover:bg-gray-800/60 transition-colors"
            >
              <Github className="w-3.5 h-3.5" />
            </a>
          </div>
        </div>
      </nav>
    </header>
  );
}
