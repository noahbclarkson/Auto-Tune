import './globals.css';
import type { Metadata } from 'next';
import { Header } from '@/components/layout/header';
import { Footer } from '@/components/layout/footer';
import { CookieConsent } from '@/components/layout/cookie-consent';

export const metadata: Metadata = {
  metadataBase: new URL('https://github.com/noahbclarkson/Auto-Tune'),
  title: 'Auto-Tune - Adaptive Market Pricing for Minecraft',
  description:
    'Adaptive economy tooling for Minecraft Paper servers with dynamic pricing based on supply, demand, and player activity.',
  openGraph: {
    title: 'Auto-Tune - Adaptive Market Pricing for Minecraft',
    description:
      'A Minecraft Paper plugin with supply-and-demand pricing, loans, auctions, a bundled dashboard, and optional cross-server price discovery.',
    url: 'https://github.com/noahbclarkson/Auto-Tune',
    siteName: 'Auto-Tune',
    type: 'website',
    images: [{ url: '/og-image.png', width: 1200, height: 630, alt: 'Auto-Tune dynamic economy overview' }],
  },
  twitter: {
    card: 'summary_large_image',
    title: 'Auto-Tune - Adaptive Market Pricing for Minecraft',
    description:
      'A Minecraft Paper plugin with supply-and-demand pricing, loans, auctions, a bundled dashboard, and optional cross-server price discovery.',
    images: ['/og-image.png'],
  },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <script
          dangerouslySetInnerHTML={{
            __html: `
              try {
                var t = localStorage.getItem('autotune-theme') || 'dark';
                document.documentElement.classList.add(t);
              } catch(e) {}
            `,
          }}
        />
      </head>
      <body className="min-h-screen bg-gray-950 text-gray-100 flex flex-col" suppressHydrationWarning>
        <Header />
        <main className="flex-1">{children}</main>
        <Footer />
        <CookieConsent />
      </body>
    </html>
  );
}
