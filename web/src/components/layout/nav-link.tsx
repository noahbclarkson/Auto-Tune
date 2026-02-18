'use client';

import { useEffect, useState } from 'react';

interface NavLinkProps {
  href: string;
  children: React.ReactNode;
}

export function NavLink({ href, children }: NavLinkProps) {
  const [active, setActive] = useState(false);

  useEffect(() => {
    const path = window.location.pathname;
    if (href === '/') {
      setActive(path === '/' || path === '/index.html');
    } else {
      setActive(path.startsWith(href));
    }
  }, [href]);

  return (
    <a
      href={href}
      className={`text-sm font-medium transition-colors ${
        active
          ? 'text-foreground'
          : 'text-muted-foreground hover:text-foreground'
      }`}
    >
      {children}
    </a>
  );
}
