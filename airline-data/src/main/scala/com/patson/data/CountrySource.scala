package com.patson.data

import scala.collection.mutable.ListBuffer
import com.patson.data.Constants._
import com.patson.model._

import scala.collection.mutable.Map

import com.patson.util.AirlineCache

import scala.collection.mutable

object CountrySource {
  def loadAllCountries() = {
    loadCountriesByCriteria(List.empty)
  }

  def loadCountriesByCriteria(criteria: List[(String, Any)]) = {
    val connection = Meta.getConnection()
    try {
      var queryString = "SELECT * FROM " + COUNTRY_TABLE

      if (!criteria.isEmpty) {
        queryString += " WHERE "
        for (i <- 0 until criteria.size - 1) {
          queryString += criteria(i)._1 + " = ? AND "
        }
        queryString += criteria.last._1 + " = ?"
      }

      val preparedStatement = connection.prepareStatement(queryString)

      for (i <- 0 until criteria.size) {
        preparedStatement.setObject(i + 1, criteria(i)._2)
      }


      val resultSet = preparedStatement.executeQuery()

      val countryData = new ListBuffer[Country]()
      //val airlineMap : Map[Int, Airline] = AirlineSource.loadAllAirlines().foldLeft(Map[Int, Airline]())( (container, airline) => container + Tuple2(airline.id, airline))


      while (resultSet.next()) {
        val country = Country(
          resultSet.getString("code"),
          resultSet.getString("name"),
          resultSet.getInt("airport_population"),
          resultSet.getInt("income"),
          resultSet.getInt("openness"),
          resultSet.getDouble("gini")
        )
        countryData += country
      }
      resultSet.close()
      preparedStatement.close()
      countryData.toList
    } finally {
      connection.close()
    }

  }


  def loadCountryByCode(countryCode: String) = {
    val result = loadCountriesByCriteria(List(("code", countryCode)))
    if (result.isEmpty) {
      None
    } else {
      Some(result(0))
    }
  }

  def saveCountries(countries: List[Country]) = {
    val connection = Meta.getConnection()
    try {
      val preparedStatement = connection.prepareStatement("INSERT INTO " + COUNTRY_TABLE + "(code, name, airport_population, income, openness, gini) VALUES (?,?,?,?,?,?)")

      connection.setAutoCommit(false)
      countries.foreach {
        country =>
          preparedStatement.setString(1, country.countryCode)
          preparedStatement.setString(2, country.name)
          preparedStatement.setInt(3, country.airportPopulation)
          preparedStatement.setInt(4, country.income)
          preparedStatement.setInt(5, country.openness)
          preparedStatement.setDouble(6, country.gini)
          preparedStatement.executeUpdate()
      }
      preparedStatement.close()
      connection.commit()
    } finally {
      connection.close()
    }
  }

  def updateCountries(countries: List[Country]) = {
    val connection = Meta.getConnection()
    try {
      val preparedStatement = connection.prepareStatement("UPDATE " + COUNTRY_TABLE + " SET name = ?, airport_population = ?,  income = ?,  openness = ?, gini = ? WHERE code = ?")

      connection.setAutoCommit(false)
      countries.foreach {
        country =>
          preparedStatement.setString(1, country.name)
          preparedStatement.setInt(2, country.airportPopulation)
          preparedStatement.setInt(3, country.income)
          preparedStatement.setInt(4, country.openness)
          preparedStatement.setDouble(5, country.gini)
          preparedStatement.setString(6, country.countryCode)
          preparedStatement.executeUpdate()
      }
      preparedStatement.close()
      connection.commit()
    } finally {
      connection.close()
    }
  }

  def purgeAllCountries() = {
    val connection = Meta.getConnection()
    try {
      val preparedStatement = connection.prepareStatement("DELETE FROM " + COUNTRY_TABLE)
      preparedStatement.executeUpdate()
      preparedStatement.close()
    } finally {
      connection.close()
    }
  }

  object CountryRelationships {
    @inline private def canonicalRelationshipCountry(countryCode: String): String = relationshipAlias.getOrElse(countryCode, countryCode)

    @inline private def normalize(c1: String, c2: String): (String, String) =
      if (c1 < c2) (c1, c2) else (c2, c1)

    private def resolveRelationshipPair(c1: String, c2: String): ((String, String), Boolean) = {
      val cc1 = canonicalRelationshipCountry(c1)
      val cc2 = canonicalRelationshipCountry(c2)
      (normalize(cc1, cc2), cc1 == cc2 && c1 != c2)
    }

