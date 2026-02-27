package com.patson.util

import java.util.concurrent.TimeUnit

import com.patson.data.CountrySource
import com.patson.model._


object CountryCache {

  import com.github.benmanes.caffeine.cache.{Caffeine, CacheLoader, LoadingCache}

  val simpleCache: LoadingCache[String, Option[Country]] = Caffeine.newBuilder().maximumSize(1000).expireAfterAccess(30, TimeUnit.MINUTES).build(new SimpleLoader())

  def getCountry(countryCode : String) : Option[Country] = {
    simpleCache.get(countryCode)
  }

  def invalidateCountry(countryCode : String) = {
    simpleCache.invalidate(countryCode)
  }

  def invalidateAll() = {
    simpleCache.invalidateAll()
  }

  class SimpleLoader extends CacheLoader[String, Option[Country]] {
    override def load(countryCode: String) = {
      CountrySource.loadCountryByCode(countryCode)
    }
  }


}



