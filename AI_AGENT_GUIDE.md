# FlightForge AI Agent Development Guide

This guide provides essential information for AI agents working on the FlightForge project.

## Quick Reference

### Starting the Server

**Using Docker (Recommended):**
```bash
# Start all services
docker compose up -d

# Wait for containers to be healthy
docker compose ps

# Initialize and start application (first time or after major changes)
docker compose exec airline-app bash /home/airline/start-all.sh

# Fast start (use after first successful initialization)
docker compose exec airline-app bash /home/airline/start-all-fast.sh
```

**Access Points:**
- Main Application: http://localhost:9000
- Admin Panel: http://localhost:9001

### Stopping the Server

```bash
# Stop all services
docker compose stop

# Stop and remove containers (keeps data)
docker compose down

# Nuclear option - remove everything including data
docker compose down -v
```

### Viewing Logs

**Docker logs:**
```bash
# All services (follow mode)
docker compose logs -f

# Specific service
docker compose logs -f airline-app

# Admin panel
docker compose logs -f admin-panel

# Last 100 lines
docker compose logs --tail=100 airline-app
```

**In-container simulation logs:**
```bash
docker compose exec airline-app bash
tail -f /tmp/sim.log    # Simulation log
tail -f /tmp/web.log    # Web server log
```

**Database logs (Bot AI decisions):**
```sql
-- Connect to MySQL
docker compose exec airline-db mysql -umfc01 -pghEtmwBdnXYBQH4 airline

-- View recent bot AI decisions
SELECT * FROM log
WHERE category = 1  -- NEGOTIATION category
ORDER BY cycle DESC
LIMIT 100;

-- View bot alerts
SELECT a.*, al.name as airline_name
FROM alert a
JOIN airline al ON a.airline = al.id
WHERE al.airline_type = 2
ORDER BY a.cycle DESC;
```

---

## Project Structure

```
FlightForge/
├── airline-data/                    # Backend simulation engine (Scala)
│   └── src/main/scala/com/patson/
│       ├── BotAISimulation.scala   # BOT AI CORE FILE (Phase 4 - Layered Brain)
│       ├── MainSimulation.scala    # Main simulation loop
│       ├── LinkSimulation.scala    # Route/link simulation
│       ├── AirlineSimulation.scala # Airline financial simulation
│       ├── DemandGenerator.scala   # Passenger demand calculation
│       ├── Computation.scala       # Distance, pricing calculations
│       ├── data/                   # Database access layer
│       │   ├── LinkSource.scala    # Route data CRUD
│       │   ├── AirlineSource.scala # Airline data CRUD
│       │   ├── AlertSource.scala   # Alert system
│       │   └── LogSource.scala     # Logging/telemetry
│       └── model/                  # Data models
│           ├── Airline.scala       # Airline model (includes AirlineType)
│           ├── Link.scala          # Route model
│           ├── Alert.scala         # Alert model
│           └── Log.scala           # Log model
├── airline-web/                    # Web frontend (Play Framework)
│   ├── app/controllers/           # API controllers
│   └── public/                    # Static assets
├── admin-panel/                   # Admin dashboard (Python/Flask)
│   ├── app.py                     # Main Flask app
│   └── templates/                 # HTML templates
└── docker-compose.yaml            # Docker orchestration
```

---

## Key Concepts

### Airline Types
```scala
object AirlineType extends Enumeration {
  val LEGACY = Value        // Regular player
  val BEGINNER = Value      // New player
  val NON_PLAYER = Value    // BOT (AI-controlled)
  val DISCOUNT = Value      // Discount airline
  val LUXURY = Value        // Luxury airline
  val REGIONAL = Value      // Regional airline
  val MEGA_HQ = Value       // Mega HQ type
  val NOSTALGIA = Value     // Nostalgia type
}
```

### Bot Personalities (BotAISimulation.scala)
| Personality | Strategy | Price Multiplier |
|-------------|----------|------------------|
| AGGRESSIVE | Price wars, rapid expansion | 0.92 (8% below market) |
| CONSERVATIVE | Slow growth, high margins | 1.12 (12% above market) |
| BALANCED | Adaptive strategy | 1.0 (market rate) |
| REGIONAL | Small airports, domestic focus | 0.95 (5% below market) |
| PREMIUM | High quality, high prices | 1.35 (35% above market) |
| BUDGET | Low cost carrier model | 0.72 (28% below market) |

### Simulation Cycle
- Each cycle = 1 week game time
- Real-world duration: ~29 minutes by default
- Bot AI runs at the end of each cycle

---

## Bot AI Architecture (Phase 4 - Layered Brain)

