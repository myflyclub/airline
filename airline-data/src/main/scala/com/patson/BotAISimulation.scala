package com.patson

import com.patson.data._
import com.patson.data.airplane.ModelSource
import com.patson.model._
import com.patson.model.airplane._
import com.patson.util.{AirlineCache, AirportCache}

import scala.collection.mutable.ListBuffer
import scala.collection.mutable
import scala.util.Random

/**
 * Bot AI Simulation - Makes bot airlines feel alive by giving them intelligent decision-making
 * 
 * PHASE 3: Demand-based route selection, dynamic pricing, and realistic behaviors
 * 
 * Key Features:
 * - Routes are selected based on ACTUAL DEMAND between airports
 * - Prices are set dynamically based on competition and demand
 * - Prices adjust over time as market conditions change
 * - Unprofitable routes are abandoned
 * - Bots monitor and respond to competitor actions
 */
object BotAISimulation {
  
  // Activity probabilities - balanced for steady growth without bankruptcy
  val ROUTE_PLANNING_PROBABILITY = 0.25 // 25% chance per cycle to plan new routes (increased to get routes established)
  val AIRPLANE_PURCHASE_PROBABILITY = 0.05 // 5% chance per cycle to buy planes
  val ROUTE_OPTIMIZATION_PROBABILITY = 0.40 // 40% chance to optimize existing routes (pricing)
  val COMPETITION_RESPONSE_PROBABILITY = 0.30 // 30% chance to respond to competition
  val ROUTE_ABANDONMENT_PROBABILITY = 0.60 // 60% chance to evaluate route abandonment
  
  val MAX_ROUTES_PER_CYCLE = 2 // Maximum new routes per cycle per bot
  val MAX_AIRCRAFT_PURCHASE = 1 // Maximum aircraft purchases per cycle per bot (safer)
  
  // Purchase pacing and leasing
  val BOT_PURCHASE_RESERVE = 5000000L // Minimum reserve to keep after purchases
  val MAX_PURCHASE_FRACTION = 0.10 // Max fraction of balance allowed for single purchase
  val PURCHASE_PROJECTION_WINDOW = 8 // cycles to project future balance when evaluating purchase
  
  // Pricing strategy constants
  val MIN_PRICE_MULTIPLIER = 0.50 // Never go below 50% of standard price
  val MAX_PRICE_MULTIPLIER = 1.80 // Never go above 180% of standard price
  val PRICE_ADJUSTMENT_STEP = 0.05 // 5% price adjustment per cycle
  
  // Route profitability thresholds
  val UNPROFITABLE_CYCLES_THRESHOLD = 2 // Abandon route after 2 unprofitable cycles (was 4)
  val MIN_LOAD_FACTOR_THRESHOLD = 0.40 // Minimum acceptable load factor (was 0.30)
  val SEVERE_LOSS_THRESHOLD = -50000 // Immediate action if single-cycle loss exceeds this
  val CRITICAL_LOAD_FACTOR = 0.20 // Immediate frequency reduction/abandonment below this

  // === LAYER 1: FINANCIAL HEALTH THRESHOLDS ===
  val CRITICAL_BALANCE_THRESHOLD = -2000000L   // -$2M triggers critical mode
  val EMERGENCY_BALANCE_THRESHOLD = -5000000L  // -$5M triggers emergency mode
  val WARNING_BALANCE_THRESHOLD = 2000000L     // $2M triggers conservative mode
  val SAFE_BALANCE_THRESHOLD = 10000000L       // $10M safe for expansion

  // === LAYER 2: PLAYER COMPETITION TRACKING ===
  val PLAYER_COMPETITION_STALE_CYCLES = 4      // Clean up tracker after 4 cycles without seeing player

  // Financial state for emergency protocol
  case class FinancialState(
    balance: Long,
    weeklyProfit: Long,
    projectedBalanceIn4Weeks: Long,
    emergencyLevel: String // "SAFE", "WARNING", "CRITICAL", "EMERGENCY"
  )

  // Route health status for condition-triggered optimization
  case class RouteHealthStatus(
    link: Link,
    loadFactor: Double,
    profit: Long,
    profitTrend: Double,
    competitorCount: Int,
    hasPlayerCompetitor: Boolean,
    cyclesSinceProfit: Int,
    recommendation: String // "OPTIMIZE", "EXPAND", "REDUCE", "ABANDON", "HOLD"
  )
  
  def simulate(cycle: Int): Unit = {
    println("============================================")
    println("Starting Bot AI Simulation - Phase 4")
    println("Layered Brain: Survival → Player Awareness → Route Health → Strategy")
    println("============================================")
    
    val botAirlines = AirlineSource.loadAllAirlines(fullLoad = true)
      .filter(_.airlineType == AirlineType.NON_PLAYER)
    
    if (botAirlines.isEmpty) {
      println("No bot airlines found, skipping simulation")
      return
    }
    
    println(s"Processing ${botAirlines.size} bot airlines")
    
    // Pre-load market data for efficiency
    val allAirports = AirportSource.loadAllAirports(fullLoad = true)
    val countryRelationships = CountrySource.getCountryMutualRelationships()
    
    botAirlines.foreach { airline =>
      try {
        var personality = determineBotPersonality(airline)
        println(s"\n[${airline.name}] Personality: $personality | Balance: $$${airline.getBalance()/1000000}M")

        // ================================================================
        // LAYER 1: SURVIVAL - Check financial health FIRST
        // ================================================================
        val emergencyAction = evaluateEmergencyConditions(airline, personality, cycle)

        if (emergencyAction) {
          // Emergency/Critical mode - skip normal operations
          println(s"[${airline.name}] Emergency mode active - skipping normal operations")
        } else {
          // ================================================================
          // LAYER 2: PLAYER AWARENESS - Always detect and respond to players
          // ================================================================
          detectAndRespondToPlayerEntries(airline, personality, cycle)

          // ================================================================
          // LAYER 3: ROUTE HEALTH - Condition-triggered optimization
          // ================================================================
          val routesNeedingAttention = identifyRoutesNeedingAttention(airline, cycle)
          if (routesNeedingAttention.nonEmpty) {
            println(s"[${airline.name}] Found ${routesNeedingAttention.size} routes needing attention")
            optimizeSpecificRoutes(airline, routesNeedingAttention, personality, cycle)
          }

          // ================================================================
          // LAYER 4: STRATEGIC PLANNING - Probability-gated expansion
          // ================================================================

          // Check if safe for expansion
          val safeForExpansion = airline.getBalance() > SAFE_BALANCE_THRESHOLD
          val isPaused = airline.getBalance() < WARNING_BALANCE_THRESHOLD

          if (!isPaused && safeForExpansion) {
            // Route planning - add new routes based on DEMAND (20% chance)
            if (Random.nextDouble() < 0.20) {
              planNewRoutes(airline, personality, cycle, allAirports, countryRelationships)
            }

            // Fleet management - buy new airplanes (5% chance)
            if (Random.nextDouble() < AIRPLANE_PURCHASE_PROBABILITY) {
              purchaseAirplanes(airline, personality, cycle)
            }
          } else if (!isPaused) {
            // Below safe threshold but not paused - reduced expansion probability
            if (Random.nextDouble() < 0.10) {
              planNewRoutes(airline, personality, cycle, allAirports, countryRelationships)
            }
          }

          // General route optimization (15% chance - most handled by Layer 3)
          if (Random.nextDouble() < 0.15) {
            optimizeExistingRoutes(airline, personality, cycle)
          }

          // Legacy competition response (10% chance - most handled by Layer 2)
          if (Random.nextDouble() < 0.10) {
            respondToCompetition(airline, personality, cycle)
          }

          // Route abandonment - ALWAYS evaluate (no probability gating)
          evaluateRouteAbandonment(airline, personality, cycle)
        }

      } catch {
        case e: Exception =>
          println(s"Error processing bot airline ${airline.name}: ${e.getMessage}")
          e.printStackTrace()
      }
    }
    
    println("\n============================================")
    println("Finished Bot AI Simulation")
    println("============================================\n")
  }
  
  /**
   * Determine bot personality based on airline characteristics
   */
  private def determineBotPersonality(airline: Airline): BotPersonality = {
    val cash = airline.getBalance()
    val reputation = airline.getReputation()
    val serviceQuality = airline.getCurrentServiceQuality()
    
    // Use airline type as base personality
    airline.airlineType match {
      case AirlineType.DISCOUNT => BotPersonality.BUDGET
      case AirlineType.LUXURY => BotPersonality.PREMIUM
      case _ =>
        // Determine by characteristics
        if (cash > 1000000000) { // > $1B
          if (serviceQuality > 40) BotPersonality.PREMIUM
          else BotPersonality.AGGRESSIVE
        } else if (cash > 100000000) { // > $100M
          BotPersonality.BALANCED
        } else if (reputation > 60) {
          BotPersonality.CONSERVATIVE
        } else {
          BotPersonality.REGIONAL
        }
    }
  }
  
  /**
   * Plan new routes based on bot personality - PHASE 3: DEMAND-BASED route selection!
   */
  private def planNewRoutes(
    airline: Airline, 
    personality: BotPersonality, 
    cycle: Int,
    allAirports: List[Airport],
    countryRelationships: Map[(String, String), Int]
  ): Unit = {
    println(s"[${airline.name}] Planning new routes based on DEMAND (${personality})")
    
    val bases = AirlineSource.loadAirlineBasesByAirline(airline.id)
    if (bases.isEmpty) {
      println(s"[${airline.name}] No bases found, cannot plan routes")
      return
    }
    
    val existingLinks = LinkSource.loadFlightLinksByAirlineId(airline.id)
    val existingDestinations = existingLinks.flatMap(link => List(link.from.id, link.to.id)).toSet
    
    // Get available cash for route expansion
    // Keep a conservative reserve to avoid bankrupting bots. Only use a small fraction for expansion.
    val reserve = Math.max(5000000L, (airline.getBalance() * 0.10).toLong) // keep at least $5,000,000 or 10% as reserve
    val availableCash = Math.max(0L, airline.getBalance() - reserve)
    if (availableCash < 5000000) {
      println(s"[${airline.name}] Conservatively skipping expansion to preserve reserve (reserve=$$${reserve}, available=$$${availableCash})")
      LogSource.insertLogs(List(Log(airline, s"Skipped expansion: insufficient available cash (reserve=$$${reserve}, available=$$${availableCash})", LogCategory.NEGOTIATION, LogSeverity.INFO, cycle, Map("reserve" -> reserve.toString, "available" -> availableCash.toString))))
      return
    }
    
    // Get available airplanes
    val allAirplanes = AirplaneSource.loadAirplanesByOwner(airline.id)
    println(s"[${airline.name}] Total airplanes: ${allAirplanes.size}")
    val assignedAirplanes = LinkSource.loadFlightLinksByAirlineId(airline.id)
      .flatMap(_.getAssignedAirplanes().keys)
      .toSet
    val availableAirplanes = allAirplanes.filter(a => 
      !assignedAirplanes.contains(a) && a.isReady
    )
    println(s"[${airline.name}] Available airplanes: ${availableAirplanes.size} (assigned: ${assignedAirplanes.size}, not ready: ${allAirplanes.count(!_.isReady)})")
    
    if (availableAirplanes.isEmpty) {
      println(s"[${airline.name}] No available aircraft for new routes")
      LogSource.insertLogs(List(Log(airline, s"Skipped expansion: no available aircraft", LogCategory.NEGOTIATION, LogSeverity.INFO, cycle, Map("availableAirplanes" -> "0"))))
      return
    }
    
    var routesCreated = 0
    
    bases.foreach { base =>
      println(s"[${airline.name}] Processing base: ${base.airport.name} (${base.airport.iata})")
      if (routesCreated >= MAX_ROUTES_PER_CYCLE) return
      
      // Find routes with HIGH DEMAND
      val potentialDestinations = findDemandBasedDestinations(
        base.airport, 
        existingDestinations, 
        personality, 
        availableCash.toLong,
        availableAirplanes,
        allAirports,
        countryRelationships
      )
      
      println(s"[${airline.name}] Found ${potentialDestinations.size} potential destinations from ${base.airport.iata}")
      
      potentialDestinations.foreach { case (destination, estimatedDemand, competitorCount, estimatedRevenue) =>
        if (routesCreated < MAX_ROUTES_PER_CYCLE) {
          // Find suitable aircraft for this route
          val distance = Computation.calculateDistance(base.airport, destination).intValue()
          val suitableAircraft = availableAirplanes.find(airplane => 
            airplane.model.range >= distance && 
            airplane.model.runwayRequirement <= Math.min(base.airport.runwayLength, destination.runwayLength)
          )
          
          suitableAircraft match {
            case Some(airplane) =>
              // Calculate optimal frequency based on demand and personality
              val frequency = calculateDemandBasedFrequency(distance, airplane, estimatedDemand, personality)

              // Simple check: just ensure there's some positive expected revenue
              val revenueJustifies = estimatedRevenue >= 500.0 // Very low threshold just to filter out worthless routes

              if (!revenueJustifies) {
                val revenueStr = estimatedRevenue.formatted("%.2f")
                println(s"[${airline.name}] Skipping route ${base.airport.iata}->${destination.iata}: estimated revenue $revenueStr too low")
                LogSource.insertLogs(List(Log(airline, s"Skipped expansion ${base.airport.iata}->${destination.iata}: estRevenue=$revenueStr too low", LogCategory.NEGOTIATION, LogSeverity.INFO, cycle, Map("from" -> base.airport.iata, "to" -> destination.iata, "estRevenue" -> estimatedRevenue.toString))))
              } else {
                // Create the link with DYNAMIC PRICING based on competition!
                val success = createRouteWithDynamicPricing(
                  airline,
                  base.airport,
                  destination,
                  airplane,
                  frequency,
                  personality,
                  cycle,
                  estimatedDemand,
                  competitorCount
                )

                if (success) {
                  routesCreated += 1
                  val revenueStr = estimatedRevenue.formatted("%.2f")
                  println(s"✈️  [${airline.name}] NEW ROUTE: ${base.airport.iata} -> ${destination.iata}")
                  println(s"    📊 Demand: $estimatedDemand pax/week | EstRevenue: $revenueStr | Competitors: $competitorCount | Freq: ${frequency}x weekly")
                }
              }
              
            case None =>
              println(s"[${airline.name}] No suitable aircraft for ${base.airport.iata} -> ${destination.iata} (${distance}km)")
          }
        }
      }
    }
    
    if (routesCreated == 0) {
      println(s"[${airline.name}] No new routes created this cycle")
    }
  }
  
