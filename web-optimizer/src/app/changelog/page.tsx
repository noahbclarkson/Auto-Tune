import ChangelogClient from './changelog-client';

export const metadata = {
  title: 'Changelog — Auto-Tune',
  description: "What's new in Auto-Tune — rewrite-2",
  openGraph: {
    title: 'Changelog — Auto-Tune',
    description: "What's new in Auto-Tune — rewrite-2",
    images: ['/og-image.png'],
  },
  twitter: { card: 'summary_large_image', images: ['/og-image.png'] },
};

export default function ChangelogPage() {
  return <ChangelogClient />;
}
