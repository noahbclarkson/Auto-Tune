'use client';

import { useWizard } from './wizard-context';
import { cn } from '@/lib/utils';
import { Check } from 'lucide-react';

const STEPS = [
  { num: 1, label: 'Server Type' },
  { num: 2, label: 'Player Count' },
  { num: 3, label: 'Goals' },
  { num: 4, label: 'Preview' },
  { num: 5, label: 'Export' },
];

export function WizardNav() {
  const { state, dispatch, canProceed } = useWizard();
  const { currentStep } = state;

  return (
    <div className="mb-8">
      {/* Step indicators */}
      <div className="flex items-center justify-center gap-0 mb-6">
        {STEPS.map((step, i) => {
          const done = currentStep > step.num;
          const active = currentStep === step.num;
          const clickable = done || canProceed(currentStep);
          return (
            <div key={step.num} className="flex items-center">
              {i > 0 && (
                <div
                  className={cn(
                    'w-8 sm:w-12 h-0.5 transition-colors',
                    currentStep > step.num ? 'bg-emerald-500' : 'bg-gray-700',
                  )}
                />
              )}
              <button
                disabled={!clickable && !done}
                onClick={() => done && dispatch({ type: 'GO_TO_STEP', step: step.num as 1 | 2 | 3 | 4 | 5 })}
                className={cn(
                  'flex flex-col items-center gap-1 transition-colors',
                  done && 'cursor-pointer',
                  active && 'text-emerald-400',
                  done && !active && 'text-emerald-500 hover:text-emerald-400',
                  !done && !active && 'text-gray-500',
                )}
              >
                <div
                  className={cn(
                    'w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold border-2 transition-colors',
                    done && 'bg-emerald-500 border-emerald-500 text-white',
                    active && 'border-emerald-500 bg-emerald-500/10 text-emerald-400',
                    !done && !active && 'border-gray-600 text-gray-500',
                  )}
                >
                  {done ? <Check className="w-3.5 h-3.5" /> : step.num}
                </div>
                <span className="text-xs hidden sm:block">{step.label}</span>
              </button>
            </div>
          );
        })}
      </div>

      {/* Back / Next buttons */}
      <div className="flex items-center justify-between max-w-md mx-auto">
        <button
          onClick={() => dispatch({ type: 'PREV_STEP' })}
          disabled={currentStep === 1}
          className={cn(
            'px-4 py-2 rounded-md text-sm font-medium transition-colors',
            currentStep === 1
              ? 'text-gray-600 cursor-not-allowed'
              : 'text-gray-300 hover:text-white hover:bg-gray-800',
          )}
        >
          ← Back
        </button>

        {currentStep < 5 && (
          <button
            onClick={() => dispatch({ type: 'NEXT_STEP' })}
            disabled={!canProceed(currentStep)}
            className={cn(
              'px-6 py-2 rounded-md text-sm font-bold transition-colors',
              canProceed(currentStep)
                ? 'bg-emerald-600 hover:bg-emerald-500 text-white'
                : 'bg-gray-700 text-gray-500 cursor-not-allowed',
            )}
          >
            Next →
          </button>
        )}
      </div>
    </div>
  );
}
