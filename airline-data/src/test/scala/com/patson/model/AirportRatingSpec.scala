package com.patson.model


import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.ImplicitSender
import org.apache.pekko.testkit.TestKit
import org.scalatest.BeforeAndAfterEach

class AirportRatingSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with AnyWordSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {

  def this() = this(ActorSystem("MySpec"))

  "print all airport ratings" should {
    "output iata and rating for each airport" in {
      val airports = com.patson.data.AirportSource.loadAllAirports(false, true)
      airports.foreach { airport =>
        println(s"${airport.iata}, ${airport.rating.overallDifficulty}")
      }
    }
  }
}