    private type ScoreMap = mutable.Map[(String, String), Int]
    val replace: (Int, Int) => Int = (_, incoming) => incoming
    val strengthenPositive: (Int, Int) => Int = (existing, incoming) => math.max(existing, incoming)
    val strengthenNegative: (Int, Int) => Int = (existing, incoming) => math.min(existing, incoming)

    private def putScore(scores: ScoreMap, c1: String, c2: String, score: Int, merge: (Int, Int) => Int = replace): Unit = {
      if (c1 != c2) {
        val key = normalize(c1, c2)
        scores.updateWith(key) {
          case Some(existing) => Some(merge(existing, score))
          case None => Some(score)
        }
      }
    }

    private def addWithinGroup(scores: ScoreMap, score: Int, group: Set[String], merge: (Int, Int) => Int = replace): Unit = {
      group.toSeq.combinations(2).foreach {
        case Seq(c1, c2) => putScore(scores, c1, c2, score, merge)
      }
    }

    private def addGroupToGroup(scores: ScoreMap, score: Int, g1: Set[String], g2: Set[String], merge: (Int, Int) => Int = replace): Unit = {
      for {
        c1 <- g1
        c2 <- g2
        if c1 != c2
      } {
        putScore(scores, c1, c2, score, merge)
      }
    }

    private def addCountryToGroup(scores: ScoreMap, score: Int, country: String, group: Set[String], merge: (Int, Int) => Int = replace): Unit = {
      group.foreach { other =>
        putScore(scores, country, other, score, merge)
      }
    }

    private def addBaselineAffinity(scores: ScoreMap, score: Int, group: Set[String]): Unit = addWithinGroup(scores, score, group, strengthenPositive)

    private def addCoreAffinity(scores: ScoreMap, score: Int, group: Set[String]): Unit = addWithinGroup(scores, score, group, strengthenPositive)

    private def addHostility(scores: ScoreMap, score: Int, g1: Set[String], g2: Set[String]): Unit = addGroupToGroup(scores, score, g1, g2, strengthenNegative)

    private def addOverride(scores: ScoreMap, score: Int, country: String, group: Set[String]): Unit = addCountryToGroup(scores, score, country, group, replace)

    val relationshipAlias: scala.collection.immutable.Map[String, String] = scala.collection.immutable.Map(
      "TC" -> "GB", "KY" -> "GB", "VG" -> "GB", "BM" -> "GB", //GB
      "AW" -> "NL", "CW" -> "NL", "SX" -> "NL", //NL
      "VI" -> "US", "GU" -> "US", "AS" -> "US", "MP" -> "US", //US
      "EE" -> "LT", "LV" -> "LT", //Baltics
      "BA" -> "HR", "ME" -> "HR", "MK" -> "HR", "SI" -> "HR", //Balkans ex AL RS XK drama
      "LU" -> "BE", //Lux
      "MC" -> "FR", //Monaco
    )

