package com.patson.init

import com.patson.data.CountrySource

import scala.collection.mutable
import scala.collection.mutable.Map

object CountryMutualRelationshipGenerator extends App {
  /**
   * - Affiliation is mutual between all members. Affiliations only "upgrade" relations, never decrease
   * - A "5" relation creates a "home market"
   *
   * Some relations set in the computation function!
   */
  lazy val OECDish = List("CA","US","FR","DE","AT","CH","IT","GB","ES","NL","BE","PL","DK","SE","IE","JP","KR","AU","SG","CL")
  lazy val CoreEU = List("BE","DE","ES","FR","GR","IE","IT","NL","PL")

  lazy val AFFILIATIONS = List(

    Affiliation("OECD", 3, OECDish),

    Affiliation("European Common Aviation Area", 4, List(
      "AL","AT","BE","BG","HR","CY","CZ","DK","EE","FI","FR","DE","GR","HU","IE","IT","LV","LT","LU","MT","ME","NL","NO","PL","PT","RO","SK","SI","ES","SE",
    )),
    Affiliation("UK Caribbean", 5, List(
      "GB", "TC", "KY", "VG", "BM", "BA"
      //other UK territories are one-one relationships
    )),
    Affiliation("Netherlands", 5, List(
      "NL", "AW", "CW", "SX"
    )),
    Affiliation("US Anglo Dutch Caribbean", 4, List(
      "US", "GB", "CA", "FR", "AW", "CW", "SX", "PR", "KN", "KY", "VG", "VI", "TC",
    )),
    Affiliation("Anglo Caribbean", 3, List(
      "US", "CA", "PR", "BM", "AG", "BB", "BS", "GY", "JM", "KY", "TC", "TT", "VG", "VI"
    )),
    Affiliation("East Caribbean", 4, List(
      "AG", "BB", "DM", "PR", "FR", "VC", "GD", "TT", "US"
    )),
    Affiliation("US", 5, List(
      "US", "VI", "PR"
    )),
    Affiliation("US Pacific", 5, List(
      "US", "MH", "FM", "AS", "GU", "MP", "PW"
    )),
    Affiliation("NAFTA", 3, List(
      "US", "CA", "MX", "PR"
    )),
    Affiliation("COFTA", 3, List(
      "US", "GT", "HN", "SV", "NI", "PR", "DO"
    )),
    Affiliation("NZ", 5, List(
      "NZ", "CK", "NU"
    )),
    Affiliation("ANZAC common market", 5, List(
      "AU", "NZ", "CK", "NU"
    )),
    Affiliation("Arab Free Trade Area", 3, List(
      "SA", "EG", "BH", "QA", "AE", "KW", "JO", "LB", "OM", "SD", "IQ", "LY", "MA", "TN", "SY"
    )),
    Affiliation("GCC+", 4, List(
      "SA", "EG", "BH", "QA", "AE", "KW", "JO", "OM"
    )),
    Affiliation("EAC", 4, List(
      "KE", "UG", "SS", "RW", "BI", "TZ"
    )),
    Affiliation("Comunidad Andina", 4, List(
      "BO", "EC", "PE", "CO"
    )),
    Affiliation("ALBA", 3, List(
      "VE", "CU", "BO", "NI"
    )),
//    Affiliation("ECOWAS", 2, List(
//      "BJ", "BF", "CV", "CI", "GM", "GH", "GN", "GW", "LR", "NE", "NG", "SN", "TG"
//    )),
//    Affiliation("ECCAS", 2, List(
//      "AO", "BI", "CM", "TD", "CD", "GQ", "GA", "CG", "RW", "ST"
//    )),
    Affiliation("SADC+", 3, List(
      "ZA", "BW", "NA", "ZM", "ZW", "AO", "MZ", "MW", "MG"
    )),
    Affiliation("SADC", 4, List(
      "ZA", "BW", "NA"
    )),
    Affiliation("SADC", 4, List(
      "ZA", "SZ", "LS"
    )),
    Affiliation("ASEAN", 3, List(
      "BN", "KH", "ID", "LA", "MY", "PH", "SG", "TH", "VN"
    )),
//    Affiliation("CPTPP", 3, List(
//      "AU", "BN", "CA", "CL", "JP", "MY", "MX", "NZ", "PE", "SG", "VN", "GB"
//    )),
    Affiliation("China", 5, List(
      "CN", "HK", "MO"
    )),
    Affiliation("CIS", 3, List(
      "RU", "BY", "KZ", "KG", "TJ", "UZ", "AZ", "AM"
    ))
  )
  lazy val FRIENDSHIPS = List(
    //pacific
    Relation("AU", Direction.BI, 3, List(
      "TH","ID","PH","HK"
    )),
    Relation("NZ", Direction.BI, 3, List(
      "SG","ID","JP","HK"
    )),
    Relation("NZ", Direction.BI, 4, List(
      "AU","GB","DE","ES","IT","FR","US","CA","JP","KR","MY","TH"
    )),
    Relation("FJ", Direction.BI, 2, List(
      "AU", "NZ", "US", "FR", "WS", "TV", "TO", "KI", "MH"
    )),
    Relation("PG", Direction.BI, 2, List(
      "AU", "GU", "PH", "JP", "CN"
    )),
    Relation("WS", Direction.BI, 4, List(
      "AU", "NZ", "AS", "CK"
    )),
    Relation("WS", Direction.BI, 2, List(
      "KR"
    )),
//    Relation("PF", Direction.BI, 4, List(
//      "AU", "NZ", "CA", "US"
//    )),
    Relation("FM", Direction.BI, 4, List(
      "JP"
    )),
    Relation("GU", Direction.BI, 4, List(
      "KR", "JP", "PH"
    )),
    Relation("FM", Direction.BI, 2, List(
      "CN", "AU"
    )),
    Relation("AS", Direction.BI, 4, List(
      "AU", "NZ", "US", "FR"
    )),
    Relation("AS", Direction.BI, 2, List(
      "KR", "JP"
    )),
    Relation("KI", Direction.BI, 3, List(
      "MH", "AU"
    )),
    Relation("VU", Direction.BI, 4, List(
      "FR", "AS"
    )),
    //e-asia
    Relation("MN", Direction.BI, 2, List(
      "JP", "CN", "RU", "KR", "NP"
    )),
    Relation("CN", Direction.BI, 4, List(
      "KH", "PK", "RU", "KZ"
    )),
    Relation("CN", Direction.BI, 3, List(
      "KP", "IR", "ET", "ZA", "KH", "LA", "MM", "UZ"
    )),
    Relation("CN", Direction.BI, 2, List(
      "DJ", "HU", "VN", "TH", "SG", "MY", "ID", "BD", "NP", "EG", "GH", "KM", "MX", "VE", "BR", "PE", "CL", "HU"
    )),
    Relation("CN", Direction.BI, 1, List(
      "CI", "NG", "KE", "TZ"
    )),
    Relation("HK", Direction.BI, 2, OECDish),
    Relation("JP", Direction.BI, 2, List(
      "PE", "BR", "IN", "VN", "TH", "FJ", "PG", "SB", "PW"
    )),
    Relation("KR", Direction.BI, 4, List(
      "SG", "TW", "US"
    )),
    Relation("KR", Direction.BI, 3, List(
      "JP"
    )),
    Relation("KR", Direction.BI, 2, List(
      "MY", "TH", "VN"
    )),
    Relation("TW", Direction.BI, 4, List(
      "NL","CA","US","JP","DE","HK"
    )),
    Relation("TW", Direction.BI, 3, List(
      "GB","JP","SG","AU","PW","SZ"
    )),
    //se-asia
    Relation("ID", Direction.BI, 3, List(
      "AU", "NZ", "JP"
    )),
    Relation("ID", Direction.BI, 2, List(
      "IN", "KR", "AE", "SA", "NL"
    )),
    Relation("PH", Direction.BI, 3, List(
      "US", "JP"
    )),
    Relation("PH", Direction.BI, 2, List(
      "KR", "AU", "AE", "SA", "KR"
    )),
    Relation("VN", Direction.BI, 2, List(
      "TW", "JP", "DE", "SA", "GB"
    )),
    Relation("TH", Direction.BI, 3, List(
      "JP", "KR", "US"
    )),
    Relation("TH", Direction.TO, 2, List(
      "RU","TR","IR","AE","SA","IL","FR","DE","GB","NL","BE","DK","SE","NO","FI","AU","NZ","US"
    )),
    Relation("MY", Direction.TO, 2, List(
      "RU", "IL", "FR", "DE", "GB", "NL", "BE", "DK", "SE", "NO", "FI", "AU", "NZ", "US", "JP", "TW", "KR"
    )),
    //south-asia
    Relation("IN", Direction.BI, 4, List(
      "BT", "AE"
    )),
    Relation("IN", Direction.BI, 3, List(
      "NP", "LK", "BD", "RU"
    )),
    Relation("IN", Direction.BI, 2, List(
      "GB", "FR", "MY", "MM", "US", "CA", "SG", "SA", "KW", "OM", "QA", "PH", "ID", "JP", "ZA", "KR"
    )),
    Relation("IN", Direction.BI, 1, List(
      "KE", "IL", "AF"
    )),
    Relation("BD", Direction.BI, 2, List(
      "SG", "MY", "AE", "SA", "GB", "US", "CA", "IT"
    )),
    Relation("PK", Direction.BI, 2, List(
      "AE", "SA", "GB", "US"
    )),
    //w-asia
    Relation("GE", Direction.BI, 3, List(
      "IL", "TR", "UA", "AE", "AZ", "DE"
    )),
    //europe
    Relation("RU", Direction.BI, 3, List(
      "EG", "KP", "TM", "IR", "IQ"
    )),
    Relation("RU", Direction.BI, 2, List(
      "SA", "ZA"
    )),
    Relation("UA", Direction.BI, 3, CoreEU ++ List(
      "SE", "DK", "IL"
    )),
    Relation("PL", Direction.BI, 2, List(
      "BR"
    )),
    Relation("TR", Direction.BI, 4, List(
      "AZ"
    )),
    Relation("TR", Direction.BI, 3, OECDish ++ List(
      "QA", "KZ", "UZ"
    )),
    Relation("TR", Direction.BI, 2, List(
      "RU", "UA", "GE", "IQ", "IR", "PK"
    )),
    Relation("RS", Direction.BI, 2, CoreEU ++ List(
      "RU", "TR", "HU", "RO", "CY"
    )),
    Relation("BA", Direction.BI, 4, CoreEU ++ List(
      "AL", "HU"
    )),
    Relation("XK", Direction.BI, 4, CoreEU ++ List(
      "GB"
    )),
    Relation("MK", Direction.BI, 4, CoreEU ++ List(
      "AL"
    )),
    Relation("IS", Direction.BI, 4, CoreEU ++ List(
      "GB", "DK", "NO", "SE", "CA", "US"
    )),
    Relation("FR", Direction.BI, 4, List(
      "GB", "US", "CA"
    )),
    Relation("FR", Direction.BI, 2, List(
      "TN", "DZ", "DJ", "MA", "SN", "CI"
    )),
    Relation("IT", Direction.BI, 2, List(
      "TN", "MA", "DZ", "IL"
    )),
    Relation("CH", Direction.BI, 4, List(
      "CA","IS","IE","GB","FR","DE","AT","IT","ES","PT","NL","BE","DK","SE","NO","FI","GR","CY"
    )),
    Relation("CH", Direction.BI, 2, List(
      "AE","EG","MA"
    )),
    Relation("GB", Direction.BI, 4, List(
      "AU","NZ","CA","US"
    )),
    Relation("GB", Direction.BI, 2, List(
      "NA","AR"
    )),
    Relation("PT", Direction.BI, 3, List(
      "CV"
    )),
    //mena
    Relation("IL", Direction.BI, 1, List(
      "IN", "RO", "AE"
    )),
    Relation("IL", Direction.BI, 2, List(
      "GB", "CY", "BG", "SG"
    )),
    Relation("IL", Direction.BI, 4, List(
      "CA", "US"
    )),
    Relation("SA", Direction.BI, 1, List(
      "SG", "CN", "IN", "BD"
    )),
    Relation("SA", Direction.BI, 2, List(
      "BN", "TR", "GB", "DJ"
    )),
    Relation("SA", Direction.BI, 3, List(
      "MY", "PK"
    )),
    Relation("AE", Direction.BI, 3, List(
      "FR", "DE", "MY", "SG", "PK"
    )),
    Relation("AE", Direction.BI, 2, List(
      "ET", "MR", "RW", "KE", "TZ", "GH"
    )),
    Relation("AE", Direction.BI, 1, List(
      "GB"
    )),
    Relation("IR", Direction.BI, 1, List(
      "AF"
    )),
    Relation("IR", Direction.BI, 2, List(
      "IN", "ID", "IQ", "BD", "AM", "MY", "SG"
    )),
    Relation("QA", Direction.BI, 3, List(
      "GB", "FR", "DE", "TR", "MY", "SG", "PK"
    )),
    Relation("QA", Direction.BI, 2, List(
      "IR", "KR", "IN", "RW", "KE", "TZ"
    )),
    Relation("EG", Direction.BI, 2, List(
      "DE", "FR", "UA", "IT", "GB"
    )),
    Relation("TN", Direction.BI, 2, List(
      "GB", "DE", "CH"
    )),
    Relation("TN", Direction.BI, 3, List(
      "FR", "AE"
    )),
    Relation("MA", Direction.BI, 5, List(
      "EH"
    )),
    Relation("MA", Direction.BI, 3, CoreEU ++ List(
      "PT", "GB", "US"
    )),
    Relation("MA", Direction.BI, 2, List(
      "TN", "BJ", "BF", "CI", "GM", "GH", "GN", "GW", "LR", "NE", "NG", "SN", "TG"
    )),
    //africa, sub
    Relation("ZA", Direction.BI, 2, List(
      "GB", "DE", "US", "AU", "MW", "IN", "TZ", "KE", "UG", "ET"
    )),
    Relation("AO", Direction.BI, 2, List(
      "PT", "MZ", "CV", "KE", "ET", "NG"
    )),
    Relation("TZ", Direction.BI, 2, List(
      "GB", "MW", "MZ", "MW", "MG", "ZA", "ZM", "ZW", "KM"
    )),
    Relation("KE", Direction.BI, 4, List(
      "SO"
    )),
    Relation("KE", Direction.BI, 2, List(
      "GB", "MW", "MZ", "MW", "ZM"
    )),
    Relation("ET", Direction.BI, 4, List(
      "DJ"
    )),
    Relation("ET", Direction.BI, 2, List(
      "EG", "UG", "RW", "AE"
    )),
    Relation("ET", Direction.BI, 1, List(
      "IT", "SD", "US", "KE"
    )),
    Relation("CI", Direction.BI, 2, List(
      "BJ", "BF", "CI", "GM", "GH", "GN", "GW", "LR", "NE", "NG", "SN", "TG", "CM"
    )),
    Relation("SN", Direction.BI, 2, List(
      "FR", "CV", "GM", "GH", "GN", "ML"
    )),
    Relation("GH", Direction.BI, 2, List(
      "US", "CA", "GB", "BF", "LR", "SL", "NG", "ST", "RW", "KE", "CM"
    )),
    Relation("LR", Direction.BI, 2, List(
      "US", "SL", "GN", "NG", "ST", "UG"
    )),
    Relation("NG", Direction.BI, 2, List(
      "KE", "UG", "GQ"
    )),
    Relation("GA", Direction.BI, 2, List(
      "CM", "BN", "CG", "GQ"
    )),
    Relation("CV", Direction.BI, 2, List(
      "NL", "GB", "PT"
    )),
    //americas
    Relation("CA", Direction.BI, 4, CoreEU ++ List(
      "JP","KR","TW","AU","NZ","BM","BS","GB","DK","NO","SE","FI","PT"
    )),
    Relation("CA", Direction.BI, 3, List(
      "CO","AE","BR"
    )),
    Relation("US", Direction.BI, 4, List(
      "JP","KR","TW","AU","NZ","BM","BS"
    )),
    Relation("US", Direction.BI, 3, List(
      "KW","QA","AE"
    )),
    Relation("US", Direction.BI, 2, List(
      "AR"
    )),
    Relation("US", Direction.BI, 1, List(
      "SA","EG","VN"
    )),
    Relation("DO", Direction.BI, 3, List(
      "CU", "PR", "JM", "PA", "CO"
    )),
    Relation("JM", Direction.BI, 4, List(
      "TC", "AG", "BB", "BS", "KY", "TT"
    )),
    Relation("CO", Direction.BI, 3, List(
      "US", "PE", "EC", "PA", "CL"
    )),
    Relation("CO", Direction.BI, 2, List(
      "MX", "BR", "BO", "ES"
    )),
    Relation("VE", Direction.BI, 2, List(
      "IR", "RU", "BR"
    )),
    Relation("PE", Direction.BI, 2, List(
      "CL", "BO", "EC", "CO", "MX", "US", "JP", "CN", "ES"
    )),
    Relation("BR", Direction.BI, 3, List(
      "PT", "AR", "BO", "PY", "UY", "PE", "CL"
    )),
    Relation("BR", Direction.BI, 2, List(
      "BO", "CO", "MX", "US", "ZA", "AO", "JP", "CN", "DE", "CV"
    )),
    Relation("BR", Direction.BI, 1, List(
      "IN", "FR"
    )),
    Relation("AR", Direction.BI, 2, List(
      "UY", "PY", "ES"
    )),
    Relation("CL", Direction.BI, 4, List(
      "CO","AR","UY","PY","MX","ES","CA","PA" //chile has FTAs with everyone
    )),
    Relation("CL", Direction.BI, 3, List(
      "US","JP","KR","HK","AU","NZ","GB","DE","PL"  //chile has FTAs with everyone
    )),
    Relation("CL", Direction.BI, 2, List(
      "PE","BO","CN"
    )),
  )
  lazy val ENMITIES = List(
    Relation("KP", Direction.BI, -3, OECDish),
    Relation("KP", Direction.BI, -2, List(
      "BN", "KH", "ID", "LA", "MY", "PH", "SG", "TH", "VN", "BR", "IN", "ZA", "TR"
    )),
    Relation("RU", Direction.BI, -1, List(
      "HU"
    )),
    Relation("RU", Direction.BI, -3, List(
      "UA", "US", "CA", "JP", "KR", "TW", "AU", "NZ", "GB", "ME", "IE", "RO", "BG", "CY", "AT", "BE", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HR", "IS", "IT", "LT", "LU", "LV", "MT", "NL", "NO", "PL", "PT", "SI", "SK", "MD", "ES", "SE", "CH"
    )),
    Relation("BY", Direction.BI, -2, OECDish ++ List(
      "UA", "MD", "EE", "LT", "LV", "BG"
    )),
    Relation("IR", Direction.BI, -1, OECDish ++ List(
      "PK", "SA"
    )),
    Relation("IR", Direction.BI, -2, List(
      "MA", "EG", "BA"
    )),
    Relation("IL", Direction.BI, -2, List(
      "IQ", "YE", "LY", "SD", "KW", "SA", "TR"
    )),
    Relation("IL", Direction.BI, -4, List(
      "IR", "SY", "LB"
    )),
    Relation("PK", Direction.BI, -2, List(
      "IN", "IR"
    )),
    Relation("TR", Direction.BI, -1, List(
      "GR", "CY", "AM"
    )),
    Relation("US", Direction.BI, -3, List(
      "IR", "CU"
    )),
    Relation("CN", Direction.BI, 0, List(
      "KR"
    )),
    Relation("CN", Direction.BI, -1, List(
      "US", "IN", "AU", "JP", "TW"
    )),
    Relation("AM", Direction.BI, -3, List(
      "AZ"
    )),
    Relation("VE", Direction.TO, -1, List(
      "CO", "SR", "AR", "GB", "CL", "AW"
    )),
    Relation("VE", Direction.TO, -2, List(
      "GY", "FR"
    )),
    Relation("VE", Direction.TO, -3, List(
      "US", "PR"
    ))
  )


