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
  val NEGOTIATION_LOSS, LEVEL_UP, LOYALIST, GAME_OVER, OLYMPICS_PRIZE, TUTORIAL, LINK_CANCELLATION = Value
}