    private val scoreMap: scala.collection.immutable.Map[(String, String), Int] = {
      val ECAA = Set("AL","AT","BE","BG","CY","CZ","DK","FI","FR","DE","GR","HR","HU","IE","IS","IT","LT","MD","MT","NL","NO","PL","PT","RO","SK","ES","SE")
      // https://en.wikipedia.org/wiki/European_Common_Aviation_Area – RS & XK are weird and only semi-compliant
      val ANZAC = Set("AU","NZ","CK","NU","TK")
      val USA = Set("US","PR","MH","PW","FM") // US & COFA Pacific
      val CN = Set("CN","MO","HK")
      val Russia = Set("BY","RU")
      val GCCPlus = Set("SA","EG","BH","QA","AE","KW","JO","OM")
      val ArabFTA = GCCPlus ++ Set("LB","SD","IQ","LY","MA","TN","SY")
      val EAC = Set("KE","TZ","UG","RW","BI")
      val SADC = Set("ZA","BW","NA","SZ","LS")
      val SADCPlus = Set("ZA","BW","NA","ZM","ZW","AO","MW","TZ")
      val ECOWAS = Set("BJ","CV","CI","GM","GH","GN","LR","NE","NG","SN","TG")
      val AES = Set("NE","ML","BF")
      val Mercosur = Set("AR","BO","BR","PY","UY")
      val ComunidadAndina = Set("BO","EC","PE","CO")
      val ALBA = Set("VE","CU","BO","NI")
      val USMCA = Set("US","PR","CA","MX")
      val AngloCaribbean = Set("US","CA","PR","AG","BB","BS","GY","JM","TT")
      val TaxHavens = Set("GB","NL","US","CA","PR","PA","IE")
      val EastCaribbean = Set("AG", "BB", "DM", "PR", "FR", "VC", "GD", "TT", "US")
      val CAFTA = Set("US","MX","GT","HN","SV","NI","PA","PR","DO")
      val ASEANPlus = Set("BN","KH","ID","LA","MY","PH","SG","TH","VN","TW")
      val PIF = Set("AU","NZ","FR","US","PG","VU","TV","TO","KI","MH","WS","FJ")
      val CIS = Set("RU","BY","KZ","KG","TJ","UZ","AZ","AM")
      val OECDish = Set("CA","US","MX","IE","GB","NL","BE","FR","DE","AT","CH","IT","CZ","ES","PT","PL","DK","SE","NO","GR","TR","JP","KR","TW","AU","NZ","SG","CL")
      val pariah = Set("IR","KP","RU","BY")
      val scores = mutable.Map[(String, String), Int]()

      // Broad affinity blocs
      addBaselineAffinity(scores, 2, CIS)
      addBaselineAffinity(scores, 2, SADCPlus)
      addBaselineAffinity(scores, 2, ECOWAS)
      addBaselineAffinity(scores, 2, PIF)
      addBaselineAffinity(scores, 2, ArabFTA)

      addBaselineAffinity(scores, 3, OECDish)
      addBaselineAffinity(scores, 3, ALBA)
      addBaselineAffinity(scores, 3, ASEANPlus)
      addBaselineAffinity(scores, 3, CAFTA)
      addBaselineAffinity(scores, 3, AngloCaribbean)
      addBaselineAffinity(scores, 3, USMCA)
      addBaselineAffinity(scores, 3, Mercosur)

      // High-trust inner circles
      addCoreAffinity(scores, 4, ECAA)
      addCoreAffinity(scores, 4, ANZAC)
      addCoreAffinity(scores, 4, GCCPlus)
      addCoreAffinity(scores, 4, ComunidadAndina)
      addCoreAffinity(scores, 4, AES)
      addCoreAffinity(scores, 4, EAC)
      addCoreAffinity(scores, 4, SADC)
      addCoreAffinity(scores, 4, EastCaribbean)
      addCoreAffinity(scores, 4, TaxHavens)
      addCoreAffinity(scores, 5, USA)
      addCoreAffinity(scores, 5, CN)
      addCoreAffinity(scores, 5, Russia)

      // Cross-bloc relationships
      addGroupToGroup(scores, 2, GCCPlus - "OM", OECDish, strengthenPositive)
      addGroupToGroup(scores, 2, GCCPlus -- Set("JO","OM"), CN - "MO", strengthenPositive)
      addGroupToGroup(scores, 1, GCCPlus -- Set("JO","EG"), ASEANPlus -- Set("KH","LA"), strengthenPositive)

      // Broad hostility blocs
      addHostility(scores, -2, OECDish, pariah)
      addHostility(scores, -2, ECAA, Russia)

      // North America
      addOverride(scores, -4, "US", Set("IR"))
      addOverride(scores, 4, "US", Set("JP","KR","TW","PH","AU","NZ","BS","IL"))
      addOverride(scores, 3, "US", Set("CO")) // CO = FTA + Plan Colombia alliance;
      addOverride(scores, 2, "US", Set("ZA","KE","NG"))
      addOverride(scores, 4, "CA", OECDish -- Set("US","TR"))
      addOverride(scores, 1, "MX", Set("CN","BR"))
      addOverride(scores, 2, "MX", ComunidadAndina ++ Set("AR","MY","VN"))
      addOverride(scores, 2, "HT", Set("US","FR","CA"))
      addOverride(scores, 2, "DO", Set("ES","DE","NL","FR","PE","CO"))
      addOverride(scores, 3, "DO", Set("CA","PR","JM"))
      addOverride(scores, -1, "DO", Set("HT"))
      addOverride(scores, 2, "CU", Set("AO","PE"))
      addOverride(scores, -3, "CU", Set("US","PR"))
      addOverride(scores, 3, "PR", Set("ES","PA","CO"))
      addOverride(scores, 2, "PA", Set("ES","DE","FR","AE","HK","PE","CO","JP","CN"))
      addOverride(scores, 3, "CR", Set("US","CA","MX","PR","PA","CO"))
      addOverride(scores, 2, "TT", Set("VE"))
      addOverride(scores, 2, "SR", Set("NL","BR","GY"))

      // South America
      addOverride(scores, 1, "BR", Set("IN","GB","FR"))
      addOverride(scores, 2, "BR", Set("BO","CO","MX","US","ZA","AO","JP","CN","ES","DE","PL","CV"))
      addOverride(scores, 3, "BR", Set("PT","CL"))
      addOverride(scores, 2, "CO", Set("ES","NI"))
      addOverride(scores, 2, "EC", Set("ES","CN"))
      addOverride(scores, 3, "AR", Set("ES","GB","US","CO","IT"))
      addOverride(scores, 2, "PE", Set("ES","JP","CN","CL","NI"))
      addOverride(scores, 4, "VE", Set("IR","RU","CU"))
      addOverride(scores, -1, "VE", Set("CL","AR","BR","GB","FR","US"))
      addOverride(scores, -2, "VE", Set("GY"))
      addOverride(scores, 2, "CL", Set("PE","BO","CN"))
      addOverride(scores, 3, "CL", Set("AR","UY","PY","MX"))
      addOverride(scores, 4, "CL", Set("CO","ES","CA"))
      addOverride(scores, 4, "PY", Set("TW"))

      // Europe
      addOverride(scores, 3, "RU", Set("EG","KP","TM","IR","IQ"))
      addOverride(scores, 2, "RU", Set("SA","ZA"))
      addOverride(scores, 2, "TR", Set("RU","GE","IQ","IR","PK"))
      addOverride(scores, 3, "TR", Set("QA","KZ","UZ","SO"))
      addOverride(scores, 4, "TR", Set("AZ"))
      addOverride(scores, 4, "IS", Set("CA","US"))
      addOverride(scores, 2, "GB", Set("LK","PG","ZM","MW","FJ","UG","RW","GY"))
      addOverride(scores, 3, "GB", Set("JM","TT","BB","SL","BN"))
      addOverride(scores, 4, "GB", ECAA ++ Set("AU","NZ"))
      addOverride(scores, 3, "CH", Set("AE","QA","TR"))
      addOverride(scores, 4, "CH", ECAA)
      addOverride(scores, 2, "DE", Set("VN","BD"))
      addOverride(scores, 1, "DE", Set("NG","KE"))
      addOverride(scores, 1, "RS", Set("HR"))
      addOverride(scores, 2, "RS", Set("AL","HU","RU","IT","DE","AT","FR","CN"))
      addOverride(scores, -1, "RS", Set("AL"))
      addOverride(scores, 4, "XK", Set("US","GB","FR","AL","TR","DE","HR"))
      addOverride(scores, -1, "XK", Set("RS","RU"))
      addOverride(scores, 2, "HR", Set("CN"))
      addOverride(scores, 3, "RO", Set("MD"))
      addOverride(scores, 2, "PT", Set("MZ"))
      addOverride(scores, 2, "UA", Set("MD","RO"))
      addOverride(scores, 3, "UA", Set("US","CZ","TR"))
      addOverride(scores, 4, "UA", Set("GB","FR","DE","NL","DK","ES","IT","CH","AT","PL","SE","NO","LT","FI","CA"))
      addOverride(scores, -4, "UA", Set("RU","BY"))

      // East Asia
      addOverride(scores, 4, "KP", Set("RU"))
      addOverride(scores, -2, "KP", Set("BN","KH","ID","LA","MY","PH","SG","TH","VN","BR","IN","ZA","TR"))
      addOverride(scores, -4, "KP", Set("KR","US"))
      addOverride(scores, 1, "JP", Set("ET","MM","MA","ZA"))
      addOverride(scores, 2, "JP", Set("BD","LK","KH"))
      addOverride(scores, 3, "JP", Set("SG"))
      addOverride(scores, 4, "JP", Set("TW"))
      addOverride(scores, 2, "KR", Set("PH"))
      addOverride(scores, 4, "KR", Set("TW"))
      addOverride(scores, 1, "CN", Set("VN","DE","ES"))
      addOverride(scores, 2, "CN", GCCPlus ++ Set("DJ","HU","TH","SG","MY","ID","BD","NP","GH","KM","MX","BR","PE","CL","NG","AO","KE","AR","SD"))
      addOverride(scores, 3, "CN", CIS ++ Set("KP","ET","ZA","KH","LA","MM","UZ","CN"))
      addOverride(scores, 4, "CN", Set("KH","IR","PK","RU","KZ"))
      addOverride(scores, -1, "CN", Set("US","IN","AU","JP","TW","PH","PY"))
      addOverride(scores, 3, "HK", ASEANPlus ++ OECDish)
      addOverride(scores, 2, "MO", ASEANPlus ++ Set("KP","PH","JP","KR"))
      addOverride(scores, 2, "BN", GCCPlus -- Set("JO","EG") ++ Set("CA","AU","JP"))
      addOverride(scores, 2, "ID", GCCPlus)
      addOverride(scores, 2, "ID", Set("JP","NL","KR"))
      addOverride(scores, 1, "ID", Set("DE","US","CA","BR","IN","ET","RU","CN","IR"))
      addOverride(scores, 2, "MY", OECDish)
      addOverride(scores, 3, "MY", GCCPlus -- Set("JO","EG"))
      addOverride(scores, 3, "SG", GCCPlus)
      addOverride(scores, 1, "TH", OECDish ++ Set("IN","BR"))
      addOverride(scores, 2, "VN", Set("CN","JP","KR","US","CA","AU","NZ","GB"))
      addOverride(scores, 2, "PH", Set("AU","JP","KR"))
      addOverride(scores, 2, "MN", Set("KZ","JP", "CN", "RU", "KR", "NP","DE"))

      // South Central Asia
      addOverride(scores, 2, "BD", Set("SG","MY","AE","SA","GB","US","CA","IT","ID","BT"))
      addOverride(scores, 1, "IN", Set("KE","IL","AF","TH"))
      addOverride(scores, 2, "IN", Set("GB","FR","MY","MM","US","CA","SG","PH","ID","ZA","KR","MU","DE","TZ","VN"))
      addOverride(scores, 3, "IN", GCCPlus ++ Set("BT","NP","LK","BD","RU","AU","JP"))
      addOverride(scores, 3, "MV", Set("IN"))
      addOverride(scores, 2, "MV", Set("CN"))
      addOverride(scores, 2, "NP", Set("GB"))
      addOverride(scores, 2, "NP", Set("AE","QA","SA"))
      addOverride(scores, 2, "LK", Set("AE","SA","CN"))
      addOverride(scores, 2, "PK", Set("AZ","AE","SA","GB","US"))
      addOverride(scores, -2, "PK", Set("IN","IR"))
      addOverride(scores, -2, "BD", Set("MM"))
      addOverride(scores, 3, "GE", Set("IL","TR","UA","AE","AZ","DE"))
      addOverride(scores, -2, "TR", Set("GR","CY","AM"))
      addOverride(scores, 3, "AM", Set("FR"))
      addOverride(scores, 2, "AM", Set("GE","DE","RO"))
      addOverride(scores, -2, "AM", Set("AZ"))
      addOverride(scores, 2, "TJ", Set("UZ","AF"))
      addOverride(scores, 2, "TM", Set("IR","AF","TR"))
      addOverride(scores, -2, "KG", Set("TJ"))
      addOverride(scores, 3, "KZ", Set("UZ","KG"))
      addOverride(scores, 2, "UZ", Set("AF"))
      addOverride(scores, 3, "AF", Set("CN"))
      addOverride(scores, 2, "AF", Set("IR","RU"))
      addOverride(scores, -1, "AF", Set("US"))
      addOverride(scores, -1, "IR", OECDish -- Set("US","TR"))
      addOverride(scores, 3, "IR", Set("IQ","SY","LB"))
      addOverride(scores, 2, "IR", Set("QA"))
      addOverride(scores, 1, "IR", Set("BD","SG","AE","GE","LK"))

      // West Asia
      addOverride(scores, 1, "IQ", Set("US"))
      addOverride(scores, 2, "IQ", Set("CN"))
      addOverride(scores, 2, "QA", Set("RW","KE"))
      addOverride(scores, 3, "QA", Set("TR","PK"))
      addOverride(scores, 2, "AE", Set("ET","MR","RW","KE","TZ","GH","JO"))
      addOverride(scores, 3, "AE", Set("FR","DE","US","MY","SG","PK"))
      addOverride(scores, 2, "SA", Set("MY","PK","BN","GB","DJ","SG","CN","IN","BD","TR"))
      addOverride(scores, 2, "OM", Set("MY","FR","DJ"))
      addOverride(scores, -3, "YE", Set("SA","AE"))
      addOverride(scores, 4, "YE", Set("IR"))
      addOverride(scores, 1, "YE", Set("JO","EG","OM"))
      addOverride(scores, 3, "JO", Set("IQ","US"))
      addOverride(scores, 2, "SY", Set("FR","RU"))
      addOverride(scores, 2, "LB", Set("FR"))
      addOverride(scores, -1, "LB", Set("SY"))
      addOverride(scores, -4, "IL", Set("IR","SY","LB"))
      addOverride(scores, -1, "IL", Set("SA","TR"))
      addOverride(scores, 1, "IL", Set("RO","AE","BH","MA"))
      addOverride(scores, 2, "IL", Set("GB","FR","DE","IT","IN"))
      addOverride(scores, 3, "IL", Set("CA","CY","BG","SG","ET","GR","AZ"))

      // Africa
      addOverride(scores, 2, "MA", Set("TN","BJ","BF","CI","GM","GH","GN","GW","LR","NE","NG","SN","TG","MR"))
      addOverride(scores, 3, "MA", Set("PT","ES","FR","IT","IE","GB","DE","CH","NL","BE","NO","SE","DK","US","CA"))
      addOverride(scores, 3, "TN", Set("PT","ES","FR","IT","IE","GB","DE","CH","NL","BE","NO","SE","DK","US","CA"))
      addOverride(scores, 2, "DZ", Set("FR","ES","IT","CN","TR","TN","LY","MR"))
      addOverride(scores, 3, "DZ", Set("RU"))
      addOverride(scores, -1, "DZ", Set("MA"))
      addOverride(scores, -2, "LY", Set("AE"))
      addOverride(scores, 1, "LY", Set("EG","RU","CN"))
      addOverride(scores, 2, "LY", Set("IT","TN","DZ"))
      addOverride(scores, 3, "LY", Set("TR"))
      addOverride(scores, 1, "EG", EAC ++ Set("PK"))
      addOverride(scores, 2, "EG", Set("MY","FR","IT","IE","GB","DE","CH","US","CA","UA","RU"))
      addOverride(scores, -2, "ER", Set("ET"))
      addOverride(scores, -1, "ER", Set("DJ"))
      addOverride(scores, 2, "ER", Set("EG","RU"))
      addOverride(scores, -1, "ET", Set("EG"))
      addOverride(scores, 1, "ET", Set("IT","SD","US","KE","DE","TR"))
      addOverride(scores, 2, "ET", Set("UG","RW","AE","QA","SO","TZ"))
      addOverride(scores, 3, "ET", Set("ZA","CN"))
      addOverride(scores, 4, "ET", Set("DJ"))
      addOverride(scores, 2, "DJ", Set("FR","US","CN"))
      addOverride(scores, 2, "SO", Set("DJ"))
      addOverride(scores, -2, "ML", Set("FR"))
      addOverride(scores, 2, "ML", Set("BF","GN","NE"))
      addOverride(scores, 2, "CM", Set("NG","GA","CF"))
      addOverride(scores, 2, "CM", Set("FR","CN"))
      addOverride(scores, 1, "CF", Set("FR","CM"))
      addOverride(scores, 3, "CF", Set("RU"))
      addOverride(scores, 2, "CD", Set("BE","FR"))
      addOverride(scores, 2, "CD", Set("ZM","AO","TZ"))
      addOverride(scores, 3, "CD", Set("CN"))
      addOverride(scores, 2, "GA", Set("FR","CN"))
      addOverride(scores, 2, "CG", Set("FR","CN"))
      addOverride(scores, 1, "GQ", Set("FR"))
      addOverride(scores, 2, "GQ", Set("ES","CN"))
      addOverride(scores, 3, "GH", Set("US","CA","GB","BF","LR","SL","NG","ST","RW","KE","CM"))
      addOverride(scores, 3, "RU", AES)
      addGroupToGroup(scores, -1, Set("FR","US"), AES, strengthenNegative) // preserves ML-FR = -2; sets NE/BF-FR to -1
      addOverride(scores, 2, "GW", Set("PT"))
      addOverride(scores, 3, "CI", Set("BJ","BF","GM","GH","GN","GW","LR","NE","NG","SN","TG","CM"))
      addOverride(scores, 2, "MG", Set("FR","CN","ZA","MZ","TZ","KE"))
      addOverride(scores, 2, "TZ", Set("GB"))
      addOverride(scores, -3, "SS", Set("SD"))
      addOverride(scores, 2, "SS", Set("ET","US","CN"))
      addOverride(scores, 2, "KE", Set("GB","MW","MZ","ZM","GH"))
      addOverride(scores, 4, "KE", Set("UG","SO","SS"))
      addOverride(scores, 2, "KM", Set("FR","SA","AE","TR","KE","TZ"))
      addOverride(scores, 3, "UG", Set("SO"))
      addOverride(scores, -1, "RW", Set("BI","CD"))
      addOverride(scores, 2, "SN", Set("FR","CV","GM","GH","GN","ML","MR"))
      addOverride(scores, 2, "LR", Set("US","SL","GN","NG","ST","UG"))
      addOverride(scores, 1, "NG", Set("GB","KE","UG","GQ","QA"))
      addOverride(scores, 2, "NG", Set("SA","AE"))
      addOverride(scores, 2, "CV", Set("NL","GB","PT"))
      addOverride(scores, 2, "AO", Set("PT","MZ","CV","KE","ET","NG"))
      addOverride(scores, 2, "SC", GCCPlus ++ Set("GB","TR","SG","MY","ET","IN"))
      addOverride(scores, 2, "MU", Set("CN","GB","FR"))
      addOverride(scores, 2, "MZ", SADC ++ Set("PT","GB","KE","TZ"))
      addOverride(scores, 3, "TD", Set("FR"))
      addOverride(scores, -2, "TD", Set("SD"))
      addOverride(scores, 1, "TD", Set("NG","CM","NE","CF","LY"))
      addOverride(scores, 1, "ZA", GCCPlus)
      addOverride(scores, 2, "ZA", Set("GB","DE","RU","US","AU","IN","TZ","KE","UG","BR"))

      // Oceania
      addOverride(scores, 3, "TL", Set("AU","MY","ID"))
      addOverride(scores, 2, "TL", Set("FJ","IN"))
      addOverride(scores, 2, "AU", Set("ID","PH","PG","FJ","WS"))
      addOverride(scores, 2, "NZ", Set("ID","PG","WS","FJ"))
      addOverride(scores, 3, "NZ", Set("TO"))
      addOverride(scores, 2, "PG", Set("PH","JP","CN","SB"))
      addOverride(scores, 2, "SB", Set("AU","FJ","FR"))
      addOverride(scores, 3, "SB", Set("CN"))
      addOverride(scores, 2, "VU", Set("CN"))
      addOverride(scores, 4, "KI", Set("CN"))
      addOverride(scores, 4, "TV", Set("TW"))
      addOverride(scores, 4, "PW", Set("TW"))
      
      addOverride(scores, 4, "AQ", Set("CL","AR","ZA","NZ","AU","US","GB","RU","JP","CN"))

      scores.toMap
    }

