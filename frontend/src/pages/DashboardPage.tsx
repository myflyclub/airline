import { useEffect, useState } from 'react';
import {
  airlines as airlinesApi,
  aircraft as aircraftApi,
  routes as routesApi,
} from '../services/api';
import type { AirlineSummary, Aircraft, Route } from '../types';
import {
  Plane,
  Route as RouteIcon,
  DollarSign,
  Star,
  TrendingUp,
  Plus,
} from 'lucide-react';

function StatCard({
  icon: Icon,
  label,
  value,
  color,
}: {
  icon: any;
  label: string;
  value: string;
  color: string;
}) {
  return (
    <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
      <div className="flex items-center gap-3 mb-2">
        <div className={`p-2 rounded-lg ${color}`}>
          <Icon size={18} className="text-white" />
        </div>
        <span className="text-sm text-slate-400">{label}</span>
      </div>
      <p className="text-2xl font-bold text-white">{value}</p>
    </div>
  );
}

function CreateAirlineForm({ onCreated }: { onCreated: () => void }) {
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await airlinesApi.create({ name, airline_code: code });
      onCreated();
    } catch (err: any) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-md mx-auto mt-20">
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-6">
        <h2 className="text-xl font-bold text-white mb-4">Create Your Airline</h2>
        <p className="text-sm text-slate-400 mb-6">
          Start your aviation empire. Choose a name and code for your new airline.
        </p>

        {error && (
          <div className="mb-4 p-3 bg-red-900/50 border border-red-700 rounded-lg text-red-300 text-sm">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm text-slate-400 mb-1">Airline Name</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g., Pacific Airways"
              className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white focus:border-sky-500 focus:outline-none"
              required
            />
          </div>
          <div>
            <label className="block text-sm text-slate-400 mb-1">
              Airline Code (2-5 chars)
            </label>
            <input
              type="text"
              value={code}
              onChange={(e) => setCode(e.target.value.toUpperCase())}
              placeholder="e.g., PA"
              maxLength={5}
              className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white focus:border-sky-500 focus:outline-none font-mono"
              required
            />
          </div>
          <button
            type="submit"
            disabled={loading}
            className="w-full py-2.5 bg-sky-600 hover:bg-sky-500 text-white rounded-lg font-medium transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
          >
            <Plus size={16} />
            {loading ? 'Creating...' : 'Create Airline'}
          </button>
        </form>
      </div>
    </div>
  );
}

export default function DashboardPage() {
  const [airline, setAirline] = useState<AirlineSummary | null>(null);
  const [recentRoutes, setRecentRoutes] = useState<Route[]>([]);
  const [fleet, setFleet] = useState<Aircraft[]>([]);
  const [noAirline, setNoAirline] = useState(false);

  const loadData = () => {
    airlinesApi
      .mine()
      .then((data) => {
        const a = data as AirlineSummary;
        setAirline(a);
        setNoAirline(false);
        // Load associated data
        routesApi
          .airlineRoutes(a.id)
          .then((r) => setRecentRoutes((r as Route[]).slice(0, 5)))
          .catch(() => {});
        aircraftApi
          .fleet(a.id)
          .then((f) => setFleet(f as Aircraft[]))
          .catch(() => {});
      })
      .catch(() => setNoAirline(true));
  };

  useEffect(() => {
    loadData();
  }, []);

  if (noAirline) {
    return <CreateAirlineForm onCreated={loadData} />;
  }

  if (!airline) {
    return (
      <div className="p-8 text-slate-400 animate-pulse">Loading dashboard...</div>
    );
  }

  return (
    <div className="p-6 max-w-6xl">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-white">{airline.name}</h1>
        <p className="text-sm text-slate-400 font-mono">{airline.airline_code}</p>
      </div>

      {/* Stats grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <StatCard
          icon={DollarSign}
          label="Balance"
          value={`$${(airline.balance / 1_000_000).toFixed(1)}M`}
          color="bg-emerald-600"
        />
        <StatCard
          icon={Plane}
          label="Fleet Size"
          value={String(airline.fleet_size)}
          color="bg-sky-600"
        />
        <StatCard
          icon={RouteIcon}
          label="Active Routes"
          value={String(airline.route_count)}
          color="bg-violet-600"
        />
        <StatCard
          icon={Star}
          label="Reputation"
          value={airline.reputation.toFixed(1)}
          color="bg-amber-600"
        />
      </div>

      {/* Routes and fleet summary */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Recent routes */}
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
          <h3 className="text-lg font-semibold text-white mb-4 flex items-center gap-2">
            <RouteIcon size={18} className="text-sky-400" />
            Recent Routes
          </h3>
          {recentRoutes.length === 0 ? (
            <p className="text-sm text-slate-500">No routes yet. Start flying!</p>
          ) : (
            <div className="space-y-2">
              {recentRoutes.map((route) => (
                <div
                  key={route.id}
                  className="flex items-center justify-between py-2 border-b border-slate-800 last:border-0"
                >
                  <div className="flex items-center gap-2">
                    <span className="text-sky-400 font-mono text-sm font-bold">
                      {route.from_airport_iata}
                    </span>
                    <TrendingUp size={14} className="text-slate-600" />
                    <span className="text-sky-400 font-mono text-sm font-bold">
                      {route.to_airport_iata}
                    </span>
                  </div>
                  <div className="text-right">
                    <span className="text-xs text-slate-400">
                      {route.distance.toLocaleString()} mi
                    </span>
                    <span className="text-xs text-slate-500 ml-2">
                      {route.frequency}x/wk
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Fleet summary */}
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
          <h3 className="text-lg font-semibold text-white mb-4 flex items-center gap-2">
            <Plane size={18} className="text-sky-400" />
            Fleet
          </h3>
          {fleet.length === 0 ? (
            <p className="text-sm text-slate-500">No aircraft yet. Visit the marketplace!</p>
          ) : (
            <div className="space-y-2">
              {fleet.slice(0, 5).map((ac) => (
                <div
                  key={ac.id}
                  className="flex items-center justify-between py-2 border-b border-slate-800 last:border-0"
                >
                  <span className="text-sm text-white">{ac.model_name}</span>
                  <div className="flex items-center gap-3">
                    <div className="w-20 bg-slate-800 rounded-full h-2">
                      <div
                        className="bg-emerald-500 h-2 rounded-full"
                        style={{ width: `${ac.condition}%` }}
                      />
                    </div>
                    <span className="text-xs text-slate-400 w-12 text-right">
                      {ac.condition.toFixed(0)}%
                    </span>
                  </div>
                </div>
              ))}
              {fleet.length > 5 && (
                <p className="text-xs text-slate-500 text-center pt-1">
                  +{fleet.length - 5} more aircraft
                </p>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
