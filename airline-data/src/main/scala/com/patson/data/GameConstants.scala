package com.patson.data

import com.patson.model.Airport

object GameConstants {
  val COUNTRIES_SUB_SAHARAN = List("AO", "BJ", "BW", "BF", "BI", "CM", "CV", "CF", "TD", "KM", "CG", "CD", "CI", "DJ", "GQ", "ER", "ET", "GA", "GM", "GH", "GN", "GW", "KE", "LS", "LR", "MG", "MW", "ML", "MR", "MU", "YT", "MZ", "NA", "NE", "NG", "RE", "RW", "ST", "SN", "SC", "SL", "SO", "ZA", "SS", "SZ", "TZ", "TG", "UG", "ZM", "ZW")
  val ISOLATED_COUNTRIES: Seq[String] = List("AG", "AI", "AS", "BQ", "BL", "BS", "BT", "CC", "CK", "CV", "DM", "FO", "GD", "KI", "KM", "KY", "MF", "MP", "MS", "MU", "MV", "NP", "SC", "ST", "SX", "TC", "VI", "VG", "VC", "VU", "WF")
  val ISOLATED_ISLAND_AIRPORTS: Seq[String] = List(
    //europe
    "SMA", "SJZ", "HOR", "FNC", "PIX", "GRW", "PXO", "CVU", //pt
    "VDE", "GMZ", "SPC", "LPA", "FUE", "ACE", "IBZ", "PMI", "MAH", //es
    "IDY", "ACI", "BIC", "ISC", "OUI", "IDY", //FR
    "UVE", "LIF", "TGJ", "MEE", "ILP", "NOU", //FR Pacific
    "PNL", "LMP", "EBA", //IT
    "HGL", "BMK", "GWT", "BMR", //DE
    "EGH", "EOI", "FIE", "FOA", "LWK", "LSI", "ACI", "TRE", "BRR", "BEB", "SYY", "KOI", "ILY", "CAL", "ISC", "GCI", "JER", "GIB", "IOM", "EOI", "NRL", "PPW", "WRY", //GB
    "BYR", "RNN", "FAE", //DK
    "CPH", //highly connected but want more demand to Jutland
    "MHQ", "KDL", "URE", "ENF", "KTT", //FI
    "KDL", "URE", //ee
    "IOR","INQ","IIA", //IE
    "PJA", "HMV", //SE
    "EN9","EN1","EN2", "SKN", "SSJ", "BNN", "MOL", "OSY", "RVK", "SDN", "SOG", "HAU", "LKN", "VRY", "SVJ", "ANX", "HAA", "HFT", "MEH", "RET", //NO
    "HZK", "GRY", //IS
    "AOK", "JMK", "JNX", "JSI", "KIT", "LKS", "MLO", "SMI", "JIK", "KGS", "RHO", "LXS", "MJT", "JKH", "ZTH", "EFL", "SMI", "JKL", "KZS", "KSJ", "JTY", "SKU", //GR
    "KGD", "ITU", "CSH", "VKT", "BVJ", "DEE", //RU
    //americas
    "MVY", "BID", "ACK", "AVX", //US
    "FRD", "ESD", "RCE", "LPS", //US San Juans
    "JNU", "SIT", "HNH", "WRG", "KTN", "PSG", "SOV", "HOM", "PDB", "TEK", "SCC", "OTZ", //US northwest passage & AK
    "MKK", "LNY", "HNL",  //US HI
    "ZMT", "YZP", "YBL", "YPW", "YAZ", //CA northwest passage
    "YGR", "YPN", "YYB", "YBE", "YAY", "YYG", //CA
    "MQC", //FR
    "FSP",
    "LQM",
    "DRJ", //SR
    "IQT", "PCL", "CIJ", "CZS", //Amazon
    "TJA", "CIJ", "TDD", "UYU", "PCL", //PE
    //caribbean
    "CYB", "UII", "SPR",  "NAS", "MQS", "GST", "FPO", "SLU",
    "STT", "STX", "SXM", "SFG", "SKB", "SBH", "NEV", "BBQ", "NCA", "XSC", "GDT",
    "GER", "CYO", //CU
    "MNI", "AXA", //GB
    "CPX", "VQS", //US
    "PTP", "FDF", "SFG", "SBH", "GBJ", "CAY", //FR
    "SAB", "EUX", "BON", "CUR", "AUA", //NL
    "ADZ", "PVA", //CO
    "LRV", "PMV", "LSP", "ICC", //VE
    "BOC", "OTD", //PA
    "GJA", "RTB", //HU
    "CYC", "CUK", "PND", "PLJ",  //BZ
    //oceania
    "NMF", "HRF", "KDM", "NAN", "MEE", "PTF", "ELC", "XCH",   //oceania & AU
    "PMK", "OKR", "SBR", "CNC", "SYU", "KUG", "HID", "ABM", "ONG", //QZ AU
    "GTE", "WSZ", "WLS", "ELC", "MGT", "SNB", "KGC", "KNS", "FLS", "CBI", //more AU
    //asia
    "HVD", "UKK", "PLX", "PWQ", "AAT", "FYN", "BPL", "ULO", "ULG", "KJI",
    "HRF", "HDK", "PRI", //indian ocean
    "KUM", "TNE", "MYE", "MK1", "OIM", "HAC", "AO1", "SDS", "OIR", "RIS", "OKI", "TSJ", "FUJ", "KKX", "TKN", "OKE", "RNJ", "UEO", "OKA", "MMY", "TRA", "ISG", "OGN", "IKI", "MMD", "KTD", "OIM", //JP
    "KNH", "MZG", "KYD", "MFK", "LZN", //TW
    "BSO", "CGM", "JOL", "CYU", "TWT", "IAO", "MBT", "USU", "ENI", //PH
    "USM", //TH
    "CNI", "DDR", "LGZ", "NGQ", //CN
    "NAH", "BTH", "WNI", "KSR", "BIK", "RJM", "ARD", "TJQ", "PGK", "RKI", "GNS", "LKI", "NAM", "MKQ",  //ID
    "TOD", "LGK", //MY
    "KHK", "KIH", "GSM", "TNJ", //IR
    "ZDY", //AE
    //africa
    "ZNZ", "MFA", //TZ
    "DZA", "RUN", //FR
    "MMO", "SSG", "VIL",
    //"CAB", //AO enclave
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
    "XQU", //vancouver island
    "YYJ",
    "HYA", "ISP", "HTO", //us allow channel crossings & block transit
    "OGG", "HNM", //us HI
    "HTI", "CDB", "NLG", "SDP", "ADQ", "MYU", "PQS", "RSH", "ANI", "NUP", "AKN", "KCG", "SDP", "NLG", "TKJ", "MNT", "OTZ", "OME", //US AK (want to ignore min distance)
    "CZM", //MX
    "CYA", //HT
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

  val HEAVY_RAIL_LINKS = List(
    ("LHR","EDI"),
    ("LHR","LBA"),
    ("LHR","MME"),
    ("LHR","NCL"),
    ("LHR","CDG"),
    ("LGW","CDG"),
    ("LCY","CDG"),
    ("STN","CDG"),
    ("LHR","ORY"),
    ("LGW","ORY"),
    ("LCY","ORY"),
    ("STN","ORY"),
    ("LHR","BRU"),
    ("LCY","BRU"),
    ("LHR","EIN"),
    ("LHR","RTM"),
    ("LGW","AMS"),
    ("LGW","EIN"),
    ("LGW","RTM"),
    ("LCY","AMS"),
    ("LHR","RTM"),
    ("LCY","RTM"),
    ("LHR","LIL"),
    ("XCR","SXB"),
    ("CDG","LYS"),
    ("ORY","LYS"),
    ("CDG","MRS"),
    ("ORY","MRS"),
    ("CDG","BOD"),
    ("ORY","BOD"),
    ("CDG","SXB"),
    ("ORY","SXB"),
    ("CDG","BSL"),
    ("ORY","BSL"),
    ("CDG","NTE"),
    ("ORY","NTE"),
    ("LYS","BCN"),
    ("MRS","BCN"),
    ("CDG","ZRH"),
    ("ORY","ZRH"),
    ("CDG","GVA"),
    ("ORY","GVA"),
    ("MRS","GVA"),
    ("CDG","BRU"),
    ("ORY","BRU"),
    ("CDG","AMS"),
    ("ORY","AMS"),
    ("CDG","EIN"),
    ("ORY","EIN"),
    ("AMS","BRU"),
    ("AMS","CGN"),
    ("AMS","DUS"),
    ("AMS","FRA"),
    ("ANR","AMS"),
    ("ANR","RTM"),
    ("LUX","RTM"),
    ("LUX","CDG"),
    ("LUX","ORY"),
    ("LUX","BRU"),
    ("BRU","CGN"),
    ("BRU","FRA"),
    ("FRA","ORY"),
    ("FRA","CDG"),
    ("FRA","CGN"),
    ("FRA","STR"),
    ("FRA","BSL"),
    ("FRA","ZRH"),
    ("MUC","STR"),
    ("BER","MUC"),
    ("BER","HAM"),
    ("BER","FRA"),
    ("HAM","FRA"),
    ("HAM","MUC"),
    ("DUS","BER"),
    ("DUS","MUC"),
    ("MAD","BCN"),
    ("MAD","SVQ"),
    ("MAD","VLC"),
    ("MAD","AGP"),
    ("MAD","ZAZ"),
    ("LIS","OPO"),
    ("FCO","LIN"),
    ("FCO","MXP"),
    ("FCO","BGY"),
    ("CIA","LIN"),
    ("CIA","MXP"),
    ("CIA","BGY"),
    ("FCO","BLQ"),
    ("FCO","FLR"),
    ("FCO","NAP"),
    ("TRN","VCE"),
    ("LIN","VCE"),
    ("ZRH","GVA"),
    ("ZRH","LUG"),
    ("GVA","MXP"),
    ("MUC","NUE"),
    ("FRA","NUE"),
    ("BER","NUE"),
    ("VIE","NUE"),
    ("VIE","SZG"),
    ("VIE","INN"),
    ("VIE","BUD"),
    ("VIE","PRG"),
    ("ARN","GOT"),
    ("MMX","GOT"),
    ("ARN","MMX"),
    ("OSL","GOT"),
    ("HND","ITM"),
    ("NRT","ITM"),
    ("HND","KIX"),
    ("NRT","KIX"),
    ("HND","NGO"),
    ("HND","SDJ"),
    ("FSZ","ITM"),
    ("FSZ","KIX"),
    ("FSZ","NRT"),
    ("FSZ","SDJ"),
    ("FSZ","UKB"),
    ("NJA","ITM"),
    ("NJA","KIX"),
    ("NJA","NGO"),
    ("NJA","SDJ"),
    ("NJA","UKB"),
    ("NRT","SDJ"),
    ("AOJ","SDJ"),
    ("AOJ","HNA"),
    ("AOJ","SDJ"),
    ("ITM","OKJ"),
    ("KIX","OKJ"),
    ("HND","TOY"),
    ("NRT","TOY"),
    ("ITM","NOG"),
    ("KIX","FUK"),
    ("HND","SDJ"),
    ("HND","AOJ"),
    ("HND","HKD"),
    ("HND","KMQ"),
    ("FUK","KOJ"),
    ("TPE","KHH"),
    ("TPE","TNN"),
    ("TSA","TNN"),
    ("KHH","TSA"),
    ("ICN","PUS"),
    ("JFK","BDI"),
    ("JFK","BOS"),
    ("JFK","PVD"),
    ("JFK","DCA"),
    ("JFK","BWI"),
    ("EWR","BWI"),
    ("EWR","PVD"),
    ("HVN","BWI"),
    ("HVN","BOS"),
    ("MDT","JFK"),
    ("MDT","EWR"),
    ("MDT","LGA"),
    ("JED","MED")
  )

  def doesPairExist(s1: String, s2: String, lookupSet: Set[(String, String)]): Boolean = {
    val lookupTuple = if (s1 < s2) (s1, s2) else (s2, s1)
    lookupSet.contains(lookupTuple)
  }

  val railLookupSet: Set[(String, String)] = HEAVY_RAIL_LINKS.map {
    case (a, b) if a < b => (a, b)
    case (a, b)          => (b, a)
  }.toSet
}
