"""FlightForge V2 Admin Panel

Standalone admin dashboard running on port 9001.
Connects to the same SQLite database as the main backend.
Provides server monitoring, user management, game controls, and bot management.
"""

import os
import time
import platform
from datetime import datetime, timezone
from pathlib import Path

import psutil
from fastapi import FastAPI, Request, Query
from fastapi.responses import HTMLResponse
from fastapi.templating import Jinja2Templates
from sqlalchemy import create_engine, text, func, inspect
from sqlalchemy.orm import sessionmaker, Session

# ---------------------------------------------------------------------------
# Database setup – connects to same DB as main backend
# ---------------------------------------------------------------------------
DB_PATH = os.environ.get(
    "DATABASE_URL",
    f"sqlite:///{Path(__file__).resolve().parent.parent / 'backend' / 'flightforge.db'}",
)

engine = create_engine(
    DB_PATH,
    connect_args={"check_same_thread": False} if "sqlite" in DB_PATH else {},
)
SessionLocal = sessionmaker(bind=engine, autocommit=False, autoflush=False)

# ---------------------------------------------------------------------------
# App setup
# ---------------------------------------------------------------------------
app = FastAPI(title="FlightForge Admin Panel", version="2.0.0")
templates = Jinja2Templates(directory=str(Path(__file__).parent / "templates"))

BOOT_TIME = time.time()
TRIGGER_DIR = "/tmp/admin-triggers"


def get_db() -> Session:
    db = SessionLocal()
    try:
        return db
    except Exception:
        db.close()
        raise


# ---------------------------------------------------------------------------
# Helper: safe table query (returns empty list if table doesn't exist)
# ---------------------------------------------------------------------------
def _table_exists(db: Session, table: str) -> bool:
    insp = inspect(engine)
    return table in insp.get_table_names()


def _safe_query(db: Session, query_str: str, params: dict | None = None):
    try:
        result = db.execute(text(query_str), params or {})
        return [dict(row._mapping) for row in result]
    except Exception:
        return []


def _safe_scalar(db: Session, query_str: str, params: dict | None = None):
    try:
        result = db.execute(text(query_str), params or {})
        row = result.fetchone()
        return row[0] if row else 0
    except Exception:
        return 0


# ============================================================================
# PAGE ROUTES
# ============================================================================

@app.get("/", response_class=HTMLResponse)
async def dashboard(request: Request):
    return templates.TemplateResponse("dashboard.html", {"request": request})


# ============================================================================
# API: SERVER MONITORING
# ============================================================================

@app.get("/api/server/resources")
def server_resources():
    cpu_freq = psutil.cpu_freq()
    mem = psutil.virtual_memory()
    disk = psutil.disk_usage("/")
    swap = psutil.swap_memory()
    net = psutil.net_io_counters()
    uptime_seconds = time.time() - psutil.boot_time()

    return {
        "cpu": {
            "percent": psutil.cpu_percent(interval=0.5),
            "cores": psutil.cpu_count(logical=True),
            "frequency_mhz": round(cpu_freq.current, 1) if cpu_freq else 0,
        },
        "memory": {
            "used_gb": round(mem.used / (1024 ** 3), 2),
            "total_gb": round(mem.total / (1024 ** 3), 2),
            "percent": mem.percent,
        },
        "disk": {
            "used_gb": round(disk.used / (1024 ** 3), 2),
            "total_gb": round(disk.total / (1024 ** 3), 2),
            "percent": disk.percent,
        },
        "swap": {
            "used_gb": round(swap.used / (1024 ** 3), 2),
            "total_gb": round(swap.total / (1024 ** 3), 2),
            "percent": swap.percent,
        },
        "network": {
            "bytes_sent_gb": round(net.bytes_sent / (1024 ** 3), 2),
            "bytes_recv_gb": round(net.bytes_recv / (1024 ** 3), 2),
        },
        "system": {
            "uptime_hours": round(uptime_seconds / 3600, 1),
            "boot_time": datetime.fromtimestamp(psutil.boot_time(), tz=timezone.utc).isoformat(),
            "process_count": len(psutil.pids()),
            "platform": platform.platform(),
            "python_version": platform.python_version(),
        },
    }


