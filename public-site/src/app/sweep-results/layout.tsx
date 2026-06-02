import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Parameter Sweep | Auto-Tune',
  description: 'Browse 840 configuration combinations tested across the Auto-Tune market engine — filter by buy ratio, volatility, debt/GDP, and more.',
  openGraph: {
    title: 'Parameter Sweep Results | Auto-Tune',
    description: '840-config parameter sweep: which engine settings produce the healthiest Minecraft economies?',
    images: [{ url: '/og-image.png', width: 1200, height: 630 }],
  },
  twitter: { card: 'summary_large_image', images: ['/og-image.png'] },
};

export default function SweepResultsLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
