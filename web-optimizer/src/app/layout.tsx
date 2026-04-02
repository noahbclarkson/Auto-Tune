import './globals.css';
import type { Metadata } from 'next';
import { Header } from '@/components/layout/header';
import { Footer } from '@/components/layout/footer';
import { CookieConsent } from '@/components/layout/cookie-consent';

export const metadata: Metadata = {
  metadataBase: new URL('https://autotune.dev'),
  title: 'Auto-Tune — Adaptive Market Pricing for Minecraft',
  description:
    'A sophisticated market engine for Minecraft Paper servers with dynamic pricing based on supply, demand, and player activity.',
  openGraph: {
    title: 'Auto-Tune — Adaptive Market Pricing for Minecraft',
    description:
      'A sophisticated market engine for Minecraft servers with supply-and-demand pricing, loans, auctions, and cross-server price discovery.',
    url: 'https://autotune.dev',
    siteName: 'Auto-Tune',
    type: 'website',
  },
  twitter: {
    card: 'summary_large_image',
    title: 'Auto-Tune — Adaptive Market Pricing for Minecraft',
    description:
      'A sophisticated market engine for Minecraft servers with supply-and-demand pricing, loans, auctions, and cross-server price discovery.',
  },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className="bg-gray-950 text-gray-100 min-h-screen flex flex-col">
        <Header />
        <main className="flex-1">{children}</main>
        <Footer />
        <CookieConsent />
      </body>
    </html>
  );
}
