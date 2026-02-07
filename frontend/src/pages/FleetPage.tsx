import { useEffect, useState } from 'react';
import {
  airlines as airlinesApi,
  aircraft as aircraftApi,
} from '../services/api';
import type { AirlineSummary, Aircraft, AircraftModel } from '../types';
import { Plane, ShoppingCart, Trash2, Wrench } from 'lucide-react';

export default function FleetPage() {
  const [airline, setAirline] = useState<AirlineSummary | null>(null);
  const [fleet, setFleet] = useState<Aircraft[]>([]);
  const [models, setModels] = useState<AircraftModel[]>([]);
  const [showMarket, setShowMarket] = useState(false);
  const [filter, setFilter] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      airlinesApi.mine().catch(() => null),
      aircraftApi.models().catch(() => []),
    ]).then(([a, m]) => {
      if (a) {
        const airline = a as AirlineSummary;
        setAirline(airline);
        aircraftApi
          .fleet(airline.id)
          .then((f) => setFleet(f as Aircraft[]))
          .catch(() => {});
      }
      setModels(m as AircraftModel[]);
      setLoading(false);
    });
  }, []);

  const handlePurchase = async (modelId: number) => {
    try {
      const result = (await aircraftApi.purchase({ model_id: modelId })) as Aircraft[];
      setFleet((prev) => [...prev, ...result]);
      // Refresh airline balance
      const a = (await airlinesApi.mine()) as AirlineSummary;
      setAirline(a);
    } catch (err: any) {
      alert(err.message);
    }
  };

  const handleSell = async (aircraftId: number) => {
    if (!confirm('Sell this aircraft?')) return;
    try {
      await aircraftApi.sell(aircraftId);
      setFleet((prev) => prev.filter((a) => a.id !== aircraftId));
      const a = (await airlinesApi.mine()) as AirlineSummary;
      setAirline(a);
    } catch (err: any) {
      alert(err.message);
    }
  };

  const filteredModels = models.filter(
    (m) =>
      !filter ||
      m.aircraft_type === filter ||
      m.manufacturer.toLowerCase().includes(filter.toLowerCase()),
  );

  if (loading) {
    return <div className="p-8 text-slate-400 animate-pulse">Loading fleet...</div>;
  }

  return (
    <div className="p-6 max-w-6xl">
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 className="text-2xl font-bold text-white">Fleet Management</h1>
          <p className="text-sm text-slate-400">
            {fleet.length} aircraft | Balance: $
            {airline ? (airline.balance / 1_000_000).toFixed(1) : '0'}M
          </p>
        </div>
        <button
          onClick={() => setShowMarket(!showMarket)}
          className="flex items-center gap-2 px-4 py-2 bg-sky-600 hover:bg-sky-500 text-white rounded-lg transition-colors"
        >
          <ShoppingCart size={16} />
          {showMarket ? 'View Fleet' : 'Buy Aircraft'}
        </button>
      </div>

      {showMarket ? (
        <>
          {/* Aircraft marketplace */}
          <div className="mb-4 flex gap-2 flex-wrap">
            {['', 'REGIONAL', 'SMALL', 'MEDIUM', 'LARGE', 'EXTRA_LARGE', 'JUMBO'].map(
              (t) => (
                <button
                  key={t}
                  onClick={() => setFilter(t)}
                  className={`px-3 py-1 text-xs rounded-full transition-colors ${
                    filter === t
                      ? 'bg-sky-600 text-white'
                      : 'bg-slate-800 text-slate-400 hover:text-white'
                  }`}
                >
                  {t || 'All'}
                </button>
              ),
            )}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {filteredModels.map((model) => (
              <div
                key={model.id}
                className="bg-slate-900 border border-slate-800 rounded-xl p-4 hover:border-slate-700 transition-colors"
              >
                <div className="flex justify-between items-start mb-3">
                  <div>
                    <h3 className="text-sm font-bold text-white">{model.name}</h3>
                    <p className="text-xs text-slate-500">{model.manufacturer}</p>
                  </div>
                  <span className="text-xs px-2 py-0.5 bg-slate-800 text-slate-400 rounded-full">
                    {model.aircraft_type}
                  </span>
                </div>

                <div className="grid grid-cols-2 gap-2 mb-3 text-xs text-slate-400">
                  <div>Seats: <span className="text-white">{model.capacity}</span></div>
                  <div>Range: <span className="text-white">{model.range_miles.toLocaleString()} mi</span></div>
                  <div>Speed: <span className="text-white">{model.speed} mph</span></div>
                  <div>Quality: <span className="text-white">{model.quality}/10</span></div>
                  <div>Runway: <span className="text-white">{model.runway_requirement.toLocaleString()} ft</span></div>
                  <div>Build: <span className="text-white">{model.construction_time} cycles</span></div>
                </div>

                <div className="flex justify-between items-center pt-3 border-t border-slate-800">
                  <span className="text-emerald-400 font-bold">
                    ${(model.price / 1_000_000).toFixed(0)}M
                  </span>
                  <button
                    onClick={() => handlePurchase(model.id)}
                    className="px-3 py-1.5 bg-sky-600 hover:bg-sky-500 text-white text-xs rounded-lg transition-colors"
                  >
                    Purchase
                  </button>
                </div>
              </div>
            ))}
          </div>
        </>
      ) : (
        <>
          {/* Fleet view */}
          {fleet.length === 0 ? (
            <div className="text-center py-20">
              <Plane className="mx-auto text-slate-700 mb-4" size={48} />
              <p className="text-slate-400">Your fleet is empty.</p>
              <button
                onClick={() => setShowMarket(true)}
                className="mt-4 px-4 py-2 bg-sky-600 hover:bg-sky-500 text-white rounded-lg text-sm transition-colors"
              >
                Browse Aircraft
              </button>
            </div>
          ) : (
            <div className="space-y-3">
              {fleet.map((ac) => (
                <div
                  key={ac.id}
                  className="bg-slate-900 border border-slate-800 rounded-xl p-4 flex items-center gap-4"
                >
                  <Plane className="text-sky-400 shrink-0" size={24} />
                  <div className="flex-1 min-w-0">
                    <h3 className="text-sm font-bold text-white">{ac.model_name}</h3>
                    <p className="text-xs text-slate-500">
                      Age: {ac.age_cycles} cycles | Purchase: $
                      {(ac.purchase_price / 1_000_000).toFixed(0)}M
                    </p>
                  </div>
                  <div className="flex items-center gap-4 shrink-0">
                    <div className="text-right">
                      <div className="flex items-center gap-2">
                        <Wrench size={12} className="text-slate-500" />
                        <div className="w-24 bg-slate-800 rounded-full h-2">
                          <div
                            className={`h-2 rounded-full ${
                              ac.condition > 70
                                ? 'bg-emerald-500'
                                : ac.condition > 40
                                  ? 'bg-amber-500'
                                  : 'bg-red-500'
                            }`}
                            style={{ width: `${ac.condition}%` }}
                          />
                        </div>
                        <span className="text-xs text-slate-400 w-10">
                          {ac.condition.toFixed(0)}%
                        </span>
                      </div>
                    </div>
                    <button
                      onClick={() => handleSell(ac.id)}
                      className="p-2 text-slate-500 hover:text-red-400 transition-colors"
                      title="Sell aircraft"
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}