    def getScore(c1: String, c2: String): Option[Int] = {
      if (c1 == c2) Some(5)
      else {
        val (pair, sameFamily) = resolveRelationshipPair(c1, c2)
        if (sameFamily) Some(5) else scoreMap.get(pair)
      }
    }
  }

  def getCountryMutualRelationship(country1 : String, country2 : String) : Int = {
    CountryRelationships.getScore(country1, country2).getOrElse(0)
  }

  def getCountryMutualRelationships() : scala.collection.immutable.Map[(String, String), Int] = {
    val countryCodes = loadAllCountries().map(_.countryCode)
    val relationships = Map[(String, String), Int]()

    countryCodes.foreach { country1 =>
      countryCodes.foreach { country2 =>
        CountryRelationships.getScore(country1, country2).foreach { score =>
          relationships.put((country1, country2), score)
        }
      }
    }

    relationships.toMap
  }

  def getCountryMutualRelationships(country : String) : scala.collection.immutable.Map[String, Int] = {
    val relationships = Map[String, Int]()

    loadAllCountries().map(_.countryCode).foreach { otherCountry =>
      relationships.put(otherCountry, CountryRelationships.getScore(country, otherCountry).getOrElse(0))
    }

    relationships.toMap
  }
  
  def loadCountryRelationshipsByCriteria(criteria : List[(String, Any)]) : scala.collection.immutable.Map[Country, scala.collection.immutable.Map[Airline, Int]] = {
    val connection = Meta.getConnection()
    try {
      var queryString = "SELECT * FROM " + COUNTRY_AIRLINE_RELATIONSHIP_TABLE

      if (!criteria.isEmpty) {
        queryString += " WHERE "
        for (i <- 0 until criteria.size - 1) {
          queryString += criteria(i)._1 + " = ? AND "
        }
        queryString += criteria.last._1 + " = ?"
      }

      val preparedStatement = connection.prepareStatement(queryString)

      for (i <- 0 until criteria.size) {
        preparedStatement.setObject(i + 1, criteria(i)._2)
      }


      val resultSet = preparedStatement.executeQuery()

      val relationShipData = Map[Country, Map[Airline, Int]]()

      val countries = Map[String, Country]()
      val airlines = Map[Int, Airline]()
      while (resultSet.next()) {
        val countryCode = resultSet.getString("country")
        val country = countries.getOrElseUpdate(countryCode, loadCountryByCode(countryCode).get)
        val airlineId = resultSet.getInt("airline")
        val airline = airlines.getOrElseUpdate(airlineId, AirlineCache.getAirline(airlineId, false).getOrElse(Airline.fromId(airlineId)))

        relationShipData.getOrElseUpdate(country, Map()).put(airline, resultSet.getInt("relationship"))
      }
      resultSet.close()
      preparedStatement.close()
      relationShipData.mapValues(_.toMap).toMap //make immutable
    } finally {
      connection.close()
    }
  }
  
