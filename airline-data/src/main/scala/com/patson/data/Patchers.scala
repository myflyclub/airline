package com.patson.data

import com.patson.model._
import com.patson.data.airplane.ModelSource
import com.patson.model.airplane._
import java.sql.PreparedStatement

import com.patson.data.Constants._
import com.patson.LinkSimulation

import scala.collection.mutable.ListBuffer
import com.patson.util.LogoGenerator

object Patchers extends App {
  main()


  def airplaneModelPatcher() {
    val existingModelsByName = ModelSource.loadAllModels().map(model => (model.name, model)).toMap

    val newModels = ListBuffer[Model]()
    Model.models.foreach { model =>
      existingModelsByName.get(model.name) match {
        case Some(existingModel) =>
          if (existingModel.price != model.price) { //adjust existing value
            val updatingAirplanes = AirplaneSource.loadAirplanesCriteria(List(("a.model", existingModel.id))).map { airplane =>
              val newValue = (model.price * airplane.condition / Airplane.MAX_CONDITION).toInt
              airplane.copy(purchasePrice = newValue);
            }
            AirplaneSource.updateAirplanesDetails(updatingAirplanes)
          }
          if (existingModel.capacity != model.capacity) { //adjust configuration and then actual capacity
            AirplaneSource.loadAirplaneConfigurationsByCriteria(List(("model", existingModel.id))).foreach { configuration =>
              val factor = model.capacity.toDouble / existingModel.capacity
              val newCapacity = ((configuration.economyVal * factor).toInt, (configuration.businessVal * factor).toInt , (configuration.firstVal * factor).toInt)
              val adjustmentDelta : Int = model.capacity - Math.ceil(newCapacity._1 * ECONOMY.spaceMultiplier + newCapacity._2 * BUSINESS.spaceMultiplier + newCapacity._3 * FIRST.spaceMultiplier).toInt
              val newConfiguration = if (configuration.economyVal == 0) {
                configuration.copy(economyVal = newCapacity._1, businessVal = newCapacity._2, firstVal = newCapacity._3)
              } else {
                configuration.copy(economyVal = newCapacity._1 + adjustmentDelta, businessVal = newCapacity._2, firstVal = newCapacity._3)
              }
              AirplaneSource.updateAirplaneConfiguration(newConfiguration)
              println(s"Configuration from $configuration to $newConfiguration")
            }
          }
       case None => newModels.append(model)
      }
    }
    ModelSource.updateModels(Model.models)
    LinkSimulation.refreshLinksPostCycle()
    ModelSource.saveModels(newModels.toList)
  }

  def adjustAirplaneConfigurations() = {
    val models = ModelSource.loadAllModels().map( model => (model.id, model)).toMap
    AirplaneSource.loadAirplaneConfigurationsByCriteria(List.empty).foreach { configuration =>
      val totalSpace = Math.ceil(configuration.economyVal * ECONOMY.spaceMultiplier + configuration.businessVal * BUSINESS.spaceMultiplier + configuration.firstVal * FIRST.spaceMultiplier).toInt
      if (totalSpace > models(configuration.model.id).capacity) {
        val delta = totalSpace - models(configuration.model.id).capacity

        if (configuration.economyVal - delta >= 0) {
          val newConfiguration = configuration.copy(economyVal = configuration.economyVal - delta)
          AirplaneSource.updateAirplaneConfiguration(newConfiguration)
          println(s"$configuration : $totalSpace, delta : $delta => $newConfiguration")
        } else {
          println(s"!!!!!!!!!!! CANNOT PATCH $configuration")
        }
      }
    }
    LinkSimulation.refreshLinksPostCycle()
  }

  def calculateTotalSpace(configuration: AirplaneConfiguration) = {
    (configuration.economyVal * ECONOMY.spaceMultiplier + configuration.businessVal * BUSINESS.spaceMultiplier + configuration.firstVal * FIRST.spaceMultiplier).toInt
  }

  def patchDelaySchema() = {
    val connection = Meta.getConnection()
    
    var statement: PreparedStatement = null

    statement = connection.prepareStatement("ALTER TABLE `airline`.`links_income` ADD COLUMN `delay_compensation` BIGINT(20) NULL DEFAULT 0 AFTER `inflight_cost`;")
    statement.execute()

    statement = connection.prepareStatement("ALTER TABLE `airline`.`link_consumption` ADD COLUMN `delay_compensation` INT(11) NULL DEFAULT 0 AFTER `inflight_cost`, ADD COLUMN `minor_delay_count` INT(4) NULL DEFAULT 0 AFTER `profit`, ADD COLUMN `major_delay_count` INT(4) NULL DEFAULT 0 AFTER `minor_delay_count`, ADD COLUMN `cancellation_count` INT(4) NULL DEFAULT 0 AFTER `major_delay_count`, ADD COLUMN `frequency` INT(4) NULL AFTER `distance`;")
    statement.execute()
    statement.close()
    
    connection.close
  }
  
  def patchAirlineCode() = {
    AirlineSource.loadAllAirlines(false).foreach { airline =>
      val code = airline.getDefaultAirlineCode
      println(code)
      AirlineSource.saveAirlineCode(airline.id, code)
    }
  }
  
  def patchFlightNumber() = {
    AirlineSource.loadAllAirlines(false).foreach { airline =>
      var counter = 0
      LinkSource.updateLinks(LinkSource.loadFlightLinksByAirlineId(airline.id).map { link =>
        counter = counter + 1
        link.copy(flightNumber = counter)
      })
    }
  }
  
  def patchAirlineLogos() {
    AirlineSource.loadAllAirlines(false).foreach { airline =>
      if (airline.id <= 250) {
        FileSource.saveLogo(airline.id, "airline", LogoGenerator.generateRandomLogo())
      }
    }
  }

  def patchLoyalist(): Unit = {
    //it might have overflow, adjust proportionally
    LoyalistSource.loadLoyalistsByCriteria(List.empty).groupBy(_.airport).foreach {
      case((airport, loyalistsOfThisAirport)) =>
        val total = loyalistsOfThisAirport.map(_.amount).sum
        if (total > airport.population) {
          val ratio = airport.population / total.toDouble
          println(s"Going to adjust loyalist for ${airport.displayText} as total pop is ${airport.population} but has $total loyalist. Going to patch with ratio $ratio")
          val adjustedEntries = loyalistsOfThisAirport.map(entry => entry.copy(amount = (entry.amount * ratio).toInt))
          LoyalistSource.updateLoyalists(adjustedEntries)
        }
    }
  }


  def main(): Unit = {
    airplaneModelPatcher()
  }
}

 