import { useState, useEffect } from 'react';
import AirportMap from '../components/Map/AirportMap';
import { airports as airportsApi, airlines as airlinesApi } from '../services/api';
import type { AirportMapPoint, Airport, AirlineSummary } from '../types';
import { Search, X, Plane, MapPin, Users, Ruler } from 'lucide-react';

export default function MapPage() {
  const [selectedAirport, setSelectedAirport] = useState<Airport | null>(null);
  const [airline, setAirline] = useState<AirlineSummary | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<AirportMapPoint[]>([]);
  const [selectedId, setSelectedId] = useState<number | undefined>();
  const [minSize, setMinSize] = useState(3);

  useEffect(() => {
    airlinesApi
      .mine()
      .then((data) => setAirline(data as AirlineSummary))
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (searchQuery.length >= 2) {
      const timeout = setTimeout(() => {
        airportsApi
          .search(searchQuery)
          .then((data) => setSearchResults(data as AirportMapPoint[]))
          .catch(() => {});
      }, 300);
      return () => clearTimeout(timeout);
    } else {
      setSearchResults([]);
    }
  }, [searchQuery]);

  const handleAirportClick = async (ap: AirportMapPoint) => {
    setSelectedId(ap.id);
    try {
      const full = (await airportsApi.get(ap.id)) as Airport;
      setSelectedAirport(full);
    } catch {
      setSelectedAirport(null);
    }
  };

  const handleSearchSelect = (ap: AirportMapPoint) => {
    setSelectedId(ap.id);
    setSearchQuery('');
    setSearchResults([]);
    airportsApi
      .get(ap.id)
      .then((data) => setSelectedAirport(data as Airport))
      .catch(() => {});
  };

  return (
    <div className="h-screen flex flex-col relative">
      {/* Search bar */}
      <div className="absolute top-3 left-3 z-[1000] w-72">
        <div className="relative">
          <Search className="absolute left-3 top-2.5 text-slate-400" size={16} />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search airports..."
            className="w-full pl-9 pr-3 py-2 bg-slate-900/95 border border-slate-700 rounded-lg text-white text-sm focus:border-sky-500 focus:outline-none backdrop-blur-sm"
          />
        </div>
        {searchResults.length > 0 && (
          <div className="mt-1 bg-slate-900/95 border border-slate-700 rounded-lg overflow-hidden backdrop-blur-sm max-h-60 overflow-y-auto">
            {searchResults.map((ap) => (
              <button
                key={ap.id}
                onClick={() => handleSearchSelect(ap)}
                className="w-full px-3 py-2 text-left text-sm hover:bg-slate-800 transition-colors flex items-center gap-2"
              >
                <span className="text-sky-400 font-mono font-bold">{ap.iata}</span>
                <span className="text-slate-300 truncate">{ap.name}</span>
                <span className="text-slate-500 text-xs ml-auto">{ap.country_code}</span>
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Size filter */}
      <div className="absolute bottom-6 left-3 z-[1000] bg-slate-900/95 border border-slate-700 rounded-lg p-2 backdrop-blur-sm">
        <label className="text-xs text-slate-400 block mb-1">Min airport size</label>
        <input
          type="range"
          min={0}
          max={7}
          value={minSize}
          onChange={(e) => setMinSize(Number(e.target.value))}
          className="w-32 accent-sky-500"
        />
        <span className="text-xs text-slate-300 ml-2">{minSize}</span>
      </div>

      {/* Airport detail panel */}
      {selectedAirport && (
        <div className="absolute top-3 right-20 z-[1000] w-80 bg-slate-900/95 border border-slate-700 rounded-xl p-4 backdrop-blur-sm">
          <div className="flex justify-between items-start mb-3">
            <div>
              <h2 className="text-lg font-bold text-white">
                {selectedAirport.iata}
                <span className="text-slate-400 font-normal text-sm ml-2">
                  {selectedAirport.icao}
                </span>
              </h2>
              <p className="text-sm text-slate-300">{selectedAirport.name}</p>
            </div>
            <button
              onClick={() => {
                setSelectedAirport(null);
                setSelectedId(undefined);
              }}
              className="text-slate-500 hover:text-white"
            >
              <X size={18} />
            </button>
          </div>

          <div className="space-y-2 text-sm">
            <div className="flex items-center gap-2 text-slate-400">
              <MapPin size={14} />
              <span>
                {selectedAirport.city}, {selectedAirport.country_code}
              </span>
            </div>
            <div className="flex items-center gap-2 text-slate-400">
              <Plane size={14} />
              <span>Size: {selectedAirport.size} / 8</span>
              <span className="ml-auto text-xs">
                {selectedAirport.airport_type.replace('_', ' ')}
              </span>
            </div>
            <div className="flex items-center gap-2 text-slate-400">
              <Ruler size={14} />
              <span>Runway: {selectedAirport.runway_length.toLocaleString()} ft</span>
            </div>
            <div className="flex items-center gap-2 text-slate-400">
              <Users size={14} />
              <span>Pop: {selectedAirport.base_population.toLocaleString()}</span>
            </div>

            <div className="pt-2 border-t border-slate-700 text-xs text-slate-500">
              {selectedAirport.latitude.toFixed(4)}, {selectedAirport.longitude.toFixed(4)}
              {' | '}Elev: {selectedAirport.elevation.toLocaleString()} ft
            </div>
          </div>
        </div>
      )}

      <AirportMap
        airlineId={airline?.id}
        onAirportClick={handleAirportClick}
        selectedAirportId={selectedId}
        minSize={minSize}
      />
    </div>
  );
}