  def loadAllCountryRelationships(): scala.collection.immutable.Map[Country, scala.collection.immutable.Map[Airline, Int]] = {
    loadCountryRelationshipsByCriteria(List.empty)
  }
  
  def loadCountryRelationshipsByCountry(countryCode : String) : scala.collection.immutable.Map[Airline, Int] = {
    loadCountryRelationshipsByCriteria(List(("country", countryCode))).find( _._1.countryCode == countryCode) match {
      case Some((_, relationships)) => relationships
      case None => scala.collection.immutable.Map.empty
    }
  }
  
  def loadCountryRelationshipsByAirline(airlineId : Int) : scala.collection.immutable.Map[Country, Int] = {
    loadCountryRelationshipsByCriteria(List(("airline", airlineId))).view.mapValues { airlineToRelationship =>
      airlineToRelationship.toIterable.head._2
    }.toMap
  }
  
  def saveMarketShares(marketShares : List[CountryMarketShare]) = {
     val connection = Meta.getConnection()
     try {  
       connection.setAutoCommit(false)
       //purge existing ones
       val truncateStatement = connection.prepareStatement("TRUNCATE TABLE "+ COUNTRY_MARKET_SHARE_TABLE);
       truncateStatement.executeUpdate()       
       
       val replaceStatement = connection.prepareStatement("REPLACE INTO " + COUNTRY_MARKET_SHARE_TABLE + "(country, airline, passenger_count) VALUES (?,?,?)")
       marketShares.foreach { marketShare =>
           replaceStatement.setString(1, marketShare.countryCode)
           marketShare.airlineShares.foreach { 
             case((airline, passenger_count)) =>
               replaceStatement.setInt(2, airline)
               replaceStatement.setDouble(3, passenger_count)
               replaceStatement.addBatch()
           }
           
       }
       
       replaceStatement.executeBatch()
       connection.commit()
       truncateStatement.close()
       replaceStatement.close()
     } finally {
       connection.close()
     }
  }
  def loadMarketSharesByCountryCode(country : String) : Option[CountryMarketShare] = {
    val result = loadMarketSharesByCriteria(List(("country", country)))
    if (result.isEmpty) {
      None
    } else {
      Some(result(0))
    }
  }
  
