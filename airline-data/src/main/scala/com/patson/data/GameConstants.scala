package com.patson.data

import com.patson.model.Airport

object GameConstants {
  val COUNTRIES_SUB_SAHARAN = List("AO", "BJ", "BW", "BF", "BI", "CM", "CV", "CF", "TD", "KM", "CG", "CD", "CI", "DJ", "GQ", "ER", "ET", "GA", "GM", "GH", "GN", "GW", "KE", "LS", "LR", "MG", "MW", "ML", "MR", "MU", "YT", "MZ", "NA", "NE", "NG", "RE", "RW", "ST", "SN", "SC", "SL", "SO", "ZA", "SS", "SZ", "TZ", "TG", "UG", "ZM", "ZW")
  val ISOLATED_COUNTRIES: Seq[String] = List("AG", "AI", "BQ", "BL", "BS", "CC", "CK", "CV", "DM", "FO", "GD", "KM", "KY", "MF", "MS", "MU", "MV", "NP", "SC", "ST", "SX", "TC", "VI", "VG", "VC", "VU", "WF")
  val ISOLATED_ISLAND_AIRPORTS: Seq[String] = List(
    //europe
    "GRW", "CVU", "PXO", "SJZ", //pt
    "VDE", "GMZ", //es
    "IDY", "ACI", "ISC", "OUI", "IDY", //FR
    "PNL", "LMP", //IT
    "HGL", "BMK", "GWT", "BMR", //DE
    "EGH", "EOI", "FIE", "FOA", "LWK", "LSI", "ACI", "TRE", "BRR", "BEB", "SYY", "KOI", "ILY", "CAL", "ISC", "GCI", "JER", "GIB", "IOM", "EOI", //GB
    "JAG", "BYR", "RNN", "FAE", //DK
    "MHQ", "KDL", "URE", "ENF", "KTT", //FI
    "KDL", "URE", //ee
    "IOR","INQ","IIA", //IE
    "PJA", //SE
    "EN9","EN1","EN2", "SKN", "SSJ", "BNN", "MOL", "OSY", "RVK", "SDN", "SOG", "HAU", //NO
    "HZK", "GRY", //IS
    "AOK", "JMK", "JNX", "JSI", "JTR", "KIT", "LKS", "MLO", "SMI", "JIK", "KGS", "RHO", "LXS", "MJT", "JKH", "ZTH", "EFL", "SMI", //GR
    "KGD", "ITU", //RU
    //americas
    "FRD", "ESD", "ACK", "MVY", "BID", "AVX", "OTZ", //US
    "JNU", //US AK
    "YGR", "YPN", "YYB", //CA
    "FSP",
    //carribean
    "PVA", "CYB", "RTB", "UII", "GJA", "CPX", "VQS", "SPR", "CYC", "CUK", "NAS", "EUX", "MNI", "MQS", "GST",
    "STT", "STX", "SAB", "EUX", "SXM", "SFG", "AXA", "SKB", "SBH", "NEV", "BBQ", "MNI", "GBJ", "NCA", "XSC", "GDT", "PTP", "FDF",
    "CAY",
    "ADZ",
    //oceania
    "WSZ", "WLS", "PMK",
    "NMF", "HRF", "KDM", "NAN", "MEE", "PTF", "ELC", "PMK", //oceania & AU
    //asia
    "HRF", "HDK", "PRI", //indian ocean
    "KUM", "TNE", "MYE", "MK1", "OIM", "HAC", "AO1", "SDS", "OIR", "RIS", "OKI", "TSJ", "FUJ", "KKX", "TKN", "OKE", "RNJ", "UEO", "OKA", "MMY", "TRA", "ISG", "OGN", "IKI", "MMD", "KTD", "OIM", //JP
    "KNH", "MZG", //TW
    "BSO", "CGM", "JOL", "CYU", "TWT", "IAO", "MBT", "USU", "ENI", //PH
    "TNJ", //TH
    "CNI", //CN
    "NAH", //ID
    "KHK", "KIH", "GSM", //IR
    "ZDY", //AE
    //africa
    "ZNZ", "MFA", //TZ
    "DZA", //FR Mayotte
    "RUN",
    "MMO", "SSG", "VIL",
    "MLN" //es enclaves
  )
  val OTHER_ISLAND_AIRPORTS: Seq[String] = List(
    //europe
    "PMI", "IBZ", "MAH", //es
    "BIA", "CLY", "AJA", "FSC", //fr
    "OLB", "AHO", "CAG", "PMO", "CTA", "TPS", "CIY", "EBA", //it
    "MLA",
    //asia
    "TBH", "TAG", "BCD", "IAO", "CGM", //ph
    "LBU", "BTH", "LGK", "TMC", "BMU", "TTE", "ARD", //my & id
    //americas
    "PMV",
    "WRG", "MQC", "YBC", "YCD", "LAK", "YPN", "YZG", "YEV", //ca
    "HYA", "MKK", "LNY", "HNH", "ISP", "HTO", //us
    //oceania
    "WLG", //nz (need to block channel crossing)

  )
  val ISLAND_AIRPORTS: Seq[String] = ISOLATED_ISLAND_AIRPORTS ++ OTHER_ISLAND_AIRPORTS


  def connectsIsland (fromAirport: Airport, toAirport: Airport) : Boolean = {
    if (ISLAND_AIRPORTS.contains(fromAirport.iata) || ISLAND_AIRPORTS.contains(toAirport.iata) || ISOLATED_COUNTRIES.contains(fromAirport.countryCode) || ISOLATED_COUNTRIES.contains(toAirport.countryCode)) {
      true
    } else {
      false
    }
  }

  def isIsland (iata: String) : Boolean = {
    ISLAND_AIRPORTS.contains(iata)
  }
}