  /**
   * Actually create a route link - PHASE 3: Dynamic pricing based on competition and demand!
   */
  private def createRouteWithDynamicPricing(
    airline: Airline,
    from: Airport,
    to: Airport,
    airplane: Airplane,
    frequency: Int,
    personality: BotPersonality,
    cycle: Int,
    estimatedDemand: Int,
    competitorCount: Int
  ): Boolean = {
    try {
      val distance = Computation.calculateDistance(from, to).intValue()
      val duration = Computation.calculateDuration(airplane.model, distance)
      
      // Calculate DYNAMIC pricing based on competition and demand!
      val pricingMap = calculateDynamicPricing(from, to, distance, personality, competitorCount, estimatedDemand, airplane.model.capacity * frequency)
      
      // Create link class configuration based on personality
      val linkClassConfig = personality.configureLinkClasses(airplane)
      
      // Create the link
      val link = Link(
        from,
        to,
        airline,
        LinkClassValues.getInstance(
          pricingMap(ECONOMY).toInt, 
          pricingMap(BUSINESS).toInt, 
          pricingMap(FIRST).toInt
        ), // Pricing
        distance,
        LinkClassValues.getInstanceByMap(linkClassConfig), // Capacity configuration
        personality.serviceQuality.toInt, // rawQuality
        duration,
        frequency,
        0 // flightNumber
      )
      
      // Assign airplane to link
      link.setAssignedAirplanes(Map(airplane -> LinkAssignment(frequency, frequency)))
      
      // Save the link
      LinkSource.saveLink(link) match {
        case Some(savedLink) =>
          val avgCompetitorPrice = if (competitorCount > 0) "competing" else "monopoly"
          println(s"    💰 Dynamic Pricing ($avgCompetitorPrice market):")
          println(s"       Economy: $$${pricingMap(ECONOMY).toInt} | Business: $$${pricingMap(BUSINESS).toInt} | First: $$${pricingMap(FIRST).toInt}")
          true
        case None =>
          println(s"    ❌ Failed to save link")
          false
      }
      
    } catch {
      case e: Exception =>
        println(s"    ❌ Error creating route: ${e.getMessage}")
        false
    }
  }
  
  /**
   * Calculate dynamic pricing based on competition and demand
   * This is the CORE of intelligent pricing!
   */
  private def calculateDynamicPricing(
    fromAirport: Airport,
    toAirport: Airport,
    distance: Int,
    personality: BotPersonality,
    competitorCount: Int,
    estimatedDemand: Int,
    ourCapacity: Int
  ): Map[LinkClass, Double] = {
    
    val flightCategory = Computation.getFlightCategory(fromAirport, toAirport)
    val baseIncome = fromAirport.baseIncome
    
    // Get standard prices as baseline
    val standardEconomy = Pricing.computeStandardPrice(distance, flightCategory, ECONOMY, PassengerType.TRAVELER, baseIncome).toDouble
    val standardBusiness = Pricing.computeStandardPrice(distance, flightCategory, BUSINESS, PassengerType.TRAVELER, baseIncome).toDouble
    val standardFirst = Pricing.computeStandardPrice(distance, flightCategory, FIRST, PassengerType.TRAVELER, baseIncome).toDouble
    
    // Load existing competitor links to analyze their pricing
    val competitorLinks = (LinkSource.loadFlightLinksByAirports(fromAirport.id, toAirport.id) ++ 
                          LinkSource.loadFlightLinksByAirports(toAirport.id, fromAirport.id))
    
    // Calculate competition multiplier
    val competitionMultiplier = if (competitorCount == 0) {
      // MONOPOLY - can charge premium!
      personality match {
        case BotPersonality.PREMIUM => 1.40  // Premium charges high
        case BotPersonality.AGGRESSIVE => 1.15 // Aggressive takes some premium
        case BotPersonality.BUDGET => 0.85   // Budget still prices low to build market
        case _ => 1.20 // Others take moderate premium
      }
    } else if (competitorCount == 1) {
      // DUOPOLY - moderate competition
      personality match {
        case BotPersonality.BUDGET => 0.80
        case BotPersonality.AGGRESSIVE => 0.90
        case BotPersonality.PREMIUM => 1.20
        case _ => 1.0
      }
    } else {
      // COMPETITIVE MARKET - price based on competition
      val avgCompetitorEconomy = if (competitorLinks.nonEmpty) {
        competitorLinks.map(_.price(ECONOMY)).sum.toDouble / competitorLinks.size
      } else standardEconomy
      
      // Price relative to competitors
      personality match {
        case BotPersonality.BUDGET => 0.75   // Undercut significantly
        case BotPersonality.AGGRESSIVE => 0.88 // Undercut slightly
        case BotPersonality.PREMIUM => 1.10   // Price above for quality
        case BotPersonality.CONSERVATIVE => 1.0 // Match market
        case _ => 0.95
      }
    }
    
    // Calculate demand multiplier
    // If demand >> capacity, we can charge more
    // If demand << capacity, we need to lower prices
    val demandRatio = if (ourCapacity > 0) estimatedDemand.toDouble / ourCapacity else 1.0
    val demandMultiplier = if (demandRatio > 2.0) {
      1.15 // High demand - increase prices
    } else if (demandRatio > 1.5) {
      1.08
    } else if (demandRatio < 0.5) {
      0.85 // Low demand - decrease prices
    } else if (demandRatio < 0.8) {
      0.92
    } else {
      1.0
    }
    
    // Apply multipliers with personality base adjustment
    val personalityBase = personality.priceMultiplier
    val finalMultiplier = Math.max(MIN_PRICE_MULTIPLIER, 
                          Math.min(MAX_PRICE_MULTIPLIER, 
                                   personalityBase * competitionMultiplier * demandMultiplier))
    
    Map(
      ECONOMY -> (standardEconomy * finalMultiplier),
      BUSINESS -> (standardBusiness * finalMultiplier * 1.05), // Business slightly higher
      FIRST -> (standardFirst * finalMultiplier * 1.10) // First class premium
    )
  }
  
  /**
   * Calculate frequency based on actual demand
   */
  private def calculateDemandBasedFrequency(
    distance: Int, 
    airplane: Airplane, 
    estimatedDemand: Int,
    personality: BotPersonality
  ): Int = {
    val seatsPerFlight = airplane.model.capacity
    
    // Target utilization based on personality
    val targetLoadFactor = (personality.targetCapacityLow + personality.targetCapacityHigh) / 2
    
    // Calculate how many flights needed to meet demand at target load factor
    val flightsNeededForDemand = if (seatsPerFlight > 0) {
      (estimatedDemand / (seatsPerFlight * targetLoadFactor)).toInt
    } else 1
    
    // Factor in distance (longer routes = fewer flights possible)
    val distanceFactor = if (distance > 8000) 0.5
                         else if (distance > 5000) 0.7
                         else if (distance > 2000) 0.85
                         else 1.0
    
    // Calculate final frequency
    val calculatedFrequency = Math.max(1, (flightsNeededForDemand * distanceFactor).toInt)
    
    // Personality adjustments
    val personalityAdjusted = personality match {
      case BotPersonality.AGGRESSIVE => Math.min(calculatedFrequency + 2, 21)
      case BotPersonality.BUDGET => Math.min(calculatedFrequency + 3, 28) // LCCs love high frequency
      case BotPersonality.PREMIUM => Math.max(1, calculatedFrequency - 1) // Premium = fewer, bigger
      case _ => calculatedFrequency
    }
    
    // Cap frequency reasonably
    Math.min(personalityAdjusted, 21) // Max 3 flights per day
  }
  