  def loadMarketSharesByCriteria(criteria : List[(String, Any)]) : List[CountryMarketShare] = {
    val connection = Meta.getConnection()
    try {  
      var queryString = "SELECT * FROM " + COUNTRY_MARKET_SHARE_TABLE
      
      if (!criteria.isEmpty) {
        queryString += " WHERE "
        for (i <- 0 until criteria.size - 1) {
          queryString += criteria(i)._1 + " = ? AND "
        }
        queryString += criteria.last._1 + " = ?"
      }
      
      val preparedStatement = connection.prepareStatement(queryString)
      
      for (i <- 0 until criteria.size) {
        preparedStatement.setObject(i + 1, criteria(i)._2)
      }
      
      
      val resultSet = preparedStatement.executeQuery()
      
      val countryMarketShares = ListBuffer[CountryMarketShare]()
      
      val resultMap = Map[String, Map[Int, Long]]()
      while (resultSet.next()) {
        val countryCode = resultSet.getString("country")
        val airlineId = resultSet.getInt("airline")
        val passengerCount = resultSet.getLong("passenger_count")
        
        val airlinePassengers = resultMap.getOrElseUpdate(countryCode, Map[Int, Long]())
        airlinePassengers.put(airlineId, passengerCount)
      }    
      resultSet.close()
      preparedStatement.close()
      
      resultMap.toList.map {
        case ((countryCode, airlinePassengers)) => CountryMarketShare(countryCode, airlinePassengers.toMap)
      }
    } finally {
      connection.close()
    }  
  }

}

