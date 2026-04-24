package controllers

import com.github.benmanes.caffeine.cache.{Cache, Caffeine}
import play.api.libs.json.{JsNull, JsValue}

import java.util.concurrent.TimeUnit

/**
 * Values store a (cycle, JsValue) tuple so the cycle-safety check in each
 * controller still works as a secondary guard even if invalidateAll() hasn't
 * fired yet (e.g. during the brief window between simulation completing and
 * the CycleCompleted actor message being processed).
 *
 * invalidateAll() is called from:
 *   - ActorCenter.LocalMainActor on CycleCompleted
 *   - Application.clearCache() for manual resets
 *
 * expireAfterWrite matches CYCLE_DURATION_SECONDS so entries
 * are naturally evicted after one cycle even if invalidateAll() is not called.
 */
object ResponseCache {

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

  /** Cycle-keyed cache for the global stock benchmarks JSON (single value) */
  @volatile var benchmarks: (Int, JsValue) = (-1, JsNull)

  def invalidateAll(): Unit = {
    benchmarks = (-1, JsNull)
    transitCache.invalidateAll()
    airportDetailCache.invalidateAll()
    demandCache.invalidateAll()
    olympicsDetailsCache.invalidateAll()
    searchRouteCache.invalidateAll()
    researchLinkCache.invalidateAll()
  }
}