  /**
   * Find destinations based on ACTUAL DEMAND between airports
   */
  private def findDemandBasedDestinations(
    fromAirport: Airport,
    existingDestinations: Set[Int],
    personality: BotPersonality,
    budget: Long,
    availableAircraft: List[Airplane],
    allAirports: List[Airport],
    countryRelationships: Map[(String, String), Int]
  ): List[(Airport, Int, Int, Double)] = { // Returns (Airport, EstimatedDemand, CompetitorCount, EstimatedRevenue)
    
    if (availableAircraft.isEmpty) return List.empty
    
    val forecastWindow = 4 // cycles to look back for simple trend estimation
    val maxRange = availableAircraft.map(_.model.range).max
    val minRunway = availableAircraft.map(_.model.runwayRequirement).min
    
    // Filter viable airports
    val viableAirports = allAirports.filter(airport => 
      !existingDestinations.contains(airport.id) &&
      airport.id != fromAirport.id &&
      airport.size >= personality.minAirportSize &&
      airport.population >= personality.minPopulation &&
      airport.runwayLength >= minRunway
    )
    
    // Score airports by ACTUAL DEMAND with simple forecasting
    val scoredAirports = viableAirports.flatMap { toAirport =>
      val distance = Computation.calculateDistance(fromAirport, toAirport).intValue()
      
      if (distance <= maxRange && distance > DemandGenerator.MIN_DISTANCE) {
        // Calculate base demand between these airports
        val relationship = countryRelationships.getOrElse((fromAirport.countryCode, toAirport.countryCode), 0)
        val affinity = Computation.calculateAffinityValue(fromAirport.zone, toAirport.zone, relationship)
        
        val demand = DemandGenerator.computeBaseDemandBetweenAirports(fromAirport, toAirport, affinity, distance)
        val baseDemand = DemandGenerator.addUpDemands(demand)
        
        // Check competition on this route
        val competitorLinks = LinkSource.loadFlightLinksByAirports(fromAirport.id, toAirport.id) ++
                             LinkSource.loadFlightLinksByAirports(toAirport.id, fromAirport.id)
        val competitorCount = competitorLinks.size
        
        // Forecast demand using recent consumption if competitors exist
        val expectedDemand = if (competitorCount > 0) {
          val linkIds = competitorLinks.map(_.id)
          val hist = LinkSource.loadLinkConsumptionsByLinksId(linkIds, forecastWindow)
          val perCycleTotals = hist.groupBy(_.cycle).mapValues(_.map(_.link.soldSeats.total).sum)
          if (perCycleTotals.nonEmpty) {
            val cycles = perCycleTotals.toSeq.sortBy(_._1)
            val first = cycles.head._2.toDouble
            val last = cycles.last._2.toDouble
            val growthFactor = if (first > 0) last / first else if (last > 0) 1.1 else 1.0
            // Dampen growth for conservative forecasting
            Math.max(1, (baseDemand * (1.0 + (growthFactor - 1.0) * 0.4)).toInt)
          } else {
            Math.max(1, baseDemand)
          }
        } else {
          Math.max(1, baseDemand)
        }
        
        // Estimate revenue (very conservative): use standard economy price * expected demand * uptake factor
        val flightCategory = Computation.getFlightCategory(fromAirport, toAirport)
        val standardEconomy = Pricing.computeStandardPrice(distance, flightCategory, ECONOMY, PassengerType.TRAVELER, fromAirport.baseIncome).toDouble
        val estimatedRevenue = expectedDemand.toDouble * standardEconomy * 0.6 // assume 60% of demand converts to paying revenue this cycle
        
        // Calculate attractiveness score combining revenue with competition and personality
        val competitionPenalty = competitorCount match {
          case 0 => 1.5
          case 1 => 1.0
          case 2 => 0.7
          case _ => 0.4
        }
        val personalityScore = personality.scoreDestination(toAirport, distance, fromAirport)
        val finalScore = estimatedRevenue * competitionPenalty * (personalityScore / 100.0)
        
        if (expectedDemand > 10) { // Lower minimum demand threshold for more route options
          Some((toAirport, expectedDemand, competitorCount, estimatedRevenue, finalScore))
        } else None
      } else None
    }
    
    // Return top candidates sorted by estimated revenue score
    scoredAirports
      .sortBy(-_._5) // sort by finalScore
      .take(5)
      .map(t => (t._1, t._2, t._3, t._4)) // (Airport, EstimatedDemand, CompetitorCount, EstimatedRevenue)
  }
  
  /**
   * Purchase airplanes based on personality and needs
   */
  private def purchaseAirplanes(airline: Airline, personality: BotPersonality, cycle: Int): Unit = {
    println(s"[${airline.name}] Considering airplane purchases (${personality})")
    
    val availableCash = airline.getBalance() * personality.fleetBudgetRatio
    if (availableCash < 50000) return // Need at least $50k for airplane purchases
    
    val currentFleet = AirplaneSource.loadAirplanesByOwner(airline.id)
    val avgAge = if (currentFleet.nonEmpty) {
      currentFleet.map(a => cycle - a.purchasedCycle).sum / currentFleet.size
    } else 0
    
    // Determine what type of aircraft is needed
    val neededCategory = personality.preferredAircraftCategory(currentFleet, avgAge)
    
    println(s"[${airline.name}] Looking for ${neededCategory} aircraft, budget: $$${availableCash/1000000}M")

    // Conservative purchase logic: ensure we remain above stronger reserve and avoid oversized single purchases
    val reserve = Math.max(BOT_PURCHASE_RESERVE, (airline.getBalance() * 0.10).toLong) // keep at least BOT_PURCHASE_RESERVE or 10% as reserve
    val purchasable = Math.max(0L, airline.getBalance() - reserve)
    if (purchasable < 100000) {
      println(s"[${airline.name}] Skipping aircraft purchases to maintain reserve (reserve=$$${reserve}, purchasable=$$${purchasable})")
      LogSource.insertLogs(List(Log(airline, s"Skipped purchase: insufficient purchasable funds (reserve=$$${reserve}, purchasable=$$${purchasable})", LogCategory.NEGOTIATION, LogSeverity.INFO, cycle, Map("reserve" -> reserve.toString, "purchasable" -> purchasable.toString))))
      return
    }

    // Find candidate models (cheapest in category to be conservative)
    val allModels = ModelSource.loadAllModels()
    val models = (neededCategory match {
      case "SMALL" | "REGIONAL" => allModels.filter(m => m.capacity <= 120)
      case "MEDIUM" => allModels.filter(m => m.capacity > 120 && m.capacity <= 200)
      case "LARGE" => allModels.filter(m => m.capacity > 200)
      case _ => allModels
    }).filter(_.price <= purchasable).sortBy(_.price)

    if (models.isEmpty) {
      println(s"[${airline.name}] No affordable models available for category ${neededCategory} under budget $$${purchasable}")
      return
    }

    // Buy at most one aircraft and ensure we still remain above reserve after purchase
    // Enforce per-airline cooldown: at most 1 purchase per 10 cycles
    val purchasesLast10Cycles = AirplaneSource.loadAirplanesByOwner(airline.id).count(a => a.purchasedCycle >= cycle - 10)
    if (purchasesLast10Cycles > 0) {
      println(s"[${airline.name}] Skipping purchase: recent purchase within last 10 cycles (count=$purchasesLast10Cycles)")
      LogSource.insertLogs(List(Log(airline, s"Skipped purchase: recent purchase within last 10 cycles", LogCategory.NEGOTIATION, LogSeverity.INFO, cycle, Map("reason" -> "cooldown", "purchasesLast10Cycles" -> purchasesLast10Cycles.toString))))
      return
    }

    // Look for overloaded routes where extra capacity would help
    val links = LinkSource.loadFlightLinksByAirlineId(airline.id)
    val linkConsumptions = LinkSource.loadLinkConsumptionsByAirline(airline.id, 1)
    val consumptionByLink = linkConsumptions.groupBy(_.link.id).mapValues(_.head)

    val candidateLinks = links.flatMap { link =>
      consumptionByLink.get(link.id).map { consumption =>
        val totalCapacity = link.capacity.total * link.frequency
        val soldSeats = consumption.link.soldSeats.total
        val loadFactor = if (totalCapacity > 0) soldSeats.toDouble / totalCapacity else 0.0
        (link, loadFactor, consumption.profit)
      }
    }.filter(_._2 > personality.targetCapacityHigh)

    if (candidateLinks.isEmpty) {
      println(s"[${airline.name}] No overloaded routes found - skipping purchase")
      LogSource.insertLogs(List(Log(airline, s"Skipped purchase: no overloaded routes", LogCategory.NEGOTIATION, LogSeverity.INFO, cycle, Map("purchasable" -> purchasable.toString))))
      return
    }

    // Buy the cheapest model that can serve at least one overloaded route with acceptable ROI and safe pacing
    val chosenOpt = models.find { m =>
      // Disallow single purchase larger than a fraction of current balance
      if (m.price > airline.getBalance() * MAX_PURCHASE_FRACTION) {
        LogSource.insertLogs(List(Log(airline, s"Skip model ${m.name} - price $$${m.price} exceeds ${MAX_PURCHASE_FRACTION*100}% of balance", LogCategory.NEGOTIATION, LogSeverity.INFO, cycle, Map("model" -> m.name, "price" -> m.price.toString))))
        false
      } else {
        // Evaluate expected ROI + projected balance safety
        candidateLinks.exists { case (link, lf, profit) =>
          val distance = link.distance
          if (m.range >= distance && m.runwayRequirement <= Math.min(link.from.runwayLength, link.to.runwayLength)) {
            // Estimate expected incremental profit per cycle if we add capacity; be conservative
            val overloadRatio = (lf - personality.targetCapacityHigh) / (1.0 - personality.targetCapacityHigh)
            val expectedProfitIncrease = profit * Math.min(0.5, Math.max(0.1, overloadRatio * 0.5)) // conservative fraction

            // Calculate recent average profit per cycle to project future liquidity
            val recentConsumptions = LinkSource.loadLinkConsumptionsByAirline(airline.id, UNPROFITABLE_CYCLES_THRESHOLD)
            val cyclesConsidered = Math.max(1, recentConsumptions.map(_.cycle).distinct.size)
            val recentProfit = if (recentConsumptions.nonEmpty) recentConsumptions.map(_.profit).sum / cyclesConsidered else 0

            val projectedBalanceAfterPurchase = airline.getBalance() - m.price + recentProfit * PURCHASE_PROJECTION_WINDOW

            val paybackCycles = if (expectedProfitIncrease > 0) m.price / expectedProfitIncrease else Long.MaxValue

            val passesPayback = expectedProfitIncrease > 0 && paybackCycles <= 24
            val passesLiquidity = projectedBalanceAfterPurchase >= BOT_PURCHASE_RESERVE

            // Log telemetry for model decision
            LogSource.insertLogs(List(Log(airline, s"Evaluate purchase ${m.name}: price=$$${m.price}, expectedProfitInc=$$${expectedProfitIncrease}, payback=${if (expectedProfitIncrease>0) paybackCycles else -1}, projectedBalance=$$${projectedBalanceAfterPurchase}", LogCategory.NEGOTIATION, LogSeverity.FINE, cycle, Map("model"->m.name, "price"->m.price.toString, "expectedProfitIncrease"->expectedProfitIncrease.toString, "paybackCycles"->paybackCycles.toString, "projectedBalance"->projectedBalanceAfterPurchase.toString))))

            passesPayback && passesLiquidity && (m.price <= purchasable)
          } else false
        }
      }
    }

    chosenOpt match {
      case Some(model) =>
        try {
          val airplane = Airplane(model, airline, cycle, cycle, 100.0, AirplaneSimulation.computeDepreciationRate(model, 100.0 / model.lifespan).toInt, model.price)
          AirplaneSource.saveAirplanes(List(airplane))
          AirlineSource.adjustAirlineBalance(airline.id, -model.price)
          println(s"[${airline.name}] Purchased 1 ${model.name} for $$${model.price}")
          LogSource.insertLogs(List(Log(airline, s"Purchased ${model.name} for $$${model.price}", LogCategory.NEGOTIATION, LogSeverity.INFO, cycle, Map("model" -> model.name, "price" -> model.price.toString, "purchasable" -> purchasable.toString))))
        } catch {
          case e: Exception =>
            println(s"[${airline.name}] Failed to purchase aircraft: ${e.getMessage}")
            LogSource.insertLogs(List(Log(airline, s"Failed to purchase ${model.name}: ${e.getMessage}", LogCategory.NEGOTIATION, LogSeverity.WARN, cycle, Map("model" -> model.name))))
        }
      case None =>
        println(s"[${airline.name}] No affordable models fit overloaded routes under budget $$${purchasable}")
        // Compute best candidate payback for telemetry
        val modelPaybacks = models.flatMap { m =>
          candidateLinks.flatMap { case (link, lf, profit) =>
            val distance = link.distance
            if (m.range >= distance && m.runwayRequirement <= Math.min(link.from.runwayLength, link.to.runwayLength)) {
              val overloadRatio = (lf - personality.targetCapacityHigh) / (1.0 - personality.targetCapacityHigh)
              val expectedProfitIncrease = profit * Math.min(0.5, Math.max(0.1, overloadRatio * 0.5))
              if (expectedProfitIncrease > 0) {
                Some((m.name, m.price, (m.price / expectedProfitIncrease).toLong))
              } else None
            } else None
          }
        }
        val best = if (modelPaybacks.nonEmpty) Some(modelPaybacks.minBy(_._3)) else None
        val props = Map("purchasable" -> purchasable.toString) ++ best.map(b => Map("best_model" -> b._1, "best_price" -> b._2.toString, "best_payback" -> b._3.toString)).getOrElse(Map.empty)
        LogSource.insertLogs(List(Log(airline, s"No affordable models fit overloaded routes under budget $$${purchasable}", LogCategory.NEGOTIATION, LogSeverity.INFO, cycle, props)))

        // Consider leasing as alternative: if purchase payback too long, recommend lease (telemetry only for now)
        if (best.isDefined) {
          val (bestModelName, bestPrice, bestPayback) = best.get
          if (bestPayback > 24) {
            LogSource.insertLogs(List(Log(airline, s"Recommendation: consider leasing $bestModelName (payback ${bestPayback} cycles) instead of buying", LogCategory.NEGOTIATION, LogSeverity.INFO, cycle, Map("recommendation"->"lease", "model"->bestModelName, "estimated_payback"->bestPayback.toString))))
          }
        }
    }
  }
  
