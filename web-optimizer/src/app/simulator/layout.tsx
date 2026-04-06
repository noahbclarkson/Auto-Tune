import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Simulator | Auto-Tune',
  description: 'Simulate market conditions and preview Auto-Tune engine behavior before committing config changes.',
  openGraph: {
    title: 'Market Simulator | Auto-Tune',
    description: 'Test your economy configuration in a browser-based simulation before deploying to your server.',
    images: [{ url: '/og-image.png', width: 1200, height: 630 }],
  },
  twitter: { card: 'summary_large_image', images: ['/og-image.png'] },
};

export default function SimulatorLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
