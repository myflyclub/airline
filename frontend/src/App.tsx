import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './hooks/useAuth';
import AppLayout from './components/Layout/AppLayout';
import LoginPage from './components/Auth/LoginPage';
import MapPage from './pages/MapPage';
import DashboardPage from './pages/DashboardPage';
import FleetPage from './pages/FleetPage';
import RoutesPage from './pages/RoutesPage';
import FinancesPage from './pages/FinancesPage';
import AirportsPage from './pages/AirportsPage';
import MarketplacePage from './pages/MarketplacePage';

export default function App() {
  const { user, loading, login, register, logout } = useAuth();

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center">
        <div className="text-sky-400 text-xl animate-pulse">Loading FlightForge...</div>
      </div>
    );
  }

  if (!user) {
    return <LoginPage onLogin={login} onRegister={register} />;
  }

  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppLayout onLogout={logout} />}>
          <Route path="/" element={<MapPage />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/fleet" element={<FleetPage />} />
          <Route path="/routes" element={<RoutesPage />} />
          <Route path="/finances" element={<FinancesPage />} />
          <Route path="/airports" element={<AirportsPage />} />
          <Route path="/marketplace" element={<MarketplacePage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