    /**
   * Optimize existing routes - PHASE 3: DYNAMIC price and frequency adjustments!
   * This is called frequently to keep pricing competitive
   */
  private def optimizeExistingRoutes(airline: Airline, personality: BotPersonality, cycle: Int): Unit = {
    println(s"[${airline.name}] Optimizing routes with dynamic pricing...")
    
    val links = LinkSource.loadFlightLinksByAirlineId(airline.id)
    if (links.isEmpty) {
      println(s"[${airline.name}] No routes to optimize")
      return
    }
    
    // Load consumption data for performance analysis
    val linkConsumptions = LinkSource.loadLinkConsumptionsByAirline(airline.id, 1)
    val consumptionByLink = linkConsumptions.groupBy(_.link.id)
    
    var priceAdjustments = 0
    var frequencyAdjustments = 0
    
    links.foreach { link =>
      // Get last cycle's performance
      val lastConsumption = consumptionByLink.get(link.id).flatMap(_.headOption)
      
      lastConsumption match {
        case Some(consumption) =>
          // Calculate load factor
          val totalCapacity = link.capacity.total * link.frequency
          val soldSeats = consumption.link.soldSeats.total
          val loadFactor = if (totalCapacity > 0) soldSeats.toDouble / totalCapacity else 0.0
          
          // Get current competition on this route
          val competitorLinks = (LinkSource.loadFlightLinksByAirports(link.from.id, link.to.id) ++
                                LinkSource.loadFlightLinksByAirports(link.to.id, link.from.id))
                                .filter(_.airline.id != airline.id)
          val competitorCount = competitorLinks.size
          
          // Analyze competitor pricing
          val avgCompetitorPrice = if (competitorLinks.nonEmpty) {
            competitorLinks.map(_.price(ECONOMY)).sum.toDouble / competitorLinks.size
          } else link.price(ECONOMY).toDouble
          
          val ourPrice = link.price(ECONOMY)
          val priceRatio = ourPrice.toDouble / avgCompetitorPrice
          
          // Decide on price adjustment
          val newPricing = calculatePriceAdjustment(
            link, 
            loadFactor, 
            priceRatio, 
            competitorCount, 
            personality,
            consumption.profit
          )
          
          // Apply price changes if significant
          if (newPricing != link.price) {
            val updatedLink = link.copy(price = newPricing)
            updatedLink.setAssignedAirplanes(link.getAssignedAirplanes())
            LinkSource.updateLink(updatedLink)
            
            val changeType = if (newPricing(ECONOMY) > link.price(ECONOMY)) "📈 RAISED" else "📉 LOWERED"
            println(s"    $changeType prices on ${link.from.iata}->${link.to.iata}: " +
                   s"$$${link.price(ECONOMY)} → $$${newPricing(ECONOMY)} (LF: ${(loadFactor*100).toInt}%, Competitors: $competitorCount)")
            priceAdjustments += 1
          }
          
          // Frequency adjustments based on load factor
          if (loadFactor > personality.targetCapacityHigh && link.frequency < 21) {
            // Route is full - increase frequency if we have aircraft
            println(s"    📊 ${link.from.iata}->${link.to.iata} is running hot (${(loadFactor*100).toInt}% LF) - consider frequency increase")
            frequencyAdjustments += 1
          } else if (loadFactor < personality.targetCapacityLow && link.frequency > 1) {
            // Route is underperforming
            println(s"    ⚠️  ${link.from.iata}->${link.to.iata} is underperforming (${(loadFactor*100).toInt}% LF)")
          }
          
        case None =>
          // No consumption data yet - new route, leave pricing as is
      }
    }
    
    if (priceAdjustments > 0) {
      println(s"[${airline.name}] Adjusted prices on $priceAdjustments routes")
    }
  }
  
  /**
   * Calculate price adjustment based on load factor and competition
   */
  private def calculatePriceAdjustment(
    link: Link,
    loadFactor: Double,
    priceRatioToCompetitors: Double,
    competitorCount: Int,
    personality: BotPersonality,
    profit: Long
  ): LinkClassValues = {
    
    val currentEconomy = link.price(ECONOMY)
    val currentBusiness = link.price(BUSINESS)
    val currentFirst = link.price(FIRST)
    
    // Get standard prices for comparison
    val flightCategory = Computation.getFlightCategory(link.from, link.to)
    val standardEconomy = Pricing.computeStandardPrice(link.distance, flightCategory, ECONOMY, PassengerType.TRAVELER, link.from.baseIncome)
    
    var multiplier = 1.0
    
    // Load factor based adjustments - MORE AGGRESSIVE price cuts for low LF
    if (loadFactor > 0.95) {
      // Nearly full - can raise prices
      multiplier += PRICE_ADJUSTMENT_STEP * 2
    } else if (loadFactor > personality.targetCapacityHigh) {
      // Above target - slight increase
      multiplier += PRICE_ADJUSTMENT_STEP
    } else if (loadFactor < CRITICAL_LOAD_FACTOR) {
      // CRITICAL: Very low load factor - dramatic price cut
      multiplier -= PRICE_ADJUSTMENT_STEP * 5
    } else if (loadFactor < MIN_LOAD_FACTOR_THRESHOLD) {
      // Below minimum - aggressive price cut
      multiplier -= PRICE_ADJUSTMENT_STEP * 3
    } else if (loadFactor < personality.targetCapacityLow) {
      // Below target - need to lower prices
      multiplier -= PRICE_ADJUSTMENT_STEP * 2
    } else if (loadFactor < personality.targetCapacityLow + 0.1) {
      // Slightly below target
      multiplier -= PRICE_ADJUSTMENT_STEP
    }
    
    // If route is losing money, cut prices more aggressively
    if (profit < 0 && loadFactor < 0.6) {
      multiplier -= PRICE_ADJUSTMENT_STEP * 2
    }
    
    // Competition based adjustments
    if (competitorCount > 0) {
      if (priceRatioToCompetitors > 1.2) {
        // We're much more expensive - lower prices unless premium
        if (personality != BotPersonality.PREMIUM) {
          multiplier -= PRICE_ADJUSTMENT_STEP
        }
      } else if (priceRatioToCompetitors < 0.8 && loadFactor > 0.8) {
        // We're cheaper and still filling up - can raise prices
        multiplier += PRICE_ADJUSTMENT_STEP
      }
    } else {
      // Monopoly - if profitable, slowly raise prices
      if (profit > 0 && loadFactor > 0.6) {
        multiplier += PRICE_ADJUSTMENT_STEP * 0.5
      }
    }
    
    // Personality adjustments
    personality match {
      case BotPersonality.AGGRESSIVE =>
        // Aggressive prefers market share over margin
        if (loadFactor < 0.7) multiplier -= PRICE_ADJUSTMENT_STEP
      case BotPersonality.BUDGET =>
        // Budget always tries to undercut
        if (priceRatioToCompetitors > 0.85) multiplier -= PRICE_ADJUSTMENT_STEP
      case BotPersonality.PREMIUM =>
        // Premium maintains high prices even if load factor suffers
        if (multiplier < 1.0) multiplier = Math.max(multiplier, 0.98)
      case _ =>
    }
    
    // Apply multiplier with bounds
    val finalMultiplier = Math.max(MIN_PRICE_MULTIPLIER, Math.min(MAX_PRICE_MULTIPLIER, multiplier))
    
    // Ensure we don't go below minimum viable prices
    val minPrice = (standardEconomy * MIN_PRICE_MULTIPLIER).toInt
    val maxPrice = (standardEconomy * MAX_PRICE_MULTIPLIER).toInt
    
    LinkClassValues.getInstance(
      Math.max(minPrice, Math.min(maxPrice, (currentEconomy * finalMultiplier).toInt)),
      Math.max((minPrice * 2), Math.min((maxPrice * 2), (currentBusiness * finalMultiplier).toInt)),
      Math.max((minPrice * 3), Math.min((maxPrice * 3), (currentFirst * finalMultiplier).toInt))
    )
  }
  
