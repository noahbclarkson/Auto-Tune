import type { Metadata } from 'next';
import { Header } from '@/components/layout/header';
import { Footer } from '@/components/layout/footer';

export const metadata: Metadata = {
  title: 'Config Change Preview — Auto-Tune',
  description: 'Paste your current and new config YAML side-by-side. See simulated 14-day outcome diff before you apply changes to a live server.',
  openGraph: {
    title: 'Config Change Preview — Auto-Tune',
    description: 'Simulate the impact of config changes before applying them to your server.',
    type: 'website',
  },
};

export default function ConfigPreviewLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      <Header />
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {children}
      </main>
      <Footer />
    </div>
  );
}
