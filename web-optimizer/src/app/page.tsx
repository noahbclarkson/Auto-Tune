import { Hero } from '@/components/landing/hero';
import { FeatureCards } from '@/components/landing/feature-cards';
import { ChangelogSection } from '@/components/landing/changelog-section';
import { CompareSection } from '@/components/landing/compare-section';
import { DynamicEconomy } from '@/components/landing/dynamic-economy';
import { AlgorithmPreview } from '@/components/landing/algorithm-preview';
import { KeyFindings } from '@/components/landing/key-findings';
import { QuickStart } from '@/components/landing/quick-start';

export default function Home() {
  return (
    <div>
      <Hero />
      <FeatureCards />
      <ChangelogSection />
      <CompareSection />
      <DynamicEconomy />
      <AlgorithmPreview />
      <KeyFindings />
      <QuickStart />
    </div>
  );
}
