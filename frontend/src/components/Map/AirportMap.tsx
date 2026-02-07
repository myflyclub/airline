import { useEffect, useState, useMemo } from 'react';
import { MapContainer, TileLayer, CircleMarker, Popup, Polyline, useMap } from 'react-leaflet';
import { airports, routes as routesApi } from '../../services/api';
import type { AirportMapPoint, RouteMapData } from '../../types';
import 'leaflet/dist/leaflet.css';

const TILE_LAYERS = {
  dark: {
    url: 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png',
    attribution: '&copy; <a href="https://carto.com/">CARTO</a>',
  },
  light: {
    url: 'https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png',
    attribution: '&copy; <a href="https://carto.com/">CARTO</a>',
  },
  osm: {
    url: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
    attribution: '&copy; OpenStreetMap contributors',
  },
};

function sizeToRadius(size: number, zoom: number): number {
  const base = Math.max(2, size * 1.2);
  return base + Math.max(0, zoom - 4) * 0.5;
}

function sizeToColor(size: number): string {
  if (size >= 7) return '#f59e0b'; // amber - large
  if (size >= 5) return '#3b82f6'; // blue - medium
  if (size >= 3) return '#6366f1'; // indigo - small
  return '#64748b'; // slate - tiny
}

interface AirportMapProps {
  airlineId?: number;
  onAirportClick?: (airport: AirportMapPoint) => void;
  selectedAirportId?: number;
  minSize?: number;
}

function MapController({ center, zoom }: { center?: [number, number]; zoom?: number }) {
  const map = useMap();
  useEffect(() => {
    if (center && zoom) {
      map.flyTo(center, zoom);
    }
  }, [center, zoom, map]);
  return null;
}

export default function AirportMap({
  airlineId,
  onAirportClick,
  selectedAirportId,
  minSize = 3,
}: AirportMapProps) {
  const [airportData, setAirportData] = useState<AirportMapPoint[]>([]);
  const [routeData, setRouteData] = useState<RouteMapData[]>([]);
  const [tileLayer, setTileLayer] = useState<keyof typeof TILE_LAYERS>('dark');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    airports
      .mapPoints(minSize)
      .then((data) => setAirportData(data as AirportMapPoint[]))
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [minSize]);

  useEffect(() => {
    if (airlineId) {
      routesApi
        .mapData(airlineId)
        .then((data) => setRouteData(data as RouteMapData[]))
        .catch(console.error);
    }
  }, [airlineId]);

  const tile = TILE_LAYERS[tileLayer];

  const selectedAirport = useMemo(
    () => airportData.find((a) => a.id === selectedAirportId),
    [airportData, selectedAirportId],
  );

  return (
    <div className="relative w-full h-full">
      {loading && (
        <div className="absolute inset-0 flex items-center justify-center bg-slate-950/80 z-[1000]">
          <div className="text-sky-400 text-lg animate-pulse">Loading airports...</div>
        </div>
      )}

      {/* Tile layer selector */}
      <div className="absolute top-3 right-3 z-[1000] flex gap-1 bg-slate-900/90 rounded-lg p-1 border border-slate-700">
        {(Object.keys(TILE_LAYERS) as (keyof typeof TILE_LAYERS)[]).map((key) => (
          <button
            key={key}
            onClick={() => setTileLayer(key)}
            className={`px-2 py-1 text-xs rounded transition-colors ${
              tileLayer === key
                ? 'bg-sky-600 text-white'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            {key.charAt(0).toUpperCase() + key.slice(1)}
          </button>
        ))}
      </div>

      <MapContainer
        center={[20, 0]}
        zoom={3}
        className="w-full h-full"
        worldCopyJump
        minZoom={2}
        maxZoom={18}
      >
        <TileLayer url={tile.url} attribution={tile.attribution} />

        {selectedAirport && (
          <MapController
            center={[selectedAirport.latitude, selectedAirport.longitude]}
            zoom={8}
          />
        )}

        {/* Route arcs */}
        {routeData.map((route) => (
          <Polyline
            key={route.id}
            positions={[
              [route.from_lat, route.from_lon],
              [route.to_lat, route.to_lon],
            ]}
            pathOptions={{
              color: route.airline_color,
              weight: 1.5,
              opacity: 0.6,
              dashArray: '4 6',
            }}
          />
        ))}

        {/* Airport markers */}
        {airportData.map((airport) => (
          <CircleMarker
            key={airport.id}
            center={[airport.latitude, airport.longitude]}
            radius={sizeToRadius(airport.size, 3)}
            pathOptions={{
              fillColor:
                airport.id === selectedAirportId ? '#ef4444' : sizeToColor(airport.size),
              color: airport.id === selectedAirportId ? '#fff' : 'transparent',
              fillOpacity: airport.id === selectedAirportId ? 1 : 0.75,
              weight: airport.id === selectedAirportId ? 2 : 0,
            }}
            eventHandlers={{
              click: () => onAirportClick?.(airport),
            }}
          >
            <Popup>
              <div className="text-sm">
                <p className="font-bold">
                  {airport.iata} - {airport.name}
                </p>
                <p className="text-gray-600">
                  {airport.city}, {airport.country_code}
                </p>
                <p className="text-gray-500 text-xs">
                  Size: {airport.size} | {airport.latitude.toFixed(3)},{' '}
                  {airport.longitude.toFixed(3)}
                </p>
              </div>
            </Popup>
          </CircleMarker>
        ))}
      </MapContainer>
    </div>
  );
}
