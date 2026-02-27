package com.patson.util

import java.util.concurrent.TimeUnit

import com.patson.data.UserSource
import com.patson.model._


object UserCache {

  import com.github.benmanes.caffeine.cache.{Caffeine, CacheLoader, LoadingCache}

  val simpleCache: LoadingCache[Int, Option[User]] = Caffeine.newBuilder().maximumSize(10000).expireAfterAccess(10, TimeUnit.MINUTES).build(new SimpleLoader())

  def getUser(userId: Int): Option[User] = {
    simpleCache.get(userId)
  }

  def invalidateUser(userId: Int) = {
    simpleCache.invalidate(userId)
  }

  def invalidateAll() = {
    simpleCache.invalidateAll()
  }

  class SimpleLoader extends CacheLoader[Int, Option[User]] {
    override def load(userId: Int) = {
      UserSource.loadUserById(userId)
    }
  }

}



