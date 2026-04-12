package controllers

import com.patson.data.{CycleSource, NotificationSource}
import com.patson.model.{Notification, NotificationCategory}
import controllers.AuthenticationObject.AuthenticatedAirline
import javax.inject.Inject
import play.api.libs.json._
import play.api.mvc._

class NotificationApplication @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  implicit val notificationWrites: Writes[Notification] = (n: Notification) => {
    val base = Json.obj(
      "id"       -> n.id,
      "category" -> n.category.toString,
      "message"  -> n.message,
      "cycle"    -> n.cycle,
      "isRead"   -> n.isRead
    )
    val withExpiry = n.expiryCycle.fold(base)(ec => base + ("expiryCycle" -> JsNumber(ec)))
    n.targetId.fold(withExpiry)(tid => withExpiry + ("targetId" -> JsString(tid)))
  }

  def getNotifications(airlineId: Int) = AuthenticatedAirline(airlineId) { _ =>
    NotificationSource.purgeExpiredByCategory(airlineId, NotificationCategory.NEGOTIATION_LOSS, CycleSource.loadCycle())
    Ok(Json.toJson(NotificationSource.loadNotificationsByAirline(airlineId)))
  }

  def getUnreadCount(airlineId: Int) = AuthenticatedAirline(airlineId) { _ =>
    Ok(Json.obj("count" -> NotificationSource.countUnreadByAirline(airlineId)))
  }

  def markAllRead(airlineId: Int) = AuthenticatedAirline(airlineId) { _ =>
    NotificationSource.markAllRead(airlineId)
    Ok(Json.obj())
  }

  def markSingleRead(airlineId: Int, notifId: Int) = AuthenticatedAirline(airlineId) { _ =>
    NotificationSource.markSingleRead(airlineId, notifId)
    Ok(Json.obj())
  }

  def deleteAllRead(airlineId: Int) = AuthenticatedAirline(airlineId) { _ =>
    NotificationSource.deleteAllRead(airlineId)
    Ok(Json.obj())
  }

  def deleteNotification(airlineId: Int, notifId: Int) = AuthenticatedAirline(airlineId) { _ =>
    NotificationSource.deleteNotification(airlineId, notifId)
    Ok(Json.obj())
  }
}
