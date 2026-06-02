import { ExchangeRatesClient } from '@/components/prices/exchange-rates-client';

export const metadata = {
  title: 'Exchange Rates | Auto-Tune',
  description: 'Cross-server exchange rates relative to true-price consensus',
  openGraph: {
    title: 'Exchange Rates | Auto-Tune',
    description: 'Cross-server exchange rates relative to true-price consensus',
    images: [{ url: '/og-image.png', width: 1200, height: 630 }],
  },
  twitter: { card: 'summary_large_image', images: ['/og-image.png'] },
};

export default function ExchangeRatesPage() {
  return <ExchangeRatesClient />;
}
