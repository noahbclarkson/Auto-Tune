'use client';

import React, { createContext, useContext, useReducer, ReactNode } from 'react';

export type ServerType = 'smp' | 'skyblock' | 'faction' | 'economy' | 'custom';
export type PlayerCount = 'solo' | 'medium' | 'large' | 'massive';
export type GoalKey = 'volume' | 'seller_protection' | 'treasury' | 'fast_discovery' | 'low_debt' | 'fun_volatility';

export interface WizardAnswers {
  serverType: ServerType | null;
  playerCount: PlayerCount | null;
  goals: GoalKey[];
}

export interface WizardState {
  currentStep: 1 | 2 | 3 | 4 | 5;
  answers: WizardAnswers;
}

type WizardAction =
  | { type: 'SET_SERVER_TYPE'; serverType: ServerType }
  | { type: 'SET_PLAYER_COUNT'; playerCount: PlayerCount }
  | { type: 'TOGGLE_GOAL'; goal: GoalKey }
  | { type: 'NEXT_STEP' }
  | { type: 'PREV_STEP' }
  | { type: 'GO_TO_STEP'; step: 1 | 2 | 3 | 4 | 5 };

const initialState: WizardState = {
  currentStep: 1,
  answers: {
    serverType: null,
    playerCount: null,
    goals: [],
  },
};

function reducer(state: WizardState, action: WizardAction): WizardState {
  switch (action.type) {
    case 'SET_SERVER_TYPE':
      return { ...state, answers: { ...state.answers, serverType: action.serverType } };
    case 'SET_PLAYER_COUNT':
      return { ...state, answers: { ...state.answers, playerCount: action.playerCount } };
    case 'TOGGLE_GOAL': {
      const goals = state.answers.goals.includes(action.goal)
        ? state.answers.goals.filter((g) => g !== action.goal)
        : state.answers.goals.length < 2
        ? [...state.answers.goals, action.goal]
        : state.answers.goals;
      return { ...state, answers: { ...state.answers, goals } };
    }
    case 'NEXT_STEP':
      return { ...state, currentStep: Math.min(5, state.currentStep + 1) as 1 | 2 | 3 | 4 | 5 };
    case 'PREV_STEP':
      return { ...state, currentStep: Math.max(1, state.currentStep - 1) as 1 | 2 | 3 | 4 | 5 };
    case 'GO_TO_STEP':
      return { ...state, currentStep: action.step };
    default:
      return state;
  }
}

interface WizardContextValue {
  state: WizardState;
  dispatch: React.Dispatch<WizardAction>;
  canProceed: (step: number) => boolean;
}

const WizardContext = createContext<WizardContextValue | null>(null);

export function WizardProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(reducer, initialState);

  const canProceed = (step: number): boolean => {
    switch (step) {
      case 1:
        return state.answers.serverType !== null;
      case 2:
        return state.answers.playerCount !== null;
      case 3:
        return true; // goals are optional
      case 4:
      case 5:
        return true;
      default:
        return false;
    }
  };

  return (
    <WizardContext.Provider value={{ state, dispatch, canProceed }}>
      {children}
    </WizardContext.Provider>
  );
}

export function useWizard() {
  const ctx = useContext(WizardContext);
  if (!ctx) throw new Error('useWizard must be used inside WizardProvider');
  return ctx;
}
