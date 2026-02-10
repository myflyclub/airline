package controllers

import com.patson.data._
import com.patson.model.Scheduling.{TimeSlot, TimeSlotStatus}
import com.patson.model.{Link, _}
import com.patson.util.{AirportCache}
import controllers.WeatherUtil.{Coordinates, Weather}
import play.api.libs.json.{Json, _}
import play.api.mvc._

import java.util.Random
import javax.inject.Inject

/**
 * This is purely presentational. It creates the airport departures view.
 */

class DepartureApplication @Inject()(cc: ControllerComponents, val configuration: play.api.Configuration) extends AbstractController(cc) {

  implicit object TimeSlotAssignmentWrites extends Writes[(TimeSlot, Link, TimeSlotStatus)] {
    def writes(timeSlotAssignment: (TimeSlot, Link, TimeSlotStatus)): JsValue = {
      val link = timeSlotAssignment._2
      JsObject(List(
        "timeSlotDay" -> JsNumber(timeSlotAssignment._1.dayOfWeek),
        "timeSlotTime" -> JsString("%02d".format(timeSlotAssignment._1.hour) + ":" + "%02d".format(timeSlotAssignment._1.minute)),
        "airline" -> JsString(link.airline.name),
        "airlineId" -> JsNumber(link.airline.id),
        "flightCode" -> JsString(LinkUtil.getFlightCode(link.airline, link.flightNumber)),
        "destination" -> JsString(if (link.to.city.nonEmpty) {
          link.to.city
        } else {
          link.to.name
        }),
        "statusCode" -> JsString(timeSlotAssignment._3.code),
        "statusText" -> JsString(timeSlotAssignment._3.text)
      ))
    }
  }


  def getDepartures(airportId: Int, dayOfWeek: Int, hour: Int, minute: Int) = Action {
    val links = LinkSource.loadFlightLinksByFromAirport(airportId, LinkSource.SIMPLE_LOAD) ++ (LinkSource.loadFlightLinksByToAirport(airportId, LinkSource.SIMPLE_LOAD).map { link => link.copy(from = link.to, to = link.from) })

    val currentTime = TimeSlot(dayOfWeek = dayOfWeek, hour = hour, minute = minute)

    val linkConsumptions: Map[Int, LinkConsumptionDetails] = LinkSource.loadLinkConsumptionsByLinksId(links.map(_.id)).map(linkConsumption => (linkConsumption.link.id, linkConsumption)).toMap

    val airport = AirportCache.getAirport(airportId, false).get
    val weather = WeatherUtil.getWeather(new Coordinates(airport.latitude, airport.longitude))

    val random = new Random()
    random.setSeed(airport.id) //so generate same result every time

    val timeSlotLinkList: List[(TimeSlot, Link, TimeSlotStatus)] = links.flatMap { link => link.schedule.map { scheduledTimeSlot => (scheduledTimeSlot, link) } }.map {
      case (timeslot, link) => (timeslot, link, if (dayOfWeek == 6 && timeslot.dayOfWeek == 0) {
        timeslot.totalMinutes + 7 * 24 * 60
      } else {
        timeslot.totalMinutes
      })
    }.filter {
      case (timeslot, _, wrappedMinutes) => wrappedMinutes >= currentTime.totalMinutes && wrappedMinutes <= currentTime.totalMinutes + 24 * 60
    }.map {
      case (timeslot, link, wrappedMinutes) => (timeslot, link, getTimeSlotStatus(linkConsumptions.get(link.id), timeslot, currentTime, weather, random), wrappedMinutes)
    }.sortBy {
      case (timeslot, _, _, wrappedMinutes) => wrappedMinutes
    }.map {
      case (timeslot, link, status, wrappedMinutes) => (timeslot, link, status)
    }

    var result = Json.obj("timeslots" -> Json.toJson(timeSlotLinkList))

    if (weather != null) {
      result = result + ("weatherIcon" -> JsString("http://openweathermap.org/img/w/" + weather.getIcon + ".png")) + ("weatherDescription" -> JsString(weather.getDescription)) + ("temperature" -> JsNumber(weather.getTemperature))
    }

    Ok(result)
  }