@app.get("/api/alerts")
def system_alerts():
    alerts = []
    cpu = psutil.cpu_percent(interval=0.3)
    mem = psutil.virtual_memory().percent
    disk = psutil.disk_usage("/").percent

    for name, value, warn, crit in [
        ("CPU", cpu, 75, 90),
        ("Memory", mem, 75, 90),
        ("Disk", disk, 80, 90),
    ]:
        if value >= crit:
            alerts.append({"level": "critical", "message": f"{name} usage is {value:.1f}%"})
        elif value >= warn:
            alerts.append({"level": "warning", "message": f"{name} usage is {value:.1f}%"})

    # Check database connectivity
    try:
        db = get_db()
        db.execute(text("SELECT 1"))
        db.close()
    except Exception:
        alerts.append({"level": "critical", "message": "Database connection failed"})

    if not alerts:
        alerts.append({"level": "ok", "message": "All systems operational"})

    return {"alerts": alerts}


# ============================================================================
# API: USER MANAGEMENT
# ============================================================================

@app.get("/api/stats")
def user_stats():
    db = get_db()
    try:
        total = _safe_scalar(db, "SELECT COUNT(*) FROM users")
        active = _safe_scalar(
            db,
            "SELECT COUNT(*) FROM users WHERE last_login IS NOT NULL",
        )
        admins = _safe_scalar(db, "SELECT COUNT(*) FROM users WHERE is_admin = 1")
        return {
            "total_users": total,
            "active_users": active,
            "admin_users": admins,
        }
    finally:
        db.close()


