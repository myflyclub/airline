package com.patson.init

import com.patson.model._
import com.patson.data.{AirportSource, DestinationSource, GameConstants}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object AirportFeaturePatcher extends App {

  import AirportFeatureType._

  lazy val featureList = Map(

    INTERNATIONAL_HUB -> Map[String, Int](
      /**
       * international vacation destinations
       */
"JED" -> 75, //Jeddah
"AYT" -> 75, //Antalya
"IST" -> 75, //Istanbul
"NRT" -> 65, //Tokyo / Narita
"DAD" -> 64, //Da Nang
"CDG" -> 64, //Paris
"DPS" -> 63, //Denpasar-Bali Island
"PQC" -> 60, //Phu Quoc Island
"LHR" -> 60, //London
"BCN" -> 57, //Barcelona
"DXB" -> 55, //Dubai
"AMS" -> 55, //Amsterdam
"CNX" -> 54, //Chiang Mai
"HKT" -> 54, //Phuket
"USM" -> 52, //Na Thon (Ko Samui Island)
"UTP" -> 52, //Rayong
"CUN" -> 50, //Cancún
"KUL" -> 50, //Kuala Lumpur
"TFS" -> 50, //Tenerife Island
"FCO" -> 50, //Rome
"MEX" -> 50, //Mexico City
"JFK" -> 50, //New York
"ATH" -> 50, //Athens
"BOM" -> 49, //Mumbai
"CXR" -> 49, //Nha Trang
"KBV" -> 49, //Krabi
"PVG" -> 49, //Shanghai
"HRG" -> 48, //Hurghada
"BKK" -> 48, //Bangkok
"KIX" -> 48, //Osaka
"SGN" -> 48,
"SIN" -> 47, //Singapore
"CUZ" -> 46, //Cusco
"LAX" -> 46, //Los Angeles
"SYD" -> 45, //Sydney Australia
"SAW" -> 45, //Istanbul
"CMN" -> 45, //Casablanca
"BOG" -> 44, //Bogota
"DEL" -> 44,
"HER" -> 44, //Heraklion
"MLE" -> 43, //Malé Maldives
"MAD" -> 42, //Madrid
"RHO" -> 42, //Rodes Island
"NLU" -> 42, //Mexico City
"SCL" -> 41, //Santiago
"PMI" -> 40, //Palma De Mallorca
"TFN" -> 40, //Tenerife Island
"MBJ" -> 40, //Montego Bay
"ICN" -> 40, //Seoul
"TPE" -> 40,
"GRU" -> 39, //São Paulo
"PUJ" -> 38, //Punta Cana
"RMF" -> 38, //Marsa Alam
"YVR" -> 38, //Vancouver
"NCE" -> 38, //Nice
"LGK" -> 38, //Langkawi
"SSH" -> 37, //Sharm el-Sheikh
"NAN" -> 37, //Nadi
"AGA" -> 37, //Agadir
"GOI" -> 37, //Vasco da Gama
"DJE" -> 36, //Djerba
"CPT" -> 36, //Cape Town
"CMB" -> 36, //Colombo
"CZM" -> 36, //Cozumel
"RAK" -> 35, //Marrakech
"ADB" -> 35, //Izmir
"GIG" -> 35, //Rio De Janeiro
"LED" -> 35, //St. Petersburg
"PNH" -> 35, //Phnom Penh
"CAI" -> 35, //Cairo Egypt
"VIE" -> 35, //Vienna
"DOH" -> 35,
"HAV" -> 34, //Havana
"YYZ" -> 34, //Toronto Canada
"TLV" -> 34, //Tel Aviv
"MED" -> 33, //Medina
"BER" -> 33, //Berlin
"DLM" -> 33, //Dalaman
"HKG" -> 33, //Hong Kong
"VCE" -> 33, //Venice
"SPX" -> 33, //Cairo
"HNL" -> 32, //Honolulu
"ALC" -> 32, //Alicante
"LIS" -> 32, //Lisbon
"LCA" -> 32, //Larnarca
"OOL" -> 32, //Gold Coast
"BJV" -> 32, //Bodrum
"LGW" -> 32, //London United Kingdom
"MIA" -> 32, //Miami
"IBZ" -> 31, //Ibiza
"ACE" -> 31, //Lanzarote Island
"ASR" -> 31, //Kayseri
"RUH" -> 30,
"MEL" -> 30, //Melbourne
"LIM" -> 30,
"FUE" -> 30, //Fuerteventura Island
"DME" -> 30, //Moscow
"EZE" -> 30, //Buenos Aires
"PTY" -> 30,
"AGP" -> 29, //Málaga
"SAI" -> 29, //Siem Reap
"MAH" -> 29, //Menorca Island
"KTM" -> 29, //Kathmandu
"SLL" -> 29, //Salalah
"PEN" -> 28, //Penang
"PPT" -> 28, //Papeete
"MRU" -> 28, //Port Louis
"OPO" -> 28,
"MCT" -> 28, //Muscat
"MUC" -> 27, //Munich
"LPB" -> 27, //La Paz / El Alto
"PEK" -> 27, //Beijing
"LPA" -> 26, //Gran Canaria Island
"BKI" -> 26, //Kota Kinabalu
"PRG" -> 26, //Prague
"LAS" -> 25, //Las Vegas
"CTG" -> 25, //Cartagena
"HAN" -> 25, //Hanoi
"GOX" -> 25, //Goa IN
"EWR" -> 25, //New York City USA
"CPH" -> 25, //Copenhagen
"ZNZ" -> 25, //Zanzibar
"EDI" -> 25, //Edinburgh
"SJD" -> 24, //San José del Cabo
"LAP" -> 24, //La Paz
"VRA" -> 24, //Varadero
"BUD" -> 24, //Budapest
"NOU" -> 24, //Nouméa
"POP" -> 24, //Puerto Plata Dominican Republic
"TIA" -> 24, //Triana
"TUN" -> 24, //Tunis
"BGY" -> 24, //Milan
"FNC" -> 23, //Funchal
"SID" -> 23, //Espargos
"GUM" -> 23, //Hagåtña Guam International Airport
"GVA" -> 23,
"BAH" -> 22, //Manama
"CTA" -> 22, //Catania
"SVO" -> 22, //Moscow
"MPH" -> 22, //Malay
"KGS" -> 22, //Kos Island
"AKL" -> 22, //Auckland
"HUI" -> 22, //Hue Phu Bai VN
"PDL" -> 21, //Azores
"CHQ" -> 21, //Heraklion
"NAP" -> 21, //Nápoli
"KEF" -> 21, //Reykjavík
"GZP" -> 21, //Gazipaşa
"SEZ" -> 21, //Mahe Island
"TER" -> 21, //Azores Lajes
"KIN" -> 21, //Kingston
"CIA" -> 20, //Ostia Antica Italy
"COV" -> 20, //Mersin TR
"VKO" -> 20, //Moscow
"CJC" -> 20, //Calama
"LPQ" -> 20, //Luang Phabang
"MXP" -> 20, //Milan
"PFO" -> 20, //Paphos
"IKA" -> 20, //Tehran
"STN" -> 20, //London
"CTS" -> 19, //Chitose / Tomakomai
"SFO" -> 19, //San Francisco
"ZRH" -> 19, //Zurich
"REU" -> 19, //Reus
"OGG" -> 18, //Kahului
"POA" -> 18, //Porto Alegre
"ZQN" -> 18, //Queenstown
"SHJ" -> 18, //Sharjah AE
"PER" -> 18, //Perth
"JNB" -> 18, //Johannesburg
"MIR" -> 18, //Monastir
"MVD" -> 18, //Montevideo
"JRO" -> 18, //Arusha
"TBS" -> 18, //Tbilisi
"PBH" -> 18,
"BVC" -> 18,
"SYX" -> 17, //Sanya
"MNL" -> 17,
"VAR" -> 17, //Varna
"DUB" -> 17, //Dublin Ireland
"CEB" -> 17, //Lapu-Lapu City
"SJU" -> 17, //San Juan
"SSA" -> 17, //Salvador
"COK" -> 17, //Kochi
"TNG" -> 17, //Tangiers
"NBE" -> 17, //Enfidha
"BEY" -> 17, //Beiruit
"MCO" -> 16, //Orlando
"OKA" -> 16, //Naha
"BNE" -> 16, //Brisbane
"PSA" -> 16, //Pisa
"FLR" -> 16, //Firenze
"AUA" -> 16, //Oranjestad
"OSL" -> 16, //Oslo
"BVC" -> 16, //Rabil
"BGI" -> 16, //Bridgetown
"BSL" -> 16, //Mulhouse French/Swiss Alps
"SJO" -> 16, //San Jose
"CJU" -> 15, //Jeju City
"CAG" -> 15, //Cagliari
"RUN" -> 15, //St Denis
"CUR" -> 15, //Willemstad
"JTR" -> 15, //Santorini Island
"ARN" -> 15, //Stockholm
"DRW" -> 15, //Darwin
"IAD" -> 15, //Washington
"GPS" -> 15, //Baltra Galapagos
"HEL" -> 15, //Helsinki
"PBH" -> 15, //Bhutan
"ROR" -> 15, //Palau
"RBA" -> 15, //Rabat
"USH" -> 15, //Ushuahia
"PUQ" -> 15, //Punta Arenas
"FAO" -> 14, //Faro
"PVR" -> 14, //Puerto Vallarta
"PMO" -> 14, //Palermo
"DMK" -> 14, //Bangkok
"CNS" -> 14, //Cairns
"CFU" -> 14, //Kerkyra Island
"KRK" -> 14, //Kraków
"LIR" -> 14, //Liberia Costa Rica
"VCS" -> 14, //Con Dao VN
"YQB" -> 14, //Quebec
"PQC" -> 14,
"ZIH" -> 14, //Ixtapa MX
"BOJ" -> 14, //Burgas
"HPH" -> 14,
"TZX" -> 14, //TR
"TIV" -> 13, //Tivat
"RTB" -> 13, //Roatan Island
"EVN" -> 13,
"DLI" -> 13,
"KOS" -> 12, //Sihanukville
"SKD" -> 12, //Samarkand
"NAS" -> 12, //Nassau
"SXM" -> 12, //Saint Martin
"TFU" -> 12, //Chengdu
"STT" -> 12, //Charlotte Amalie
"JMK" -> 12, //Mykonos Island
"QSR" -> 12, //Amalfi coast
"PLZ" -> 12, //Addo Elephant National Park South Africa
"HDS" -> 12, //Kruger National Park South Africa
"WAW" -> 12, //Warsaw
"PPS" -> 12, //Puerto Princesa City
"EBB" -> 12, //Kampala
"LBJ" -> 12, //Komodo National Park Indonesia
"MAO" -> 12, //Manaus
"PDP" -> 12, //Punta del Este
"RAI" -> 12, //Praia
"TQO" -> 12, //Tulum
"VTE" -> 12, //Luang Prabang Laos
"IQT" -> 12, //PE Amazon
"SMA" -> 12, //Azores
"TPS" -> 12, //Trapani IT
"TIR" -> 12, //Tirumala Venkateswara Temple
"ROR" -> 12,
"PKR" -> 12,
"ADD" -> 12,
"GLA" -> 12,
"BTH" -> 11, //Batam Island
"DBV" -> 11, //Dubrovnik
"VFA" -> 11, //Victoria Falls
"MRS" -> 11, //Marseille
"AMM" -> 11, //Amman
"ZTH" -> 11, //Zakynthos Island
"BJL" -> 11, //Banjul
"GAN" -> 11, //Maldives
"LXA" -> 11, //Lhasa
"VDO" -> 11, //Van Don VN
"LRM" -> 11, //La Romana DR
"TGD" -> 11,
"PTP" -> 10, //Pointe-Ã -Pitre
"GYD" -> 10, //Baku
"YUL" -> 10, //Montreal
"MFM" -> 10, //Macau
"SPU" -> 10, //Split
"SPC" -> 10,
"YHZ" -> 10, //Halifax
"HUX" -> 10, //Huatulco
"CGK" -> 10,
"BOD" -> 10,
"BWN" -> 10, //Bandar Seri Begawan
"JNU" -> 10, //Juneau
"TNM" -> 10, //AQ
"AUH" -> 10,
"FLG" -> 10, //Flagstaff Grand Canyon
"FLW" -> 10, //Azores Flores
"BUS" -> 10, //Armenia
"BRS" -> 10,
"REC" -> 9, //Recife
"LVI" -> 9, //Livingstone
"BRI" -> 9, //Bari
"NBO" -> 9, //Nairobi
"JAI" -> 9, //Jaipur
"TRN" -> 9, //Turin Italian Alps
"YZF" -> 9, //Yellowknife
"ANC" -> 9, //Anchorage
"GUA" -> 9, //Tikal Guatemala
"CCJ" -> 9, //Calicut
"FEZ" -> 9,
"LTN" -> 9, //London
"AER" -> 8, //Sochi
"TOS" -> 8, //Tromsø
"IGU" -> 8, //Foz Do IguaÃ§u
"UVF" -> 8, //Vieux Fort
"ANU" -> 8, //St. John's
"YYT" -> 8, //St John
"MUB" -> 8, //Maun
"STX" -> 8, //Christiansted
"SZG" -> 8, //Salzburg Austrian Alps
"MQP" -> 8, //Mpumalanga
"CCC" -> 8, //Cayo Coco
"GND" -> 8,
"UPN" -> 8, //Kgalagadi Transfrontier Park South Africa/Botswana
"VDE" -> 8, //Canary Islands
"TAO" -> 8, //Qingdao
"NJF" -> 8, //Shia pilgirms
"NOS" -> 8,
"BRN" -> 8,
"JHB" -> 8,
"ESU" -> 8, //MA
"VLI" -> 8,
"GZT" -> 8, //TR
"VXE" -> 8,
"MSY" -> 7, //New Orleans
"IGR" -> 7, //Puerto Iguazu
"BIO" -> 7, //Bilbao
"SZG" -> 7, //Berchtesgaden National Park Germany
"KLO" -> 7, //Boracay
"XIY" -> 7, //Xi'an
"LIF" -> 7, //Lifou
"LJU" -> 7, //Triglav National Park Slovenia
"GDT" -> 7, //Cockburn Town
"BZE" -> 7, //Chiquibul National Park Belize
"GOA" -> 7,
"FAI" -> 7, //Fairbanks
"RAR" -> 7, //Cook Islands
"IAG" -> 7, //Niagra
"CGY" -> 7, //Cagayan de Oro PH
"ASW" -> 7, //Abu Simbel Egypt
"KLX" -> 7,
"INV" -> 7,
"FDF" -> 6, //Fort-de-France
"GMZ" -> 6, //Canary Islands
"TLN" -> 6, //Toulon
"LUA" -> 6, //Everest
"MBA" -> 6, //Mombasa
"CGB" -> 6, //Cuiabá Ecotourism
"CYO" -> 6, //Cayo Largo del Sur Cuba
"BTS" -> 6, //Devin Castle Slovakia
"FPO" -> 6, //Bahamas
"LXR" -> 6, //Luxor
"PNT" -> 6, //Torres del Paine National Park Chile
"SJZ" -> 6, //Azores São Jorge
"WVB" -> 6,
"FAE" -> 6, //Faroe Islands
"VRN" -> 6, //Verano
"HAH" -> 6,
"NUM" -> 6, //SA
"ACA" -> 6, //Acapulco
"GGT" -> 6, //Bahamas
"PJM" -> 6, //Costa rica
"AXA" -> 6,
"BOC" -> 6, //Bocas del Toro
"SMR" -> 5, //Santa Marta
"VLC" -> 5, //Valencia
"BRC" -> 5, //San Carlos de Bariloche
"PXO" -> 5, //Peneda-Gerês National Park Portugal
"BON" -> 5, //Kralendijk Bonaire
"SVQ" -> 5, //Seville ES
"MCZ" -> 5,
"XIY" -> 5, //Terracotta Army China
"EYW" -> 5, //Key West
"AYQ" -> 5, //Ayers Rock
"IAO" -> 5,
"FTE" -> 5, //El Calafate
"MPL" -> 5,
"FAT" -> 5, //Yosemite National Park USA
"SLC" -> 5, //Salt Lake City
"GCN" -> 5, //Grand Canyon
"SXB" -> 5, //Strasbourg
"GHB" -> 5, //Governor's Harbour Bahamas
"TNJ" -> 5, //Bintan Island, ID
"TRV" -> 5, //Thiruvananthapuram
"PTF" -> 5, //Mamanuca Islands
"HOG" -> 5, //CU
"FPO" -> 5,
"HOR" -> 5, //Azores Horta
"SKB" -> 5,
"TAB" -> 5,
"OAX" -> 5, //Oaxaca
"HBE" -> 5, //Alexandria
"SZG" -> 5, //Salzburg
"BOB" -> 5, //Bora Bora French Polynesia
"JNX" -> 5, //GR
"MMY" -> 5, //Miyako JP
"SMI" -> 5, //GR
"TMR" -> 5, //Ahaggar National Park
"WDH" -> 5,
"YAS" -> 5, //Fiji
"YXY" -> 5, //Whitehorse
"EIS" -> 5, //BVI
"ZAG" -> 5,
"TPP" -> 5, //PE
"RJM" -> 5,
"AOK" -> 5,
"KOE" -> 5, //ID
"APW" -> 5, //Samoa
"GAY" -> 5, //Bodh Gaya
"BES" -> 5, //Britinay
"STM" -> 5, //Amazon
"DOM" -> 5, //Dominica
"GOH" -> 5, //Greenland
"LIO" -> 5,
"PRI" -> 5, //Seychelles
"PTF" -> 5,
"KVG" -> 5, //PG
"SPR" -> 5, //Belize
"RIH" -> 5, //PA
"KGC" -> 5, //Kangroo Island
"TMC" -> 5, //ID
"BIQ" -> 5, //Biarritz
"AVN" -> 5, //Avignon
"CCF" -> 5, //Carcassonne
"TRS" -> 5, //Trieste IT
"BYO" -> 5,
"BLJ" -> 5, //Timgad & Batna
"GBJ" -> 5, //Guadaloupe
"MQS" -> 5,
"AEY" -> 5, //Thingvellir National Park Iceland
"BHR" -> 5,
"DSS" -> 5,
"TGZ" -> 4, //Tuxtla Gutiérrez
"MRE" -> 4, //Maasai Mara National Reserve Kenya
"MFA" -> 4, //Mafia Island TZ
"ETM" -> 4, //Eliat IS
"MFU" -> 4, //Zambia
"SEU" -> 4,
"ZSA" -> 4,
"SAB" -> 4,
"CUK" -> 4, //Belize
"TOE" -> 4, //Tozeur
"LEU" -> 4, //Andorra
"MNF" -> 4, //Fiji
"PTF" -> 4, //Fiji
"YFB" -> 4, //Iqaluit
"SCT" -> 4, //Socotra Islands
"HLE" -> 4, //St Helena
"PLJ" -> 4, //BZ
"OTD" -> 3,
"HAL" -> 3,
"BBK" -> 3, //BW
"LBU" -> 3,
"CYB" -> 3, //West End
"MHH" -> 3, //Marsh Harbour Bahammas
"FSP" -> 3,
"RSI" -> 3, //SA
"WYS" -> 3, //Yellowstone NP
"JAV" -> 3, //Greenland
"EGS" -> 3, //Iceland
"LYR" -> 3,
"SBZ" -> 2, //Sibiu
"RTB" -> 2, //Roatan
"LRH" -> 2,
"HZK" -> 2, //IS
 ),
    FINANCIAL_HUB -> Map[String, Int](
"NRT" -> 70, //Tokyo
"PVG" -> 60, //Shanghai
"SIN" -> 60, //Singapore
"HND" -> 60, //Tokyo
"ICN" -> 55, //Seoul
"GVA" -> 54, //Geneva
"SZX" -> 53, //Shenzhen
"FRA" -> 52, //Frankfurt
"DXB" -> 50, //Dubai
"LAX" -> 50, //Los Angeles
"BOM" -> 49, //Mumbai
"ZRH" -> 49, //Zurich
"SCL" -> 47, //Santiago
"KUL" -> 46, //Kuala Lumpur
"DEL" -> 45, //New Delhi
"LHR" -> 44, //London
"CAN" -> 44, //Guangzhou
"JFK" -> 43, //New York
"JNB" -> 43, //Johannesburg
"GRU" -> 43, //Sao Paulo
"BOG" -> 42, //Bogota
"YYZ" -> 42, //Toronto
"SFO" -> 42, //San Francisco
"PHX" -> 41, //Phoenix
"TPE" -> 41, //Taipei
"SEA" -> 40, //Seattle
"AUH" -> 40, //Abu Dhabi
"AMS" -> 40, //Amsterdam
"MAD" -> 39, //Madrid
"YVR" -> 39, //Vancouver
"SVO" -> 39, //Moscow
"MNL" -> 39, //Manila
"DOH" -> 38, //Doha
"DFW" -> 38, //Dallas Fort Worth
"SGN" -> 38, //Ho Chi Minh City
"MUC" -> 37, //Munich
"MEX" -> 37, //Mexico City
"HKG" -> 36, //Hong Kong
"ORD" -> 36, //Chicago
"FUK" -> 35, //Fukuoka
"SYD" -> 35, //Sydney
"DEN" -> 35, //Denver
"CLT" -> 35, //Charlotte
"IAH" -> 35, //Houston
"CPH" -> 34, //Copenhagen
"AKL" -> 34, //Auckland
"BER" -> 34, //Berlin
"KIX" -> 33, //Osaka
"TLV" -> 33, //Tel Aviv
"YUL" -> 33, //Montreal
"PUS" -> 33, //Busan
"CDG" -> 33, //Paris
"EWR" -> 32, //New York
"ITM" -> 32, //Osaka
"YYC" -> 32, //Calgary
"JED" -> 32, //Jeddah
"DME" -> 31, //Moscow
"PEK" -> 31, //Beijing
"MIA" -> 31, //Miami
"KWI" -> 30, //Kuwait City
"CGK" -> 30, //Jakarta
"IST" -> 30, //Istanbul
"SLC" -> 30, //Salt Lake City
"TFU" -> 30, //Chengdu
"MEL" -> 29, //Melbourne
"GMP" -> 29, //Seoul
"BRU" -> 29, //Brussels
"PTY" -> 29, //Panama City
"VIE" -> 28, //Vienna
"MXP" -> 28, //Milan
"SHA" -> 27, //Shanghai
"LIM" -> 27, //Lima
"DUS" -> 27, //Dusseldorf
"LED" -> 27, //St Petersburg
"OSL" -> 26, //Oslo
"TAS" -> 26, //Tashkent
"BAH" -> 25, //Bahrain
"GIG" -> 25, //Rio de Janeiro
"DUB" -> 24, //Dublin
"LGW" -> 24, //London
"LGA" -> 24, //New York
"BCN" -> 23, //Barcelona
"DCA" -> 23, //Washington DC
"EZE" -> 22, //Buenos Aires
"LIN" -> 22, //Milan
"HAN" -> 22, //Hanoi
"KHH" -> 22, //Kaohsiung
"RUH" -> 21, //Riyadh
"LUX" -> 21, //Luxembourg
"LOS" -> 21, //Lagos
"CTU" -> 21, //Chengdu
"BLR" -> 21, //Bangalore
"MSP" -> 21, //Minneapolis
"ATL" -> 20, //Atlanta
"BKK" -> 20, //Bangkok
"ARN" -> 20, //Stockholm
"BUD" -> 20, //Budapest
"VKO" -> 20, //
"CGH" -> 20, //Sao Paulo
"NBO" -> 20, //Nairobi
"FCO" -> 19, //Rome
"PER" -> 19, //Perth
"PRG" -> 19, //Prague
"WAW" -> 19, //Warsaw
"SAN" -> 19, //San Diego
"BOS" -> 18, //Boston
"NGO" -> 18, //Nagoya
"BLQ" -> 18, //Bologna
"CGN" -> 18, //Cologne
"HEL" -> 17, //Helsinki
"KMG" -> 17, //
"TRN" -> 17, //Turin
"LCY" -> 17, //London
"IKA" -> 17, //Tehran
"BSB" -> 16, //Brasilia
"ALG" -> 16, //Algiers
"TLL" -> 16, //Tallinn
"YTZ" -> 16, //Toronto
"IAD" -> 16, //Washington DC
"HYD" -> 16, //Hyderabad
"DMM" -> 16, //
"MDW" -> 15, //Chicago
"LAS" -> 15, //
"DTW" -> 15, //Detroit
"RMO" -> 15, //Chisinau
"YQB" -> 15, //Quebec City
"YOW" -> 15, //Ottawa
"FLL" -> 15, //
"TPA" -> 15, //Tampa
"GYD" -> 15, //Baku
"DAL" -> 14, //Dallas
"PKX" -> 14, //Beijing
"VNO" -> 14, //Vilnius
"HAJ" -> 14, //Hanover
"MTY" -> 14, //Monterrey
"TAE" -> 14, //
"YEG" -> 14, //Edmonton
"CPT" -> 14, //Cape Town
"PDX" -> 14, //Portland
"GDL" -> 14, //
"ADD" -> 14, //Addis Ababa
"CMN" -> 13, //Casablanca
"ALA" -> 13, //Almaty
"RIX" -> 13, //Riga
"BWI" -> 13, //Baltimore
"BNE" -> 12, //Brisbane
"EDI" -> 12, //Edinburgh
"RTM" -> 12, //The Hague
"TAO" -> 12, //Qingdao
"BEG" -> 12, //Belgrade
"CEB" -> 12, //
"DVO" -> 12, //
"BNA" -> 12, //Nashville
"MLA" -> 12, //Malta
"SHJ" -> 12, //
"SJC" -> 11, //San Francisco
"KUN" -> 11, //Kaunas
"CCP" -> 11, //Concepcion
"AEP" -> 10, //Buenos Aires
"PHL" -> 10, //Philadelphia
"TSA" -> 10, //Taipei
"HAM" -> 10, //Hamburg
"ESB" -> 10, //Ankara
"DUR" -> 10, //Durban
"MCT" -> 10, //
"TLS" -> 9, //Toulouse
"BGO" -> 9, //Bergen
"AMD" -> 9, //GIFT City-Gujarat
"AUS" -> 9, //Austin
"MDE" -> 9, //Medellin
"WLG" -> 9, //Wellington
"XIY" -> 9, //Xi'an
"ORY" -> 8, //Paris
"ADL" -> 8, //Adelaide
"CBR" -> 8, //Canberra
"BDA" -> 8, //Bermuda
"BTS" -> 8, //Bratislava
"KHI" -> 8, //Karachi
"NLU" -> 8, //Mexico City
"POS" -> 8, //Port of Spain
"STR" -> 8, //Stuttgart
"YWG" -> 8, //Winnipeg
"SMF" -> 8, //Sacramento
"HOU" -> 8, //Houston
"ACC" -> 8, //
"THR" -> 8, //
"LIS" -> 7, //
"MAA" -> 7, //Chennai
"ANC" -> 7, //Anchorage
"TSN" -> 7, //Tianjin
"NQZ" -> 7, //Nur-Sultan
"PNQ" -> 7, //Pune
"PEN" -> 7, //
"KGL" -> 7, //Kigali
"LIN" -> 7, //Milan
"LEJ" -> 7, //Leipzig
"YQR" -> 7, //Regina
"AMM" -> 7, //
"MAN" -> 7, //Manchester
"DMK" -> 6, //Bangkok
"UPG" -> 6, //Makassar
"LYS" -> 6, //Grenoble
"NCL" -> 6, //Newcastle
"NAS" -> 6, //Nassau
"OAK" -> 6, //San Francisco
"SOF" -> 6, //Sofia
"GLA" -> 6, //Glasgow
"ABV" -> 6, //
"UIO" -> 6, //
"FRU" -> 6, //
"AAL" -> 5, //Aalborg
"GOT" -> 5, //Gothenburg
"ATH" -> 5, //Athens
"PIT" -> 5, //Pittsburgh
"AHB" -> 5, //
"CLO" -> 5, //Cali
"CZX" -> 5, //Changzhou
"MBA" -> 5, //Mombasa
"MVD" -> 5, //
"UKB" -> 5, //
"OVB" -> 5, //
"BOI" -> 5, //
"LAD" -> 5, //Luanda
"ABJ" -> 5, //
"STL" -> 4, //
"SDQ" -> 4, //
"CRL" -> 4, //Brussles
"NKG" -> 4, //Nanjing
"BGI" -> 4, //Bridgetown
"SNA" -> 4, //
"YXE" -> 4, //Saskatoon
"HAV" -> 4, //Havanna
"GYE" -> 4, //
"HLP" -> 4, //Jakarta
"CVG" -> 4, //
"LFW" -> 4, //
"NUE" -> 4, //
"BUR" -> 4, //
"ILO" -> 4, //
"DAR" -> 4, //
"DYU" -> 4, //
"BHX" -> 4, //Birmingham
"NSI" -> 4, //
"TRD" -> 3, //Trondheim
"KEF" -> 3, //Reykjavik
"IOM" -> 3, //Castletown
"JNU" -> 3, //Juneau
"MCI" -> 3, //
"SJJ" -> 3, //Sarajevo
"LCA" -> 3, //Nicosia
"RDU" -> 3, //
"CCU" -> 3, //
"JAX" -> 3, //
"JIB" -> 3, ////Djibouti
"SVX" -> 3, //
"ISB" -> 3, //
"RGN" -> 3, //
"ASB" -> 3, //
"EBL" -> 3, //
"EBB" -> 3, //
"DLA" -> 3, //
"KNO" -> 2, //
"SDJ" -> 2, //Sendai
"ABQ" -> 2, //
"CLE" -> 2, //
"EBL" -> 2, //Arbil
"MKE" -> 2, //
"YWG" -> 2, //
"PZU" -> 2, //Port Sudan
"CAY" -> 2, //
"AOJ" -> 2, //Aomori
"CMH" -> 2, //
"IND" -> 2, //
"SAT" -> 2, //
"BSL" -> 2, //
"KGD" -> 2, //
"DSS" -> 2, //
"MPM" -> 2, //
"CHC" -> 2, //
"HGH" -> 2, //Hangzhou
"MRV" -> 2, //
"CXH" -> 1, //Vancouver Heliport
"JRA" -> 1, //NYC Heliport
"JRB" -> 1, //NYC Heliport
"SKM" -> 1, //Sao Paulo, fictional IATA code
"HHP" -> 1, //Hong Kong
"SDB" -> 1, //Rio Heliport

),
DOMESTIC_AIRPORT -> getDomesticAirports(),
BUSH_HUB -> getBushHubs(),
GATEWAY_AIRPORT -> getGatewayAirports().map(iata => (iata, 0)).toMap) + (ELITE_CHARM -> getEliteDestinations()
)

  patchFeatures()

  def patchFeatures() = {
    val airportFeatures = scala.collection.mutable.Map[String, ListBuffer[AirportFeature]]()
    featureList.foreach {
      case (featureType, airportMap) =>
        airportMap.foreach {
          case (airportIata, featureStrength) =>
            val featuresForThisAirport = airportFeatures.getOrElseUpdate(airportIata, ListBuffer[AirportFeature]())
            featuresForThisAirport += AirportFeature(featureType, featureStrength)
        }
    }


    airportFeatures.toList.foreach {
        case (iata, features) =>
          AirportSource.loadAirportByIata(iata) match {
            case Some(airport) =>
              AirportSource.updateAirportFeatures(airport.id, features.toList)
            case None =>
              println(s">>> Cannot find airport with iata $iata to patch $features")
          }
      }
      IsolatedAirportPatcher.patchIsolatedAirports()
  }

  def getEliteDestinations() : Map[String, Int] = {
    val destinations = DestinationSource.loadAllDestinations()
    val iataMap = destinations.groupBy(_.airport.iata).view.mapValues(_.length).toMap
    println("inserting elite destinations to features...")
    println(iataMap)
    iataMap
  }

  def getBushHubs() : Map[String, Int] = {
    List(
      //CA
      "YFB",
      "YHU",
      "YPL",
      "YQT",
      "YRB",
      "YRT",
      "YAB",
      "YTZ",
      "YVP",
      "CXH",
      "YXE",
      "YXL",
      "YXY",
      "YZF",
      "YZV",
      //US
      "BET",
      "ANI",
      "BRW",
      "OME",
      "OTZ",
      "AKN",
      "DLG",
      "ADQ",
      "VDZ",
      "ANC",
      "FAI",
      "JNU",
      "FRD",
      "BFI",
      //AU
      "DRW",
      "BME",
      "ADL",
      "DBO",
      "PER",
      "CNS",
      "AVV",
      "ASP",
      "AYQ",
      "HID",
      //Pacific
      "SUV",
      "PPT",
      "BOB",
      "RGI",
      "POM",
      "HGU",
      "RAB",
      "PNI",
      "MAJ",
      "KWA",
      "VLI",
      "SON",
      "HIR",
      "MUA",
      "TRW",
      "TBU",
      "GEA",
      //ID
      "DJJ",
      "BPN",
      "KOE",
      "UPG",
      "KNO",
      //PH
      "CEB",
      //JP
      "KOJ",
      //CN
      "LXA",
      //KZ
      "NQZ",
      "UBN",
      //RU
      "GDX",
      "KJA",
      "YKS",
      "PKC",
      "IKT",
      "KHV",
      "UUS",
      //EU
      "KOI",
      "ABV",
      //Greenland
      "GOH",
      "UAK",
      "JAV",
      //LATAM
      "PAC",
      "SJO",
      "CBB",
      "DGA",
      //Caribbean
      "NAS",
      "ANU",
      "SXM",
      "POS",
    ).map(_ -> 0).toMap
  }

  def getDomesticAirports() : Map[String, Int] = {
    List(
      "LGA",
      "DCA",
      "MDW",
      "SNA",
      "BUR",
      "OAK",
      "DAL",
      "HOU",
      "AZA",
      "COS",
      "PAE",
      "PIE",
      "SFB",
      "USA",
      "PGD",
      "OGD",
      "LIH",
      "OGG",
      "CAK",
      "ORH",
      "SIG",
      //canada
      "YFB",
      "YHU",
      "YPL",
      "YRT",
      "YTZ",
      "YVP",
      "YXL",
      "YZV",
      //mexico
      "TLC",
      "CJS",
      //pa
      "PAC",
      "BLB",
      //EU
      "ORK",
      "EIN",
      "CRL",
      "ANR",
      "BVA",
      "FNI",
      "TLN",
      "HHN",
      "LBC",
      "FKB",
      "NRN",
      "BRE",
      "DTM",
      "FMM",
      "REU",
      "GRO",
      "LIN",
      "CIA",
      "TSF",
      "NYO",
      "BMA",
      "TRF",
      "WMI",
      "BBU",
      "CAT",
      "AGH",
      "ORK",
      //GB
      "BHD",
      "KOI",
      //iceland
      "RKV",
      //greenland
      "UAK",
      "JAV",
      //china
      "TSN",
      "WNZ",
      "SHA",
      "ZUH",
      "LHW",
      "HUZ",
      "FUO",
      "CTU",
      //japan
      "ITM",
      "UKB",
      "IBR",
      "OKD",
      "GAJ",
      //korea
      "GMP",
      "USN",
      //malaysia
      "MYY",
      "SZB",
      //argentina
      "AEP",
      //brazil
      "CGH",
      "SDU",
      //colombia
      "EOH",
      "FLA",
      //chile
      "LSC",
      //dominican-republic
      "JBQ",
      //iran
      "THR",
      "PGU",
      "ABD",
      "KIH",
      "AWZ",
      //india
      "HDO",
      "DHM",
      "BDQ",
      "PNY",
      "AIP",
      "STV",
      "KNU",
      "NAG",
      //russia
      "CEK",
      "KJA",
      "KEJ",
      "BTK",
      "YKS",
      "NSK",
      "UUS",
      "ZIA",
      //eastern africa
      "WIL",
      //southern africa
      "HLA",
      "ERS",
      //indonesia
      "HLP",
      "SOC",
      //Australia
      "AVV",
      "MCY",
      "LST"
    ).map(_ -> 0).toMap
  }


  def getGatewayAirports() : List[String] = {
    //The most powerful airport of every country
    val airportsByCountry = AirportSource.loadAllAirports().groupBy(_.countryCode).filter(_._2.length > 0)
    val topAirportByCountry = airportsByCountry.view.mapValues(_.sortBy(_.basePopMiddleIncome).last)

    val baseList = topAirportByCountry.values.map(_.iata).toList

    val list: mutable.ListBuffer[String] = collection.mutable.ListBuffer(baseList:_*)

    list -= "CGO" //China
    list -= "OSS" //Uzbekistan
    list += "SKD"
    list -= "LHE" //Pakistan
    list -= "OKZ"
    list += "VTE" //Laos
    list -= "PKZ"
    list += "ISB"
    list -= "GYE" //Ecuador
    list += "UIO"
    list -= "THR" //Iran
    list += "IKA"
    list -= "RUH" //Saudi
    list += "JED"
    list -= "OND" //Namibia
    list += "WDH"
    list -= "ZND" //Mali
    list += "NIM"
    list -= "BYK" //Ivory Coast
    list += "ABJ"
    list -= "DLA" //Cameroon
    list += "NSI"
    list -= "MQQ" //Chad
    list += "NDJ"
    list -= "BLZ" //Malawi
    list += "LLW"
    list -= "KGA" //DRC
    list -= "MJM"
    list += "FIH"
    list -= "KAN" //Nigeria
    list -= "APL" //Mozambique
    list += "MPM"
    list -= "MWZ" //Tanzania
    list += "DAR"
    list -= "HGU" //PNG
    list += "POM"
    list -= "STX" //US VI
    list += "STT"
    list -= "XSC" //
    list += "PLS"
    list += "NZF" //Pegasus Airfield, AQ (fictional IATA)
    list += "PPT"
    list += "NOU"
    list += "GOH" //Greenland
    list += "NAN" //Fiji
    list -= "SUV"
    list -= "AEP" //Argentina
    list += "EZE"
    list -= "SKB" //Remove minor Caribbean ones
    list -= "SVD"
    list -= "DCF"
    list -= "EUN"
    list -= "CKY"
    list -= "FNA"
    list -= "BJL"
    list -= "MSQ" //Remove some EU ones
    list -= "MCM"
    list -= "BTS"
    list -= "RMO"
    list -= "LJU"

    //add extra ones for bigger countries
    list.appendAll(List(
      "NZF", //McMurdo AQ
      "CAN", //China
      "PVG",
      "PEK",
      "JFK", //US
      "LAX",
      "SFO",
      "MIA",
      "ATL",
      "BOM", //India
      "RUH", //Saudi
      "AUH", //UAE
      "CPT", //South Africa
      "LOS", //Nigeria
      "ABV",
      "GIG", //Brazil
      "GRU",
      "MDE", //Colombia
      "NRT", //Japan
      "HND",
      "KIX",
      "SVO", //Russia
      "DME",
      "LED",
      "FCO", //Italy
      "MXP",
      "GOH", //Greenland / DK
      "LGW", //UK
      "RUN", //France Reunion
      "MAD", //Spain
      "BCN",
      "FRA", //Germany
      "MUC",
      "SYD", //Australia
      "MEL",
      "PER",
      "YVR", //Canada
      "YUL",
      "YYZ",
      "NLU" //Mexico
    ))
    list.toList
  }
}