  /**
   * Respond to competition on shared routes - PHASE 3: Intelligent responses
   */
  private def respondToCompetition(airline: Airline, personality: BotPersonality, cycle: Int): Unit = {
    println(s"[${airline.name}] Analyzing competition (${personality})")
    
    val ownLinks = LinkSource.loadFlightLinksByAirlineId(airline.id)
    val consumptions = LinkSource.loadLinkConsumptionsByAirline(airline.id, 1)
    val consumptionByLink = consumptions.groupBy(_.link.id)
    
    var competitiveResponses = 0
    
    ownLinks.foreach { link =>
      // Find competing airlines on same route
      val allLinksOnRoute = (LinkSource.loadFlightLinksByAirports(link.from.id, link.to.id) ++
                            LinkSource.loadFlightLinksByAirports(link.to.id, link.from.id))
                            .filter(_.airline.id != airline.id)
      
      if (allLinksOnRoute.nonEmpty) {
        val competitorCount = allLinksOnRoute.size
        
        // Analyze competitor capacity and pricing
        val totalCompetitorCapacity = allLinksOnRoute.map(l => l.capacity.total * l.frequency).sum
        val ourCapacity = link.capacity.total * link.frequency
        val marketShare = if ((totalCompetitorCapacity + ourCapacity) > 0) {
          ourCapacity.toDouble / (totalCompetitorCapacity + ourCapacity)
        } else 0.0
        
        val avgCompetitorPrice = allLinksOnRoute.map(_.price(ECONOMY)).sum / competitorCount
        val ourPrice = link.price(ECONOMY)
        
        // Check if we're losing market share
        val consumption = consumptionByLink.get(link.id).flatMap(_.headOption)
        val loadFactor = consumption.map { c =>
          val cap = link.capacity.total * link.frequency
          if (cap > 0) c.link.soldSeats.total.toDouble / cap else 0.0
        }.getOrElse(0.5)
        
        // Determine competitive response based on personality
        val shouldRespond = (loadFactor < personality.targetCapacityLow) || 
                           (marketShare < 0.3 && competitorCount > 1) ||
                           (ourPrice > avgCompetitorPrice * 1.3 && personality != BotPersonality.PREMIUM)
        
        if (shouldRespond) {
          personality match {
            case BotPersonality.AGGRESSIVE =>
              // Aggressive: Match or undercut competitor prices
              if (ourPrice > avgCompetitorPrice) {
                val newPrice = (avgCompetitorPrice * 0.95).toInt
                val newPricing = LinkClassValues.getInstance(
                  newPrice,
                  (link.price(BUSINESS) * 0.95).toInt,
                  link.price(FIRST)
                )
                val updatedLink = link.copy(price = newPricing)
                updatedLink.setAssignedAirplanes(link.getAssignedAirplanes())
                LinkSource.updateLink(updatedLink)
                println(s"    ⚔️  AGGRESSIVE: Cut prices on ${link.from.iata}->${link.to.iata} to undercut competition")
                competitiveResponses += 1
              }
              
            case BotPersonality.BUDGET =>
              // Budget: Always try to be cheapest
              val lowestCompetitor = allLinksOnRoute.map(_.price(ECONOMY)).min
              if (ourPrice >= lowestCompetitor) {
                val newPrice = Math.max((lowestCompetitor * 0.90).toInt, 
                                       (Pricing.computeStandardPrice(link.distance, 
                                         Computation.getFlightCategory(link.from, link.to), 
                                         ECONOMY, PassengerType.TRAVELER, link.from.baseIncome) * MIN_PRICE_MULTIPLIER).toInt)
                val newPricing = LinkClassValues.getInstance(
                  newPrice,
                  (link.price(BUSINESS) * 0.90).toInt,
                  link.price(FIRST)
                )
                val updatedLink = link.copy(price = newPricing)
                updatedLink.setAssignedAirplanes(link.getAssignedAirplanes())
                LinkSource.updateLink(updatedLink)
                println(s"    💸 BUDGET: Slashed prices on ${link.from.iata}->${link.to.iata} to be cheapest")
                competitiveResponses += 1
              }
              
            case BotPersonality.PREMIUM =>
              // Premium: Ignore budget competitors, focus on quality
              val premiumCompetitors = allLinksOnRoute.filter(_.rawQuality >= 50)
              if (premiumCompetitors.isEmpty) {
                println(s"    💎 PREMIUM: No premium competitors on ${link.from.iata}->${link.to.iata} - maintaining position")
              }
              
            case BotPersonality.CONSERVATIVE =>
              // Conservative: Slight price adjustment, focus on stability
              if (loadFactor < 0.5) {
                val newPrice = Math.max((ourPrice * 0.95).toInt, 
                                       (Pricing.computeStandardPrice(link.distance,
                                         Computation.getFlightCategory(link.from, link.to),
                                         ECONOMY, PassengerType.TRAVELER, link.from.baseIncome) * 0.85).toInt)
                val newPricing = LinkClassValues.getInstance(
                  newPrice,
                  (link.price(BUSINESS) * 0.97).toInt,
                  link.price(FIRST)
                )
                val updatedLink = link.copy(price = newPricing)
                updatedLink.setAssignedAirplanes(link.getAssignedAirplanes())
                LinkSource.updateLink(updatedLink)
                println(s"    🏛️  CONSERVATIVE: Moderate price cut on ${link.from.iata}->${link.to.iata}")
                competitiveResponses += 1
              }
              
            case BotPersonality.REGIONAL =>
              // Regional: Find niche, avoid head-on competition with big carriers
              if (marketShare < 0.2 && competitorCount > 2) {
                println(s"    🏔️  REGIONAL: Too much competition on ${link.from.iata}->${link.to.iata} - may abandon")
              }
              
            case BotPersonality.BALANCED =>
              // Balanced: Tactical response based on situation
              if (loadFactor < 0.6 && ourPrice > avgCompetitorPrice) {
                val newPrice = ((ourPrice + avgCompetitorPrice) / 2).toInt
                val newPricing = LinkClassValues.getInstance(
                  newPrice,
                  (link.price(BUSINESS) * 0.97).toInt,
                  link.price(FIRST)
                )
                val updatedLink = link.copy(price = newPricing)
                updatedLink.setAssignedAirplanes(link.getAssignedAirplanes())
                LinkSource.updateLink(updatedLink)
                println(s"    ⚖️  BALANCED: Adjusted prices on ${link.from.iata}->${link.to.iata} to match market")
                competitiveResponses += 1
              }
          }
        }
      }
    }
    
    if (competitiveResponses > 0) {
      println(s"[${airline.name}] Made $competitiveResponses competitive responses")
    } else {
      println(s"[${airline.name}] No competitive action needed")
    }
  }
  
  /**
   * Evaluate and abandon unprofitable routes
   */
  private def evaluateRouteAbandonment(airline: Airline, personality: BotPersonality, cycle: Int): Unit = {
    println(s"[${airline.name}] Evaluating route profitability...")
    
    val links = LinkSource.loadFlightLinksByAirlineId(airline.id)
    if (links.isEmpty) return
    
    // Load multiple cycles of consumption data to detect trends
    val linkConsumptions = LinkSource.loadLinkConsumptionsByAirline(airline.id, Math.max(UNPROFITABLE_CYCLES_THRESHOLD, 4))
    val consumptionByLink = linkConsumptions.groupBy(_.link.id)
    
    var abandondedRoutes = 0
    var frequencyReduced = 0
    
    links.foreach { link =>
      val consumptionHistory = consumptionByLink.getOrElse(link.id, List.empty)
      
      if (consumptionHistory.nonEmpty) {
        val latestConsumption = consumptionHistory.maxBy(_.cycle)
        val latestProfit = latestConsumption.profit
        val latestCap = latestConsumption.link.capacity.total * latestConsumption.link.frequency
        val latestLoadFactor = if (latestCap > 0) latestConsumption.link.soldSeats.total.toDouble / latestCap else 0.0
        
        // IMMEDIATE ACTION: Severe single-cycle loss or critically low load factor
        if (latestProfit < SEVERE_LOSS_THRESHOLD || latestLoadFactor < CRITICAL_LOAD_FACTOR) {
          // Try reducing frequency first if > 1
          if (link.frequency > 1) {
            val newFreq = Math.max(1, link.frequency / 2)
            val updatedLink = link.copy(frequency = newFreq)
            updatedLink.setAssignedAirplanes(link.getAssignedAirplanes().map { case (ap, la) =>
              (ap, LinkAssignment(newFreq, la.flightMinutes * newFreq / link.frequency))
            })
            LinkSource.updateLink(updatedLink)
            println(s"    ⚠️  FREQUENCY CUT: ${link.from.iata}->${link.to.iata} freq ${link.frequency}->${newFreq} (loss=$$${latestProfit}, LF=${(latestLoadFactor*100).toInt}%)")
            LogSource.insertLogs(List(Log(airline, s"Reduced frequency ${link.from.iata}->${link.to.iata} from ${link.frequency} to $newFreq due to losses", LogCategory.NEGOTIATION, LogSeverity.WARN, cycle, Map("profit" -> latestProfit.toString, "loadFactor" -> latestLoadFactor.toString))))
            frequencyReduced += 1
          } else {
            // Already at frequency 1 and still losing - abandon immediately
            LinkSource.deleteLink(link.id)
            println(s"    ❌ IMMEDIATE ABANDON: ${link.from.iata}->${link.to.iata} (loss=$$${latestProfit}, LF=${(latestLoadFactor*100).toInt}%)")
            LogSource.insertLogs(List(Log(airline, s"Abandoned ${link.from.iata}->${link.to.iata} due to severe losses (profit=$$${latestProfit})", LogCategory.NEGOTIATION, LogSeverity.WARN, cycle, Map("profit" -> latestProfit.toString, "loadFactor" -> latestLoadFactor.toString))))
            abandondedRoutes += 1
          }
        } else if (consumptionHistory.size >= UNPROFITABLE_CYCLES_THRESHOLD) {
          // Standard logic: check for consistent losses over multiple cycles
          val unprofitableCycles = consumptionHistory.count(_.profit < 0)
          val avgLoadFactor = {
            val lfs = consumptionHistory.map { c =>
              val cap = c.link.capacity.total * c.link.frequency
              if (cap > 0) c.link.soldSeats.total.toDouble / cap else 0.0
            }
            if (lfs.nonEmpty) lfs.sum / lfs.size else 0.0
          }
          val totalLoss = consumptionHistory.filter(_.profit < 0).map(_.profit).sum
          
          // Decide if we should abandon this route
          val shouldAbandon = (unprofitableCycles >= UNPROFITABLE_CYCLES_THRESHOLD) ||
                             (avgLoadFactor < MIN_LOAD_FACTOR_THRESHOLD && consumptionHistory.size >= 2)
          
          // All personalities now cut losses more aggressively
          val personalityAllowsAbandonment = personality match {
            case BotPersonality.AGGRESSIVE => avgLoadFactor < 0.35 // was 0.2
            case BotPersonality.CONSERVATIVE => avgLoadFactor < 0.50 // was 0.4
            case BotPersonality.BUDGET => avgLoadFactor < 0.45 // was 0.35
            case _ => avgLoadFactor < MIN_LOAD_FACTOR_THRESHOLD
          }
          
          if (shouldAbandon && personalityAllowsAbandonment) {
            LinkSource.deleteLink(link.id)
            println(s"    ❌ ABANDONED: ${link.from.iata}->${link.to.iata} (${unprofitableCycles} unprofitable cycles, ${(avgLoadFactor*100).toInt}% avg LF, total loss=$$${totalLoss})")
            LogSource.insertLogs(List(Log(airline, s"Abandoned ${link.from.iata}->${link.to.iata} after ${unprofitableCycles} unprofitable cycles (total loss=$$${totalLoss})", LogCategory.NEGOTIATION, LogSeverity.INFO, cycle, Map("unprofitableCycles" -> unprofitableCycles.toString, "avgLoadFactor" -> avgLoadFactor.toString, "totalLoss" -> totalLoss.toString))))
            abandondedRoutes += 1
          }
        }
      }
    }
    
    if (abandondedRoutes > 0 || frequencyReduced > 0) {
      println(s"[${airline.name}] Route optimization: abandoned $abandondedRoutes, reduced frequency on $frequencyReduced")
    } else {
      println(s"[${airline.name}] All routes performing acceptably")
    }
  }

  // ============================================================================
  // LAYER 2: PLAYER COMPETITION TRACKING (Memory System)
  // ============================================================================

  /**
   * Tracks player competition events for reactive bot behavior.
   * This state persists across simulation cycles within the JVM lifetime.
   */
  case class PlayerCompetitionInfo(
    playerAirlineId: Int,
    playerAirlineName: String,
    firstDetectedCycle: Int,
    lastSeenCycle: Int,
    playerCapacity: Int,
    playerPriceEconomy: Int,
    responsesTaken: Int
  )

  // Map[BotAirlineId -> Map[RouteKey -> PlayerCompetitionInfo]]
  private val competitionHistory: mutable.Map[Int, mutable.Map[String, PlayerCompetitionInfo]] = mutable.Map.empty

  private def routeKey(fromId: Int, toId: Int): String = {
    if (fromId < toId) s"$fromId-$toId" else s"$toId-$fromId"
  }

  private def recordPlayerCompetition(
    botAirlineId: Int,
    fromAirportId: Int,
    toAirportId: Int,
    playerLink: Link,
    cycle: Int
  ): Unit = {
    val key = routeKey(fromAirportId, toAirportId)
    val botRoutes = competitionHistory.getOrElseUpdate(botAirlineId, mutable.Map.empty)

    botRoutes.get(key) match {
      case Some(existing) =>
        botRoutes(key) = existing.copy(
          lastSeenCycle = cycle,
          playerCapacity = playerLink.capacity.total * playerLink.frequency,
          playerPriceEconomy = playerLink.price(ECONOMY)
        )
      case None =>
        botRoutes(key) = PlayerCompetitionInfo(
          playerAirlineId = playerLink.airline.id,
          playerAirlineName = playerLink.airline.name,
          firstDetectedCycle = cycle,
          lastSeenCycle = cycle,
          playerCapacity = playerLink.capacity.total * playerLink.frequency,
          playerPriceEconomy = playerLink.price(ECONOMY),
          responsesTaken = 0
        )
    }
  }

  private def getPlayerCompetition(botAirlineId: Int, fromAirportId: Int, toAirportId: Int): Option[PlayerCompetitionInfo] = {
    val key = routeKey(fromAirportId, toAirportId)
    competitionHistory.get(botAirlineId).flatMap(_.get(key))
  }

  private def isNewPlayerEntry(botAirlineId: Int, fromAirportId: Int, toAirportId: Int, cycle: Int): Boolean = {
    getPlayerCompetition(botAirlineId, fromAirportId, toAirportId) match {
      case Some(info) => info.firstDetectedCycle == cycle
      case None => false
    }
  }

  private def incrementResponseCount(botAirlineId: Int, fromAirportId: Int, toAirportId: Int): Unit = {
    val key = routeKey(fromAirportId, toAirportId)
    competitionHistory.get(botAirlineId).foreach { routes =>
      routes.get(key).foreach { info =>
        routes(key) = info.copy(responsesTaken = info.responsesTaken + 1)
      }
    }
  }

