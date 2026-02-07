import { useEffect, useState } from 'react';
import {
  airlines as airlinesApi,
  routes as routesApi,
  airports as airportsApi,
} from '../services/api';
import type { AirlineSummary, Route, AirportSearchResult, PriceSuggestion } from '../types';
import { Route as RouteIcon, Plus, Trash2, Search, ArrowRight } from 'lucide-react';

function NewRouteForm({
  onCreated,
}: {
  onCreated: () => void;
}) {
  const [fromQuery, setFromQuery] = useState('');
  const [toQuery, setToQuery] = useState('');
  const [fromResults, setFromResults] = useState<AirportSearchResult[]>([]);
  const [toResults, setToResults] = useState<AirportSearchResult[]>([]);
  const [fromAirport, setFromAirport] = useState<AirportSearchResult | null>(null);
  const [toAirport, setToAirport] = useState<AirportSearchResult | null>(null);
  const [pricing, setPricing] = useState<PriceSuggestion | null>(null);
  const [frequency, setFrequency] = useState(7);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  // Search airports
  useEffect(() => {
    if (fromQuery.length >= 2 && !fromAirport) {
      const t = setTimeout(() => {
        airportsApi.search(fromQuery).then((r) => setFromResults(r as AirportSearchResult[]));
      }, 300);
      return () => clearTimeout(t);
    }
    setFromResults([]);
  }, [fromQuery, fromAirport]);

  useEffect(() => {
    if (toQuery.length >= 2 && !toAirport) {
      const t = setTimeout(() => {
        airportsApi.search(toQuery).then((r) => setToResults(r as AirportSearchResult[]));
      }, 300);
      return () => clearTimeout(t);
    }
    setToResults([]);
  }, [toQuery, toAirport]);

  // Get price suggestion when both airports selected
  useEffect(() => {
    if (fromAirport && toAirport) {
      routesApi
        .suggestPrice(fromAirport.id, toAirport.id)
        .then((p) => setPricing(p as PriceSuggestion));
    }
  }, [fromAirport, toAirport]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!fromAirport || !toAirport || !pricing) return;
    setError('');
    setLoading(true);
    try {
      await routesApi.create({
        from_airport_id: fromAirport.id,
        to_airport_id: toAirport.id,
        frequency,
        price_economy: pricing.economy,
        price_business: pricing.business,
        price_first: pricing.first_class,
        price_discount: pricing.discount,
        capacity_economy: 150,
        capacity_business: 20,
        capacity_first: 8,
      });
      onCreated();
      setFromAirport(null);
      setToAirport(null);
      setFromQuery('');
      setToQuery('');
      setPricing(null);
    } catch (err: any) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="bg-slate-900 border border-slate-800 rounded-xl p-5 mb-6">
      <h3 className="text-lg font-semibold text-white mb-4">Create New Route</h3>

      {error && (
        <div className="mb-4 p-3 bg-red-900/50 border border-red-700 rounded-lg text-red-300 text-sm">
          {error}
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
        {/* From airport */}
        <div className="relative">
          <label className="block text-xs text-slate-400 mb-1">From</label>
          {fromAirport ? (
            <div className="flex items-center gap-2 px-3 py-2 bg-slate-800 rounded-lg">
              <span className="text-sky-400 font-mono font-bold">{fromAirport.iata}</span>
              <span className="text-sm text-slate-300 truncate">{fromAirport.name}</span>
              <button
                type="button"
                onClick={() => { setFromAirport(null); setFromQuery(''); }}
                className="text-slate-500 hover:text-white ml-auto"
              >
                &times;
              </button>
            </div>
          ) : (
            <>
              <div className="relative">
                <Search className="absolute left-2 top-2.5 text-slate-500" size={14} />
                <input
                  type="text"
                  value={fromQuery}
                  onChange={(e) => setFromQuery(e.target.value)}
                  placeholder="Search airport..."
                  className="w-full pl-7 pr-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white text-sm focus:border-sky-500 focus:outline-none"
                />
              </div>
              {fromResults.length > 0 && (
                <div className="absolute z-10 mt-1 w-full bg-slate-800 border border-slate-700 rounded-lg overflow-hidden max-h-40 overflow-y-auto">
                  {fromResults.map((ap) => (
                    <button
                      key={ap.id}
                      type="button"
                      onClick={() => { setFromAirport(ap); setFromResults([]); }}
                      className="w-full px-3 py-1.5 text-left text-xs hover:bg-slate-700 flex items-center gap-2"
                    >
                      <span className="text-sky-400 font-mono font-bold">{ap.iata}</span>
                      <span className="text-slate-300 truncate">{ap.name}</span>
                    </button>
                  ))}
                </div>
              )}
            </>
          )}
        </div>

        {/* To airport */}
        <div className="relative">
          <label className="block text-xs text-slate-400 mb-1">To</label>
          {toAirport ? (
            <div className="flex items-center gap-2 px-3 py-2 bg-slate-800 rounded-lg">
              <span className="text-sky-400 font-mono font-bold">{toAirport.iata}</span>
              <span className="text-sm text-slate-300 truncate">{toAirport.name}</span>
              <button
                type="button"
                onClick={() => { setToAirport(null); setToQuery(''); }}
                className="text-slate-500 hover:text-white ml-auto"
              >
                &times;
              </button>
            </div>
          ) : (
            <>
              <div className="relative">
                <Search className="absolute left-2 top-2.5 text-slate-500" size={14} />
                <input
                  type="text"
                  value={toQuery}
                  onChange={(e) => setToQuery(e.target.value)}
                  placeholder="Search airport..."
                  className="w-full pl-7 pr-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white text-sm focus:border-sky-500 focus:outline-none"
                />
              </div>
              {toResults.length > 0 && (
                <div className="absolute z-10 mt-1 w-full bg-slate-800 border border-slate-700 rounded-lg overflow-hidden max-h-40 overflow-y-auto">
                  {toResults.map((ap) => (
                    <button
                      key={ap.id}
                      type="button"
                      onClick={() => { setToAirport(ap); setToResults([]); }}
                      className="w-full px-3 py-1.5 text-left text-xs hover:bg-slate-700 flex items-center gap-2"
                    >
                      <span className="text-sky-400 font-mono font-bold">{ap.iata}</span>
                      <span className="text-slate-300 truncate">{ap.name}</span>
                    </button>
                  ))}
                </div>
              )}
            </>
          )}
        </div>

        {/* Frequency */}
        <div>
          <label className="block text-xs text-slate-400 mb-1">Frequency (per week)</label>
          <input
            type="number"
            value={frequency}
            onChange={(e) => setFrequency(Number(e.target.value))}
            min={1}
            max={28}
            className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white text-sm focus:border-sky-500 focus:outline-none"
          />
        </div>
      </div>

      {/* Pricing preview */}
      {pricing && (
        <div className="grid grid-cols-4 gap-2 mb-4">
          <div className="bg-slate-800 rounded-lg p-2 text-center">
            <p className="text-xs text-slate-500">Discount</p>
            <p className="text-sm text-white font-bold">${pricing.discount}</p>
          </div>
          <div className="bg-slate-800 rounded-lg p-2 text-center">
            <p className="text-xs text-slate-500">Economy</p>
            <p className="text-sm text-white font-bold">${pricing.economy}</p>
          </div>
          <div className="bg-slate-800 rounded-lg p-2 text-center">
            <p className="text-xs text-slate-500">Business</p>
            <p className="text-sm text-white font-bold">${pricing.business}</p>
          </div>
          <div className="bg-slate-800 rounded-lg p-2 text-center">
            <p className="text-xs text-slate-500">First</p>
            <p className="text-sm text-white font-bold">${pricing.first_class}</p>
          </div>
        </div>
      )}

      <button
        type="submit"
        disabled={!fromAirport || !toAirport || loading}
        className="px-4 py-2 bg-sky-600 hover:bg-sky-500 text-white rounded-lg text-sm transition-colors disabled:opacity-50 flex items-center gap-2"
      >
        <Plus size={16} />
        {loading ? 'Creating...' : 'Create Route'}
      </button>
    </form>
  );
}

export default function RoutesPage() {
  const [routes, setRoutes] = useState<Route[]>([]);
  const [showNew, setShowNew] = useState(false);

  const loadRoutes = () => {
    airlinesApi.mine().then((a) => {
      const al = a as AirlineSummary;
      routesApi.airlineRoutes(al.id).then((r) => setRoutes(r as Route[]));
    });
  };

  useEffect(() => {
    loadRoutes();
  }, []);

  const handleDelete = async (id: number) => {
    if (!confirm('Deactivate this route?')) return;
    await routesApi.delete(id);
    loadRoutes();
  };

  return (
    <div className="p-6 max-w-6xl">
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 className="text-2xl font-bold text-white">Route Network</h1>
          <p className="text-sm text-slate-400">{routes.length} active routes</p>
        </div>
        <button
          onClick={() => setShowNew(!showNew)}
          className="flex items-center gap-2 px-4 py-2 bg-sky-600 hover:bg-sky-500 text-white rounded-lg transition-colors"
        >
          <Plus size={16} />
          New Route
        </button>
      </div>

      {showNew && (
        <NewRouteForm
          onCreated={() => {
            loadRoutes();
            setShowNew(false);
          }}
        />
      )}

      {routes.length === 0 ? (
        <div className="text-center py-20">
          <RouteIcon className="mx-auto text-slate-700 mb-4" size={48} />
          <p className="text-slate-400">No routes yet. Create your first route!</p>
        </div>
      ) : (
        <div className="space-y-3">
          {routes.map((route) => (
            <div
              key={route.id}
              className="bg-slate-900 border border-slate-800 rounded-xl p-4"
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="text-center">
                    <p className="text-lg font-bold text-sky-400 font-mono">
                      {route.from_airport_iata}
                    </p>
                    <p className="text-xs text-slate-500 truncate max-w-24">
                      {route.from_airport_name}
                    </p>
                  </div>
                  <div className="flex flex-col items-center px-4">
                    <ArrowRight size={16} className="text-slate-600" />
                    <span className="text-xs text-slate-500">
                      {route.distance.toLocaleString()} mi
                    </span>
                  </div>
                  <div className="text-center">
                    <p className="text-lg font-bold text-sky-400 font-mono">
                      {route.to_airport_iata}
                    </p>
                    <p className="text-xs text-slate-500 truncate max-w-24">
                      {route.to_airport_name}
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-6">
                  <div className="text-right">
                    <p className="text-xs text-slate-500">Frequency</p>
                    <p className="text-sm text-white">{route.frequency}x /wk</p>
                  </div>
                  <div className="text-right">
                    <p className="text-xs text-slate-500">Economy</p>
                    <p className="text-sm text-emerald-400">${route.price_economy}</p>
                  </div>
                  <div className="text-right">
                    <p className="text-xs text-slate-500">Business</p>
                    <p className="text-sm text-emerald-400">${route.price_business}</p>
                  </div>
                  <button
                    onClick={() => handleDelete(route.id)}
                    className="p-2 text-slate-500 hover:text-red-400 transition-colors"
                    title="Deactivate route"
                  >
                    <Trash2 size={16} />
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
