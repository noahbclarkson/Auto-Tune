'use client';

import { Github } from 'lucide-react';

export function Footer() {
  return (
    <footer className="border-t border-border mt-auto">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 py-5 flex flex-col sm:flex-row items-center justify-between gap-3">
        <p className="text-xs text-muted-foreground">
          Auto<span className="text-primary font-semibold">Tune</span> Market Engine
        </p>
        <div className="flex items-center gap-4">
          <a
            href="https://github.com/noahbclarkson/Auto-Tune"
            target="_blank"
            rel="noopener noreferrer"
            className="flex items-center gap-1.5 text-xs text-muted-foreground hover:text-primary transition-colors"
          >
            <Github className="h-3.5 w-3.5" />
            GitHub
          </a>
          <span className="text-xs text-muted-foreground/50">rewrite-2 · Paper 1.21.4</span>
        </div>
      </div>
    </footer>
  );
}
