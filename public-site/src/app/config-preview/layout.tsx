import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Config Preview - Auto-Tune',
  description: 'Compare Auto-Tune configuration changes before applying them to a live server.',
  openGraph: {
    title: 'Config Preview - Auto-Tune',
    description: 'Compare Auto-Tune configuration changes before applying them to a live server.',
    images: ['/og-image.png'],
  },
  twitter: { card: 'summary_large_image', images: ['/og-image.png'] },
};

export default function ConfigPreviewLayout({ children }: { children: React.ReactNode }) {
  return children;
}