```
LAYER 1: SURVIVAL (Always First)
├── Check financial health
├── EMERGENCY (<-$5M): Abandon all unprofitable routes, sell aircraft
├── CRITICAL (<-$2M): Cut severely unprofitable routes
└── WARNING (<$2M): Force conservative mode

LAYER 2: PLAYER AWARENESS (Always Runs)
├── Scan all routes for player competitors
├── Detect NEW player entries
├── IMMEDIATE personality-based response
└── Update memory (PlayerCompetitionTracker)

LAYER 3: ROUTE HEALTH (Condition-Triggered)
├── Analyze ALL routes
├── Assign recommendations: ABANDON/REDUCE/OPTIMIZE/EXPAND/HOLD
└── Execute actions for routes needing attention

LAYER 4: STRATEGIC PLANNING (Probability-Gated)
├── 20% → Plan new routes (if financially safe)
├── 5%  → Purchase aircraft
├── 15% → General route optimization
└── 10% → Legacy competition response
```

---

## Database Tables

**Key tables for bot AI:**
| Table | Purpose |
|-------|---------|
| `airline` | Airline data (balance, reputation, type) |
| `link` | Routes (airports, pricing, frequency) |
| `link_consumption` | Route performance history |
| `airplane` | Aircraft ownership |
| `log` | Bot decision logs (category=1 for NEGOTIATION) |
| `alert` | Low balance alerts |

**Useful queries:**
```sql
-- All bot airlines
SELECT id, name, balance FROM airline WHERE airline_type = 2;

-- Bot route performance (last cycle)
SELECT a.name, lc.profit, lc.cycle,
       CONCAT(ap1.iata, '->', ap2.iata) as route
FROM link_consumption lc
JOIN link l ON lc.link = l.id
JOIN airline a ON l.airline = a.id
JOIN airport ap1 ON l.from_airport = ap1.id
JOIN airport ap2 ON l.to_airport = ap2.id
WHERE a.airline_type = 2
ORDER BY lc.cycle DESC, lc.profit ASC
LIMIT 50;

-- Bot financial health
SELECT name, balance,
       CASE
         WHEN balance < -5000000 THEN 'EMERGENCY'
         WHEN balance < -2000000 THEN 'CRITICAL'
         WHEN balance < 2000000 THEN 'WARNING'
         ELSE 'SAFE'
       END as status
FROM airline
WHERE airline_type = 2
ORDER BY balance ASC;
```

---

## Testing Changes

1. Make changes to Scala files
2. Recompile:
   ```bash
   docker compose exec airline-app bash
   cd /home/airline/airline/airline-data
   sbt compile
   ```
3. Restart simulation (kill existing and restart):
   ```bash
   # Kill existing processes
   pkill -f MainSimulation
   pkill -f "sbt run"

   # Restart
   /home/airline/start-data.sh &
   /home/airline/start-web.sh &
   ```
4. Monitor logs for bot activity

---

## Known Issues / Current State

### Resolved Issues (Phase 4)
- [x] Bot bankruptcy risk - Added emergency protocols
- [x] Player competition blindness - Added player detection and immediate response
- [x] Probability-only decisions - Added condition-triggered actions
- [x] No memory - Added PlayerCompetitionTracker

### Current Behavior
- Bots detect player entries and respond immediately based on personality
- Financial health checked every cycle (EMERGENCY/CRITICAL/WARNING/SAFE)
- Routes automatically optimized based on load factor and profitability
- Route abandonment runs every cycle (no probability gating)

### Potential Future Improvements
- [ ] Alliance formation between bots
- [ ] Hub-and-spoke strategy implementation
- [ ] Seasonal demand awareness
- [ ] Learning from past successful strategies

---

## Adding New Bot Behaviors

1. Add new method in `BotAISimulation` object
2. Decide which layer it belongs to:
   - Layer 1: Survival/Emergency
   - Layer 2: Player Awareness
   - Layer 3: Route Health
   - Layer 4: Strategic Planning
3. Call from `simulate()` method in appropriate layer
4. Add logging via `LogSource.insertLogs()`
5. Test with single bot first (filter by `airline.id`)

**Example - Adding a new behavior:**
```scala
private def myNewBehavior(airline: Airline, personality: BotPersonality, cycle: Int): Unit = {
  println(s"[${airline.name}] Executing new behavior...")

  // Your logic here

  // Log for telemetry
  LogSource.insertLogs(List(Log(
    airline,
    s"Executed new behavior",
    LogCategory.NEGOTIATION,
    LogSeverity.INFO,
    cycle,
    Map("key" -> "value")
  )))
}
```

---

## Debugging Tips

- Add `println()` statements (visible in docker logs)
- Use `LogSource.insertLogs()` for persistent logging to database
- Check `alert` table for LOW_BALANCE warnings
- Query `link_consumption` for route performance
- Use admin panel `/api/stats` endpoint for quick overview
- Filter bots: `WHERE airline_type = 2` in SQL queries

---

## Contact & Resources

- Project Repository: Check `.git` for remote URL
- Docker Guide: `DOCKER_GUIDE.md`
- Bot AI System Doc: `BOT_AI_SYSTEM.md`
