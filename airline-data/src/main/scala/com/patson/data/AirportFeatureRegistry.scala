package com.patson.data

import com.patson.model._

import scala.collection.mutable
import scala.io.Source

/**
 * Loads static airport features from CSV files at JVM startup.
 * Features keyed by IATA code for O(1) lookup during airport loading.
 * Game-state features (PRESTIGE_CHARM, OLYMPICS_PREPARATIONS, OLYMPICS_IN_PROGRESS)
 * are NOT here — they are computed at load time from their authoritative DB tables.
 */
object AirportFeatureRegistry {

  lazy val featuresForIata: Map[String, List[AirportFeature]] = {
    val builder = mutable.HashMap[String, mutable.ListBuffer[AirportFeature]]()

    def add(iata: String, feature: AirportFeature): Unit =
      builder.getOrElseUpdate(iata, mutable.ListBuffer()).append(feature)

    def loadCsv(resourcePath: String): Iterator[Array[String]] = {
      val stream = getClass.getClassLoader.getResourceAsStream(resourcePath)
      if (stream == null) {
        println(s"[AirportFeatureRegistry] WARNING: resource not found: $resourcePath")
        Iterator.empty
      } else {
        Source.fromInputStream(stream, "UTF-8").getLines()
          .drop(1) // skip header
          .filter(_.trim.nonEmpty)
          .map(_.split(",", -1).map(_.trim))
      }
    }

    // Hand-curated hub features (iata,strength)
    loadCsv("airport-features/international_hub.csv").foreach { cols =>
      if (cols.length >= 2) add(cols(0), InternationalHubFeature(cols(1).toInt))
    }
    loadCsv("airport-features/vacation_hub.csv").foreach { cols =>
      if (cols.length >= 2) add(cols(0), VacationHubFeature(cols(1).toInt))
    }
    loadCsv("airport-features/financial_hub.csv").foreach { cols =>
      if (cols.length >= 2) add(cols(0), FinancialHubFeature(cols(1).toInt))
    }

    // Hand-curated list features (iata only)
    loadCsv("airport-features/domestic_airports.csv").foreach { cols =>
      if (cols.nonEmpty) add(cols(0), DomesticAirportFeature())
    }
    loadCsv("airport-features/bush_hubs.csv").foreach { cols =>
      if (cols.nonEmpty) add(cols(0), BushHubFeature())
    }

    // World-init generated features (iata only)
    loadCsv("airport-features/gateway_airports.csv").foreach { cols =>
      if (cols.nonEmpty) add(cols(0), GatewayAirportFeature())
    }

    // World-init generated features (iata,strength)
    loadCsv("airport-features/isolated_towns.csv").foreach { cols =>
      if (cols.length >= 2) add(cols(0), IsolatedTownFeature(cols(1).toInt))
    }

    builder.view.mapValues(_.toList).toMap
  }

  def apply(iata: String): List[AirportFeature] = featuresForIata.getOrElse(iata, List.empty)
}
