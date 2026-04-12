package com.patson.model

case class Notification(
  airlineId: Int,
  category: NotificationCategory.Value,
  message: String,
  cycle: Int,
  isRead: Boolean = false,
  var id: Int = 0,
  level: Option[Int] = None,
  targetId: Option[String] = None,
  expiryCycle: Option[Int] = None
)

object NotificationCategory extends Enumeration {
  type NotificationCategory = Value

  /**
   * NEGOTIATION_LOSS — Awarded when a negotiation fails and a discount is granted.
   *   targetId:    "{fromAirportId}-{toAirportId}"
   *   expiryCycle: cycle + LinkNegotiationDiscount.DURATION
   *   Navigation:  planLink(fromId, toId)
   *   Deleted:     on successful negotiation for the same route pair; expired ones
   *                purged by NotificationSource.purgeExpiredNegotiationLoss on getNotifications.
   *
   * LEVEL_UP — Airline reached a new level.
   *   targetId: none
   *
   * LOYALIST — A loyalist event occurred.
   *   targetId: none
   *
   * GAME_OVER — Airline went bankrupt / game over.
   *   targetId: none  (never deleted by deleteAllExceptGameOver)
   *
   * OLYMPICS_PRIZE — Olympics prize awarded.
   *   targetId: none
   *
   * TUTORIAL — Server-driven tutorial step.
   *   targetId: none
   *
   * LINK_CANCELLATION — A link was cancelled due to low load factor.
   *   targetId:    "{linkId}" (numeric string)
   *   expiryCycle: none
   *   Navigation:  /flights/{linkId}
   *   Lifecycle:   managed entirely by LinkSimulation (checkLoadFactor / purgeAlerts);
   *                exempt from the 100-notification retention purge.
   */
  val NEGOTIATION_LOSS, LEVEL_UP, LOYALIST, GAME_OVER, OLYMPICS_PRIZE, TUTORIAL, LINK_CANCELLATION = Value
}
