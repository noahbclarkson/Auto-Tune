'use client';

import { useWizard, WizardProvider } from '@/components/setup/wizard-context';
import { WizardNav } from '@/components/setup/wizard-nav';
import { ServerTypeSelector } from '@/components/setup/server-type-selector';
import { PlayerCountSelector } from '@/components/setup/player-count-selector';
import { EconomyGoalsSelector } from '@/components/setup/economy-goals-selector';
import { StabilityPreview } from '@/components/setup/stability-preview';
import { ConfigExport } from '@/components/setup/config-export';
import { Wand2 } from 'lucide-react';

function WizardContent() {
  const { state } = useWizard();
  const { currentStep } = state;

  return (
    <div className="min-h-screen bg-gray-950 text-white">
      {/* Header */}
      <div className="border-b border-gray-800/60 bg-gray-950/80">
        <div className="max-w-5xl mx-auto px-4 py-6">
          <div className="flex items-center gap-3 mb-1">
            <div className="w-8 h-8 rounded-lg bg-emerald-600/20 border border-emerald-600/40 flex items-center justify-center">
              <Wand2 className="w-4 h-4 text-emerald-400" />
            </div>
            <h1 className="text-lg font-bold text-white">
              Auto<span className="text-emerald-400">-Tune</span> Setup Wizard
            </h1>
          </div>
          <p className="text-gray-400 text-sm">
            Answer 5 questions to get a production-ready config. Takes about 2 minutes.
          </p>
        </div>
      </div>

      {/* Step content */}
      <div className="max-w-5xl mx-auto px-4 py-8">
        <WizardNav />

        <div className="mt-6">
          {currentStep === 1 && <ServerTypeSelector />}
          {currentStep === 2 && <PlayerCountSelector />}
          {currentStep === 3 && <EconomyGoalsSelector />}
          {currentStep === 4 && <StabilityPreview />}
          {currentStep === 5 && <ConfigExport />}
        </div>
      </div>
    </div>
  );
}

export default function SetupPage() {
  return (
    <WizardProvider>
      <WizardContent />
    </WizardProvider>
  );
}
