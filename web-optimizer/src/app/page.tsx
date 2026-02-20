import { Hero } from '@/components/landing/hero';
import { FeatureCards } from '@/components/landing/feature-cards';
import { AlgorithmPreview } from '@/components/landing/algorithm-preview';

export default function Home() {
  return (
    <div>
      <Hero />
      <FeatureCards />
      <AlgorithmPreview />
    </div>
  );
}
