import { useEffect, useState } from 'react';
import { aircraft as aircraftApi, airlines as airlinesApi } from '../services/api';
import type { AircraftModel, AirlineSummary } from '../types';
import { Filter, ShoppingCart, Check } from 'lucide-react';

export default function MarketplacePage() {
  const [models, setModels] = useState<AircraftModel[]>([]);
  const [airline, setAirline] = useState<AirlineSummary | null>(null);
  const [filter, setFilter] = useState('');
  const [sortBy, setSortBy] = useState<'capacity' | 'price' | 'range'>('capacity');
  const [purchased, setPurchased] = useState<Set<number>>(new Set());

  useEffect(() => {
    aircraftApi.models().then((m) => setModels(m as AircraftModel[]));
    airlinesApi.mine().then((a) => setAirline(a as AirlineSummary)).catch(() => {});
  }, []);

  const handlePurchase = async (modelId: number) => {
    try {
      await aircraftApi.purchase({ model_id: modelId });
      setPurchased((prev) => new Set([...prev, modelId]));
      const a = (await airlinesApi.mine()) as AirlineSummary;
      setAirline(a);
      setTimeout(() => {
        setPurchased((prev) => {
          const next = new Set(prev);
          next.delete(modelId);
          return next;
        });
      }, 2000);
    } catch (err: any) {
      alert(err.message);
    }
  };

  const filtered = models
    .filter((m) => !filter || m.aircraft_type === filter)
    .sort((a, b) => {
      if (sortBy === 'price') return a.price - b.price;
      if (sortBy === 'range') return b.range_miles - a.range_miles;
      return a.capacity - b.capacity;
    });

  const types = [...new Set(models.map((m) => m.aircraft_type))];

  return (
    <div className="p-6 max-w-6xl">
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 className="text-2xl font-bold text-white">Aircraft Marketplace</h1>
          <p className="text-sm text-slate-400">
            {models.length} models available
            {airline && ` | Balance: $${(airline.balance / 1_000_000).toFixed(1)}M`}
          </p>
        </div>
      </div>

      {/* Filters */}
      <div className="flex gap-3 mb-6 flex-wrap items-center">
        <div className="flex items-center gap-2 text-sm text-slate-400">
          <Filter size={14} />
          Type:
        </div>
        <button
          onClick={() => setFilter('')}
          className={`px-3 py-1 text-xs rounded-full transition-colors ${
            !filter ? 'bg-sky-600 text-white' : 'bg-slate-800 text-slate-400 hover:text-white'
          }`}
        >
          All
        </button>
        {types.map((t) => (
          <button
            key={t}
            onClick={() => setFilter(t)}
            className={`px-3 py-1 text-xs rounded-full transition-colors ${
              filter === t ? 'bg-sky-600 text-white' : 'bg-slate-800 text-slate-400 hover:text-white'
            }`}
          >
            {t}
          </button>
        ))}

        <div className="ml-auto flex items-center gap-2 text-sm text-slate-400">
          Sort:
          {(['capacity', 'price', 'range'] as const).map((s) => (
            <button
              key={s}
              onClick={() => setSortBy(s)}
              className={`px-2 py-1 text-xs rounded transition-colors ${
                sortBy === s ? 'bg-slate-700 text-white' : 'text-slate-500 hover:text-white'
              }`}
            >
              {s.charAt(0).toUpperCase() + s.slice(1)}
            </button>
          ))}
        </div>
      </div>

      {/* Aircraft grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {filtered.map((model) => (
          <div
            key={model.id}
            className="bg-slate-900 border border-slate-800 rounded-xl p-5 hover:border-slate-700 transition-colors"
          >
            <div className="flex justify-between items-start mb-3">
              <div>
                <h3 className="font-bold text-white">{model.name}</h3>
                <p className="text-xs text-slate-500">{model.manufacturer} | {model.family}</p>
              </div>
              <span className="text-xs px-2 py-0.5 bg-slate-800 text-slate-400 rounded-full">
                {model.aircraft_type}
              </span>
            </div>

            <div className="grid grid-cols-3 gap-y-2 mb-4 text-xs">
              <div>
                <p className="text-slate-500">Capacity</p>
                <p className="text-white font-bold">{model.capacity}</p>
              </div>
              <div>
                <p className="text-slate-500">Speed</p>
                <p className="text-white font-bold">{model.speed} mph</p>
              </div>
              <div>
                <p className="text-slate-500">Range</p>
                <p className="text-white font-bold">{model.range_miles.toLocaleString()} mi</p>
              </div>
              <div>
                <p className="text-slate-500">Quality</p>
                <p className="text-white font-bold">{model.quality}/10</p>
              </div>
              <div>
                <p className="text-slate-500">Runway</p>
                <p className="text-white font-bold">{model.runway_requirement.toLocaleString()} ft</p>
              </div>
              <div>
                <p className="text-slate-500">Build Time</p>
                <p className="text-white font-bold">{model.construction_time} cyc</p>
              </div>
            </div>

            {/* Fuel efficiency bar */}
            <div className="mb-4">
              <div className="flex justify-between text-xs mb-1">
                <span className="text-slate-500">Fuel Efficiency</span>
                <span className="text-slate-400">{model.fuel_burn.toFixed(1)} /mi</span>
              </div>
              <div className="w-full bg-slate-800 rounded-full h-1.5">
                <div
                  className="bg-emerald-500 h-1.5 rounded-full"
                  style={{ width: `${Math.max(10, 100 - model.fuel_burn * 7)}%` }}
                />
              </div>
            </div>

            <div className="flex justify-between items-center pt-3 border-t border-slate-800">
              <span className="text-lg font-bold text-emerald-400">
                ${(model.price / 1_000_000).toFixed(0)}M
              </span>
              <button
                onClick={() => handlePurchase(model.id)}
                disabled={purchased.has(model.id)}
                className={`flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                  purchased.has(model.id)
                    ? 'bg-emerald-600 text-white'
                    : 'bg-sky-600 hover:bg-sky-500 text-white'
                }`}
              >
                {purchased.has(model.id) ? (
                  <>
                    <Check size={14} />
                    Purchased
                  </>
                ) : (
                  <>
                    <ShoppingCart size={14} />
                    Buy
                  </>
                )}
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
