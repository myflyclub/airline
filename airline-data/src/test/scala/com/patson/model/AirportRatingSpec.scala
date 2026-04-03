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
      val airports = com.patson.data.AirportSource.loadAllAirports(fullLoad = false, loadFeatures = true)
      airports.sortBy(_.rating.overallDifficulty).reverse.foreach { airport =>
        println(s"${airport.iata}, ${airport.countryCode}, ${airport.rating.overallDifficulty}")
      }
     val jfk = airports.find(_.iata == "JFK").get.rating.overallDifficulty
     val lhr = airports.find(_.iata == "LHR").get.rating.overallDifficulty
     val tpe = airports.find(_.iata == "TPE").get.rating.overallDifficulty
     val mke = airports.find(_.iata == "MKE").get.rating.overallDifficulty
     val bet = airports.find(_.iata == "BET").get.rating.overallDifficulty
     val ark = airports.find(_.iata == "ARK").get.rating.overallDifficulty
     jfk.should(be >= lhr)
     lhr.should(be >= tpe)
     tpe.should(be >= mke)
     mke.should(be >= bet)
     bet.should(be >= ark)
    }
  }
}