import { NavLink } from 'react-router-dom';
import {
  Map,
  Plane,
  Route,
  LayoutDashboard,
  DollarSign,
  Building2,
  LogOut,
  PlaneTakeoff,
} from 'lucide-react';

interface SidebarProps {
  onLogout: () => void;
  airlineName?: string;
}

const navItems = [
  { to: '/', icon: Map, label: 'World Map' },
  { to: '/dashboard', icon: LayoutDashboard, label: 'Dashboard' },
  { to: '/fleet', icon: Plane, label: 'Fleet' },
  { to: '/routes', icon: Route, label: 'Routes' },
  { to: '/finances', icon: DollarSign, label: 'Finances' },
  { to: '/airports', icon: Building2, label: 'Airports' },
  { to: '/marketplace', icon: PlaneTakeoff, label: 'Marketplace' },
];

export default function Sidebar({ onLogout, airlineName }: SidebarProps) {
  return (
    <aside className="fixed left-0 top-0 h-screen w-60 bg-slate-900 text-slate-100 flex flex-col z-50">
      <div className="p-4 border-b border-slate-700">
        <h1 className="text-xl font-bold tracking-tight">
          <span className="text-sky-400">Flight</span>Forge
        </h1>
        <span className="text-xs text-slate-400">v2.0</span>
        {airlineName && (
          <p className="text-sm text-sky-300 mt-1 truncate">{airlineName}</p>
        )}
      </div>

      <nav className="flex-1 py-4 overflow-y-auto">
        {navItems.map(({ to, icon: Icon, label }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              `flex items-center gap-3 px-4 py-2.5 text-sm transition-colors ${
                isActive
                  ? 'bg-sky-600/20 text-sky-400 border-r-2 border-sky-400'
                  : 'text-slate-300 hover:bg-slate-800 hover:text-white'
              }`
            }
          >
            <Icon size={18} />
            {label}
          </NavLink>
        ))}
      </nav>

      <div className="p-4 border-t border-slate-700">
        <button
          onClick={onLogout}
          className="flex items-center gap-2 text-sm text-slate-400 hover:text-red-400 transition-colors w-full"
        >
          <LogOut size={16} />
          Sign Out
        </button>
      </div>
    </aside>
  );
}