  mainFlow()



  def mainFlow() = {
    var mutualRelationshipMap = getCountryMutualRelationship()

    mutualRelationshipMap = affiliationAdjustment(mutualRelationshipMap)
    mutualRelationshipMap = relationAdjustment(mutualRelationshipMap, FRIENDSHIPS)
    mutualRelationshipMap = relationAdjustment(mutualRelationshipMap, ENMITIES)

//    println("Saving country mutual relationships: " + mutualRelationshipMap)

    CountrySource.updateCountryMutualRelationships(mutualRelationshipMap)

    println("DONE Saving country mutual relationships")
  }

  def affiliationAdjustment(existingMap : mutable.Map[(String, String), Int]) : Map[(String, String), Int] = {
    println(s"affiliations: $AFFILIATIONS")
    AFFILIATIONS.foreach {
      case Affiliation(id, relationship, members) =>
        members.foreach { memberX =>
          if (CountrySource.loadCountryByCode(memberX).isDefined) {
            members.foreach { memberY =>
              if (memberX != memberY) {
                val shouldPatch = existingMap.get((memberX, memberY)) match {
                  case Some(existingValue) => existingValue < relationship
                  case None => true
                }
                if (shouldPatch) {
//                  println(s"patching $memberX vs $memberY from $id with $relationship")
                  existingMap.put((memberX, memberY), relationship)
                } else {
//                  println(s"Not patching $memberX vs $memberY from $id with $relationship as existing value is greater")
                }
              }
            }
          } else {
            println(s"Country code $memberX not found")
          }
        }
    }
    existingMap
  }

