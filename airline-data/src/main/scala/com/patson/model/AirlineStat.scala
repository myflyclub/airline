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
                        repLeaderboards: Int
                      ) {
  def update(other: AirlineStat): AirlineStat = {
    AirlineStat(
      airlineId,
      other.cycle, // use the latest cycle
      period,
      tourists + other.tourists,
      elites + other.elites,
      business + other.business,
      total + other.total,
      codeshares + other.codeshares,
      (RASK + other.RASK) / 2,
      (CASK + other.CASK) / 2,
      (satisfaction + other.satisfaction) / 2,
      (loadFactor + other.loadFactor) / 2,
      (onTime + other.onTime) / 2,
      (cashOnHand + other.cashOnHand) / 2,
      (eps + other.eps) / 2,
      ((linkCount + other.linkCount).toDouble / 2).toInt,
      ((repTotal + other.repTotal).toDouble / 2).toInt,
      ((repLeaderboards + other.repLeaderboards).toDouble / 2).toInt
    )
  }
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