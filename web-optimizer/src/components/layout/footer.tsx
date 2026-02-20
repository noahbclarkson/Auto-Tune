import { Calculator } from 'lucide-react';

export function Footer() {
  return (
    <footer className="bg-gray-900 border-t border-gray-800 py-8">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex flex-col md:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-2 text-gray-400">
            <Calculator className="w-5 h-5 text-emerald-500" />
            <span>Auto-Tune — Adaptive Market Pricing for Minecraft</span>
          </div>
          
          <div className="flex items-center gap-6 text-sm text-gray-500">
            <a 
              href="https://github.com/Aetheraudios/Auto-Tune"
              target="_blank"
              rel="noopener noreferrer"
              className="hover:text-emerald-500 transition-colors"
            >
              GitHub
            </a>
            <a
              href="https://www.spigotmc.org/"
              target="_blank"
              rel="noopener noreferrer"
              className="hover:text-emerald-500 transition-colors"
            >
              SpigotMC
            </a>
            <span>© 2024 Auto-Tune</span>
          </div>
        </div>
      </div>
    </footer>
  );
}
