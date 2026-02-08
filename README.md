# FlightForge V2

An open-source airline simulation game. Build and manage your own airline empire — create routes, purchase aircraft, manage finances, and compete on a world map with real airport data.

**Live at**: [https://flightforge.app/](https://flightforge.app/)
**Forked from**: [Airline Club](https://www.airline-club.com/)

## V2 Tech Stack

| Component | Technology |
|-----------|-----------|
| **Backend** | Python 3.12 + FastAPI + SQLAlchemy |
| **Frontend** | React 19 + TypeScript + Vite |
| **Styling** | Tailwind CSS |
| **Maps** | React-Leaflet + OpenStreetMap |
| **Database** | SQLite (dev) / PostgreSQL (prod) |
| **Auth** | JWT (python-jose + bcrypt) |
| **Admin Panel** | FastAPI + Jinja2 (port 9001) |
| **Real-time** | FastAPI WebSocket |

## Dependencies

- Python 3.11+
- Node.js 20+
- npm 10+

## Quick Start

### 1. Backend Setup

```bash
cd backend

# Create virtual environment
python -m venv venv
source venv/bin/activate  # Linux/Mac
# venv\Scripts\activate   # Windows

# Install dependencies
pip install -r requirements.txt

# Run the server (auto-seeds airport data on first run)
uvicorn app.main:app --reload --port 8000
```

The backend will:
- Create the SQLite database automatically
- Load 82,797 airports from the CSV data on first boot
- Seed 24 aircraft models (from ATR-42 to A380)
- Serve the API at `http://localhost:8000`
- Auto-generate API docs at `http://localhost:8000/docs`

### 2. Frontend Setup

```bash
cd frontend

# Install dependencies
npm install

# Start development server
npm run dev
```

The frontend runs at `http://localhost:5173` and proxies API calls to the backend.

### 3. Admin Panel Setup

```bash
cd admin

# Create virtual environment
python -m venv venv
source venv/bin/activate

# Install dependencies
pip install -r requirements.txt

# Run the admin panel on port 9001
uvicorn app:app --reload --port 9001
```

The admin panel is accessible at `http://localhost:9001`.

## Docker Setup

```bash
# Start everything
docker compose -f docker-compose.v2.yaml up -d
```

| Service | Port | URL |
|---------|------|-----|
| Backend API | 8000 | http://localhost:8000 |
| Frontend | 5173 | http://localhost:5173 |
| Admin Panel | 9001 | http://localhost:9001 |

## Project Structure

```
FlightForge/
├── backend/                  # FastAPI backend
│   ├── app/
│   │   ├── main.py          # App entry point + startup
│   │   ├── config.py        # Settings & environment
│   │   ├── database.py      # SQLAlchemy setup
│   │   ├── models/          # Database models
│   │   │   ├── airport.py   # Airport, Runway, AirportFeature
│   │   │   ├── airline.py   # Airline, AirlineBase, Loan, FinancialRecord
│   │   │   ├── aircraft.py  # AircraftModel, Aircraft
│   │   │   ├── route.py     # Route, RouteAssignment, RouteStatistics
│   │   │   └── user.py      # User
│   │   ├── routers/         # API endpoints
│   │   │   ├── auth.py      # Register, login, JWT
│   │   │   ├── airports.py  # Airport CRUD + search + map data
│   │   │   ├── airlines.py  # Airline CRUD + bases + loans
│   │   │   ├── aircraft.py  # Fleet management + marketplace
│   │   │   ├── routes.py    # Route CRUD + pricing + assignments
│   │   │   └── websocket.py # Real-time chat & notifications
│   │   ├── schemas/         # Pydantic request/response models
│   │   ├── services/
│   │   │   └── simulation.py # Game cycle simulation engine
│   │   └── utils/
│   │       ├── auth.py      # JWT + password hashing
│   │       └── data_loader.py # CSV airport importer + aircraft seeder
│   ├── data/
│   │   └── airports.csv     # 82,797 real-world airports
│   └── requirements.txt
├── frontend/                 # React frontend
│   ├── src/
│   │   ├── App.tsx          # Router + auth guard
│   │   ├── components/
│   │   │   ├── Auth/        # Login/register page
│   │   │   ├── Layout/      # Sidebar + app layout
│   │   │   └── Map/         # Interactive airport map
│   │   ├── pages/
│   │   │   ├── MapPage.tsx       # World map with airport search
│   │   │   ├── DashboardPage.tsx # Airline overview + creation
│   │   │   ├── FleetPage.tsx     # Fleet management
│   │   │   ├── RoutesPage.tsx    # Route network management
│   │   │   ├── FinancesPage.tsx  # Balance, loans, net worth
│   │   │   ├── AirportsPage.tsx  # Airport directory
│   │   │   └── MarketplacePage.tsx # Aircraft catalog
│   │   ├── hooks/useAuth.ts      # Auth state management
│   │   ├── services/api.ts       # API client
│   │   └── types/index.ts        # TypeScript type definitions
│   ├── package.json
│   └── vite.config.ts
├── admin/                    # Admin panel (port 9001)
│   ├── app.py               # FastAPI admin API + dashboard
│   ├── templates/
│   │   └── dashboard.html   # Single-page admin UI
│   └── requirements.txt
└── airline-data/             # Legacy V1 data files (reference)
    ├── airports.csv          # Source airport coordinate data
    ├── runways.csv
    └── destinations.csv
```

## Features

### Player Features
- **World Map** — Interactive Leaflet map with 82K+ real airports, search, and route visualization
- **Airline Management** — Create and manage your airline with custom name, code, and branding
- **Fleet Management** — Purchase from 24 real aircraft models (ATR-42 to A380), manage condition and assignments
- **Route Network** — Create routes with auto-calculated distance and suggested pricing across 4 fare classes
- **Finances** — Track balance, take loans, monitor cash flow
- **Airport Browser** — Search and filter airports by size, country, name, or IATA code

### Admin Features (port 9001)
- **Server Monitoring** — Real-time CPU, memory, disk, and network metrics with color-coded alerts
- **User Management** — Search users, view details, ban/unban accounts
- **Airline Administration** — View all airlines, inspect routes/fleet/bases, reset or delete airlines
- **Database Statistics** — Airport counts, fleet size, route network overview
- **Game Control** — View current cycle, trigger simulation turns manually
- **System Alerts** — Automatic warnings for resource thresholds

## API Documentation

With the backend running, visit:
- **Swagger UI**: http://localhost:8000/docs
- **ReDoc**: http://localhost:8000/redoc

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DATABASE_URL` | `sqlite:///./flightforge.db` | Database connection string |
| `SECRET_KEY` | (built-in dev key) | JWT signing key — **change in production** |
| `CORS_ORIGINS` | `["http://localhost:5173"]` | Allowed CORS origins |
| `ACCESS_TOKEN_EXPIRE_MINUTES` | `1440` | JWT token lifetime |

## Nginx Reverse Proxy

Example nginx config for production with Cloudflare HTTPS:

```nginx
server {
    listen 443 ssl http2;
    server_name flightforge.app;

    ssl_certificate     /etc/ssl/flightforge.app.crt;
    ssl_certificate_key /etc/ssl/flightforge.app.key;

    # Frontend
    location / {
        proxy_pass http://localhost:5173;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
    }

    # Backend API
    location /api {
        proxy_pass http://localhost:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # WebSocket
    location /ws {
        proxy_pass http://localhost:8000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
    }
}
```

## Attribution
- Airport data sourced from [OurAirports](https://ourairports.com/)
- Some icons by [Yusuke Kamiyamane](http://p.yusukekamiyamane.com/) — [CC BY 3.0](http://creativecommons.org/licenses/by/3.0/)
