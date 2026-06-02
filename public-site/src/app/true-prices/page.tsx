import { TruePricesPageClient } from '@/components/prices/true-prices-page-client';

export const metadata = {
  title: 'True Prices | Auto-Tune',
  description: 'Cross-server item values computed from opt-in Auto-Tune server submissions.',
  openGraph: {
    title: 'True Prices | Auto-Tune',
    description: 'Cross-server item values computed from opt-in Auto-Tune server submissions.',
    images: [{ url: '/og-image.png', width: 1200, height: 630 }],
  },
  twitter: { card: 'summary_large_image', images: ['/og-image.png'] },
};

export default function TruePricesPage() {
  return <TruePricesPageClient />;
}
