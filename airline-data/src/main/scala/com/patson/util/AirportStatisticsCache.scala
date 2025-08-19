package com.patson.util

import java.util.concurrent.TimeUnit

import com.patson.data.AirportStatisticsSource
import com.patson.model._


object AirportStatisticsCache {

  import com.google.common.cache.{CacheBuilder, CacheLoader, LoadingCache}

  val simpleCache: LoadingCache[Int, Option[AirportStatistics]] = CacheBuilder.newBuilder.maximumSize(1000).expireAfterAccess(10, TimeUnit.MINUTES).build(new SimpleLoader())

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
