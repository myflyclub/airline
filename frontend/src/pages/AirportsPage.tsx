import { useEffect, useState } from 'react';
import { airports as airportsApi } from '../services/api';
import type { Airport } from '../types';
import { Building2, Search, MapPin } from 'lucide-react';

export default function AirportsPage() {
  const [airportList, setAirportList] = useState<Airport[]>([]);
  const [query, setQuery] = useState('');
  const [minSize, setMinSize] = useState(5);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);

  const loadAirports = () => {
    setLoading(true);
    if (query.length >= 2) {
      airportsApi
        .search(query)
        .then((data) => setAirportList(data as Airport[]))
        .finally(() => setLoading(false));
    } else {
      airportsApi
        .list({ min_size: minSize, limit: 50, offset: page * 50 })
        .then((data) => setAirportList(data as Airport[]))
        .finally(() => setLoading(false));
    }
  };

  useEffect(() => {
    loadAirports();
  }, [minSize, page]);

  useEffect(() => {
    const t = setTimeout(() => loadAirports(), 300);
    return () => clearTimeout(t);
  }, [query]);

  return (
    <div className="p-6 max-w-6xl">
      <h1 className="text-2xl font-bold text-white mb-6">Airports</h1>

      {/* Filters */}
      <div className="flex gap-4 mb-6 flex-wrap">
        <div className="relative flex-1 min-w-64">
          <Search className="absolute left-3 top-2.5 text-slate-400" size={16} />
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search by IATA, name, or city..."
            className="w-full pl-9 pr-3 py-2 bg-slate-900 border border-slate-700 rounded-lg text-white text-sm focus:border-sky-500 focus:outline-none"
          />
        </div>
        <div className="flex items-center gap-2">
          <label className="text-sm text-slate-400">Min size:</label>
          <select
            value={minSize}
            onChange={(e) => { setMinSize(Number(e.target.value)); setPage(0); }}
            className="px-3 py-2 bg-slate-900 border border-slate-700 rounded-lg text-white text-sm"
          >
            {[0, 1, 2, 3, 4, 5, 6, 7].map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </div>
      </div>

      {loading ? (
        <p className="text-slate-400 animate-pulse">Loading airports...</p>
      ) : airportList.length === 0 ? (
        <div className="text-center py-20">
          <Building2 className="mx-auto text-slate-700 mb-4" size={48} />
          <p className="text-slate-400">No airports found.</p>
        </div>
      ) : (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
            {airportList.map((airport) => (
              <div
                key={airport.id}
                className="bg-slate-900 border border-slate-800 rounded-xl p-4 hover:border-slate-700 transition-colors"
              >
                <div className="flex justify-between items-start mb-2">
                  <div>
                    <h3 className="font-bold text-white">
                      <span className="text-sky-400 font-mono">{airport.iata}</span>
                      <span className="text-slate-600 text-xs ml-1">{airport.icao}</span>
                    </h3>
                    <p className="text-sm text-slate-300 truncate">{airport.name}</p>
                  </div>
                  <div className="flex items-center gap-1">
                    {Array.from({ length: Math.min(airport.size, 8) }).map((_, i) => (
                      <div
                        key={i}
                        className="w-1.5 h-1.5 rounded-full bg-sky-500"
                      />
                    ))}
                  </div>
                </div>

                <div className="flex items-center gap-2 text-xs text-slate-500">
                  <MapPin size={12} />
                  <span>
                    {airport.city}, {airport.country_code}
                  </span>
                </div>

                <div className="mt-2 flex gap-3 text-xs text-slate-500">
                  <span>
                    {airport.latitude.toFixed(2)}, {airport.longitude.toFixed(2)}
                  </span>
                  <span>Elev: {airport.elevation} ft</span>
                </div>
              </div>
            ))}
          </div>

          {/* Pagination */}
          {!query && (
            <div className="flex justify-center gap-2 mt-6">
              <button
                onClick={() => setPage(Math.max(0, page - 1))}
                disabled={page === 0}
                className="px-3 py-1 bg-slate-800 text-slate-300 rounded disabled:opacity-50"
              >
                Prev
              </button>
              <span className="px-3 py-1 text-slate-400">Page {page + 1}</span>
              <button
                onClick={() => setPage(page + 1)}
                disabled={airportList.length < 50}
                className="px-3 py-1 bg-slate-800 text-slate-300 rounded disabled:opacity-50"
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