  def getTimeSlotStatus(linkConsumptionOption: Option[LinkConsumptionDetails], scheduledTime: TimeSlot, currentTime: TimeSlot, weather: Weather, random: Random): TimeSlotStatus = {
    var isMinorDelay = false
    var isMajorDelay = false
    var isCancelled = false
    var delayAmount = 0

    linkConsumptionOption.foreach { linkConsumption =>
      getWeatherError(weather, random) match {
        case (r1, r2, r3) => {
          isMinorDelay = r1
          isMajorDelay = r2
          isCancelled = r3
        }
      }

      val cancellationMarker = linkConsumption.link.cancellationCount
      val majorDelayMarker = cancellationMarker + linkConsumption.link.majorDelayCount
      val minorDelayMarker = majorDelayMarker + linkConsumption.link.minorDelayCount

      val flightInterval = 60 * 24 * 7 / linkConsumption.link.frequency
      val flightIndex = scheduledTime.totalMinutes / flightInterval //nth flight on this route within this week

      val randomizedFlightIndex = (flightIndex + random.nextInt(linkConsumption.link.frequency)) % linkConsumption.link.frequency

      //println(cancellationMarker + "|" + majorDelayMarker + "|" + minorDelayMarker + " RI " + randomizedFlightIndex)
      //if u r unlucky enough to be smaller or equal to marker than BOOM!
      if (randomizedFlightIndex < cancellationMarker) {
        isCancelled = true
      } else if (randomizedFlightIndex < majorDelayMarker) {
        isMajorDelay = true
      } else if (randomizedFlightIndex < minorDelayMarker) {
        isMinorDelay = true
      }

      if (isMajorDelay) {
        delayAmount = 5 * 60 + randomizedFlightIndex * 30
      } else if (isMinorDelay) {
        delayAmount = 20 + 100 / (randomizedFlightIndex + 1) //within 2 hours
      }
    }

    if (isCancelled) {
      TimeSlotStatus("CANCELLED", "Cancelled")
    } else if (isMajorDelay || isMinorDelay) {
      val newTime = scheduledTime.increment(delayAmount)
      TimeSlotStatus("DELAY", "Delayed " + "%02d".format(newTime.hour) + ":" + "%02d".format(newTime.minute))
    } else if (scheduledTime.totalMinutes - currentTime.totalMinutes < 0) { // wrap around time, thats ok
      TimeSlotStatus("ON_TIME", "On Time")
    } else if (scheduledTime.totalMinutes - currentTime.totalMinutes <= 10) {
      TimeSlotStatus("GATE_CLOSED", "Gate Closed")
    } else if (scheduledTime.totalMinutes - currentTime.totalMinutes <= 20) {
      TimeSlotStatus("FINAL_CALL", "Final Call")
    } else if (scheduledTime.totalMinutes - currentTime.totalMinutes <= 30) {
      TimeSlotStatus("BOARDING", "Boarding")
    } else {
      TimeSlotStatus("ON_TIME", "On Time")
    }
  }

  def getWeatherError(weather: Weather, random: Random): (Boolean, Boolean, Boolean) = {
    val errorChance: Double = //chance for Major delay/cancellation
      if (weather.getWindSpeed() >= 30) { //hurricane
        1; //all cancelled or major delay
      } else if (weather.getWindSpeed() >= 25) {
        0.9;
      } else if (weather.getWindSpeed() >= 20) {
        0.6;
      } else if (weather.getWindSpeed() >= 20) {
        0.3;
      } else { //other weather conditions
        val weatherId: Int = weather.getWeatherId()
        if (weatherId / 100 == 2) { //thunderstorm
          if (weatherId == 202 || weatherId == 212 || weatherId == 221) {
            0.8
          } else {
            0.2
          }
        } else if (weatherId / 100 == 5) { //rain
          if (weatherId == 502 || weatherId == 503) {
            0.30
          } else if (weatherId == 504) {
            0.50
          } else if (weatherId == 522) {
            0.30
          } else if (weatherId == 531) {
            0.50
          } else {
            0.0
          }
        } else if (weatherId / 100 == 6) { //snow
          if (weatherId == 601) {
            0.30
          } else if (weatherId == 602) {
            0.80
          } else {
            0.10
          }
        } else {
          0
        }
      }

    if (errorChance == 0) {
      (false, false, false)
    } else {
      var randomNumber: Double = random.nextDouble()

      randomNumber = randomNumber * 100 - (randomNumber * 100).toInt //somehow nextDouble doesnt give evenly distributed number...
      if (randomNumber <= errorChance) { //too bad...HIT!
        if (random.nextDouble < 0.3) {
          (false, false, true) // cancellation
        } else {
          (false, true, false) //major delay
        }
      } else if (randomNumber / 2 <= errorChance) { //ok..minor delay
        (true, false, false)
      } else {
        (false, false, false) //safe...
      }
    }
  }

}