  def relationAdjustment(existingMap: mutable.Map[(String, String), Int], adjustmentMap: List[Relation] ): Map[(String, String), Int] = {
    import Direction._
    adjustmentMap.foreach {
      case Relation(id, direction, relationship, members) =>
        members.foreach { member =>
          if (CountrySource.loadCountryByCode(member).isDefined && member != id) {
            if(direction == Direction.TO){
              existingMap.put((member, id), relationship)
//              println(s"$member -> $id with $relationship")
            } else if (direction == Direction.FROM) {
              existingMap.put((id, member), relationship)
//              println(s"$id -> $member with $relationship")
            } else {
              existingMap.put((id, member), relationship)
              existingMap.put((member, id), relationship)
//              println(s"$id <-> $member with $relationship")
            }
          } else {
            println(s"Country code $member not found | duplicate entry")
          }
        }
    }
    existingMap
  }

  /**
   * get from country-mutual-relationship.csv
   */
  def getCountryMutualRelationship() = {
    val nameToCode = CountrySource.loadAllCountries().map( country => (country.name, country.countryCode)).toMap
//    val linesIter = scala.io.Source.fromFile("country-mutual-relationship.csv").getLines()
//    val headerLine = linesIter.next()
//
//    val countryHeader = headerLine.split(',').filter(!_.isEmpty())
//
    val mutualRelationshipMap = Map[(String, String), Int]()
//
//    while (linesIter.hasNext) {
//      val tokens = linesIter.next().split(',').filter(!_.isEmpty())
//      //first token is the country name itself
//      val fromCountry = tokens(0)
//      for (i <- 1 until tokens.size) {
//        val relationship = tokens(i)
//        val strength = relationship.count( _ == '1') //just count the number of ones should be sufficient
//        val toCountry = countryHeader(i - 1)
//        //println(fromCountry + " " + toCountry + " " + strength)
//        if (strength > 0) {
//          if (nameToCode.contains(fromCountry) && nameToCode.contains(toCountry)) {
//            mutualRelationshipMap.put((nameToCode(fromCountry), nameToCode(toCountry)), strength)
//          }
//        }
//      }
//    }

    nameToCode.values.foreach { countryCode =>
      mutualRelationshipMap.put((countryCode, countryCode), 5) //country with itself is 5 HomeCountry
    }

    mutualRelationshipMap
  }

  case class Relation(id : String, direction : Direction.Value, relationship: Int, members : List[String])

  object Direction extends Enumeration {
    type Direction = Value
    val FROM, TO, BI = Value
  }

  case class Affiliation(id : String, relationship: Int, members : List[String])



}