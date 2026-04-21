import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Admin Command Reference | Auto-Tune',
  description:
    'Complete reference for all Auto-Tune admin commands: economy health, price management, market events, item overrides, recovery mode, and more.',
  openGraph: {
    title: 'Admin Command Reference | Auto-Tune',
    description: 'All Auto-Tune admin commands with descriptions and permission nodes.',
    images: [{ url: '/og-image.png', width: 1200, height: 630, alt: 'Admin Command Reference' }],
  },
  twitter: { card: 'summary_large_image', images: ['/og-image.png'] },
};

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return children;
}