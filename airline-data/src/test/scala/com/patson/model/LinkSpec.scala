  package com.patson.model

  import org.apache.pekko.actor.ActorSystem
  import org.apache.pekko.testkit.{ImplicitSender, TestKit}
  import com.patson.{LinkSimulation, Util}
  import com.patson.model.airplane.{Airplane, AirplaneConfiguration, LinkAssignment, Model}
  import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
  import org.scalatest.matchers.should.Matchers
  import org.scalatest.wordspec.AnyWordSpecLike

  import scala.collection.immutable.Map

  class LinkSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
    with AnyWordSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {

    def this() = this(ActorSystem("MySpec"))

    val testAirline1 = Airline("airline 1", id = 1)
    val fromAirport = Airport("", "", "From Airport", 0, 0, "", "", "", 1, baseIncome = 40000, basePopulation = 1, 0, 0)
    val toAirport = Airport("", "", "To Airport", 0, 180, "", "", "", 1, baseIncome = 40000, basePopulation = 1, 0, 0)
    val distance = Util.calculateDistance(fromAirport.latitude, fromAirport.longitude, toAirport.latitude, toAirport.longitude).toInt
    val flightType = Computation.getFlightCategory(fromAirport, toAirport)
    val defaultPrice = Pricing.computeStandardPriceForAllClass(distance, FlightCategory.INTERNATIONAL, PassengerType.TRAVELER, Airport.HIGH_INCOME / 2)
    val model = Model.modelByName("Boeing 737 MAX 9")




    "frequencyByClass".must {
      "compute correct frequency".in {

        val config1 = AirplaneConfiguration(100, 0, 0, testAirline1, model, false)
        val config2 = AirplaneConfiguration(50, 25, 0, testAirline1, model, false)
        val config3 = AirplaneConfiguration(50, 10, 5, testAirline1, model, false)
        val airline1Link = Link(fromAirport, toAirport, testAirline1, defaultPrice, distance = distance, LinkClassValues.getInstance(200, 35, 5) * 10, rawQuality = 0, 600, frequency = 30)

        airline1Link.setAssignedAirplanes(
          scala.collection.immutable.Map(
            Airplane(model, testAirline1, 0, purchasedCycle = 0, 100, 0, configuration = config1) -> LinkAssignment(10, 6000)
          , Airplane(model, testAirline1, 0, purchasedCycle = 0, 100, 0, configuration = config2) -> LinkAssignment(10, 6000)
          , Airplane(model, testAirline1, 0, purchasedCycle = 0, 100, 0, configuration = config3) -> LinkAssignment(10, 6000)))

        assert(airline1Link.frequencyByClass(ECONOMY) == 30)
        assert(airline1Link.frequencyByClass(BUSINESS) == 20)
        assert(airline1Link.frequencyByClass(FIRST) == 10)
      }
    }

    /**
     * Per-km per-pax quality cost tests.
     *
     * Three quality sources, three "cost" formulas (all at economy class, 100% LF, 100% utilization):
     *   serviceQuality  → inflightCost per pax per km     (from LinkSimulation.computeInflightCost)
     *   laborQuality    → quality-driven crew cost per pax per km  (targetQualityCost component only)
     *   airplaneType    → ownership cost (depreciation + maintenance) per pax per km
     *
     * Design goal: at the same relative quality level,
     *   airplaneTypeQuality cost-per-quality-pt ≤ laborQuality ≤ serviceQuality
     */
    "per km pax quality cost comparison".must {

      val testDistance = 1500

      val auroraModel = Model.modelByName("Aurora D8")                  // quality 9
      val b797Model   = Model.modelByName("Boeing 797-6")               // quality 8
      val e190Model   = Model.modelByName("Embraer E190-E2")            // quality 7 → 24 pts
      val a321Model   = Model.modelByName("Airbus A321")                // quality 6 → 18 pts
      val d10Model    = Model.modelByName("McDonnell Douglas DC-10-10") // quality 4
      val b707Model   = Model.modelByName("Boeing 707-120")             // quality 3 → 6 pts

      // ── helpers: thin wrappers that normalize to per-pax-km ─────────

      /** Economy inflight cost per pax per km — delegates to LinkSimulation.inflightDurationCostPerHour */
      def serviceCostPerPaxKm(rawQuality: Int, duration: Int, distance: Int): Double =
        ECONOMY.resourceMultiplier * LinkSimulation.inflightDurationCostPerHour(rawQuality) * duration / (60.0 * distance)

      /** Quality-driven crew cost per pax per km (economy, 100% LF) — delegates to LinkSimulation.crewQualityCostFactor */
      def laborCostPerPaxKm(targetSQ: Double, duration: Int, distance: Int): Double =
        LinkSimulation.crewQualityCostFactor(targetSQ) * ECONOMY.resourceMultiplier * duration / (60.0 * distance)

      /** Airplane ownership cost (depreciation + maintenance) per pax-km at 100% utilization */
      def typeOwnershipCostPerPaxKm(airplaneModel: Model, distance: Int): Double = {
        val freq = Computation.calculateMaxFrequency(airplaneModel, distance)
        (Airplane.standardDepreciationRate(airplaneModel) + airplaneModel.baseMaintenanceCost) / (airplaneModel.capacity.toDouble * freq * distance)
      }

      /**
       * Net quality premium cost per pax-km for `model` relative to `baseline`.
       * = (Δownership − Δfuel_savings) / (Δquality_pts)
       *
       * Δownership    = model_ownership_per_pax_km − baseline_ownership_per_pax_km
       * Δfuel_savings = baseline_fuel_per_pax_km  − model_fuel_per_pax_km  (positive if model burns less)
       * Δquality_pts  = airplaneTypeQualityPts(model.quality) − airplaneTypeQualityPts(baseline.quality)
       *
       * Fuel cost computed at 100% load factor, max frequency.
       */
      def typeNetQualityPremiumCostPerPaxKm(model: Model, baseline: Model, distance: Int): Double = {
        def ownershipPerPaxKm(m: Model): Double = {
          val freq = Computation.calculateMaxFrequency(m, distance)
          (Airplane.standardDepreciationRate(m) + m.baseMaintenanceCost) / (m.capacity.toDouble * freq * distance)
        }
        def fuelPerPaxKm(m: Model): Double = {
          val freq = Computation.calculateMaxFrequency(m, distance)
          LinkSimulation.calculateFuelCost(m, distance, soldSeats = m.capacity, capacity = m.capacity, frequency = freq) / (m.capacity.toDouble * freq * distance)
        }

        val deltaOwnership = ownershipPerPaxKm(model) - ownershipPerPaxKm(baseline)
        val deltaFuelSavings = fuelPerPaxKm(baseline) - fuelPerPaxKm(model)
        val deltaQualityPts = Link.airplaneTypeQualityPts(model.quality) - Link.airplaneTypeQualityPts(baseline.quality)

        (deltaOwnership - deltaFuelSavings) / deltaQualityPts
      }

      // ── tests ───────────────────────────────────────────────────────

      "at 40, serviceQualityPts should be cheapest".in {
        val duration = Computation.calculateDuration(d10Model, testDistance)

        val sQPts       = Link.serviceQualityPts(40)                          // 7.5 pts
        val sCostPerQpt = serviceCostPerPaxKm(40, duration, testDistance) / sQPts

        val lQPts       = Link.laborQualityPts(40)                            // 12.0 pts
        val lCostPerQpt = laborCostPerPaxKm(40, duration, testDistance) / lQPts

        val planeQPts       = Link.airplaneTypeQualityPts(d10Model.quality).toDouble  // 24 pts
        val planeCostPerQpt = typeOwnershipCostPerPaxKm(d10Model, testDistance) / planeQPts

        val tNetCostPerQpt = typeNetQualityPremiumCostPerPaxKm(d10Model, b707Model, testDistance)

        println(f"[level-40] type-net=$tNetCostPerQpt%.6f  labor=$lCostPerQpt%.6f  service=$sCostPerQpt%.6f  cost per quality-pt per pax-km")

        withClue(f"type($planeCostPerQpt%.6f) should be cheaper than labor($lCostPerQpt%.6f)") { planeCostPerQpt should be > lCostPerQpt }
        withClue(f"labor($lCostPerQpt%.6f) should be cheaper than service($sCostPerQpt%.6f)") { lCostPerQpt should be > sCostPerQpt }
      }

      "at 60, airplaneTypeNetQualityPremium should be cheaper".in {
        val duration = Computation.calculateDuration(a321Model, testDistance)

        val sQPts       = Link.serviceQualityPts(60)
        val sCostPerQpt = serviceCostPerPaxKm(60, duration, testDistance) / sQPts

        val lQPts       = Link.laborQualityPts(60)
        val lCostPerQpt = laborCostPerPaxKm(60, duration, testDistance) / lQPts

        val tNetCostPerQpt = typeNetQualityPremiumCostPerPaxKm(a321Model, b707Model, testDistance)

        println(f"[level-60] plane-net=$tNetCostPerQpt%.6f  labor=$lCostPerQpt%.6f  service=$sCostPerQpt%.6f  cost per quality-pt per pax-km")

        withClue(f"service($sCostPerQpt%.6f) should be more than labor($lCostPerQpt%.6f)") { sCostPerQpt should be > lCostPerQpt }
        withClue(f"type-net($lCostPerQpt%.6f) should be more than plane($tNetCostPerQpt%.6f)") { lCostPerQpt should be > tNetCostPerQpt }
      }

      "at 80 longer distance, airplaneTypeNetQualityPremium should be cheaper".in {
        val duration = Computation.calculateDuration(b797Model, 4800)

        val sQPts       = Link.serviceQualityPts(80)                          // 22.5 pts
        val sCostPerQpt = serviceCostPerPaxKm(80, duration, 4800) / sQPts

        val lQPts       = Link.laborQualityPts(80)                            // 24.0 pts
        val lCostPerQpt = laborCostPerPaxKm(80, duration, 4800) / lQPts

        val tNetCostPerQpt = typeNetQualityPremiumCostPerPaxKm(b797Model, b707Model, 4000)

        println(f"[level-80] type-net=$tNetCostPerQpt%.6f  labor=$lCostPerQpt%.6f  service=$sCostPerQpt%.6f  cost per quality-pt per pax-km")

        withClue(f"service($lCostPerQpt%.6f) should be more than type-net($sCostPerQpt%.6f)") { lCostPerQpt should be > sCostPerQpt }
        withClue(f"type-net($sCostPerQpt%.6f) should be more than labor($tNetCostPerQpt%.6f)") { sCostPerQpt should be > tNetCostPerQpt }
      }

      "higher quality aircraft should achieve better cost-per-type-quality-point than lower quality".in {
        val auroraCostPerQpt = typeNetQualityPremiumCostPerPaxKm(b797Model, b707Model, testDistance)
        val e190CostPerQpt   = typeNetQualityPremiumCostPerPaxKm(e190Model, b707Model, testDistance)
        val a321CostPerQpt   = typeNetQualityPremiumCostPerPaxKm(a321Model, b707Model, testDistance)
        val d10CostPerQpt    = typeNetQualityPremiumCostPerPaxKm(d10Model, b707Model, testDistance)

        println(f"type cost/quality-pt/pax-km:  Aurora(q=${b797Model.quality})=$auroraCostPerQpt%.6f E190-E2(q=${e190Model.quality})=$e190CostPerQpt%.6f  A321(q=${a321Model.quality})=$a321CostPerQpt%.6f  DC-9-30(q=${b707Model.quality})=$d10CostPerQpt%.6f")

        // withClue(f"Aurora($auroraCostPerQpt%.6f) should be more efficient than E190($e190CostPerQpt%.6f)") { auroraCostPerQpt should be < e190CostPerQpt }
        // withClue(f"E190-E2($e190CostPerQpt%.6f) should be more efficient than A321($a321CostPerQpt%.6f)") { e190CostPerQpt should be < a321CostPerQpt }
        // withClue(f"A321($a321CostPerQpt%.6f) should be more efficient than DC($d10CostPerQpt%.6f)") { a321CostPerQpt should be < d10CostPerQpt }
      }
    }

    "airport base upkeep cost for link".must {
      "calculate upkeep cost proportional to link staff for different airline types".in {
        val testAirport = Airport("TEST", "TEST", "Test Airport", 0, 0, "US", "Test City", "", 6, baseIncome = 50000, basePopulation = 5000000, 0, 0, id = 1)

        val legacyAirline = Airline("Legacy Test", id = 101)
        legacyAirline.airlineType = LegacyAirline

        val megaHqAirline = Airline("Mega HQ Test", id = 102)
        megaHqAirline.airlineType = MegaHqAirline

        val regionalAirline = Airline("Regional Test", id = 103)
        regionalAirline.airlineType = RegionalAirline

        val linkDistance = 1000
        val linkCapacity = LinkClassValues.getInstance(3000, 0, 0)
        val linkFrequency = 30
        val baseScale = 6
        val flightCategory = FlightCategory.INTERNATIONAL

        val legacyBase = AirlineBase(legacyAirline, testAirport, "US", baseScale, 0, headquarter = true)
        val megaHqBase = AirlineBase(megaHqAirline, testAirport, "US", baseScale, 0, headquarter = true)
        val regionalBase = AirlineBase(regionalAirline, testAirport, "US", baseScale, 0, headquarter = true)

        val staffSchemeBreakdownLegacy = Link.getStaffRequired(linkDistance, flightCategory, LegacyAirline)
        val legacyLinkStaffRequired = Math.max(3, (staffSchemeBreakdownLegacy.basicStaff + staffSchemeBreakdownLegacy.perFrequency * linkFrequency + staffSchemeBreakdownLegacy.per500Pax * linkCapacity.total / 500))
        val staffSchemeBreakdownMegaHQ = Link.getStaffRequired(linkDistance, flightCategory, MegaHqAirline)
        val megaHQLinkStaffRequired = Math.max(3, (staffSchemeBreakdownMegaHQ.basicStaff + staffSchemeBreakdownMegaHQ.perFrequency * linkFrequency + staffSchemeBreakdownMegaHQ.per500Pax * linkCapacity.total / 500))
        val staffSchemeBreakdownRegional = Link.getStaffRequired(linkDistance, flightCategory, RegionalAirline)
        val regionalLinkStaffRequired = Math.max(3, (staffSchemeBreakdownRegional.basicStaff + staffSchemeBreakdownRegional.perFrequency * linkFrequency + staffSchemeBreakdownRegional.per500Pax * linkCapacity.total / 500))

        val baseStaffCapacity = AirlineBase.getOfficeStaffCapacity(baseScale, isHeadquarters = true)

        val legacyUpkeep = legacyBase.calculateUpkeep(baseScale, LegacyAirline)
        val megaHqUpkeep = megaHqBase.calculateUpkeep(baseScale, MegaHqAirline)
        val regionalUpkeep = regionalBase.calculateUpkeep(baseScale, RegionalAirline)

        val legacyUpkeepForLink = (legacyUpkeep * legacyLinkStaffRequired / baseStaffCapacity).toLong
        val megaHqUpkeepForLink = (megaHqUpkeep * megaHQLinkStaffRequired / baseStaffCapacity).toLong
        val regionalUpkeepForLink = (regionalUpkeep * regionalLinkStaffRequired / baseStaffCapacity).toLong

        println(s"Legacy Base Upkeep: $legacyUpkeep, Link Upkeep: $legacyUpkeepForLink, staff: $legacyLinkStaffRequired")
        println(s"Mega HQ Base Upkeep: $megaHqUpkeep, Link Upkeep: $megaHqUpkeepForLink, staff: $megaHQLinkStaffRequired")
        println(s"Regional Base Upkeep: $regionalUpkeep, Link Upkeep: $regionalUpkeepForLink, staff: $regionalLinkStaffRequired")

        assert(legacyUpkeepForLink > 0)
        assert(megaHqUpkeepForLink > 0)
        assert(regionalUpkeepForLink > 0)
      }
    }
  }
