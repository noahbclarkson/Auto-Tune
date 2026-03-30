import { Hero } from '@/components/landing/hero';
import { FeatureCards } from '@/components/landing/feature-cards';
import { SocialProof } from '@/components/landing/social-proof';
import { ChangelogSection } from '@/components/landing/changelog-section';
import { CompareSection } from '@/components/landing/compare-section';
import { DynamicEconomy } from '@/components/landing/dynamic-economy';
import { LiveDemo } from '@/components/landing/live-demo';
import { AlgorithmPreview } from '@/components/landing/algorithm-preview';
import { KeyFindings } from '@/components/landing/key-findings';
import { QuickStart } from '@/components/landing/quick-start';

export default function Home() {
  return (
    <div>
      <Hero />
      <FeatureCards />
      <SocialProof />
      <ChangelogSection />
      <CompareSection />
      <DynamicEconomy />

      {/* ── Live Demo ── */}
      <section className="py-20 bg-gray-950 border-y border-gray-800/50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center max-w-2xl mx-auto mb-8">
            <p className="text-xs text-emerald-400 uppercase tracking-widest font-medium mb-2">Interactive Demo</p>
            <h2 className="text-2xl sm:text-3xl font-bold text-white mb-4">
              Watch a real economy breathe
            </h2>
            <p className="text-gray-400 leading-relaxed">
              This is a live browser simulation running the Auto-Tune engine. No backend, no server —
              just the algorithm playing out in real time. Prices respond to simulated trade pressure
              and random market events. Pause it, reset it, watch what happens.
            </p>
          </div>
          <LiveDemo />
          <div className="text-center mt-8">
            <p className="text-sm text-gray-500 mb-4">
              This is what Auto-Tune looks like in action. Want to test your own scenarios?
            </p>
            <div className="flex items-center justify-center gap-3">
              <a
                href="/simulator"
                className="inline-flex items-center gap-2 px-5 py-2.5 bg-emerald-600 hover:bg-emerald-500 text-white font-semibold rounded-lg transition-colors shadow-lg shadow-emerald-600/20 text-sm"
              >
                Open the full simulator →
              </a>
              <a
                href="/how-it-works"
                className="inline-flex items-center gap-2 px-5 py-2.5 border border-gray-700 hover:border-gray-600 text-gray-300 font-semibold rounded-lg transition-colors text-sm"
              >
                How it works
              </a>
            </div>
          </div>
        </div>
      </section>

      <AlgorithmPreview />
      <KeyFindings />
      <QuickStart />
    </div>
  );
}
