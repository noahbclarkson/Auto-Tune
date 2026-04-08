import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Economy Health Badge — Auto-Tune',
  description:
    'Generate an embeddable HTML badge showing your server economy health. Drop it on your forum or website.',
};

export default function HealthBadgeLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <>{children}</>;
}
