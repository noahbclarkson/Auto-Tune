import { ServersPageClient } from '@/components/servers/servers-page-client';

export const metadata = {
  title: 'Servers | Auto-Tune',
  description: 'Registered Auto-Tune servers and their latest cross-server API submissions.',
  openGraph: {
    title: 'Servers | Auto-Tune',
    description: 'Registered Auto-Tune servers and their latest cross-server API submissions.',
    images: [{ url: '/og-image.png', width: 1200, height: 630 }],
  },
  twitter: { card: 'summary_large_image', images: ['/og-image.png'] },
};

export default function ServersPage() {
  return <ServersPageClient />;
}
