import { Suspense } from 'react';
import { AdminContent } from './admin-content';

function LoadingScreen() {
  return (
    <div className="min-h-screen bg-background">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 py-8">
        <div className="text-muted-foreground">Loading economy health...</div>
      </div>
    </div>
  );
}

export default function AdminPage() {
  return (
    <Suspense fallback={<LoadingScreen />}>
      <AdminContent />
    </Suspense>
  );
}
