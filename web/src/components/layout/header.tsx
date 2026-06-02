'use client';

import { useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { NavLink } from '@/components/layout/nav-link';
import { ThemeToggle } from '@/components/layout/theme-toggle';
import { LiveIndicator } from '@/components/dashboard/live-indicator';
import { TrendingUp, Users, Menu, X } from 'lucide-react';

interface HeaderProps {
  totalItems: number;
  onlinePlayers: number;
}

/** Inline SVG Auto-Tune logo — a stylised price chart with a pulse line. */
function LogoMark() {
  return (
    <svg
      width="28"
      height="28"
      viewBox="0 0 28 28"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className="shrink-0"
    >
      <rect x="1" y="1" width="26" height="26" rx="6" fill="currentColor" className="text-primary/10" stroke="currentColor" strokeWidth="1" strokeOpacity="0.3" />
      <line x1="4" y1="20" x2="24" y2="20" stroke="currentColor" strokeWidth="0.75" strokeOpacity="0.2" className="text-primary" />
      <line x1="4" y1="14" x2="24" y2="14" stroke="currentColor" strokeWidth="0.75" strokeOpacity="0.12" className="text-primary" />
      <rect x="5" y="16" width="3" height="6" rx="1" fill="currentColor" className="text-primary" fillOpacity="0.4" />
      <rect x="10" y="12" width="3" height="10" rx="1" fill="currentColor" className="text-primary" fillOpacity="0.55" />
      <rect x="15" y="9" width="3" height="13" rx="1" fill="currentColor" className="text-primary" fillOpacity="0.7" />
      <rect x="20" y="6" width="3" height="16" rx="1" fill="currentColor" className="text-primary" />
      <polyline
        points="6.5,15.5 11.5,11.5 16.5,8.5 21.5,5"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="text-primary"
      />
    </svg>
  );
}

const NAV_LINKS = [
  { href: '/', label: 'Dashboard' },
  { href: '/items/', label: 'Items' },
  { href: '/economy/', label: 'Economy' },
  { href: '/loans/', label: 'Loans' },
  { href: '/auction/', label: 'Auction' },
  { href: '/portfolio/', label: 'Portfolio' },
  { href: '/leaderboard/', label: 'Leaderboard' },
  { href: '/compare/', label: 'Compare' },
  { href: '/badges/', label: 'Badges' },
  { href: '/changelog/', label: 'Changelog' },
  { href: '/admin/', label: 'Admin' },
];

export function Header({ totalItems, onlinePlayers }: HeaderProps) {
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <header className="border-b border-border bg-card/80 backdrop-blur-sm">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 py-3 flex items-center justify-between">
        {/* Brand */}
        <div className="flex items-center gap-3">
          <LogoMark />
          <div className="flex flex-col leading-none">
            <span className="text-base font-bold text-foreground tracking-tight">Auto<span className="text-primary">Tune</span></span>
            <span className="text-[10px] text-muted-foreground font-mono tracking-widest uppercase hidden sm:block mt-0.5">Market Engine</span>
          </div>

          {/* Desktop nav */}
          <div className="hidden md:flex items-center gap-1 ml-6 pl-6 border-l border-border">
            <nav className="flex items-center gap-1">
              {NAV_LINKS.map((link) => (
                <NavLink key={link.href} href={link.href}>{link.label}</NavLink>
              ))}
            </nav>
          </div>
        </div>

        {/* Status */}
        <div className="flex items-center gap-2">
          <LiveIndicator />
          <Badge variant="outline" className="gap-1.5 hidden sm:flex">
            <TrendingUp className="h-3 w-3 text-primary" />
            {totalItems.toLocaleString()} items
          </Badge>
          <Badge variant="outline" className="gap-1.5">
            <Users className="h-3 w-3" />
            {onlinePlayers} online
          </Badge>
          <ThemeToggle />

          {/* Mobile hamburger */}
          <button
            onClick={() => setMobileOpen((v) => !v)}
            className="md:hidden rounded-md p-2 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            aria-label="Toggle navigation"
          >
            {mobileOpen ? <X className="h-4 w-4" /> : <Menu className="h-4 w-4" />}
          </button>
        </div>
      </div>

      {/* Mobile nav dropdown */}
      {mobileOpen && (
        <div className="md:hidden border-t border-border bg-card">
          <nav className="mx-auto max-w-7xl px-4 py-3 flex flex-col gap-1">
            {NAV_LINKS.map((link) => (
              <a
                key={link.href}
                href={link.href}
                onClick={() => setMobileOpen(false)}
                className="text-sm font-medium text-muted-foreground hover:text-foreground px-3 py-2 rounded-md hover:bg-muted transition-colors"
              >
                {link.label}
              </a>
            ))}
          </nav>
        </div>
      )}
    </header>
  );
}
