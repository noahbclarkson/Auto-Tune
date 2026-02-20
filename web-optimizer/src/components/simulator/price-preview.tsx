'use client';

import { TrendingUp, TrendingDown, Percent, DollarSign } from 'lucide-react';
import { formatPrice } from '@/lib/utils';

interface PricePreviewProps {
  buyPrice: number;
  sellPrice: number;
  basePrice: number;
  bpd: number;
  spd: number;
}

export function PricePreview({ buyPrice, sellPrice, basePrice, bpd, spd }: PricePreviewProps) {
  const totalSpread = bpd + spd;
  const profitMargin = ((buyPrice - sellPrice) / basePrice) * 100;

  return (
    <div className="bg-gray-900 border border-gray-800 rounded-xl p-4 sm:p-6">
      <h3 className="text-base sm:text-lg font-semibold text-white border-b border-gray-800 pb-3 mb-4 sm:mb-6">
        Calculated Prices
      </h3>

      {/* Main Price Display */}
      <div className="grid grid-cols-2 gap-3 sm:gap-6 mb-4 sm:mb-6">
        {/* Buy Price */}
        <div className="bg-green-950/30 border border-green-800/50 rounded-lg p-4 text-center">
          <div className="flex items-center justify-center gap-2 text-green-400 mb-2">
            <TrendingUp className="w-5 h-5" />
            <span className="text-sm font-medium uppercase">Buy Price</span>
          </div>
          <div className="text-3xl font-bold text-green-400">
            {formatPrice(buyPrice)}
          </div>
          <div className="text-sm text-gray-400 mt-1">
            +{(bpd * 100).toFixed(2)}% spread
          </div>
        </div>

        {/* Sell Price */}
        <div className="bg-red-950/30 border border-red-800/50 rounded-lg p-4 text-center">
          <div className="flex items-center justify-center gap-2 text-red-400 mb-2">
            <TrendingDown className="w-5 h-5" />
            <span className="text-sm font-medium uppercase">Sell Price</span>
          </div>
          <div className="text-3xl font-bold text-red-400">
            {formatPrice(sellPrice)}
          </div>
          <div className="text-sm text-gray-400 mt-1">
            -{(spd * 100).toFixed(2)}% spread
          </div>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-3 gap-4">
        <div className="bg-gray-800 rounded-lg p-3 text-center">
          <div className="flex items-center justify-center gap-1 text-gray-400 mb-1">
            <DollarSign className="w-4 h-4" />
            <span className="text-xs">Base</span>
          </div>
          <div className="text-lg font-semibold text-white">
            {formatPrice(basePrice)}
          </div>
        </div>

        <div className="bg-gray-800 rounded-lg p-3 text-center">
          <div className="flex items-center justify-center gap-1 text-gray-400 mb-1">
            <Percent className="w-4 h-4" />
            <span className="text-xs">Total Spread</span>
          </div>
          <div className="text-lg font-semibold text-white">
            {(totalSpread * 100).toFixed(2)}%
          </div>
        </div>

        <div className="bg-gray-800 rounded-lg p-3 text-center">
          <div className="flex items-center justify-center gap-1 text-gray-400 mb-1">
            <Percent className="w-4 h-4" />
            <span className="text-xs">Server Margin</span>
          </div>
          <div className={`text-lg font-semibold ${profitMargin >= 0 ? 'text-emerald-400' : 'text-red-400'}`}>
            {profitMargin.toFixed(2)}%
          </div>
        </div>
      </div>

      {/* Explanation */}
      <div className="mt-6 p-4 bg-gray-800/50 rounded-lg">
        <p className="text-sm text-gray-400">
          <strong className="text-gray-300">How it works:</strong> Players buy at the{' '}
          <span className="text-green-400">green price</span> and sell at the{' '}
          <span className="text-red-400">red price</span>. The difference is the server&apos;s
          profit margin, which covers transaction costs and prevents exploits.
        </p>
      </div>
    </div>
  );
}
