package com.patson

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths, StandardOpenOption}

import com.patson.model._

import scala.jdk.CollectionConverters._

object PassengerDemandFixture {
  case class Loaded(demandChunks: List[(PassengerGroup, Airport, Int)],
                    initiallyMissedDemand: List[(PassengerGroup, Airport, Int)])

  private val Header = "role\tfrom_airport_id\tto_airport_id\tpassenger_type_id\tpreferred_link_class\tpreference_kind\tprice_modifier\tlounge_level_required\tloyalty_ratio\tpreference_id\tpassenger_count"
  private val DemandRole = "demand"
  private val InitialMissedRole = "initial_missed"
  private val DealKind = "deal"
  private val LastMinuteKind = "last_minute"
  private val AppealKind = "appeal"

  def buildAirportLookup(demand: Iterable[(PassengerGroup, Airport, Int)], links: Iterable[Transport]): Map[Int, Airport] = {
    val demandAirports = demand.flatMap {
      case (passengerGroup, toAirport, _) => List(passengerGroup.fromAirport, toAirport)
    }
    val linkAirports = links.flatMap(link => List(link.from, link.to))

    (demandAirports ++ linkAirports).map(airport => airport.id -> airport).toMap
  }

  def save(path: String,
           demandChunks: Seq[(PassengerGroup, Airport, Int)],
           initiallyMissedDemand: Seq[(PassengerGroup, Airport, Int)]): Unit = {
    val fixturePath = Paths.get(path)
    val parent = fixturePath.getParent
    if (parent != null) {
      Files.createDirectories(parent)
    }

    val rows =
      Header +: (demandChunks.map(row(DemandRole)) ++ initiallyMissedDemand.map(row(InitialMissedRole)))

    Files.write(
      fixturePath,
      rows.asJava,
      StandardCharsets.UTF_8,
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING,
      StandardOpenOption.WRITE
    )

    println(s"passengerDemandFixtureCapture path=$path demandChunks=${demandChunks.size} initiallyMissedDemand=${initiallyMissedDemand.size}")
  }

  def load(path: String, airportById: Map[Int, Airport]): Loaded = {
    val fixturePath = Paths.get(path)
    val lines = Files.readAllLines(fixturePath, StandardCharsets.UTF_8).asScala.toList
    lines.headOption match {
      case Some(Header) =>
      case Some(other) => throw new IllegalArgumentException(s"Unsupported passenger demand fixture header: $other")
      case None => throw new IllegalArgumentException(s"Passenger demand fixture is empty: $path")
    }

    val entries = lines.drop(1).filter(_.trim.nonEmpty).map(parseRow(_, airportById))
    val (demandChunks, initiallyMissedDemand) = entries.partition(_._1 == DemandRole)

    val loaded = Loaded(
      demandChunks = demandChunks.map(_._2),
      initiallyMissedDemand = initiallyMissedDemand.map(_._2)
    )

    println(s"passengerDemandFixtureReplay path=$path demandChunks=${loaded.demandChunks.size} initiallyMissedDemand=${loaded.initiallyMissedDemand.size}")
    loaded
  }

  private def row(role: String)(entry: (PassengerGroup, Airport, Int)): String = {
    val (passengerGroup, toAirport, passengerCount) = entry
    val preference = passengerGroup.preference
    val (kind, priceModifier, loungeLevelRequired, loyaltyRatio, preferenceId) = preference match {
      case DealPreference(_, _, capturedPriceModifier) =>
        (DealKind, capturedPriceModifier, 0, 0.0, 0)
      case LastMinutePreference(_, _, capturedPriceModifier, capturedLoungeLevelRequired) =>
        (LastMinuteKind, capturedPriceModifier, capturedLoungeLevelRequired, 0.0, 0)
      case AppealPreference(_, _, capturedPriceModifier, capturedLoungeLevelRequired, capturedLoyaltyRatio, capturedId) =>
        (AppealKind, capturedPriceModifier, capturedLoungeLevelRequired, capturedLoyaltyRatio, capturedId)
      case other =>
        throw new IllegalArgumentException(s"Unsupported passenger demand fixture preference: ${other.getClass.getName}")
    }

    List(
      role,
      passengerGroup.fromAirport.id,
      toAirport.id,
      passengerGroup.passengerType.id,
      preference.preferredLinkClass.code,
      kind,
      priceModifier,
      loungeLevelRequired,
      loyaltyRatio,
      preferenceId,
      passengerCount
    ).mkString("\t")
  }

  private def parseRow(line: String, airportById: Map[Int, Airport]): (String, (PassengerGroup, Airport, Int)) = {
    val columns = line.split("\t", -1)
    if (columns.length != 11) {
      throw new IllegalArgumentException(s"Passenger demand fixture row has ${columns.length} columns, expected 11: $line")
    }

    val role = columns(0)
    if (role != DemandRole && role != InitialMissedRole) {
      throw new IllegalArgumentException(s"Unsupported passenger demand fixture role: $role")
    }

    val fromAirport = airportById.getOrElse(
      columns(1).toInt,
      throw new IllegalArgumentException(s"Passenger demand fixture references missing from airport ${columns(1)}")
    )
    val toAirport = airportById.getOrElse(
      columns(2).toInt,
      throw new IllegalArgumentException(s"Passenger demand fixture references missing to airport ${columns(2)}")
    )
    val passengerType = PassengerType(columns(3).toInt)
    val preferredLinkClass = LinkClass.fromCode(columns(4))
    val preference = columns(5) match {
      case DealKind =>
        DealPreference(fromAirport, preferredLinkClass, columns(6).toDouble)
      case LastMinuteKind =>
        LastMinutePreference(fromAirport, preferredLinkClass, columns(6).toDouble, columns(7).toInt)
      case AppealKind =>
        AppealPreference(fromAirport, preferredLinkClass, columns(6).toDouble, columns(7).toInt, columns(8).toDouble, columns(9).toInt)
      case other =>
        throw new IllegalArgumentException(s"Unsupported passenger demand fixture preference kind: $other")
    }

    (role, (PassengerGroup(fromAirport, preference, passengerType), toAirport, columns(10).toInt))
  }
}