  private def cleanupStaleEntries(currentCycle: Int): Unit = {
    competitionHistory.foreach { case (_, routes) =>
      routes.filterInPlace { case (_, info) =>
        currentCycle - info.lastSeenCycle <= PLAYER_COMPETITION_STALE_CYCLES
      }
    }
  }

  // ============================================================================
  // LAYER 1: SURVIVAL - Financial Health & Emergency Protocols
  // ============================================================================

  /**
   * Check if an airline is a player (not a bot)
   */
  private def isPlayerAirline(airline: Airline): Boolean = {
    airline.airlineType != AirlineType.NON_PLAYER
  }

  /**
   * Load all player airlines competing on a specific route
   */
  private def getPlayerCompetitorsOnRoute(fromAirportId: Int, toAirportId: Int, excludeAirlineId: Int): List[Link] = {
    val allLinksOnRoute = (LinkSource.loadFlightLinksByAirports(fromAirportId, toAirportId) ++
                          LinkSource.loadFlightLinksByAirports(toAirportId, fromAirportId))
                          .filter(_.airline.id != excludeAirlineId)

    allLinksOnRoute.filter(link => isPlayerAirline(link.airline))
  }

  /**
   * LAYER 1: Evaluate financial health and trigger emergency protocols if needed
   * Returns true if emergency action was taken (skip normal operations)
   */
  private def evaluateEmergencyConditions(airline: Airline, personality: BotPersonality, cycle: Int): Boolean = {
    val balance = airline.getBalance()

    // Calculate recent profit trend
    val recentConsumptions = LinkSource.loadLinkConsumptionsByAirline(airline.id, 4)
    val cyclesConsidered = Math.max(1, recentConsumptions.map(_.cycle).distinct.size)
    val avgWeeklyProfit = if (recentConsumptions.nonEmpty) {
      recentConsumptions.map(_.profit).sum / cyclesConsidered
    } else 0L

    val projectedBalance = balance + (avgWeeklyProfit * 4)

    val emergencyLevel = if (balance < EMERGENCY_BALANCE_THRESHOLD) {
      "EMERGENCY"
    } else if (balance < CRITICAL_BALANCE_THRESHOLD || projectedBalance < EMERGENCY_BALANCE_THRESHOLD) {
      "CRITICAL"
    } else if (balance < WARNING_BALANCE_THRESHOLD || projectedBalance < CRITICAL_BALANCE_THRESHOLD) {
      "WARNING"
    } else {
      "SAFE"
    }

    val state = FinancialState(balance, avgWeeklyProfit, projectedBalance, emergencyLevel)

    emergencyLevel match {
      case "EMERGENCY" =>
        executeEmergencyProtocol(airline, state, cycle)
        true

      case "CRITICAL" =>
        executeCriticalProtocol(airline, state, cycle)
        true

      case "WARNING" =>
        executeWarningProtocol(airline, state, cycle)
        false // Allow some normal operations

      case _ =>
        false
    }
  }

  /**
   * EMERGENCY protocol - aggressive cost cutting
   */
  private def executeEmergencyProtocol(airline: Airline, state: FinancialState, cycle: Int): Unit = {
    println(s"🚨 [${airline.name}] EMERGENCY PROTOCOL - Balance: $$${state.balance}, Weekly: $$${state.weeklyProfit}, Projected: $$${state.projectedBalanceIn4Weeks}")

    LogSource.insertLogs(List(Log(
      airline,
      s"EMERGENCY: Balance $$${state.balance}, weekly profit $$${state.weeklyProfit}",
      LogCategory.NEGOTIATION,
      LogSeverity.WARN,
      cycle,
      Map("emergencyLevel" -> "EMERGENCY", "balance" -> state.balance.toString, "weeklyProfit" -> state.weeklyProfit.toString)
    )))

    // 1. Abandon ALL unprofitable routes immediately
    val links = LinkSource.loadFlightLinksByAirlineId(airline.id)
    val consumptions = LinkSource.loadLinkConsumptionsByAirline(airline.id, 1)
    val consumptionByLink = consumptions.groupBy(_.link.id)

    var abandonedCount = 0
    links.foreach { link =>
      consumptionByLink.get(link.id).flatMap(_.headOption).foreach { consumption =>
        if (consumption.profit < 0) {
          LinkSource.deleteLink(link.id)
          abandonedCount += 1
          println(s"    🚨 [EMERGENCY] Abandoned ${link.from.iata}->${link.to.iata} (loss: $$${consumption.profit})")
        }
      }
    }

    // 2. Sell unassigned aircraft
    val remainingLinks = LinkSource.loadFlightLinksByAirlineId(airline.id)
    val assignedAirplanes = remainingLinks.flatMap(_.getAssignedAirplanes().keys).toSet
    val allAircraft = AirplaneSource.loadAirplanesByOwner(airline.id)
    val unassignedAircraft = allAircraft.filter(a => !assignedAirplanes.contains(a) && a.isReady && !a.isSold)

    var soldCount = 0
    unassignedAircraft.take(5).foreach { airplane => // Limit to 5 per cycle
      try {
        val sellValue = (airplane.value * 0.8).toLong // 80% of current value
        AirlineSource.adjustAirlineBalance(airline.id, sellValue)
        AirplaneSource.deleteAirplane(airplane.id, None)
        soldCount += 1
        println(s"    🚨 [EMERGENCY] Sold ${airplane.model.name} for $$${sellValue}")
      } catch {
        case e: Exception => println(s"    Failed to sell aircraft: ${e.getMessage}")
      }
    }

    // 3. Reduce frequency on remaining routes
    val finalLinks = LinkSource.loadFlightLinksByAirlineId(airline.id)
    finalLinks.filter(_.frequency > 1).foreach { link =>
      val newFreq = Math.max(1, link.frequency / 2)
      val updatedLink = link.copy(frequency = newFreq)
      updatedLink.setAssignedAirplanes(link.getAssignedAirplanes().map {
        case (ap, la) => (ap, LinkAssignment(newFreq, la.flightMinutes * newFreq / link.frequency))
      })
      LinkSource.updateLink(updatedLink)
    }

    println(s"🚨 [${airline.name}] Emergency actions: Abandoned $abandonedCount routes, sold $soldCount aircraft")
  }

  /**
   * CRITICAL protocol - moderate cost cutting
   */
  private def executeCriticalProtocol(airline: Airline, state: FinancialState, cycle: Int): Unit = {
    println(s"⚠️  [${airline.name}] CRITICAL PROTOCOL - Balance: $$${state.balance}")

    LogSource.insertLogs(List(Log(
      airline,
      s"CRITICAL: Balance $$${state.balance}, cutting severely unprofitable routes",
      LogCategory.NEGOTIATION,
      LogSeverity.WARN,
      cycle,
      Map("emergencyLevel" -> "CRITICAL", "balance" -> state.balance.toString)
    )))

    // Abandon severely unprofitable routes only
    val links = LinkSource.loadFlightLinksByAirlineId(airline.id)
    val consumptions = LinkSource.loadLinkConsumptionsByAirline(airline.id, 2)
    val consumptionByLink = consumptions.groupBy(_.link.id)

    links.foreach { link =>
      val history = consumptionByLink.getOrElse(link.id, List.empty)
      if (history.size >= 2 && history.forall(_.profit < SEVERE_LOSS_THRESHOLD)) {
        LinkSource.deleteLink(link.id)
        println(s"    ⚠️  [CRITICAL] Abandoned severely unprofitable ${link.from.iata}->${link.to.iata}")
      }
    }
  }

  /**
   * WARNING protocol - conservative operations
   */
  private def executeWarningProtocol(airline: Airline, state: FinancialState, cycle: Int): Unit = {
    println(s"📊 [${airline.name}] WARNING PROTOCOL - Balance: $$${state.balance}, being conservative")

    LogSource.insertLogs(List(Log(
      airline,
      s"WARNING: Low balance $$${state.balance}, switching to conservative mode",
      LogCategory.NEGOTIATION,
      LogSeverity.INFO,
      cycle,
      Map("emergencyLevel" -> "WARNING", "balance" -> state.balance.toString)
    )))
  }

  // ============================================================================
  // LAYER 2: PLAYER AWARENESS - Detection & Immediate Response
  // ============================================================================

  /**
   * LAYER 2: Detect and respond to player entries on bot routes
   * This runs EVERY cycle without probability gating
   */
  private def detectAndRespondToPlayerEntries(airline: Airline, personality: BotPersonality, cycle: Int): Unit = {
    val ownLinks = LinkSource.loadFlightLinksByAirlineId(airline.id)
    var playerDetections = 0
    var immediateResponses = 0

    ownLinks.foreach { link =>
      val playerCompetitors = getPlayerCompetitorsOnRoute(link.from.id, link.to.id, airline.id)

      playerCompetitors.foreach { playerLink =>
        // Record this competition in memory
        recordPlayerCompetition(airline.id, link.from.id, link.to.id, playerLink, cycle)

        val isNew = isNewPlayerEntry(airline.id, link.from.id, link.to.id, cycle)

        if (isNew) {
          playerDetections += 1
          println(s"    🎯 [PLAYER DETECTED] ${playerLink.airline.name} entered ${link.from.iata}->${link.to.iata}!")

          // Log this event for telemetry
          LogSource.insertLogs(List(Log(
            airline,
            s"Player ${playerLink.airline.name} entered route ${link.from.iata}->${link.to.iata}",
            LogCategory.NEGOTIATION,
            LogSeverity.WARN,
            cycle,
            Map(
              "event" -> "PLAYER_ENTRY",
              "playerAirline" -> playerLink.airline.name,
              "playerCapacity" -> (playerLink.capacity.total * playerLink.frequency).toString,
              "playerPrice" -> playerLink.price(ECONOMY).toString,
              "routeFrom" -> link.from.iata,
              "routeTo" -> link.to.iata
            )
          )))

          // IMMEDIATE RESPONSE based on personality
          val responded = executeImmediatePlayerResponse(airline, link, playerLink, personality, cycle)
          if (responded) immediateResponses += 1
        }
      }
    }

    // Cleanup stale tracker entries
    cleanupStaleEntries(cycle)

    if (playerDetections > 0) {
      println(s"[${airline.name}] Detected $playerDetections new player entries, responded to $immediateResponses")
    }
  }

