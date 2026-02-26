package com.patson.init

import com.patson.data.AirportSource
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class InitSpec extends AnyWordSpecLike with Matchers {
  "Affinity patch list IATAs" should {
    "all exist in the airport database" in {

      println("loading affinity-patch-list.csv")
      val iatas = scala.io.Source.fromFile("affinity-patch-list.csv").getLines().map(_.split(",", -1)).map { tokens =>
        tokens(0)
      }.toList

      
      val missingIatas = iatas.filter { iata =>
        AirportSource.loadAirportByIata(iata, fullLoad = false).isEmpty
      }
      
      if (missingIatas.nonEmpty) {
        println(s"Missing IATAs: ${missingIatas.mkString(", ")}")
      }
      
      missingIatas shouldBe empty
    }
  }

  "Airport feature patcher IATAs" should {
    "all exist in the airport database" in {
      val iatas = AirportFeaturePatcher.featureList.values.flatMap(_.keys).toList

      val missingIatas = iatas.filter { iata =>
        AirportSource.loadAirportByIata(iata, fullLoad = false).isEmpty
      }

      if (missingIatas.nonEmpty) {
        println(s"Missing IATAs in AirportFeaturePatcher: ${missingIatas.distinct.mkString(", ")}")
      }

      missingIatas shouldBe empty
    }
  }
}
