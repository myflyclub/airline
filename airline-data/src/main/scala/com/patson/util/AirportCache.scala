package com.patson.util

import com.github.benmanes.caffeine.cache.{Caffeine, CacheLoader, LoadingCache}
import com.patson.data.AirportSource
import com.patson.model._

import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._

object AirportCache {

  val detailedCache: LoadingCache[Int, Option[Airport]] = Caffeine.newBuilder().maximumSize(2500).expireAfterAccess(30, TimeUnit.MINUTES).build[Int, Option[Airport]](new DetailedLoader())
  val simpleCache: LoadingCache[Int, Option[Airport]] = Caffeine.newBuilder().maximumSize(5000).expireAfterAccess(30, TimeUnit.MINUTES).build[Int, Option[Airport]](new SimpleLoader())

  def getAirport(airportId: Int, fullLoad: Boolean = false): Option[Airport] = {
    if (fullLoad) {
      detailedCache.get(airportId)
    } else {
      simpleCache.get(airportId)
    }
  }

  def getAirports(airportIds: List[Int], fullLoad: Boolean = false) : Map[Int, Airport] = {
    val cache = if (fullLoad) detailedCache else simpleCache
    cache.getAll(airportIds.asJava).asScala.collect { case (id, Some(airport)) => (id, airport) }.toMap
  }

  def getAllAirports(fullLoad: Boolean = false): List[Airport] = {
    val allAirportIds = AirportSource.loadAllAirportIds()
    getAirports(allAirportIds, fullLoad).values.toList
  }

  def refreshAirport(airportId: Int, fullLoad: Boolean = false) = {
    invalidateAirport(airportId)
    getAirport(airportId, fullLoad)
  }

  def invalidateAirport(airportId: Int) = {
    detailedCache.invalidate(airportId)
    simpleCache.invalidate(airportId)
  }

  def invalidateAll() = {
    detailedCache.invalidateAll()
    simpleCache.invalidateAll()
  }

  def getCacheStats: String = {
    val detailedStats = detailedCache.stats()
    val simpleStats = simpleCache.stats()
    s"Detailed cache - Size: ${detailedCache.estimatedSize()}, Hit rate: ${detailedStats.hitRate()}, Evictions: ${detailedStats.evictionCount()}; " +
      s"Simple cache - Size: ${simpleCache.estimatedSize()}, Hit rate: ${simpleStats.hitRate()}, Evictions: ${simpleStats.evictionCount()}"
  }

  class DetailedLoader extends CacheLoader[Int, Option[Airport]] {
    override def load(airportId: Int) = {
      AirportSource.loadAirportById(airportId, true)
    }

    override def loadAll(keys: java.util.Set[_ <: Int]): java.util.Map[_ <: Int, _ <: Option[Airport]] = {
      val airports = AirportSource.loadAirportsByIds(keys.asScala.toList, fullLoad = true)
      val airportMap = airports.map(airport => (airport.id, Some(airport))).toMap
      //for keys that are not found, we should still return a None in the map
      val result = keys.asScala.map(key => (key, airportMap.get(key).flatten))
      result.toMap.asJava
    }
  }

  class SimpleLoader extends CacheLoader[Int, Option[Airport]] {
    override def load(airportId: Int) = {
      AirportSource.loadAirportById(airportId, loadFeatures = true)
    }

    override def loadAll(keys: java.util.Set[_ <: Int]): java.util.Map[_ <: Int, _ <: Option[Airport]] = {
      val airports = AirportSource.loadAirportsByIds(keys.asScala.toList, loadFeatures = true)
      val airportMap = airports.map(airport => (airport.id, Some(airport))).toMap
      //for keys that are not found, we should still return a None in the map
      val result = keys.asScala.map(key => (key, airportMap.get(key).flatten))
      result.toMap.asJava
    }
  }
}
