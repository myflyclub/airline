package com.patson.util

import java.util.concurrent.TimeUnit

import com.patson.data.AirportStatisticsSource
import com.patson.model._


object AirportStatisticsCache {

  import com.github.benmanes.caffeine.cache.{Caffeine, CacheLoader, LoadingCache}

  val simpleCache: LoadingCache[Int, Option[AirportStatistics]] = Caffeine.newBuilder().maximumSize(4000).build(new SimpleLoader())

  def getAirportStatistics(airportId : Int) : Option[AirportStatistics] = {
    simpleCache.get(airportId)
  }

  def invalidateAirportStats(airportId : Int) = {
    simpleCache.invalidate(airportId)
  }

  def invalidateAll() = {
    simpleCache.invalidateAll()
  }

  class SimpleLoader extends CacheLoader[Int, Option[AirportStatistics]] {
    override def load(airportId: Int) = {
      AirportStatisticsSource.loadAirportStatsById(airportId)
    }
  }


}
