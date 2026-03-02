package com.patson

import scala.collection.mutable.ListBuffer
import com.patson.data._
import com.patson.data.airplane.ModelSource
import com.patson.model._
import com.patson.model.airplane._
import com.patson.util.AirlineCache

import scala.collection.mutable
import scala.util.Random


object AirplaneSimulation {
  def airplaneSimulation(cycle: Int) : List[Airplane] = {
    println("starting airplane simulation")
    println("loading all airplanes")
    //do 2nd hand market adjustment
    secondHandAirplaneSimulate(cycle)
    
    //do decay
    val allAirplanes = AirplaneSource.loadAirplanesCriteria(List.empty)
    val linkAssignments: Map[Int, LinkAssignments] = AirplaneSource.loadAirplaneLinkAssignmentsByCriteria(List.empty)
    
    println("finished loading all airplanes")
    
    val updatingAirplanesListBuffer = ListBuffer[Airplane]()
    allAirplanes.groupBy { _.owner }.foreach {
      case (owner, airplanes) => {
        AirlineCache.getAirline(owner.id, true) match {
          case Some(airline) =>
            val readyAirplanes = airplanes.filter(_.isReady)
            val readyAirplanesWithAssignedLinks : Map[Airplane, LinkAssignments] = readyAirplanes.map { airplane =>
              (airplane, linkAssignments.getOrElse(airplane.id, LinkAssignments(Map.empty)))
            }.toMap
            updatingAirplanesListBuffer ++= decayAirplanesByAirline(readyAirplanesWithAssignedLinks, airline)
          case None => println("airline " + owner.id + " has airplanes but the airline cannot be loaded!")//invalid airline?
        }
      }
    }
    
    var updatingAirplanes = updatingAirplanesListBuffer.toList 
    val updatedAirplanes = AirplaneSource.updateAirplanesDetails(updatingAirplanes, true) //version check to avoid manual renewal from web UI
    println(s"Finished updating all airplanes expected ${updatingAirplanes.size} actual ${updatedAirplanes.size}")
    
    println("Start renewing airplanes")
    renewAirplanes(updatedAirplanes, cycle)
    println("Finished renewing airplanes")
    
    println("Start retiring airplanes")
    //adjustLinksBasedOnAirplaneStatus(updatingAirplanes, cycle)
    val retiredCount = retireAgingAirplanes(updatedAirplanes)
    println(s"Finished retiring $retiredCount airplanes")

    updatedAirplanes.toList
  }

