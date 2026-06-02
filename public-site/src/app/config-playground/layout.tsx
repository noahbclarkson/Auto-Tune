import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Config Playground | Auto-Tune',
  description: 'Interactive engine parameter editor for Auto-Tune — preview spread, BPD, and SPD before committing config changes.',
  openGraph: {
    title: 'Config Playground | Auto-Tune',
    description: 'Interactive engine parameter editor for Auto-Tune with live spread preview.',
    images: [{ url: '/og-image.png', width: 1200, height: 630 }],
  },
  twitter: { card: 'summary_large_image', images: ['/og-image.png'] },
};

export default function ConfigPlaygroundLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
