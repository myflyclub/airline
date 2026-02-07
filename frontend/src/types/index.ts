// ---- User & Auth ----
export interface User {
  id: number;
  username: string;
  email: string;
  is_active: boolean;
  is_admin: boolean;
  created_at?: string;
}

export interface TokenResponse {
  access_token: string;
  token_type: string;
  user: User;
}

// ---- Airport ----
export interface Airport {
  id: number;
  iata: string;
  icao: string;
  name: string;
  latitude: number;
  longitude: number;
  country_code: string;
  city: string;
  size: number;
  airport_type: string;
  elevation: number;
  region: string;
  state_code: string;
  base_income: number;
  base_population: number;
  runway_length: number;
}

export interface AirportMapPoint {
  id: number;
  iata: string;
  name: string;
  latitude: number;
  longitude: number;
  size: number;
  country_code: string;
  city: string;
}

export interface AirportSearchResult {
  id: number;
  iata: string;
  name: string;
  city: string;
  country_code: string;
  size: number;
}

// ---- Airline ----
export interface Airline {
  id: number;
  name: string;
  airline_code: string;
  country_code: string;
  airline_type: string;
  balance: number;
  reputation: number;
  service_quality: number;
  target_service_quality: number;
  stock_price: number;
  logo_url: string;
  color: string;
  slogan: string;
  is_active: boolean;
  owner_id?: number;
}

export interface AirlineSummary {
  id: number;
  name: string;
  airline_code: string;
  balance: number;
  reputation: number;
  fleet_size: number;
  route_count: number;
}

export interface AirlineBase {
  id: number;
  airport_id: number;
  base_type: string;
  scale: number;
  airport_iata: string;
  airport_name: string;
}

// ---- Aircraft ----
export interface AircraftModel {
  id: number;
  name: string;
  family: string;
  manufacturer: string;
  capacity: number;
  quality: number;
  speed: number;
  fuel_burn: number;
  range_miles: number;
  price: number;
  lifespan: number;
  construction_time: number;
  runway_requirement: number;
  aircraft_type: string;
  image_url: string;
}

export interface Aircraft {
  id: number;
  model_id: number;
  model_name: string;
  airline_id: number;
  condition: number;
  age_cycles: number;
  is_active: boolean;
  home_base_id?: number;
  purchase_price: number;
}

// ---- Route ----
export interface Route {
  id: number;
  from_airport_id: number;
  to_airport_id: number;
  airline_id: number;
  distance: number;
  flight_number: number;
  frequency: number;
  duration: number;
  is_active: boolean;
  price_economy: number;
  price_business: number;
  price_first: number;
  price_discount: number;
  capacity_economy: number;
  capacity_business: number;
  capacity_first: number;
  capacity_discount: number;
  raw_quality: number;
  from_airport_iata: string;
  from_airport_name: string;
  to_airport_iata: string;
  to_airport_name: string;
  airline_name: string;
}

export interface RouteMapData {
  id: number;
  from_lat: number;
  from_lon: number;
  to_lat: number;
  to_lon: number;
  from_iata: string;
  to_iata: string;
  airline_id: number;
  airline_color: string;
}

export interface PriceSuggestion {
  economy: number;
  business: number;
  first_class: number;
  discount: number;
}

export interface Loan {
  id: number;
  principal: number;
  remaining: number;
  interest_rate: number;
  term_cycles: number;
  remaining_cycles: number;
}