@app.get("/api/users")
def list_users(
    page: int = Query(1, ge=1),
    per_page: int = Query(50, ge=1, le=200),
    search: str = Query("", max_length=100),
):
    db = get_db()
    try:
        offset = (page - 1) * per_page

        if search:
            pattern = f"%{search}%"
            total = _safe_scalar(
                db,
                "SELECT COUNT(*) FROM users WHERE username LIKE :p OR email LIKE :p",
                {"p": pattern},
            )
            rows = _safe_query(
                db,
                "SELECT id, username, email, is_active, is_admin, created_at, last_login "
                "FROM users WHERE username LIKE :p OR email LIKE :p "
                "ORDER BY id DESC LIMIT :limit OFFSET :offset",
                {"p": pattern, "limit": per_page, "offset": offset},
            )
        else:
            total = _safe_scalar(db, "SELECT COUNT(*) FROM users")
            rows = _safe_query(
                db,
                "SELECT id, username, email, is_active, is_admin, created_at, last_login "
                "FROM users ORDER BY id DESC LIMIT :limit OFFSET :offset",
                {"limit": per_page, "offset": offset},
            )

        # Enrich with airline info
        for row in rows:
            airlines = _safe_query(
                db,
                "SELECT id, name, airline_code, balance FROM airlines "
                "WHERE owner_id = :uid AND is_active = 1",
                {"uid": row["id"]},
            )
            row["airlines"] = airlines
            row["status"] = "ACTIVE" if row.get("is_active") else "INACTIVE"

        return {
            "users": rows,
            "total": total,
            "page": page,
            "per_page": per_page,
            "pages": max(1, (total + per_page - 1) // per_page),
        }
    finally:
        db.close()


@app.get("/api/users/{user_id}")
def get_user_detail(user_id: int):
    db = get_db()
    try:
        rows = _safe_query(
            db,
            "SELECT id, username, email, is_active, is_admin, created_at, last_login "
            "FROM users WHERE id = :uid",
            {"uid": user_id},
        )
        if not rows:
            return {"error": "User not found"}

        user = rows[0]
        user["airlines"] = _safe_query(
            db,
            "SELECT a.id, a.name, a.airline_code, a.balance, a.reputation, "
            "a.service_quality, a.airline_type "
            "FROM airlines a WHERE a.owner_id = :uid",
            {"uid": user_id},
        )

        return user
    finally:
        db.close()


@app.post("/api/admin/ban-user/{user_id}")
def ban_user(user_id: int):
    db = get_db()
    try:
        db.execute(
            text("UPDATE users SET is_active = 0 WHERE id = :uid"),
            {"uid": user_id},
        )
        db.commit()
        return {"message": f"User {user_id} banned"}
    finally:
        db.close()


@app.post("/api/admin/unban-user/{user_id}")
def unban_user(user_id: int):
    db = get_db()
    try:
        db.execute(
            text("UPDATE users SET is_active = 1 WHERE id = :uid"),
            {"uid": user_id},
        )
        db.commit()
        return {"message": f"User {user_id} unbanned"}
    finally:
        db.close()


# ============================================================================
# API: DATABASE STATISTICS
# ============================================================================

@app.get("/api/database/stats")
def database_stats():
    db = get_db()
    try:
        stats = {
            "total_airlines": _safe_scalar(db, "SELECT COUNT(*) FROM airlines"),
            "active_airlines": _safe_scalar(
                db, "SELECT COUNT(*) FROM airlines WHERE is_active = 1"
            ),
            "total_airports": _safe_scalar(db, "SELECT COUNT(*) FROM airports"),
            "large_airports": _safe_scalar(
                db, "SELECT COUNT(*) FROM airports WHERE size >= 7"
            ),
            "medium_airports": _safe_scalar(
                db, "SELECT COUNT(*) FROM airports WHERE size >= 5 AND size < 7"
            ),
            "active_routes": _safe_scalar(
                db, "SELECT COUNT(*) FROM routes WHERE is_active = 1"
            ),
            "total_aircraft": _safe_scalar(db, "SELECT COUNT(*) FROM aircraft"),
            "active_aircraft": _safe_scalar(
                db, "SELECT COUNT(*) FROM aircraft WHERE is_active = 1"
            ),
            "aircraft_models": _safe_scalar(db, "SELECT COUNT(*) FROM aircraft_models"),
            "total_users": _safe_scalar(db, "SELECT COUNT(*) FROM users"),
            "airline_bases": _safe_scalar(db, "SELECT COUNT(*) FROM airline_bases"),
            "total_loans": _safe_scalar(db, "SELECT COUNT(*) FROM loans"),
        }

        # Database file size
        db_file = DB_PATH.replace("sqlite:///", "")
        if os.path.exists(db_file):
            stats["db_size_mb"] = round(os.path.getsize(db_file) / (1024 * 1024), 2)
        else:
            stats["db_size_mb"] = 0

        return stats
    finally:
        db.close()


# ============================================================================
# API: GAME MANAGEMENT
# ============================================================================

@app.get("/api/game/cycle")
def game_cycle():
    """Get current game cycle info."""
    # We read the simulation engine's cycle from the route_statistics table
    db = get_db()
    try:
        max_cycle = _safe_scalar(
            db, "SELECT COALESCE(MAX(cycle), 0) FROM route_statistics"
        )
        week = (max_cycle % 52) + 1
        year = (max_cycle // 52) + 1
        return {"cycle": max_cycle, "week": week, "year": year}
    finally:
        db.close()


@app.get("/api/game/activity")
def game_activity():
    db = get_db()
    try:
        # Top airlines by balance
        top_airlines = _safe_query(
            db,
            "SELECT id, name, airline_code, balance, reputation "
            "FROM airlines WHERE is_active = 1 "
            "ORDER BY balance DESC LIMIT 10",
        )

        # Busiest routes
        busiest = _safe_query(
            db,
            "SELECT r.id, a1.iata AS from_iata, a2.iata AS to_iata, "
            "r.distance, r.frequency, al.name AS airline_name "
            "FROM routes r "
            "JOIN airports a1 ON r.from_airport_id = a1.id "
            "JOIN airports a2 ON r.to_airport_id = a2.id "
            "JOIN airlines al ON r.airline_id = al.id "
            "WHERE r.is_active = 1 "
            "ORDER BY r.frequency DESC LIMIT 10",
        )

        # Recent airlines
        recent = _safe_query(
            db,
            "SELECT id, name, airline_code, balance "
            "FROM airlines ORDER BY id DESC LIMIT 5",
        )

        return {
            "top_airlines": top_airlines,
            "busiest_routes": busiest,
            "recent_airlines": recent,
        }
    finally:
        db.close()


@app.post("/api/admin/trigger-turn")
def trigger_turn():
    """Signal the simulation engine to run a cycle."""
    os.makedirs(TRIGGER_DIR, exist_ok=True)
    trigger_file = os.path.join(TRIGGER_DIR, "trigger_turn")
    with open(trigger_file, "w") as f:
        f.write(str(time.time()))
    return {"message": "Turn trigger created", "file": trigger_file}


# ============================================================================
# API: AIRLINE MANAGEMENT (admin actions)
# ============================================================================

@app.post("/api/admin/reset-airline/{airline_id}")
def reset_airline(airline_id: int):
    """Reset an airline: delete routes/aircraft, reset balance."""
    db = get_db()
    try:
        # Check airline exists
        rows = _safe_query(
            db, "SELECT id, name FROM airlines WHERE id = :aid", {"aid": airline_id}
        )
        if not rows:
            return {"error": "Airline not found"}

        name = rows[0]["name"]

        # Delete route assignments first (FK constraint)
        db.execute(
            text(
                "DELETE FROM route_assignments WHERE route_id IN "
                "(SELECT id FROM routes WHERE airline_id = :aid)"
            ),
            {"aid": airline_id},
        )
        # Delete route statistics
        db.execute(
            text(
                "DELETE FROM route_statistics WHERE route_id IN "
                "(SELECT id FROM routes WHERE airline_id = :aid)"
            ),
            {"aid": airline_id},
        )
        # Delete routes
        db.execute(
            text("DELETE FROM routes WHERE airline_id = :aid"),
            {"aid": airline_id},
        )
        # Delete aircraft
        db.execute(
            text("DELETE FROM aircraft WHERE airline_id = :aid"),
            {"aid": airline_id},
        )
        # Delete bases
        db.execute(
            text("DELETE FROM airline_bases WHERE airline_id = :aid"),
            {"aid": airline_id},
        )
        # Delete loans
        db.execute(
            text("DELETE FROM loans WHERE airline_id = :aid"),
            {"aid": airline_id},
        )
        # Delete financial records
        db.execute(
            text("DELETE FROM financial_records WHERE airline_id = :aid"),
            {"aid": airline_id},
        )
        # Reset airline stats
        db.execute(
            text(
                "UPDATE airlines SET balance = 50000000, reputation = 0, "
                "service_quality = 50.0 WHERE id = :aid"
            ),
            {"aid": airline_id},
        )
        db.commit()
        return {"message": f"Airline '{name}' (#{airline_id}) has been reset"}
    finally:
        db.close()


@app.delete("/api/admin/delete-airline/{airline_id}")
def delete_airline(airline_id: int):
    """Permanently delete an airline and all associated data."""
    db = get_db()
    try:
        rows = _safe_query(
            db, "SELECT id, name FROM airlines WHERE id = :aid", {"aid": airline_id}
        )
        if not rows:
            return {"error": "Airline not found"}

        name = rows[0]["name"]

        # Cascade delete all related data
        for table_query in [
            "DELETE FROM route_assignments WHERE route_id IN (SELECT id FROM routes WHERE airline_id = :aid)",
            "DELETE FROM route_statistics WHERE route_id IN (SELECT id FROM routes WHERE airline_id = :aid)",
            "DELETE FROM routes WHERE airline_id = :aid",
            "DELETE FROM aircraft WHERE airline_id = :aid",
            "DELETE FROM airline_bases WHERE airline_id = :aid",
            "DELETE FROM loans WHERE airline_id = :aid",
            "DELETE FROM financial_records WHERE airline_id = :aid",
            "DELETE FROM airlines WHERE id = :aid",
        ]:
            db.execute(text(table_query), {"aid": airline_id})

        db.commit()
        return {"message": f"Airline '{name}' (#{airline_id}) permanently deleted"}
    finally:
        db.close()


@app.get("/api/airlines/all")
def all_airlines():
    """Get all airlines with detailed stats for admin view."""
    db = get_db()
    try:
        airlines = _safe_query(
            db,
            "SELECT a.id, a.name, a.airline_code, a.balance, a.reputation, "
            "a.service_quality, a.airline_type, a.is_active, a.owner_id, "
            "u.username AS owner_name "
            "FROM airlines a "
            "LEFT JOIN users u ON a.owner_id = u.id "
            "ORDER BY a.balance DESC",
        )

        for airline in airlines:
            airline["route_count"] = _safe_scalar(
                db,
                "SELECT COUNT(*) FROM routes WHERE airline_id = :aid AND is_active = 1",
                {"aid": airline["id"]},
            )
            airline["aircraft_count"] = _safe_scalar(
                db,
                "SELECT COUNT(*) FROM aircraft WHERE airline_id = :aid AND is_active = 1",
                {"aid": airline["id"]},
            )
            airline["base_count"] = _safe_scalar(
                db,
                "SELECT COUNT(*) FROM airline_bases WHERE airline_id = :aid",
                {"aid": airline["id"]},
            )

        return {"airlines": airlines}
    finally:
        db.close()


@app.get("/api/airlines/{airline_id}/detail")
def airline_detail(airline_id: int):
    """Get detailed info for a specific airline."""
    db = get_db()
    try:
        airlines = _safe_query(
            db,
            "SELECT a.*, u.username AS owner_name "
            "FROM airlines a "
            "LEFT JOIN users u ON a.owner_id = u.id "
            "WHERE a.id = :aid",
            {"aid": airline_id},
        )
        if not airlines:
            return {"error": "Airline not found"}

        airline = airlines[0]

        # Bases
        airline["bases"] = _safe_query(
            db,
            "SELECT ab.id, ab.base_type, ab.scale, ab.founded_cycle, "
            "ap.iata, ap.name AS airport_name, ap.city "
            "FROM airline_bases ab "
            "JOIN airports ap ON ab.airport_id = ap.id "
            "WHERE ab.airline_id = :aid",
            {"aid": airline_id},
        )

        # Routes
        airline["routes"] = _safe_query(
            db,
            "SELECT r.id, a1.iata AS from_iata, a2.iata AS to_iata, "
            "r.distance, r.frequency, r.price_economy, r.price_business, "
            "r.capacity_economy, r.capacity_business, r.raw_quality "
            "FROM routes r "
            "JOIN airports a1 ON r.from_airport_id = a1.id "
            "JOIN airports a2 ON r.to_airport_id = a2.id "
            "WHERE r.airline_id = :aid AND r.is_active = 1 "
            "ORDER BY r.distance DESC LIMIT 20",
            {"aid": airline_id},
        )

        # Fleet summary
        airline["fleet"] = _safe_query(
            db,
            "SELECT m.name AS model_name, COUNT(*) AS count, "
            "ROUND(AVG(ac.condition), 1) AS avg_condition "
            "FROM aircraft ac "
            "JOIN aircraft_models m ON ac.model_id = m.id "
            "WHERE ac.airline_id = :aid AND ac.is_active = 1 "
            "GROUP BY m.name",
            {"aid": airline_id},
        )

        # Loans
        airline["loans"] = _safe_query(
            db,
            "SELECT * FROM loans WHERE airline_id = :aid AND remaining_cycles > 0",
            {"aid": airline_id},
        )

        return airline
    finally:
        db.close()


# ============================================================================
# API: ROUTE STATISTICS
# ============================================================================

@app.get("/api/routes/overview")
def routes_overview():
    """Get route network statistics."""
    db = get_db()
    try:
        return {
            "total_routes": _safe_scalar(
                db, "SELECT COUNT(*) FROM routes WHERE is_active = 1"
            ),
            "total_distance": _safe_scalar(
                db, "SELECT COALESCE(SUM(distance), 0) FROM routes WHERE is_active = 1"
            ),
            "avg_frequency": round(
                _safe_scalar(
                    db,
                    "SELECT COALESCE(AVG(frequency), 0) FROM routes WHERE is_active = 1",
                )
                or 0,
                1,
            ),
            "avg_distance": round(
                _safe_scalar(
                    db,
                    "SELECT COALESCE(AVG(distance), 0) FROM routes WHERE is_active = 1",
                )
                or 0,
                0,
            ),
            "busiest_airports": _safe_query(
                db,
                "SELECT ap.iata, ap.name, COUNT(*) AS route_count "
                "FROM routes r "
                "JOIN airports ap ON r.from_airport_id = ap.id "
                "WHERE r.is_active = 1 "
                "GROUP BY ap.iata, ap.name "
                "ORDER BY route_count DESC LIMIT 10",
            ),
        }
    finally:
        db.close()


# ============================================================================
# Run with: uvicorn app:app --port 9001
# ============================================================================