  /**
   * Execute immediate response when player enters bot's route
   */
  private def executeImmediatePlayerResponse(
    airline: Airline,
    ownLink: Link,
    playerLink: Link,
    personality: BotPersonality,
    cycle: Int
  ): Boolean = {
    val ourPrice = ownLink.price(ECONOMY)
    val playerPrice = playerLink.price(ECONOMY)
    val playerCapacity = playerLink.capacity.total * playerLink.frequency
    val ourCapacity = ownLink.capacity.total * ownLink.frequency

    val flightCategory = Computation.getFlightCategory(ownLink.from, ownLink.to)
    val minAllowedPrice = (Pricing.computeStandardPrice(ownLink.distance, flightCategory, ECONOMY, PassengerType.TRAVELER, ownLink.from.baseIncome) * MIN_PRICE_MULTIPLIER).toInt

    // Determine response strategy based on personality
    val response: Option[(String, LinkClassValues)] = personality match {
      case BotPersonality.AGGRESSIVE =>
        // Aggressive: Immediate price war, undercut by 10%
        val newPrice = Math.max((playerPrice * 0.90).toInt, minAllowedPrice)
        if (newPrice < ourPrice) {
          Some(("⚔️ AGGRESSIVE_UNDERCUT_10%", LinkClassValues.getInstance(
            newPrice,
            (ownLink.price(BUSINESS) * 0.92).toInt,
            ownLink.price(FIRST)
          )))
        } else None

      case BotPersonality.BUDGET =>
        // Budget: Always try to be cheapest, undercut by 15%
        val newPrice = Math.max((playerPrice * 0.85).toInt, minAllowedPrice)
        if (newPrice < ourPrice) {
          Some(("💸 BUDGET_UNDERCUT_15%", LinkClassValues.getInstance(
            newPrice,
            (ownLink.price(BUSINESS) * 0.88).toInt,
            ownLink.price(FIRST)
          )))
        } else None

      case BotPersonality.PREMIUM =>
        // Premium: Ignore budget competitors, slight adjustment for quality competitors
        if (playerLink.rawQuality >= 50) {
          val newPrice = Math.min(ourPrice, (playerPrice * 1.05).toInt)
          if (newPrice != ourPrice) {
            Some(("💎 PREMIUM_QUALITY_ADJUST", LinkClassValues.getInstance(
              newPrice,
              ownLink.price(BUSINESS),
              ownLink.price(FIRST)
            )))
          } else None
        } else {
          println(s"    💎 [PREMIUM] Ignoring budget competitor ${playerLink.airline.name}")
          None
        }

      case BotPersonality.CONSERVATIVE =>
        // Conservative: Moderate response if player is large
        if (playerCapacity > ourCapacity * 1.5) {
          val newPrice = Math.max((ourPrice * 0.95).toInt, minAllowedPrice)
          Some(("🏛️ CONSERVATIVE_DEFEND", LinkClassValues.getInstance(
            newPrice,
            (ownLink.price(BUSINESS) * 0.97).toInt,
            ownLink.price(FIRST)
          )))
        } else None

      case BotPersonality.REGIONAL =>
        // Regional: If player is dominant, flag for retreat; otherwise compete
        if (playerCapacity > ourCapacity * 2) {
          println(s"    🏔️ [REGIONAL] Large player ${playerLink.airline.name} - flagging route for review")
          None
        } else {
          val newPrice = Math.max((ourPrice * 0.95).toInt, minAllowedPrice)
          Some(("🏔️ REGIONAL_COMPETE", LinkClassValues.getInstance(
            newPrice,
            ownLink.price(BUSINESS),
            ownLink.price(FIRST)
          )))
        }

      case BotPersonality.BALANCED =>
        // Balanced: Mirror player's strategy
        if (playerPrice < ourPrice) {
          val newPrice = Math.max(((ourPrice + playerPrice) / 2).toInt, minAllowedPrice)
          Some(("⚖️ BALANCED_MIRROR", LinkClassValues.getInstance(
            newPrice,
            (ownLink.price(BUSINESS) * 0.97).toInt,
            ownLink.price(FIRST)
          )))
        } else None
    }

    response match {
      case Some((strategy, newPricing)) =>
        val updatedLink = ownLink.copy(price = newPricing)
        updatedLink.setAssignedAirplanes(ownLink.getAssignedAirplanes())
        LinkSource.updateLink(updatedLink)

        incrementResponseCount(airline.id, ownLink.from.id, ownLink.to.id)

        println(s"    $strategy: ${ownLink.from.iata}->${ownLink.to.iata} $$${ourPrice} -> $$${newPricing(ECONOMY)}")
        LogSource.insertLogs(List(Log(
          airline,
          s"Responded to ${playerLink.airline.name} with $strategy",
          LogCategory.NEGOTIATION,
          LogSeverity.INFO,
          cycle,
          Map(
            "strategy" -> strategy,
            "oldPrice" -> ourPrice.toString,
            "newPrice" -> newPricing(ECONOMY).toString,
            "playerAirline" -> playerLink.airline.name,
            "route" -> s"${ownLink.from.iata}->${ownLink.to.iata}"
          )
        )))
        true

      case None =>
        false
    }
  }

  // ============================================================================
  // LAYER 3: ROUTE HEALTH - Condition-Triggered Optimization
  // ============================================================================

  /**
   * LAYER 3: Identify routes needing attention based on actual conditions
   */
  private def identifyRoutesNeedingAttention(airline: Airline, cycle: Int): List[RouteHealthStatus] = {
    val links = LinkSource.loadFlightLinksByAirlineId(airline.id)
    val consumptions = LinkSource.loadLinkConsumptionsByAirline(airline.id, 4)
    val consumptionByLink = consumptions.groupBy(_.link.id)

    links.flatMap { link =>
      val history = consumptionByLink.getOrElse(link.id, List.empty).sortBy(-_.cycle)

      if (history.isEmpty) {
        None // New route, no data yet
      } else {
        val latest = history.head
        val totalCapacity = link.capacity.total * link.frequency
        val loadFactor = if (totalCapacity > 0) latest.link.soldSeats.total.toDouble / totalCapacity else 0.0
        val profit = latest.profit

        // Calculate profit trend
        val profitTrend = if (history.size >= 2) {
          val oldProfit = history.drop(1).map(_.profit).sum.toDouble / (history.size - 1)
          if (oldProfit != 0) (profit - oldProfit) / Math.abs(oldProfit) else 0.0
        } else 0.0

        // Count competitors
        val competitors = (LinkSource.loadFlightLinksByAirports(link.from.id, link.to.id) ++
                          LinkSource.loadFlightLinksByAirports(link.to.id, link.from.id))
                          .filter(_.airline.id != airline.id)
        val hasPlayerCompetitor = competitors.exists(c => isPlayerAirline(c.airline))

        // Count unprofitable cycles
        val cyclesSinceProfit = history.takeWhile(_.profit < 0).size

        // Determine recommendation
        val recommendation = if (profit < SEVERE_LOSS_THRESHOLD || loadFactor < CRITICAL_LOAD_FACTOR) {
          "ABANDON"
        } else if (cyclesSinceProfit >= UNPROFITABLE_CYCLES_THRESHOLD) {
          "ABANDON"
        } else if (loadFactor < MIN_LOAD_FACTOR_THRESHOLD) {
          "REDUCE"
        } else if (loadFactor > 0.95 && profit > 0) {
          "EXPAND"
        } else if (profit < 0 || loadFactor < 0.6) {
          "OPTIMIZE"
        } else {
          "HOLD"
        }

        // Only return routes that need attention
        if (recommendation != "HOLD") {
          Some(RouteHealthStatus(
            link = link,
            loadFactor = loadFactor,
            profit = profit,
            profitTrend = profitTrend,
            competitorCount = competitors.size,
            hasPlayerCompetitor = hasPlayerCompetitor,
            cyclesSinceProfit = cyclesSinceProfit,
            recommendation = recommendation
          ))
        } else None
      }
    }
  }

  /**
   * LAYER 3: Optimize specific routes based on their health status
   */
  private def optimizeSpecificRoutes(
    airline: Airline,
    routes: List[RouteHealthStatus],
    personality: BotPersonality,
    cycle: Int
  ): Unit = {
    routes.foreach { status =>
      status.recommendation match {
        case "ABANDON" =>
          LinkSource.deleteLink(status.link.id)
          println(s"    ❌ [AUTO-ABANDON] ${status.link.from.iata}->${status.link.to.iata} " +
                 s"(LF: ${(status.loadFactor*100).toInt}%, profit: $$${status.profit})")
          LogSource.insertLogs(List(Log(
            airline,
            s"Auto-abandoned ${status.link.from.iata}->${status.link.to.iata}",
            LogCategory.NEGOTIATION,
            LogSeverity.WARN,
            cycle,
            Map("reason" -> status.recommendation, "loadFactor" -> status.loadFactor.toString, "profit" -> status.profit.toString)
          )))

        case "REDUCE" =>
          if (status.link.frequency > 1) {
            val newFreq = Math.max(1, status.link.frequency - 1)
            val updatedLink = status.link.copy(frequency = newFreq)
            updatedLink.setAssignedAirplanes(status.link.getAssignedAirplanes().map {
              case (ap, la) => (ap, LinkAssignment(newFreq, la.flightMinutes * newFreq / status.link.frequency))
            })
            LinkSource.updateLink(updatedLink)
            println(s"    📉 [AUTO-REDUCE] ${status.link.from.iata}->${status.link.to.iata} freq: ${status.link.frequency}->${newFreq}")
          }

        case "EXPAND" =>
          println(s"    📈 [EXPAND-CANDIDATE] ${status.link.from.iata}->${status.link.to.iata} at ${(status.loadFactor*100).toInt}% LF")

        case "OPTIMIZE" =>
          val newPricing = calculatePriceAdjustment(
            status.link,
            status.loadFactor,
            1.0,
            status.competitorCount,
            personality,
            status.profit
          )
          val updatedLink = status.link.copy(price = newPricing)
          updatedLink.setAssignedAirplanes(status.link.getAssignedAirplanes())
          LinkSource.updateLink(updatedLink)
          println(s"    🔧 [AUTO-OPTIMIZE] ${status.link.from.iata}->${status.link.to.iata} price adjusted")

        case _ => // HOLD - do nothing
      }
    }
  }
}

/**
 * Bot personality types with different strategies
 * PHASE 3: Added priceMultiplier for dynamic pricing
 */
sealed trait BotPersonality {
  def minAirportSize: Int
  def minPopulation: Long
  def targetCapacityLow: Double
  def targetCapacityHigh: Double
  def fleetBudgetRatio: Double
  def serviceQuality: Double
  def priceMultiplier: Double // Base price multiplier for this personality
  
  def scoreDestination(airport: Airport, distance: Int, fromAirport: Airport): Double
  def preferredAircraftCategory(currentFleet: List[Airplane], avgAge: Int): String
  def calculatePricing(fromAirport: Airport, toAirport: Airport, distance: Int): Map[LinkClass, Double]
  def configureLinkClasses(airplane: Airplane): Map[LinkClass, Int]
  def calculateOptimalFrequency(distance: Int, airplane: Airplane): Int
}

object BotPersonality {
  
  case object AGGRESSIVE extends BotPersonality {
    val minAirportSize = 3
    val minPopulation = 500000L
    val targetCapacityLow = 0.60
    val targetCapacityHigh = 0.90
    val fleetBudgetRatio = 0.25 // Spend 25% on fleet
    val serviceQuality = 50.0 // Moderate service
    val priceMultiplier = 0.92 // Slightly below market - competitive pricing
    
    def scoreDestination(airport: Airport, distance: Int, fromAirport: Airport): Double = {
      // Prefer larger airports, longer routes
      val sizeScore = airport.size * 15.0
      val popScore = Math.log10(airport.population) * 10.0
      val distanceScore = if (distance > 3000) 20.0 else distance / 150.0
      sizeScore + popScore + distanceScore
    }
    
    def preferredAircraftCategory(currentFleet: List[Airplane], avgAge: Int): String = {
      if (currentFleet.size < 10) "REGIONAL" else "LARGE"
    }
    
    def calculatePricing(fromAirport: Airport, toAirport: Airport, distance: Int): Map[LinkClass, Double] = {
      // Competitive pricing - 10% below standard
      val flightCategory = Computation.getFlightCategory(fromAirport, toAirport)
      val baseIncome = fromAirport.baseIncome
      Map(
        ECONOMY -> (Pricing.computeStandardPrice(distance, flightCategory, ECONOMY, PassengerType.TRAVELER, baseIncome) * 0.90),
        BUSINESS -> (Pricing.computeStandardPrice(distance, flightCategory, BUSINESS, PassengerType.TRAVELER, baseIncome) * 0.90),
        FIRST -> (Pricing.computeStandardPrice(distance, flightCategory, FIRST, PassengerType.TRAVELER, baseIncome) * 0.90)
      )
    }
    
    def configureLinkClasses(airplane: Airplane): Map[LinkClass, Int] = {
      // Growth-focused: 80% economy, 15% business, 5% first
      val totalSeats = airplane.model.capacity
      Map(
        ECONOMY -> (totalSeats * 0.80).toInt,
        BUSINESS -> (totalSeats * 0.15).toInt,
        FIRST -> (totalSeats * 0.05).toInt
      )
    }
    
    def calculateOptimalFrequency(distance: Int, airplane: Airplane): Int = {
      // High frequency for competitive edge
      val baseFrequency = (distance / 500.0).toInt + 2
      Math.min(baseFrequency, 14) // Cap at 14 weekly flights (2 per day)
    }
  }
  
