import { Hero } from '@/components/landing/hero';
import { FeatureCards } from '@/components/landing/feature-cards';
import { DynamicEconomy } from '@/components/landing/dynamic-economy';
import { AlgorithmPreview } from '@/components/landing/algorithm-preview';
import { QuickStart } from '@/components/landing/quick-start';

export default function Home() {
  return (
    <div>
      <Hero />
      <FeatureCards />
      <DynamicEconomy />
      <AlgorithmPreview />
      <QuickStart />
    </div>
  );
}
