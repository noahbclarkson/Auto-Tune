'use client';

import { Badge } from '@/components/ui/badge';
import { NavLink } from '@/components/layout/nav-link';
import { ThemeToggle } from '@/components/layout/theme-toggle';
import { LiveIndicator } from '@/components/dashboard/live-indicator';
import { Activity, Users } from 'lucide-react';

interface HeaderProps {
  totalItems: number;
  onlinePlayers: number;
}

export function Header({ totalItems, onlinePlayers }: HeaderProps) {
  return (
    <header className="border-b border-border bg-card">
      <div className="mx-auto max-w-7xl px-6 py-4 flex items-center justify-between">
        <div className="flex items-center gap-6">
          <div className="flex items-center gap-3">
            <Activity className="h-6 w-6 text-primary" />
            <h1 className="text-xl font-bold text-foreground">Auto-Tune</h1>
          </div>
          <nav className="hidden md:flex items-center gap-4">
            <NavLink href="/">Dashboard</NavLink>
            <NavLink href="/items/">Items</NavLink>
            <NavLink href="/economy/">Economy</NavLink>
            <NavLink href="/loans/">Loans</NavLink>
            <NavLink href="/leaderboard/">Leaderboard</NavLink>
            <NavLink href="/compare/">Compare</NavLink>
          </nav>
        </div>
        <div className="flex items-center gap-3">
          <LiveIndicator />
          <Badge variant="outline" className="gap-1.5">
            <span className="h-2 w-2 rounded-full bg-emerald-500" />
            {totalItems} items
          </Badge>
          <Badge variant="outline" className="gap-1.5">
            <Users className="h-3 w-3" />
            {onlinePlayers} online
          </Badge>
          <ThemeToggle />
        </div>
      </div>
    </header>
  );
}
