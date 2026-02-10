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
"JFK" -> 55, //New York
"HKT" -> 54, //Phuket
"USM" -> 52, //Na Thon (Ko Samui Island)
"UTP" -> 52, //Rayong
"CUN" -> 50, //Cancún
"KUL" -> 50, //Kuala Lumpur
"TFS" -> 50, //Tenerife Island
"FCO" -> 50, //Rome
"MEX" -> 50, //Mexico City
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
"CNX" -> 46, //Chiang Mai
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
"HKG" -> 41, //Hong Kong
"PMI" -> 40, //Palma De Mallorca
"TFN" -> 40, //Tenerife Island
"MBJ" -> 40, //Montego Bay
"ICN" -> 40, //Seoul
"TPE" -> 40,
"GRU" -> 39, //São Paulo
"LIS" -> 39, //Lisbon
"PUJ" -> 38, //Punta Cana
"RMF" -> 38, //Marsa Alam
"YVR" -> 38, //Vancouver
"NCE" -> 38, //Nice
"LGK" -> 38, //Langkawi
"HNL" -> 37, //Honolulu
"SSH" -> 37, //Sharm el-Sheikh
"NAN" -> 37, //Nadi
"GOI" -> 37, //Vasco da Gama
"DJE" -> 36, //Djerba
"CPT" -> 36, //Cape Town
"CMB" -> 36, //Colombo
"CZM" -> 36, //Cozumel
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
"VCE" -> 33, //Venice
"SPX" -> 33, //Cairo
"EWR" -> 33, //New York City USA
"ALC" -> 32, //Alicante
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
"RAK" -> 30, //Marrakech
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
"USH" -> 15, //Ushuahia
"PUQ" -> 15, //Punta Arenas
"FAO" -> 14, //Faro
"PVR" -> 14, //Puerto Vallarta
"PMO" -> 14, //Palermo
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
"YUL" -> 10, //Montreal
"PTP" -> 10, //Pointe-Ã -Pitre
"DMK" -> 10, //Bangkok
"GYD" -> 10, //Baku
"MFM" -> 10, //Macau
"SPU" -> 10, //Split
"SPC" -> 10,
"YHZ" -> 10, //Halifax
"HUX" -> 10, //Huatulco
"CGK" -> 10,
"RBA" -> 10, //Rabat
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
"BOB" -> 7, //Bora Bora French Polynesia
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
VACATION_HUB -> Map[String, Int](
"CJU" -> 265, //Jeju City
"PMI" -> 131, //Palma De Mallorca
"MCO" -> 118, //Orlando
"JED" -> 95, //Jeddah
"CTS" -> 90, //Chitose / Tomakomai
"HNL" -> 87, //Honolulu
"CUN" -> 80, //Cancún
"HND" -> 80, //Tokyo / Haneda
"LAS" -> 79, //Las Vegas
"AER" -> 78, //Sochi
"OGG" -> 70, //Kahului
"THR" -> 70, //Tehran
"DXB" -> 69, //Dubai
"BOG" -> 68, //Bogota
"SCL" -> 65, //Santiago
"OKA" -> 65, //Naha
"BAH" -> 61, //Manama
"AGP" -> 60, //Málaga
"POA" -> 60, //Porto Alegre
"ZQN" -> 60, //Queenstown
"LPA" -> 57, //Gran Canaria Island
"CTA" -> 57, //Catania
"BKI" -> 55, //Kota Kinabalu
"MLA" -> 55, //Valletta
"ITM" -> 54, //Osaka Japan
"REC" -> 52, //Recife
"SYD" -> 50, //Sydney Australia
"SJD" -> 50, //San José del Cabo
"PKX" -> 50, //Beijing China
"GRU" -> 49, //São Paulo
"MHD" -> 49, //Mashhad
"RUH" -> 48,
"KOS" -> 48, //Sihanukville
"PMV" -> 48, //Isla Margarita
"GRO" -> 48, //Girona
"MDE" -> 47,
"SAW" -> 46, //Istanbul
"BOM" -> 45, //Mumbai
"DEL" -> 45,
"FAO" -> 45, //Faro
"TRD" -> 45, //Trondheim
"OLB" -> 45, //Olbia (SS)
"GMP" -> 45, //Seoul
"BCN" -> 44, //Barcelona
"PVR" -> 44, //Puerto Vallarta
"ECN" -> 44, //TR Cyprus
"KUL" -> 42, //Kuala Lumpur
"CAG" -> 42, //Cagliari
"CGH" -> 41, //São Paulo
"FLN" -> 41, //Florianópolis
"TFS" -> 40, //Tenerife Island
"SHJ" -> 40, //Sharjah AE
"SYX" -> 40, //Sanya
"YUL" -> 40, //Montreal
"MAD" -> 39, //Madrid
"BTH" -> 39, //Batam Island
"AEP" -> 39, //Buenos Aires
"ALC" -> 38, //Alicante
"CTG" -> 38, //Cartagena
"CIA" -> 38, //Ostia Antica Italy
"MNL" -> 38,
"RSW" -> 38, //Fort Myers
"MID" -> 38, //Mérida
"MSY" -> 37, //New Orleans
"AGA" -> 36, //Agadir
"PMO" -> 36, //Palermo
"SMR" -> 36, //Santa Marta
"YIA" -> 36, //Yogyakarta
"LLA" -> 36, //LuleÃ¥
"MED" -> 35, //Medina
"MEL" -> 35, //Melbourne
"DBV" -> 35, //Dubrovnik
"PTP" -> 35, //Pointe-Ã -Pitre
"VLC" -> 35, //Valencia
"BNA" -> 35, //Nashville
"HBA" -> 35, //Hobart
"DCA" -> 35, //Washington
"SVO" -> 34, //Moscow
"VAR" -> 34, //Varna
"PMC" -> 34, //Puerto Montt
"GDN" -> 34, //Gdańsk
"CHC" -> 34, //Christchurch
"HAK" -> 34, //Haikou
"LVI" -> 33, //Livingstone
"KIH" -> 33, //Kish Island IR
"TFN" -> 32, //Tenerife Island
"LIS" -> 32, //Lisbon
"FNC" -> 32, //Funchal
"TOS" -> 32, //Tromsø
"IGU" -> 32, //Foz Do IguaÃ§u
"FUK" -> 32, //Fukuoka
"OKD" -> 32, //Sapporo
"LIH" -> 32, //Lihue
"BNE" -> 31, //Brisbane
"SHA" -> 31, //Shanghai China
"CNF" -> 31, //Belo Horizonte
"CWB" -> 31, //Curitiba
"PUJ" -> 30, //Punta Cana
"RAK" -> 30, //Marrakech
"ADB" -> 30, //Izmir
"BER" -> 30, //Berlin
"MPH" -> 30, //Malay
"DUB" -> 30, //Dublin Ireland
"PSA" -> 30, //Pisa
"RUN" -> 30, //St Denis
"DMK" -> 30, //Bangkok
"CNS" -> 30, //Cairns
"BRI" -> 30, //Bari
"FDF" -> 30, //Fort-de-France
"ADZ" -> 30, //San Andrés
"NSN" -> 30, //NZ
"LIN" -> 30, //Milan Italian Alps
"STI" -> 30, //Santiago
"MBJ" -> 29, //Montego Bay
"IGR" -> 29, //Puerto Iguazu
"LYS" -> 29, //Lyon
"ORY" -> 29, //Paris
"ALG" -> 29, //Algiers
"VIX" -> 29, //Vitória
"GIG" -> 28, //Rio De Janeiro
"LED" -> 28, //St. Petersburg
"COV" -> 28, //Mersin TR
"GYD" -> 28, //Baku
"DEN" -> 28,
"TSV" -> 28, //Townsville
"KOA" -> 28, //Kailua-Kona
"RVN" -> 28, //Rovaniemi
"FOR" -> 28, //Fortaleza
"BAQ" -> 28, //Barranquilla
"CEB" -> 27, //Lapu-Lapu City
"SVG" -> 27, //Stavanger
"CTM" -> 27, //Chetumal MX
"LIM" -> 26,
"VKO" -> 26, //Moscow
"SKD" -> 26, //Samarkand
"BRC" -> 26, //San Carlos de Bariloche
"KZN" -> 26, //Kazan
"IBZ" -> 25, //Ibiza
"FUE" -> 25, //Fuerteventura Island
"DME" -> 25, //Moscow
"CFU" -> 25, //Kerkyra Island
"VFA" -> 25, //Victoria Falls
"SDU" -> 25, //Rio De Janeiro
"FLL" -> 25, //Miami
"SXR" -> 25, //Srinagar
"HIJ" -> 25, //Hiroshima
"RMF" -> 24, //Marsa Alam
"SJU" -> 24, //San Juan
"NAS" -> 24, //Nassau
"KRR" -> 24, //Krasnodar
"BPS" -> 24, //Porto Seguro
"AJA" -> 24, //Ajaccio/NapolÃ©on Bonaparte
"KCH" -> 24, //MY
"KTT" -> 24, //Kittilä FI
"CJC" -> 23, //Calama
"FLR" -> 23, //Firenze
"LOP" -> 23, //Mataram
"SDQ" -> 23, //Santo Domingo
"ADL" -> 23, //Adelaide, AU
"PEN" -> 22, //Penang
"HAN" -> 22, //Hanoi
"SXM" -> 22, //Saint Martin
"MFM" -> 22, //Macau
"PXO" -> 22, //Peneda-Gerês National Park Portugal
"HAM" -> 22, //Hamburg
"NVT" -> 22, //Navegantes
"IKT" -> 22, //Irkutsk
"CXB" -> 22,
"BLQ" -> 22, //Bologna
"JHG" -> 22, //Xishuangbanna
"KOJ" -> 22, //JP
"SNN" -> 22,
"SSA" -> 21, //Salvador
"TFU" -> 21, //Chengdu
"KWL" -> 21, //Guilin City
"BAR" -> 21, //Qionghai
"TPA" -> 21, //Tampa
"SIP" -> 21, //Simferopol
"BUF" -> 21, //Buffalo
"HTI" -> 21, //Hamilton Island Resort
"ENO" -> 21, //Encarnación
"PLS" -> 21, //Providenciales Turks and Caicos
"SFB" -> 21, //Orlando
"RMU" -> 21,
"CXR" -> 20, //Nha Trang
"YVR" -> 20, //Vancouver
"DJE" -> 20, //Djerba
"ACE" -> 20, //Lanzarote Island
"SAI" -> 20, //Siem Reap
"AUA" -> 20, //Oranjestad
"CUR" -> 20, //Willemstad
"KRK" -> 20, //Kraków
"LIR" -> 20, //Liberia Costa Rica
"STT" -> 20, //Charlotte Amalie
"MRS" -> 20, //Marseille
"SPU" -> 20, //Split
"UVF" -> 20, //Vieux Fort
"BGO" -> 20, //Bergen
"KMQ" -> 20, //Kumamoto
"KNH" -> 20, //Kinmen
"VCP" -> 20, //Campinas
"ZIA" -> 20, //Moscow
"YOW" -> 20, //Ottawa
"YTZ" -> 20,
"LCA" -> 19, //Larnarca
"MAH" -> 19, //Menorca Island
"GOX" -> 19, //Goa IN
"AMM" -> 19, //Amman
"BIO" -> 19, //Bilbao
"NQN" -> 19, //Neuquen
"RNO" -> 19, //Reno
"BWI" -> 19, //Washington
"BIA" -> 19, //Bastia-Poretta
"ITO" -> 19, //Hilo
"PBI" -> 19,
"TBZ" -> 19, //Iran
"LEI" -> 19, //ES
"SPC" -> 18,
"YHZ" -> 18, //Halifax
"NBO" -> 18, //Nairobi
"ANU" -> 18, //St. John's
"BON" -> 18, //Kralendijk Bonaire
"YYC" -> 18, //Calgary
"BOO" -> 18, //Nordland NO
"OOL" -> 17, //Gold Coast
"YYT" -> 17, //St John
"SVQ" -> 17, //Seville ES
"ORN" -> 17, //Oran
"RVN" -> 17, //Rovaniemi FI
"CGB" -> 17, //Cuiabá
"BDS" -> 17, //Brindisi
"CDT" -> 17, //ES
"CTU" -> 17, //Chengdu
"PNQ" -> 17, //Pune
"JMK" -> 16, //Mykonos Island
"QSR" -> 16, //Amalfi coast
"HUX" -> 16, //Huatulco
"JAI" -> 16, //Jaipur
"KRS" -> 16, //NO
"GCM" -> 16, //Georgetown
"PPP" -> 16, //Whitsunday Coast Airport
"RKT" -> 16,
"SRQ" -> 16, //Sarasota/Bradenton
"RMI" -> 16,
"BJV" -> 15, //Bodrum
"PDL" -> 15, //Azores
"TIV" -> 15, //Tivat
"CGK" -> 15,
"LGA" -> 15, //New York
"BOS" -> 15,
"IOS" -> 15, //Ilhéus
"ISG" -> 15, //Ishigaki JP
"SNA" -> 15, //Santa Ana
"XMN" -> 15, //Xiamen
"YQY" -> 15,
"KGS" -> 14, //Kos Island
"PER" -> 14, //Perth
"OSL" -> 14, //Oslo
"MCZ" -> 14,
"BSB" -> 14, //Brasília
"INN" -> 14, //Innsbruck
"XCH" -> 14,
"MDQ" -> 14,
"BME" -> 14, //Broome
"VBY" -> 14, //Visby, SE
"PGD" -> 14, //FL
"MAA" -> 14, //Chennai
"EFL" -> 13, //Kefallinia Island
"SCQ" -> 13, //Santiago de Compostela ES
"DLM" -> 12, //Dalaman
"CHQ" -> 12, //Heraklion
"LPQ" -> 12, //Luang Phabang
"COK" -> 12, //Kochi
"BVC" -> 12, //Rabil
"ZTH" -> 12, //Zakynthos Island
"XIY" -> 12, //Terracotta Army China
"TGZ" -> 12, //Tuxtla Gutiérrez
"PHL" -> 12,
"ORD" -> 12, //Chicago
"EVE" -> 12, //NO
"VNS" -> 12, //Varanasi
"CAT" -> 12, //Lisbon
"MZG" -> 12, //TW
"SDJ" -> 12, //Sendai JP
"JHG" -> 12, //Xishuangbanna CN
"ACA" -> 12, //Acapulco
"PIE" -> 12, //FL
"DAB" -> 12, //Daytona Beach
"TLU" -> 12,
"SKG" -> 12,
"NLK" -> 12,
"EYW" -> 11, //Key West
"ASP" -> 11, //Alice Springs
"KNO" -> 11, //North Sumatra
"JER" -> 11, //Guernsey
"AQJ" -> 11, //Aqaba
"LMP" -> 11, //Italy
"OVD" -> 11, //Avilés ES
"BZN" -> 11, //Bozeman
"ALH" -> 11, //AU
"HRG" -> 10, //Hurghada
"SSH" -> 10, //Sharm el-Sheikh
"CPT" -> 10, //Cape Town
"LAP" -> 10, //La Paz
"SID" -> 10, //Espargos
"AKL" -> 10, //Auckland
"PLZ" -> 10, //Addo Elephant National Park South Africa
"MUB" -> 10, //Maun
"STX" -> 10, //Christiansted
"AYQ" -> 10, //Ayers Rock
"CCK" -> 10,
"OSD" -> 10, //Åre SE
"IXB" -> 10, //Bagdogra Darjeeling
"AGX" -> 10, //Agatti
"FSC" -> 10, //Figari Sud-Corse
"GRQ" -> 10, //Grenoble French Alps
"ISG" -> 10, //Ishigaki
"CLY" -> 10, //Calvi-Sainte-Catherine
"WRO" -> 10, //Wroclaw PL
"XRY" -> 10,
"FCA" -> 10, //Glacier National Park
"JAC" -> 10, //Jackson
"NAP" -> 9, //Nápoli
"IAO" -> 9,
"FTE" -> 9, //El Calafate
"GYN" -> 9, //Goiânia
"IXZ" -> 9, //Port Blair
"SAN" -> 9, //San Diego USA
"YYJ" -> 9,
"ZAD" -> 9, //Zemunik (Zadar)
"AQP" -> 9, //Peru
"REG" -> 9,
"GCI" -> 9, //Jersey
"AES" -> 9, //NO
"LTO" -> 9, //Loreto MX
"PNL" -> 9, //Italy
"ASE" -> 9, //Aspen
"EGE" -> 9, //Vail/Beaver Creek Colorado USA
"LDH" -> 9,
"SZG" -> 8, //Salzburg Austrian Alps
"SZG" -> 8, //Berchtesgaden National Park Germany
"MPL" -> 8,
"SEA" -> 8,
"KTN" -> 8, //Ketchikan
"VOG" -> 8, //Volgograd
"MYR" -> 8, //Myrtle Beach
"YLW" -> 8, //Jasper National Park Canada
"BTV" -> 8, //Burlington Stowe/Sugarbush Vermont USA
"SLZ" -> 8, //São Luís
"YXC" -> 8, //Banff National Park Canada
"BZR" -> 8, //FR
"NAT" -> 8, //Natal
"EVE" -> 8, //NO
"CUU" -> 8,
"NQU" -> 8,
"SVX" -> 8, //RU
"LCG" -> 8, //Coruña ES
"YDF" -> 8, //Gros Morne National Park Canada
"SAF" -> 8, //Santa Fe
"COS" -> 8, //Colorado Springs
"LZN" -> 8, //TW islands
"AMD" -> 8, //Ahmedabad
"CCU" -> 8,
"GMZ" -> 7, //Canary Islands
"NGO" -> 7, //Tokoname
"IXC" -> 7, //Chandigarh
"ATQ" -> 7, //Amritsar
"FEN" -> 7, //Fernando De Noronha
"FSZ" -> 7, //Fuji-Hakone-Izu National Park Japan
"MUH" -> 7, //El Alamein EG
"GPT" -> 7, //Gulf port
"TPQ" -> 7, //Tepic MX
"PVA" -> 7,
"KMJ" -> 7, //JP
"MTJ" -> 7, //Montrose (Ski resort)
"VQS" -> 7, //Vieques PR
"AVL" -> 7,
"SUN" -> 7, //Hailey Sun Valley Idaho USA
"YYB" -> 7, //North Bay
"MSO" -> 7, //Missoula
"YQQ" -> 7, //Vancouver Island
"CND" -> 6, //Constanța RO
"BHE" -> 6,
"LKO" -> 6, //Lucknow
"NKG" -> 6, //Nanjing
"DYG" -> 6,
"TSN" -> 6, //Tianjin
"VER" -> 6, //Pico de Orizaba National Park Mexico
"THE" -> 6, //Teresina
"ECP" -> 6, //Panama City Beach
"YKS" -> 6, //Serbia
"TSN" -> 6, //Tainan TW
"STS" -> 6,
"NTQ" -> 6, //Wajima JP
"IXU" -> 6, //Ellora caves
"NTE" -> 6, //Nantes FR
"GRZ" -> 6, //Graz AT
"CGR" -> 6,
"SCR" -> 6,
"IDA" -> 6,
"MFR" -> 6,
"TVC" -> 6, //Traverse City
"CPX" -> 6, //Culebra PR
"GTF" -> 6,
"TAT" -> 6, //Poprad-Tatry
"FAT" -> 5, //Yosemite National Park USA
"SBZ" -> 5, //Sibiu
"EPR" -> 5, //AU
"DED" -> 5, //Rishikesh and Uttarakhand
"DLC" -> 5, //Dalian
"SHE" -> 5, //Shenyang
"CLQ" -> 5, //Nevado de Colima National Park Mexico
"IPC" -> 5, //Isla De Pascua
"SUV" -> 5,
"YTY" -> 5, //Yangzhou
"PKU" -> 5, //Pekanbaru ID
"PLM" -> 5, //Palembang ID
"UST" -> 5, //St Augustine FL
"PSR" -> 5, //Pescara IT
"SCQ" -> 5,
"WKA" -> 5,
"DUD" -> 5, //NZ
"WRE" -> 5, //NZ
"BHE" -> 5, //NZ
"KAT" -> 5, //NZ
"KKE" -> 5, //NZ
"ISC" -> 5, //Isles of Scilly GB
"ACV" -> 5, //Eureka
"LSI" -> 5, //Shetland
"DLU" -> 5, //Dali CN
"PQQ" -> 5,
"MLB" -> 5, //FL
"CHS" -> 5,
"VRB" -> 5, //FL
"RAP" -> 5, //South Dakota NPs
"JTR" -> 4, //Santorini Island
"TLN" -> 4, //Toulon
"FOC" -> 4, //Fuzhou
"IOM" -> 4, //Isle of Man
"UNA" -> 4, //Transamérica Resort Comandatuba Island
"CSX" -> 4, //Changsha
"HRB" -> 4, //Harbin
"CMF" -> 4, //Chambéry
"DBB" -> 4, //EG
"HHH" -> 4, //Hilton Head Island
"KUM" -> 4,
"SLK" -> 4,
"ROT" -> 4, //NZ
"TUO" -> 4, //NZ
"PGK" -> 4, //Bangka Belitung Islands ID
"LDE" -> 4, //Lourdes
"AOI" -> 4, //Ancona IT
"JJD" -> 4, //Jericoacoara BR
"KLV" -> 4, //Karlovy CZ
"CPE" -> 4,
"PXM" -> 4, //Puerto Escondido MX
"KMI" -> 4, //JP
"SGU" -> 4, //Zion National Park
"CNY" -> 4, //Arches National Park USA
"HDN" -> 4, //Hayden Steamboat Springs Colorado USA
"HAC" -> 4,
"HGL" -> 4,
"VPS" -> 4, //Gulf coast
"OIM" -> 4, //JP
"GJT" -> 4,
"ANX" -> 4,
"YRB" -> 4,
"SHL" -> 4, //IN rainforest
"ZUH" -> 3, //Zhuhai
"BEJ" -> 3,
"BDO" -> 3, //Bandung ID
"EBA" -> 3,
"CRV" -> 3, //Crotone IT
"SRL" -> 3, //Mulegé MX
"ACK" -> 3, //Nantucket
"BRW" -> 3,
"BHB" -> 3, //Acadia NP
"YQA" -> 3, //Muskoka CA
"ACY" -> 3, //Atlantic City
"CFB" -> 3, //BR
"GUZ" -> 3, //BR
"MMH" -> 3, //Yellowstone
"HYA" -> 2, //Cape Cod
"MFR" -> 2, //Crater lake
"OTH" -> 2, //North Bend
"GRB" -> 2, //Door County WI
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
"JFK" -> 48, //New York
"SCL" -> 47, //Santiago
"KUL" -> 46, //Kuala Lumpur
"DEL" -> 45, //New Delhi
"HKG" -> 45, //Hong Kong
"LHR" -> 44, //London
"CAN" -> 44, //Guangzhou
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
"ORD" -> 39, //Chicago
"DOH" -> 38, //Doha
"DFW" -> 38, //Dallas Fort Worth
"SGN" -> 38, //Ho Chi Minh City
"MUC" -> 37, //Munich
"MEX" -> 37, //Mexico City
"FUK" -> 35, //Fukuoka
"SYD" -> 35, //Sydney
"DEN" -> 35, //Denver
"CLT" -> 35, //Charlotte
"IAH" -> 35, //Houston
"EWR" -> 35, //New York
"PEK" -> 35, //Beijing
"CPH" -> 34, //Copenhagen
"AKL" -> 34, //Auckland
"BER" -> 34, //Berlin
"KIX" -> 33, //Osaka
"TLV" -> 33, //Tel Aviv
"YUL" -> 33, //Montreal
"PUS" -> 33, //Busan
"ITM" -> 32, //Osaka
"YYC" -> 32, //Calgary
"JED" -> 32, //Jeddah
"CDG" -> 31, //Paris
"DME" -> 31, //Moscow
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
"ATL" -> 29, //Atlanta
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
"CMN" -> 7, //Casablanca
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
"TLS" -> 4, //Toulouse
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
"LYS" -> 3, //Grenoble
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
"BPN" -> 2, //
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
    list -= "KRT"
    list -= "OND" //Namibia
    list += "WDH"
    list -= "LAD" //Angola
    list += "NBJ"
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
