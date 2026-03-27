package com.patson.model

case class AirlineStat(
                        airlineId : Int,
                        cycle : Int,
                        period : Period.Value = Period.WEEKLY,
                        tourists : Int,
                        elites : Int,
                        business : Int,
                        total : Int,
                        codeshares : Int,
                        RASK: Double,
                        CASK: Double,
                        satisfaction: Double,
                        loadFactor: Double,
                        onTime: Double,
                        cashOnHand: Int,
                        eps: Double,
                        linkCount: Int,
                        repTotal: Int,
                        repLeaderboards: Int,
                        dividendsPerShare: Double = 0.0
                      ) {
  def update(other: AirlineStat): AirlineStat = AirlineStat(
    airlineId,
    other.cycle, // use the latest cycle
    period,
    tourists + other.tourists,
    elites + other.elites,
    business + other.business,
    total + other.total,
    codeshares + other.codeshares,
    RASK + other.RASK,
    CASK + other.CASK,
    satisfaction + other.satisfaction,
    loadFactor + other.loadFactor,
    onTime + other.onTime,
    cashOnHand + other.cashOnHand,
    eps + other.eps,
    linkCount + other.linkCount,
    repTotal + other.repTotal,
    repLeaderboards + other.repLeaderboards,
    dividendsPerShare + other.dividendsPerShare
  )

  def toAverage(count: Int): AirlineStat = if (count <= 1) this else copy(
    RASK = RASK / count,
    CASK = CASK / count,
    satisfaction = satisfaction / count,
    loadFactor = loadFactor / count,
    onTime = onTime / count,
    cashOnHand = (cashOnHand.toDouble / count).toInt,
    eps = eps / count,
    linkCount = (linkCount.toDouble / count).toInt,
    repTotal = (repTotal.toDouble / count).toInt,
    repLeaderboards = (repLeaderboards.toDouble / count).toInt,
    dividendsPerShare = dividendsPerShare / count
  )
}

//intermediate data objects
case class AirlinePaxStat(tourists : Int, elites : Int, business : Int, total : Int, codeshares : Int)
case class AirlineSimStat(RASK: Double, CASK: Double, satisfaction: Double, loadFactor: Double, onTime: Double, cashOnHand: Int, eps: Double, linkCount: Int)

object AirlinePaxStat {
  val empty: AirlinePaxStat = AirlinePaxStat(0, 0, 0, 0, 0)
}
object AirlineSimStat {
  val empty: AirlineSimStat = AirlineSimStat(0.0, 0.0, 0.0, 0.0, 0.0, 0, 0.0, 0)
}