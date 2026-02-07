import { Outlet } from 'react-router-dom';
import Sidebar from './Sidebar';

interface AppLayoutProps {
  onLogout: () => void;
  airlineName?: string;
}

export default function AppLayout({ onLogout, airlineName }: AppLayoutProps) {
  return (
    <div className="flex min-h-screen bg-slate-950">
      <Sidebar onLogout={onLogout} airlineName={airlineName} />
      <main className="flex-1 ml-60">
        <Outlet />
      </main>
    </div>
  );
}