  val SECOND_HAND_MAX_AIRPLANE_PER_MODEL_COUNT = 30
  def secondHandAirplaneSimulate(cycle : Int) = {
    val secondHandAirplanes = AirplaneSource.loadAirplanesCriteria(List(("is_sold", true)))
    val secondHandAirplanesByModelId = secondHandAirplanes.groupBy(_.model.id)

    val allRemovingAirplanes = ListBuffer[Airplane]()
    val allKeepingAirplanes = ListBuffer[Airplane]()

    secondHandAirplanesByModelId.foreach {
      case(modelId, airplanesByModelId) =>
        var keepingByModel = ListBuffer[Airplane]()
        airplanesByModelId.foreach { airplane =>
          if (cycle - airplane.purchasedCycle <= Airplane.MAX_DEALER_WEEKS) {
            keepingByModel.append(airplane)
          } else {
            allRemovingAirplanes.append(airplane)
          }
        }
        if (keepingByModel.length > SECOND_HAND_MAX_AIRPLANE_PER_MODEL_COUNT) {
          val removalCount = keepingByModel.length - SECOND_HAND_MAX_AIRPLANE_PER_MODEL_COUNT
          keepingByModel = Random.shuffle(keepingByModel)
          allRemovingAirplanes.appendAll(keepingByModel.take(removalCount))
          keepingByModel = keepingByModel.drop(removalCount)
        }
        allKeepingAirplanes.appendAll(keepingByModel)
    }

    allRemovingAirplanes.foreach { airplane =>
      AirplaneSource.deleteAirplanesByCriteria(List(("a.id", airplane.id), ("is_sold", true))) //need to be careful here, make sure it is still in 2nd hand market
    }
  }
  
  
  def renewAirplanes(airplanes : List[Airplane], currentCycle : Int) = {
    val renewalThresholdsByAirline : scala.collection.immutable.Map[Int, Int] = AirlineSource.loadAirplaneRenewals()
    val costsByAirline : scala.collection.mutable.Map[Int, (Long, Long, Long)] = mutable.HashMap[Int, (Long, Long, Long)]()
    val airlinesByid = AirlineSource.loadAllAirlines(false).map(airline => (airline.id, airline)).toMap
    val renewedAirplanes : ListBuffer[Airplane] = ListBuffer[Airplane]()
    val secondHandAirplanes  = ListBuffer[Airplane]()
    val fundsExhaustedAirlineIds = mutable.HashSet[Int]()

    val updatingAirplanes = airplanes //this contains airplanes from all airlines
        .sortBy(_.condition) //lowest conditional airplane gets renewal first
        .map { airplane =>
      renewalThresholdsByAirline.get(airplane.owner.id) match {
        case Some(threshold) =>
          if (!fundsExhaustedAirlineIds.contains(airplane.owner.id)
            && airplane.condition < threshold
            && airplane.purchasedCycle <= currentCycle - airplane.model.constructionTime) { //only renew airplane if it has been purchased longer than the construction time required
             val airlineId = airplane.owner.id
             val (existingCost, existingBuyPlane, existingSellPlane) : (Long, Long, Long) = costsByAirline.getOrElse(airlineId, (0, 0, 0))
             val sellValue = Computation.calculateAirplaneSellValue(airplane)

             val originalModel = airplane.model
             val adjustedModel = originalModel.applyDiscount(ModelDiscount.getCombinedDiscountsByModelId(airlineId, originalModel.id))
             val renewCost = adjustedModel.price - sellValue
             val newCost = existingCost + renewCost
             val newBuyPlane = existingBuyPlane + adjustedModel.price
             val newSellPlane = existingSellPlane + sellValue

             if ((newCost <= airlinesByid(airplane.owner.id).getBalance()) && (airlinesByid(airplane.owner.id).getBalance() - newCost >= airlinesByid(airplane.owner.id).getMinimumRenewalBalance())) {
               costsByAirline.put(airlineId, (newCost, newBuyPlane, newSellPlane))
               if (airplane.condition >= Airplane.BAD_CONDITION) { //create a clone as the sold airplane
                 secondHandAirplanes.append(airplane.copy(isSold = true, purchasedCycle = currentCycle, configuration = AirplaneConfiguration.empty, id = 0))
               }
               val renewedAirplane = airplane.copy(constructedCycle = currentCycle, purchasedCycle = currentCycle, condition = Airplane.MAX_CONDITION, purchasePrice = adjustedModel.price)
               renewedAirplanes.append(renewedAirplane)
               renewedAirplane
             } else { //not enough fund
               fundsExhaustedAirlineIds.add(airplane.owner.id)
               airplane
             }
          } else {
            airplane
          }
        case None => airplane
      }
    }

    //now deduct money and record ledger entries
    costsByAirline.foreach {
      case(airlineId, (cost, buyAirplane, sellAirplane)) => {
        AirlineSource.saveLedgerEntries(List(
          AirlineLedgerEntry(airlineId, currentCycle, LedgerType.SELL_AIRPLANE, sellAirplane, Some("Airplane Renewal, buying new aircraft")),
          AirlineLedgerEntry(airlineId, currentCycle, LedgerType.BUY_AIRPLANE, -1 * buyAirplane, Some("Airplane Renewal, selling old aircraft"))
        ))
      }
    }
    //save the 2nd hand airplanes
    AirplaneSource.saveAirplanes(secondHandAirplanes.toList)
    //save the renewed airplanes
    AirplaneSource.updateAirplanes(renewedAirplanes.toList) //no version check, since money is deducted already, and renewed airplanes are safe to save
  }

  def retireAgingAirplanes(airplanes : List[Airplane]) : Int = {
    var deletedCount = 0
    airplanes.filter(_.condition <= 0).foreach { airplane =>
      println("Deleting airplane " + airplane)
      deletedCount += AirplaneSource.deleteAirplane(airplane.id, Some(airplane.version))
    }
    deletedCount
  }
  
  def decayAirplanesByAirline(airplanesWithAssignedLink : Map[Airplane, LinkAssignments], owner : Airline) : List[Airplane] = {
    val updatingAirplanes = ListBuffer[Airplane]()

    airplanesWithAssignedLink.foreach {
      case(airplane, linkAssignments) =>
        val baseDecayRate = Airplane.MAX_CONDITION.toDouble / airplane.model.lifespan //live the whole lifespan
        val decayRate = baseDecayRate / 3 + baseDecayRate * (2.0 / 3) * airplane.utilizationRate
        val newCondition = airplane.condition - decayRate

        updatingAirplanes.append(airplane.copy(condition = newCondition))
    }
    updatingAirplanes.toList
  }
}
