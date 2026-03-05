package com.patson

import com.patson.model._
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.matchers.should.Matchers

class AirlineSimulationSpec extends AnyWordSpecLike with Matchers {

  "getNewQuality" should {
    "for increment at current quality 0, multiplier 1x; current quality 100, multiplier 0.1x" in {
      assert(AirlineSimulation.getNewQuality(0, Airline.EQ_MAX) == 0 + AirlineSimulation.MAX_SERVICE_QUALITY_INCREMENT) //get full increment
      assert(AirlineSimulation.getNewQuality(Airline.EQ_MAX, Airline.EQ_MAX) == Airline.EQ_MAX) //no increment
      assert(AirlineSimulation.getNewQuality(50, Airline.EQ_MAX) < 50 + AirlineSimulation.MAX_SERVICE_QUALITY_INCREMENT) //slow down
      assert(AirlineSimulation.getNewQuality(50, Airline.EQ_MAX) > 50) //but should still have increment
      assert(AirlineSimulation.getNewQuality(Airline.EQ_MAX - 1, Airline.EQ_MAX) < Airline.EQ_MAX) //slow down
      assert(AirlineSimulation.getNewQuality(Airline.EQ_MAX - 1, Airline.EQ_MAX) > Airline.EQ_MAX - 1) //but should still have increment
    }
    "for decrement at current quality 0, multiplier 0.1x; current quality 100, multiplier 1x" in {
      assert(AirlineSimulation.getNewQuality(Airline.EQ_MAX, 0) == Airline.EQ_MAX - AirlineSimulation.MAX_SERVICE_QUALITY_DECREMENT) //get full decrement
      assert(AirlineSimulation.getNewQuality(0, 0) == 0) //no decrement
      assert(AirlineSimulation.getNewQuality(50, 0) > 50 - AirlineSimulation.MAX_SERVICE_QUALITY_DECREMENT) //slow down
      assert(AirlineSimulation.getNewQuality(50, 0) < 50) //but should still have decrement
      assert(AirlineSimulation.getNewQuality(1, 0) == 0)
    }
  }
}