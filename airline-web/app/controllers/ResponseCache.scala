package controllers

import com.github.benmanes.caffeine.cache.{Cache, Caffeine}
import play.api.libs.json.JsValue

import java.util.concurrent.TimeUnit

/**
 * Centralized Guava caches for per-cycle JSON responses.
 *
 * Values store a (cycle, JsValue) tuple so the cycle-safety check in each
 * controller still works as a secondary guard even if invalidateAll() hasn't
 * fired yet (e.g. during the brief window between simulation completing and
 * the CycleCompleted actor message being processed).
 *
 * invalidateAll() is called from:
 *   - ActorCenter.LocalMainActor on CycleCompleted
 *   - Application.clearCache() for manual resets
 *
 * expireAfterWrite matches MainSimulation.CYCLE_DURATION (5 min) so entries
 * are naturally evicted after one cycle even if invalidateAll() is not called.
 */
object ResponseCache {

  /** Mirrors MainSimulation.CYCLE_DURATION = 60 sec * 30 min but cleared via ActorCenter */
  private val CYCLE_DURATION_SECONDS = 60 * 30

  /** Per-airport generic transit data — keyed by airportId */
  val transitCache: Cache[Int, (Int, JsValue)] =
    Caffeine.newBuilder()
      .maximumSize(4000)
      .expireAfterWrite(CYCLE_DURATION_SECONDS, TimeUnit.SECONDS)
      .build[Int, (Int, JsValue)]()

  /** Per-airport detail panel data — keyed by airportId */
  val airportDetailCache: Cache[Int, (Int, JsValue)] =
    Caffeine.newBuilder()
      .maximumSize(4000)
      .expireAfterWrite(CYCLE_DURATION_SECONDS, TimeUnit.SECONDS)
      .build[Int, (Int, JsValue)]()

  /** Per-airport demand data — keyed by airportId */
  val demandCache: Cache[Int, (Int, JsValue)] =
    Caffeine.newBuilder()
      .maximumSize(4000)
      .expireAfterWrite(CYCLE_DURATION_SECONDS, TimeUnit.SECONDS)
      .build[Int, (Int, JsValue)]()

  /** Per-Olympics event detail data — keyed by eventId */
  val olympicsDetailsCache: Cache[Int, (Int, JsValue)] =
    Caffeine.newBuilder()
      .maximumSize(100)
      .expireAfterWrite(CYCLE_DURATION_SECONDS, TimeUnit.SECONDS)
      .build[Int, (Int, JsValue)]()

  /** Per-airport-pair route search results — keyed by "fromId-toId" */
  val searchRouteCache: Cache[String, (Int, JsValue)] =
    Caffeine.newBuilder()
      .maximumSize(1000)
      .expireAfterWrite(CYCLE_DURATION_SECONDS, TimeUnit.SECONDS)
      .build[String, (Int, JsValue)]()

  /** Per-airport-pair research link data — keyed by "fromId-toId" */
  val researchLinkCache: Cache[String, (Int, JsValue)] =
    Caffeine.newBuilder()
      .maximumSize(1000)
      .expireAfterWrite(CYCLE_DURATION_SECONDS, TimeUnit.SECONDS)
      .build[String, (Int, JsValue)]()

  def invalidateAll(): Unit = {
    transitCache.invalidateAll()
    airportDetailCache.invalidateAll()
    demandCache.invalidateAll()
    olympicsDetailsCache.invalidateAll()
    searchRouteCache.invalidateAll()
    researchLinkCache.invalidateAll()
  }
}
