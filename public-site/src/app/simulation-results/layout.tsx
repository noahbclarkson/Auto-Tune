import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Simulation Results | Auto-Tune',
  description: 'Historical Auto-Tune market simulation runs with detailed economy health analysis.',
  openGraph: {
    title: 'Simulation Results | Auto-Tune',
    description: 'Detailed analysis of Auto-Tune market simulation runs across multiple scenarios.',
    images: [{ url: '/og-image.png', width: 1200, height: 630 }],
  },
  twitter: { card: 'summary_large_image', images: ['/og-image.png'] },
};

export default function SimulationResultsLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
