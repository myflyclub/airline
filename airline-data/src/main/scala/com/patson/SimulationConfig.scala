package com.patson

import com.patson.data.Constants

object RouteKernelMode extends Enumeration {
  type RouteKernelMode = Value
  val LEGACY, COMPACT, ADJACENCY = Value

  def parse(value: String): RouteKernelMode = {
    value.trim.toLowerCase match {
      case "legacy" => LEGACY
      case "compact" => COMPACT
      case "adjacency" => ADJACENCY
      case "compact-adjacency" => ADJACENCY
      case other => throw new IllegalArgumentException(s"Unsupported simulation.routeKernel '$other'. Expected legacy, compact, or adjacency.")
    }
  }
}

object SimulationConfig {
  import RouteKernelMode._

  private def configString(path: String): Option[String] = {
    if (Constants.configFactory.hasPath(path)) Some(Constants.configFactory.getString(path)) else None
  }

  private def configBoolean(path: String): Option[Boolean] = {
    if (Constants.configFactory.hasPath(path)) Some(Constants.configFactory.getBoolean(path)) else None
  }

  private def configOptionalString(path: String): Option[String] = {
    configString(path).map(_.trim).filter(_.nonEmpty)
  }

  val routeKernel: RouteKernelMode = {
    sys.env.get("ROUTE_KERNEL")
      .orElse(Option(System.getProperty("simulation.routeKernel")).filter(_.nonEmpty))
      .orElse(configString("simulation.routeKernel"))
      .map(RouteKernelMode.parse)
      .getOrElse(LEGACY)
  }

  val routeKernelName: String = routeKernel.toString.toLowerCase

  val passengerDemandFixtureCapture: Option[String] = {
    sys.env.get("PASSENGER_DEMAND_FIXTURE_CAPTURE")
      .orElse(Option(System.getProperty("simulation.passengerDemandFixtureCapture")).map(_.trim).filter(_.nonEmpty))
      .orElse(configOptionalString("simulation.passengerDemandFixtureCapture"))
  }

  val passengerDemandFixtureReplay: Option[String] = {
    sys.env.get("PASSENGER_DEMAND_FIXTURE_REPLAY")
      .orElse(Option(System.getProperty("simulation.passengerDemandFixtureReplay")).map(_.trim).filter(_.nonEmpty))
      .orElse(configOptionalString("simulation.passengerDemandFixtureReplay"))
  }

  def passengerRouteCostSeed: Option[Long] = {
    sys.env.get("PASSENGER_ROUTE_COST_SEED")
      .orElse(Option(System.getProperty("simulation.passengerRouteCostSeed")).map(_.trim).filter(_.nonEmpty))
      .orElse(configOptionalString("simulation.passengerRouteCostSeed"))
      .map(_.toLong)
  }

  def linkSimulationRandomSeed: Option[Long] = {
    sys.env.get("LINK_SIMULATION_RANDOM_SEED")
      .orElse(Option(System.getProperty("simulation.linkSimulationRandomSeed")).map(_.trim).filter(_.nonEmpty))
      .orElse(configOptionalString("simulation.linkSimulationRandomSeed"))
      .map(_.toLong)
  }

  def loyalistRandomSeed: Option[Long] = {
    sys.env.get("LOYALIST_RANDOM_SEED")
      .orElse(Option(System.getProperty("simulation.loyalistRandomSeed")).map(_.trim).filter(_.nonEmpty))
      .orElse(configOptionalString("simulation.loyalistRandomSeed"))
      .map(_.toLong)
  }

  val passengerConsumeParallel: Boolean = {
    sys.env.get("PASSENGER_CONSUME_PARALLEL")
      .map(_.toBoolean)
      .orElse(Option(System.getProperty("simulation.passengerConsumeParallel")).filter(_.nonEmpty).map(_.toBoolean))
      .orElse(configBoolean("simulation.passengerConsumeParallel"))
      .getOrElse(true)
  }

  if (passengerDemandFixtureCapture.nonEmpty && passengerDemandFixtureReplay.nonEmpty) {
    throw new IllegalArgumentException("Configure only one of passengerDemandFixtureCapture or passengerDemandFixtureReplay.")
  }

  private val allowDefaultSchemaForCandidate: Boolean = {
    sys.env.get("ROUTE_KERNEL_ALLOW_DEFAULT_SCHEMA")
      .map(_.toBoolean)
      .orElse(Option(System.getProperty("simulation.allowDefaultSchemaForCandidate")).filter(_.nonEmpty).map(_.toBoolean))
      .orElse(configBoolean("simulation.allowDefaultSchemaForCandidate"))
      .getOrElse(false)
  }

  def validateRouteKernelSchema(): Unit = {
    if (routeKernel != LEGACY && Constants.SCHEMA_NAME.equalsIgnoreCase("airline") && !allowDefaultSchemaForCandidate) {
      throw new IllegalStateException(
        "Refusing to run candidate route kernel against default schema 'airline'. " +
          "Use a cloned schema via DB_NAME or mysqldb.schema."
      )
    }
  }

  def validateClonedSchemaForExperiment(source: String): Unit = {
    if (Constants.SCHEMA_NAME.equalsIgnoreCase("airline")) {
      throw new IllegalStateException(
        s"Refusing to run $source against default schema 'airline'. " +
          "Create a cloned schema and pass it via DB_NAME or mysqldb.schema."
      )
    }
  }

  def logRuntimeContext(source: String): Unit = {
    println(
      s"simulationRuntime source=$source dbHost=${Constants.DB_HOST} schema=${Constants.SCHEMA_NAME} " +
        s"routeKernel=$routeKernelName allowDefaultSchemaForCandidate=$allowDefaultSchemaForCandidate " +
        s"passengerDemandFixtureCapture=${passengerDemandFixtureCapture.getOrElse("")} " +
        s"passengerDemandFixtureReplay=${passengerDemandFixtureReplay.getOrElse("")} " +
        s"passengerRouteCostSeed=${passengerRouteCostSeed.map(_.toString).getOrElse("")} " +
        s"linkSimulationRandomSeed=${linkSimulationRandomSeed.map(_.toString).getOrElse("")} " +
        s"loyalistRandomSeed=${loyalistRandomSeed.map(_.toString).getOrElse("")} " +
        s"passengerConsumeParallel=$passengerConsumeParallel"
    )
  }
}

object SimulationLog {
  def time[T](timer: String, cycle: Option[Int] = None, loop: Option[Int] = None, metrics: scala.collection.Seq[(String, Any)] = Seq.empty)(body: => T): T = {
    val start = System.nanoTime()
    try {
      body
    } finally {
      val durationMs = (System.nanoTime() - start) / 1000000L
      log(timer, durationMs, cycle, loop, metrics)
    }
  }

  def log(timer: String, durationMs: Long, cycle: Option[Int] = None, loop: Option[Int] = None, metrics: scala.collection.Seq[(String, Any)] = Seq.empty): Unit = {
    val values =
      Seq(
        "routeKernel" -> SimulationConfig.routeKernelName,
        "schema" -> Constants.SCHEMA_NAME
      ) ++
        cycle.map("cycle" -> _).toSeq ++
        loop.map("loop" -> _).toSeq ++
        Seq(
          "timer" -> timer,
          "durationMs" -> durationMs
        ) ++ metrics

    println(values.map { case (key, value) => s"$key=$value" }.mkString(" "))
  }
}
