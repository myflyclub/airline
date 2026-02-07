const API_BASE = '/api';

function getToken(): string | null {
  return localStorage.getItem('token');
}

async function request<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...((options.headers as Record<string, string>) || {}),
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  });

  if (!res.ok) {
    const error = await res.json().catch(() => ({ detail: res.statusText }));
    throw new Error(error.detail || `Request failed: ${res.status}`);
  }

  return res.json();
}

// ---- Auth ----
export const auth = {
  register: (data: { username: string; email: string; password: string }) =>
    request('/auth/register', { method: 'POST', body: JSON.stringify(data) }),
  login: (data: { username: string; password: string }) =>
    request('/auth/login', { method: 'POST', body: JSON.stringify(data) }),
  me: () => request('/auth/me'),
};

// ---- Airports ----
export const airports = {
  list: (params?: { country?: string; min_size?: number; limit?: number; offset?: number }) => {
    const qs = new URLSearchParams();
    if (params?.country) qs.set('country', params.country);
    if (params?.min_size !== undefined) qs.set('min_size', String(params.min_size));
    if (params?.limit !== undefined) qs.set('limit', String(params.limit));
    if (params?.offset !== undefined) qs.set('offset', String(params.offset));
    return request(`/airports/?${qs}`);
  },
  mapPoints: (minSize = 3) =>
    request(`/airports/map?min_size=${minSize}`),
  search: (q: string) =>
    request(`/airports/search?q=${encodeURIComponent(q)}`),
  get: (id: number) => request(`/airports/${id}`),
  getByIata: (iata: string) => request(`/airports/iata/${iata}`),
};

// ---- Airlines ----
export const airlines = {
  list: () => request('/airlines/'),
  get: (id: number) => request(`/airlines/${id}`),
  mine: () => request('/airlines/mine'),
  create: (data: { name: string; airline_code: string; country_code?: string }) =>
    request('/airlines/', { method: 'POST', body: JSON.stringify(data) }),
  update: (id: number, data: Record<string, unknown>) =>
    request(`/airlines/${id}`, { method: 'PATCH', body: JSON.stringify(data) }),
  getBases: (id: number) => request(`/airlines/${id}/bases`),
  createBase: (id: number, data: { airport_id: number; base_type?: string }) =>
    request(`/airlines/${id}/bases`, { method: 'POST', body: JSON.stringify(data) }),
  getLoans: (id: number) => request(`/airlines/${id}/loans`),
  takeLoan: (id: number, data: { amount: number; term_cycles: number }) =>
    request(`/airlines/${id}/loans`, { method: 'POST', body: JSON.stringify(data) }),
};

// ---- Aircraft ----
export const aircraft = {
  models: (params?: { aircraft_type?: string; min_range?: number }) => {
    const qs = new URLSearchParams();
    if (params?.aircraft_type) qs.set('aircraft_type', params.aircraft_type);
    if (params?.min_range) qs.set('min_range', String(params.min_range));
    return request(`/aircraft/models?${qs}`);
  },
  getModel: (id: number) => request(`/aircraft/models/${id}`),
  fleet: (airlineId: number) => request(`/aircraft/fleet/${airlineId}`),
  purchase: (data: { model_id: number; quantity?: number; home_base_id?: number }) =>
    request('/aircraft/purchase', { method: 'POST', body: JSON.stringify(data) }),
  sell: (id: number) => request(`/aircraft/${id}`, { method: 'DELETE' }),
};

// ---- Routes ----
export const routes = {
  airlineRoutes: (airlineId: number) => request(`/routes/airline/${airlineId}`),
  airportRoutes: (airportId: number) => request(`/routes/airport/${airportId}`),
  mapData: (airlineId: number) => request(`/routes/map/${airlineId}`),
  suggestPrice: (fromId: number, toId: number) =>
    request(`/routes/suggest-price/${fromId}/${toId}`),
  create: (data: Record<string, unknown>) =>
    request('/routes/', { method: 'POST', body: JSON.stringify(data) }),
  update: (id: number, data: Record<string, unknown>) =>
    request(`/routes/${id}`, { method: 'PATCH', body: JSON.stringify(data) }),
  delete: (id: number) => request(`/routes/${id}`, { method: 'DELETE' }),
  assign: (routeId: number, data: { aircraft_id: number; frequency?: number }) =>
    request(`/routes/${routeId}/assign`, { method: 'POST', body: JSON.stringify(data) }),
};
