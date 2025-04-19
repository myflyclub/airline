package com.patson.data

import com.patson.model.Airport

object GameConstants {
  val COUNTRIES_SUB_SAHARAN = List("AO", "BJ", "BW", "BF", "BI", "CM", "CV", "CF", "TD", "KM", "CG", "CD", "CI", "DJ", "GQ", "ER", "ET", "GA", "GM", "GH", "GN", "GW", "KE", "LS", "LR", "MG", "MW", "ML", "MR", "MU", "YT", "MZ", "NA", "NE", "NG", "RE", "RW", "ST", "SN", "SC", "SL", "SO", "ZA", "SS", "SZ", "TZ", "TG", "UG", "ZM", "ZW")
  val ISOLATED_COUNTRIES: Seq[String] = List("AG", "AI", "BQ", "BL", "BS", "CC", "CK", "CV", "DM", "FO", "GD", "KM", "KY", "MF", "MS", "MU", "MV", "NP", "SC", "ST", "SX", "TC", "VI", "VG", "VC", "VU", "WF")
  val ISOLATED_ISLAND_AIRPORTS: Seq[String] = List(
    //europe
    "SMA", "SJZ", "HOR", "FNC", "PIX", "GRW", "PXO", "CVU", //pt
    "VDE", "GMZ", "SPC", "LPA", "FUE", "ACE", "IBZ", "PMI", "MAH", //es
    "IDY", "ACI", "BIC", "ISC", "OUI", "IDY", //FR
    "UVE", "LIF", "TGJ", "MEE", "ILP", //FR Pacific
    "PNL", "LMP", "EBA", //IT
    "HGL", "BMK", "GWT", "BMR", //DE
    "EGH", "EOI", "FIE", "FOA", "LWK", "LSI", "ACI", "TRE", "BRR", "BEB", "SYY", "KOI", "ILY", "CAL", "ISC", "GCI", "JER", "GIB", "IOM", "EOI", //GB
    "BYR", "RNN", "FAE", //DK
    "MHQ", "KDL", "URE", "ENF", "KTT", //FI
    "KDL", "URE", //ee
    "IOR","INQ","IIA", //IE
    "PJA", //SE
    "EN9","EN1","EN2", "SKN", "SSJ", "BNN", "MOL", "OSY", "RVK", "SDN", "SOG", "HAU", "LKN", "VRY", "SVJ", "ANX", "HAA", "HFT", "MEH", //NO
    "HZK", "GRY", //IS
    "AOK", "JMK", "JNX", "JSI", "KIT", "LKS", "MLO", "SMI", "JIK", "KGS", "RHO", "LXS", "MJT", "JKH", "ZTH", "EFL", "SMI", "JKL", "KZS", //GR
    "KGD", "ITU", "CSH", "VKT", "BVJ", "DEE", //RU
    //americas
    "FRD", "ESD", "ACK", "MVY", "BID", "AVX", "OTZ", //US
    "JNU", "SIT", "HNH", "WRG", "KTN", "PSG", "SOV", "HOM", "PDB", "TEK", "SCC", //US northwest passage & AK
    "MKK", "LNY", //US HI
    "ZMT", "YZP", "YBL", "YPW", "YAZ", //CA northwest passage
    "YGR", "YPN", "YYB", "YBE", "YAY", "YYG", //CA
    "MQC", //FR
    "FSP",
    //caribbean
    "CYB", "RTB", "UII", "SPR",  "NAS", "MQS", "GST", "FPO",
    "STT", "STX", "SXM", "SFG", "SKB", "SBH", "NEV", "BBQ", "NCA", "XSC", "GDT",
    "MNI", "AXA", //GB
    "CPX", "VQS", //US
    "PTP", "FDF", "SFG", "SBH", "GBJ", "CAY", //FR
    "SAB", "EUX", "BON", //NL
    "ADZ", "PVA", //CO
    "LRV", "PMV", //VE
    "BOC", "OTD", //PA
    "GJA", "RTB", //HU
    "CYC", "CUK", //BE
    //oceania
    "NMF", "HRF", "KDM", "NAN", "MEE", "PTF", "ELC",  //oceania & AU
    "PMK", "OKR", "SBR", "CNC", "SYU", "KUG", "HID", "ABM", "ONG", //QZ AU
    "GTE", "WSZ", "WLS", "ELC", "MGT", "SNB", "KGC", "KNS", "FLS", "CBI", //more AU
    //asia
    "HRF", "HDK", "PRI", //indian ocean
    "KUM", "TNE", "MYE", "MK1", "OIM", "HAC", "AO1", "SDS", "OIR", "RIS", "OKI", "TSJ", "FUJ", "KKX", "TKN", "OKE", "RNJ", "UEO", "OKA", "MMY", "TRA", "ISG", "OGN", "IKI", "MMD", "KTD", "OIM", //JP
    "KNH", "MZG", //TW
    "BSO", "CGM", "JOL", "CYU", "TWT", "IAO", "MBT", "USU", "ENI", //PH
    "USM", //TH
    "CNI", //CN
    "NAH", "BTH", "WNI", "KSR", "BIK", "RJM", "ARD", "TJQ", "PGK", "RKI", "GNS", "LKI", "NAM", "MKQ",  //ID
    "TOD", "LGK", //MY
    "KHK", "KIH", "GSM", "TNJ", //IR
    "ZDY", //AE
    //africa
    "ZNZ", "MFA", //TZ
    "DZA", "RUN", //FR
    "MMO", "SSG", "VIL",
    "CAB", //AO enclave
    "MLN", "JCU", //es enclaves
  )
  val OTHER_ISLAND_AIRPORTS: Seq[String] = List(
    //europe
    "JEG", "UAK", "KUZ", "KUS", "AGM", "CNP", "JQA", "JUV", //DK GL
    "TER", "PDL", //pt
    "PMI", "IBZ", "MAH", //es
    "BIA", "CLY", "AJA", "FSC", "MCM", //fr
    "OLB", "AHO", "CAG", "PMO", "CTA", "TPS", "CIY", "EBA", //it
    "JTR", //gr
    "MLA",
    //asia
    "BAH", "AUH", "AAN", //block local connects
    "TBH", "TAG", "BCD", "IAO", "CGM", "TAG", "TBH", //ph
    "LBU", "BTH", "LGK", "TMC", "BMU", "TTE", "ARD", "DPS", "LOP", "NAM", "TTE",  //my & id
    //americas
    "PMV",
    "YBC", "YCD", "LAK", "YPN", "YZG", "YEV", "LAK", "YVQ", "ZFN", "ZKE", "YKQ", "YZG", "YZG", "YSO", "YMN", //ca
    "YYJ",
    "HYA", "ISP", "HTO", //us allow channel crossings & block transit
    "HNL", "OGG", "HNM", //us HI
    "HTI", "CDB", "NLG", "SDP", "ADQ", "MYU", "PQS", "RSH", "ANI", "NUP", "AKN", "KCG", "SDP", "NLG", "TKJ", "MNT", "OTZ", "OME", //US AK (want to ignore min distance)
    "CZM", //MX
    //oceania
    "WLG", //nz (allow channel crossing)
    //africa
    "SSH", //eg (allow channel crossing)
    "BZV", "KGL", "BSG", "COO", "LFW", "BJL", //support local int'l connections
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
