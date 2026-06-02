import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Server Setup Wizard — Auto-Tune',
  description:
    'Step-by-step wizard to configure Auto-Tune for your server. Choose your player base, set economy goals, and export production-ready YAML.',
  openGraph: {
    title: 'Server Setup Wizard — Auto-Tune',
    description:
      'Step-by-step wizard to configure Auto-Tune for your server. Choose your player base, set economy goals, and export production-ready YAML.',
    images: ['/og-image.png'],
  },
  twitter: { card: 'summary_large_image', images: ['/og-image.png'] },
};

export default function SetupLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <>{children}</>;
}
