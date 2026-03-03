package com.patson.util

import java.util.concurrent.TimeUnit
import com.patson.data.AirlineSource
import com.patson.model._

import java.util.stream.{Collectors, StreamSupport}


object AirlineCache {
  import scala.jdk.CollectionConverters._
  import com.github.benmanes.caffeine.cache.{Caffeine, CacheLoader, LoadingCache}

  val detailedCache: LoadingCache[Int, Option[Airline]] = Caffeine.newBuilder().maximumSize(5000).expireAfterAccess(10, TimeUnit.MINUTES).build(new DetailedLoader())
  val simpleCache: LoadingCache[Int, Option[Airline]] = Caffeine.newBuilder().maximumSize(5000).expireAfterAccess(10, TimeUnit.MINUTES).build(new SimpleLoader())

  def getAirline(airlineId : Int, fullLoad : Boolean = false) : Option[Airline] = {
    if (fullLoad) {
      detailedCache.get(airlineId)
    } else {
      simpleCache.get(airlineId)
    }
  }

  def getAirlines(airlineIds : List[Int], fullLoad : Boolean = false) : Map[Int, Airline] = {
    val result = {
      if (fullLoad) {
        detailedCache.getAll(airlineIds.asJava).asScala.view.mapValues(_.get).toMap
      } else {
        simpleCache.getAll(airlineIds.asJava).asScala.view.mapValues(_.get).toMap
      }
    }
    result
  }

  def invalidateAirline(airlineId : Int) = {
    detailedCache.invalidate(airlineId)
    simpleCache.invalidate(airlineId)
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

  class DetailedLoader extends CacheLoader[Int, Option[Airline]] {
    override def load(airlineId: Int) = {
      AirlineSource.loadAirlineById(airlineId, true)
    }

    override def loadAll(keys: java.util.Set[_ <: Int]) : java.util.Map[_ <: Int, _ <: scala.Option[com.patson.model.Airline]] = {
      val result = AirlineSource.loadAirlinesByIds(keys.asScala.toList, true)
      val list : Seq[(Int, Option[Airline])] = result.map(airline => (airline.id, Some(airline)))
      list.toMap.asJava
    }
  }

  class SimpleLoader extends CacheLoader[Int, Option[Airline]] {
    override def load(airlineId: Int) = {
      AirlineSource.loadAirlineById(airlineId, false)
    }

    override def loadAll(keys: java.util.Set[_ <: Int]) : java.util.Map[_ <: Int, _ <: scala.Option[com.patson.model.Airline]] = {
      val result = AirlineSource.loadAirlinesByIds(keys.asScala.toList, false)
      val list : Seq[(Int, Option[Airline])] = result.map(airline => (airline.id, Some(airline)))
      list.toMap.asJava
    }
  }


}
