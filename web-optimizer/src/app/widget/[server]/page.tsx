import { EmbeddableWidget } from '@/components/widget/embeddable-widget';

// Standalone embeddable widget page.
// Usage: /widget/https%3A%2F%2Fyour-server%3A8989
// The widget fetches from the server's /api/admin/health endpoint.

// Required for output:export — pre-renders one demo param.
// Real widget URLs are generated at runtime by server admins.
export async function generateStaticParams() {
  return [{ server: 'demo' }];
}

interface Props {
  params: Promise<{ server: string }>;
}

export default async function WidgetPage({ params }: Props) {
  const { server } = await params;
  const apiUrl = decodeURIComponent(server).trim().replace(/\/$/, '');
  const isPreview = apiUrl === 'demo';

  return (
    <div
      style={{
        background: '#0a0a0f',
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '1rem',
      }}
    >
      <div className="w-full" style={{ maxWidth: 360 }}>
        {isPreview ? (
          <div
            style={{
              background: '#131313',
              border: '1px solid #222',
              borderRadius: '0.75rem',
              padding: '1.5rem',
              textAlign: 'center',
            }}
          >
            <p style={{ color: '#9ca3af', fontSize: '0.75rem', fontWeight: 500, marginBottom: '0.5rem' }}>
              Widget Preview
            </p>
            <p style={{ color: '#6b7280', fontSize: '0.7rem' }}>
              Replace <code style={{ color: '#9ca3af', background: '#1a1a1a', padding: '0.125rem 0.375rem', borderRadius: '0.25rem' }}>demo</code> in the URL with your server URL.
            </p>
          </div>
        ) : (
          <EmbeddableWidget apiUrl={apiUrl} />
        )}
      </div>
    </div>
  );
}