  case object CONSERVATIVE extends BotPersonality {
    val minAirportSize = 5
    val minPopulation = 2000000L
    val targetCapacityLow = 0.75
    val targetCapacityHigh = 0.95
    val fleetBudgetRatio = 0.15
    val serviceQuality = 65.0 // Good service
    val priceMultiplier = 1.12 // Above market - premium for reliability
    
    def scoreDestination(airport: Airport, distance: Int, fromAirport: Airport): Double = {
      // Prefer major hubs, established routes
      val sizeScore = airport.size * 25.0
      val popScore = Math.log10(airport.population) * 15.0
      val distanceScore = if (distance < 5000 && distance > 1000) 15.0 else 5.0
      sizeScore + popScore + distanceScore
    }
    
    def preferredAircraftCategory(currentFleet: List[Airplane], avgAge: Int): String = {
      if (avgAge > 15) "MEDIUM" else "LARGE" // Replace aging fleet first
    }
    
    def calculatePricing(fromAirport: Airport, toAirport: Airport, distance: Int): Map[LinkClass, Double] = {
      // Premium pricing - 15% above standard
      val flightCategory = Computation.getFlightCategory(fromAirport, toAirport)
      val baseIncome = fromAirport.baseIncome
      Map(
        ECONOMY -> (Pricing.computeStandardPrice(distance, flightCategory, ECONOMY, PassengerType.TRAVELER, baseIncome) * 1.15),
        BUSINESS -> (Pricing.computeStandardPrice(distance, flightCategory, BUSINESS, PassengerType.TRAVELER, baseIncome) * 1.15),
        FIRST -> (Pricing.computeStandardPrice(distance, flightCategory, FIRST, PassengerType.TRAVELER, baseIncome) * 1.15)
      )
    }
    
    def configureLinkClasses(airplane: Airplane): Map[LinkClass, Int] = {
      // Traditional: 70% economy, 20% business, 10% first
      val totalSeats = airplane.model.capacity
      Map(
        ECONOMY -> (totalSeats * 0.70).toInt,
        BUSINESS -> (totalSeats * 0.20).toInt,
        FIRST -> (totalSeats * 0.10).toInt
      )
    }
    
    def calculateOptimalFrequency(distance: Int, airplane: Airplane): Int = {
      // Steady frequency
      val baseFrequency = (distance / 700.0).toInt + 1
      Math.min(baseFrequency, 10) // Cap at 10 weekly flights
    }
  }
  
  case object BALANCED extends BotPersonality {
    val minAirportSize = 4
    val minPopulation = 1000000L
    val targetCapacityLow = 0.70
    val targetCapacityHigh = 0.88
    val fleetBudgetRatio = 0.18
    val serviceQuality = 55.0 // Standard service
    val priceMultiplier = 1.0 // Market rate
    
    def scoreDestination(airport: Airport, distance: Int, fromAirport: Airport): Double = {
      val sizeScore = airport.size * 18.0
      val popScore = Math.log10(airport.population) * 12.0
      val distanceScore = distance / 200.0
      sizeScore + popScore + distanceScore
    }
    
    def preferredAircraftCategory(currentFleet: List[Airplane], avgAge: Int): String = {
      if (currentFleet.size % 3 == 0) "LARGE" else "MEDIUM"
    }
    
    def calculatePricing(fromAirport: Airport, toAirport: Airport, distance: Int): Map[LinkClass, Double] = {
      // Market rate pricing
      val flightCategory = Computation.getFlightCategory(fromAirport, toAirport)
      val baseIncome = fromAirport.baseIncome
      Map(
        ECONOMY -> Pricing.computeStandardPrice(distance, flightCategory, ECONOMY, PassengerType.TRAVELER, baseIncome).toDouble,
        BUSINESS -> Pricing.computeStandardPrice(distance, flightCategory, BUSINESS, PassengerType.TRAVELER, baseIncome).toDouble,
        FIRST -> Pricing.computeStandardPrice(distance, flightCategory, FIRST, PassengerType.TRAVELER, baseIncome).toDouble
      )
    }
    
    def configureLinkClasses(airplane: Airplane): Map[LinkClass, Int] = {
      // Standard: 75% economy, 20% business, 5% first
      val totalSeats = airplane.model.capacity
      Map(
        ECONOMY -> (totalSeats * 0.75).toInt,
        BUSINESS -> (totalSeats * 0.20).toInt,
        FIRST -> (totalSeats * 0.05).toInt
      )
    }
    
    def calculateOptimalFrequency(distance: Int, airplane: Airplane): Int = {
      // Moderate frequency
      val baseFrequency = (distance / 600.0).toInt + 1
      Math.min(baseFrequency, 12) // Cap at 12 weekly flights
    }
  }
  
  case object REGIONAL extends BotPersonality {
    val minAirportSize = 2
    val minPopulation = 100000L
    val targetCapacityLow = 0.65
    val targetCapacityHigh = 0.85
    val fleetBudgetRatio = 0.20
    val serviceQuality = 45.0 // Basic service
    val priceMultiplier = 0.95 // Slightly below market
    
    def scoreDestination(airport: Airport, distance: Int, fromAirport: Airport): Double = {
      // Prefer smaller airports, shorter routes
      val sizeScore = if (airport.size <= 4) airport.size * 25.0 else airport.size * 5.0
      val popScore = Math.log10(airport.population) * 8.0
      val distanceScore = if (distance < 2000) 25.0 else 2000.0 / distance
      val sameCountry = if (airport.countryCode == fromAirport.countryCode) 30.0 else 0.0
      sizeScore + popScore + distanceScore + sameCountry
    }
    
    def preferredAircraftCategory(currentFleet: List[Airplane], avgAge: Int): String = {
      "REGIONAL"
    }
    
    def calculatePricing(fromAirport: Airport, toAirport: Airport, distance: Int): Map[LinkClass, Double] = {
      // Regional pricing - slightly below market
      val flightCategory = Computation.getFlightCategory(fromAirport, toAirport)
      val baseIncome = fromAirport.baseIncome
      Map(
        ECONOMY -> (Pricing.computeStandardPrice(distance, flightCategory, ECONOMY, PassengerType.TRAVELER, baseIncome) * 0.95),
        BUSINESS -> (Pricing.computeStandardPrice(distance, flightCategory, BUSINESS, PassengerType.TRAVELER, baseIncome) * 0.95),
        FIRST -> (Pricing.computeStandardPrice(distance, flightCategory, FIRST, PassengerType.TRAVELER, baseIncome) * 0.95)
      )
    }
    
    def configureLinkClasses(airplane: Airplane): Map[LinkClass, Int] = {
      // Efficient: 85% economy, 15% business, no first class
      val totalSeats = airplane.model.capacity
      Map(
        ECONOMY -> (totalSeats * 0.85).toInt,
        BUSINESS -> (totalSeats * 0.15).toInt,
        FIRST -> 0
      )
    }
    
    def calculateOptimalFrequency(distance: Int, airplane: Airplane): Int = {
      // High frequency on short routes
      val baseFrequency = if (distance < 1000) {
        (1000.0 / distance).toInt + 2
      } else {
        (distance / 800.0).toInt + 1
      }
      Math.min(baseFrequency, 21) // Cap at 21 weekly flights (3 per day)
    }
  }
  
  case object PREMIUM extends BotPersonality {
    val minAirportSize = 6
    val minPopulation = 5000000L
    val targetCapacityLow = 0.70
    val targetCapacityHigh = 0.85 // Lower utilization OK for premium
    val fleetBudgetRatio = 0.22
    val serviceQuality = 80.0 // Excellent service
    val priceMultiplier = 1.35 // Premium pricing - well above market
    
    def scoreDestination(airport: Airport, distance: Int, fromAirport: Airport): Double = {
      // Prefer major hubs, long-haul premium routes
      val sizeScore = airport.size * 30.0
      val popScore = Math.log10(airport.population) * 18.0
      val distanceScore = if (distance > 5000) 40.0 else distance / 125.0
      val income = if (airport.incomeLevel >= 50) 25.0 else 0.0
      sizeScore + popScore + distanceScore + income
    }
    
    def preferredAircraftCategory(currentFleet: List[Airplane], avgAge: Int): String = {
      // Always prefer large aircraft for premium service
      "LARGE"
    }
    
    def calculatePricing(fromAirport: Airport, toAirport: Airport, distance: Int): Map[LinkClass, Double] = {
      // Premium pricing - significantly above market
      val flightCategory = Computation.getFlightCategory(fromAirport, toAirport)
      val baseIncome = fromAirport.baseIncome
      Map(
        ECONOMY -> (Pricing.computeStandardPrice(distance, flightCategory, ECONOMY, PassengerType.TRAVELER, baseIncome) * 1.20),
        BUSINESS -> (Pricing.computeStandardPrice(distance, flightCategory, BUSINESS, PassengerType.TRAVELER, baseIncome) * 1.40),
        FIRST -> (Pricing.computeStandardPrice(distance, flightCategory, FIRST, PassengerType.TRAVELER, baseIncome) * 1.60)
      )
    }
    
    def configureLinkClasses(airplane: Airplane): Map[LinkClass, Int] = {
      // Luxury mix: 50% economy, 30% business, 20% first
      val totalSeats = airplane.model.capacity
      Map(
        ECONOMY -> (totalSeats * 0.50).toInt,
        BUSINESS -> (totalSeats * 0.30).toInt,
        FIRST -> (totalSeats * 0.20).toInt
      )
    }
    
    def calculateOptimalFrequency(distance: Int, airplane: Airplane): Int = {
      // Moderate frequency - focus on quality over quantity
      val baseFrequency = (distance / 800.0).toInt + 1
      Math.min(baseFrequency, 7) // Cap at 7 weekly flights (1 per day)
    }
  }
  
  case object BUDGET extends BotPersonality {
    val minAirportSize = 4
    val minPopulation = 1000000L
    val targetCapacityLow = 0.80 // High utilization required
    val targetCapacityHigh = 0.98
    val fleetBudgetRatio = 0.12 // Low fleet spending
    val serviceQuality = 30.0 // Basic service
    val priceMultiplier = 0.72 // Deep discount pricing
    
    def scoreDestination(airport: Airport, distance: Int, fromAirport: Airport): Double = {
      // Prefer high-demand, short-haul routes
      val sizeScore = airport.size * 20.0
      val popScore = Math.log10(airport.population) * 15.0
      val distanceScore = if (distance < 3000) 30.0 else 3000.0 / distance
      sizeScore + popScore + distanceScore
    }
    
    def preferredAircraftCategory(currentFleet: List[Airplane], avgAge: Int): String = {
      "SMALL" // Efficient single-aisle aircraft
    }
    
    def calculatePricing(fromAirport: Airport, toAirport: Airport, distance: Int): Map[LinkClass, Double] = {
      // Budget pricing - significantly below market
      val flightCategory = Computation.getFlightCategory(fromAirport, toAirport)
      val baseIncome = fromAirport.baseIncome
      Map(
        ECONOMY -> (Pricing.computeStandardPrice(distance, flightCategory, ECONOMY, PassengerType.TRAVELER, baseIncome) * 0.70),
        BUSINESS -> (Pricing.computeStandardPrice(distance, flightCategory, BUSINESS, PassengerType.TRAVELER, baseIncome) * 0.80),
        FIRST -> (Pricing.computeStandardPrice(distance, flightCategory, FIRST, PassengerType.TRAVELER, baseIncome) * 0.80)
      )
    }
    
    def configureLinkClasses(airplane: Airplane): Map[LinkClass, Int] = {
      // All economy: 100% economy, no business or first class
      val totalSeats = airplane.model.capacity
      Map(
        ECONOMY -> totalSeats,
        BUSINESS -> 0,
        FIRST -> 0
      )
    }
    
    def calculateOptimalFrequency(distance: Int, airplane: Airplane): Int = {
      // Very high frequency - maximize utilization
      val baseFrequency = (distance / 400.0).toInt + 3
      Math.min(baseFrequency, 21) // Cap at 21 weekly flights (3 per day)
    }
  }
}
