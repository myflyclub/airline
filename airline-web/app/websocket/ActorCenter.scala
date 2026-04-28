package websocket

import org.apache.pekko.actor.{Actor, ActorRef, ActorSelection, ActorSystem, Cancellable, Props}
import com.patson.model.{Airline, NotificationCategory}
import com.patson.stream.{CycleCompleted, CycleInfo, KeepAlivePing, KeepAlivePong, SimulationEvent}
import com.patson.util.{AirlineCache, AirplaneOwnershipCache, AirportCache, AirportStatisticsCache}
import com.typesafe.config.ConfigFactory
import controllers.{AirlineTutorial, AirportUtil, Application, GooglePhotoUtil, ResponseCache}
import models.PendingAction
import play.api.libs.json.{JsString, Json}
import websocket.chat.TriggerPing

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._

sealed class LocalActor(out: ActorRef, airlineId: Int) extends Actor {
  override def preStart(): Unit = {
    Broadcaster.subscribeToBroadcaster(self, airlineId)
    context.system.eventStream.subscribe(self, classOf[TriggerPing])
    context.system.eventStream.subscribe(self, classOf[(SimulationEvent, Any)])

    ActorCenter.remoteMainActor ! "getCycleInfo"
  }

  def receive: Receive = {
    case Notification(message) =>
      out ! message

    case (topic: SimulationEvent, payload: Any) => Some(topic).collect {
      case CycleCompleted(cycle, _) =>
        println(s"${self.path} Received cycle completed: $cycle")
        out ! Json.obj("messageType" -> "cycleCompleted", "cycle" -> cycle)

      case CycleInfo(cycle, fraction, cycleDurationEstimation) =>
        println(s"${self.path} Received cycle info on cycle: $cycle")
        out ! Json.obj("messageType" -> "cycleInfo", "cycle" -> cycle, "fraction" -> fraction, "cycleDurationEstimation" -> cycleDurationEstimation)
    }

    case _: TriggerPing =>
      out ! Json.obj("ping" -> "event")

    case BroadcastMessage(text) =>
      out ! Json.obj("messageType" -> "broadcastMessage", "message" -> text)

    case AirlineDirectMessage(_, text) =>
      out ! Json.obj("messageType" -> "airlineMessage", "message" -> text)

    case AirlinePrompts(_, prompts) =>
      prompts.notices.foreach { notification =>
        println(s"Sending notice ${notification.category} to airline ${notification.airlineId}")
        val notifCategory = notification.category.toString
        val notifId = notification.id
        val notifMsg = notification.message

        notification.category match {
          case NotificationCategory.LEVEL_UP | NotificationCategory.LOYALIST =>
            val lvl: Int = notification.level.getOrElse(0)
            out ! Json.obj("messageType" -> "notice", "category" -> notifCategory, "notificationId" -> notifId, "description" -> notifMsg, "level" -> lvl)
          case _ =>
            out ! Json.obj("messageType" -> "notice", "category" -> notifCategory, "notificationId" -> notifId, "description" -> notifMsg)
        }
      }
      prompts.tutorials.foreach {
        case AirlineTutorial(airline, tutorial) =>
          println(s"Sending tutorial $tutorial to $airline")
          out ! Json.obj("messageType" -> "tutorial", "category" -> tutorial.category, "id" -> tutorial.id, "highlight" -> JsString(tutorial.highlight.getOrElse("")))
      }

    case AirlinePendingActions(_, pendingActions: List[PendingAction]) =>
      out ! Json.obj("messageType" -> "pendingAction", "actions" -> Json.toJson(pendingActions.map(_.category.toString)))

    case any =>
      println(s"received $any not handled")
  }

  override def postStop(): Unit = {
    Broadcaster.unsubscribeFromBroadcaster(self)
    context.system.eventStream.unsubscribe(self)
    println(self.path.toString + " stopped (post stop), unsubscribed from all event streams")
  }
}

sealed class LocalMainActor(remoteActor: ActorSelection) extends Actor {
  implicit val ec = context.dispatcher
  val blockingEc = scala.concurrent.ExecutionContext.global

  private val pingInterval             = 60.seconds
  private val pongTimeout              = 10.seconds
  private val initialReconnectInterval = 5.seconds
  private val maxReconnectInterval     = 10.minutes

  private var connected         = true
  private var reconnectInterval = initialReconnectInterval
  private var tickTask:         Option[Cancellable] = None
  private var pongTimeoutTask:  Option[Cancellable] = None

  val configFactory = ConfigFactory.load()
  val bannerEnabled = if (configFactory.hasPath("bannerEnabled")) configFactory.getBoolean("bannerEnabled") else false

  private case object Tick
  private case object PongTimeout

  override def preStart(): Unit = {
    super.preStart()
    remoteActor ! "subscribe"
    scheduleNextTick(pingInterval)
  }

  override def postStop(): Unit = {
    println(self.path.toString + " local main actor stopped (post stop)")
    tickTask.foreach(_.cancel())
    pongTimeoutTask.foreach(_.cancel())
  }

  private def scheduleNextTick(delay: FiniteDuration): Unit = {
    tickTask.foreach(_.cancel())
    tickTask = Some(context.system.scheduler.scheduleOnce(delay, self, Tick))
  }

  override def receive: Receive = {
    case Tick =>
      remoteActor ! KeepAlivePing
      pongTimeoutTask.foreach(_.cancel())
      pongTimeoutTask = Some(context.system.scheduler.scheduleOnce(pongTimeout, self, PongTimeout))

    case KeepAlivePong =>
      pongTimeoutTask.foreach(_.cancel())
      pongTimeoutTask = None
      if (!connected) {
        println(s"${self.path} Sim reconnected — resubscribing")
        connected = true
        reconnectInterval = initialReconnectInterval
        remoteActor ! "subscribe"
      } else {
        println("Connection to sim is healthy!")
      }
      scheduleNextTick(pingInterval)

    case PongTimeout =>
      pongTimeoutTask = None
      if (connected) {
        println(s"${self.path} CRITICAL: Sim connection lost. Starting reconnect.")
        connected = false
      }
      println(s"${self.path} Retrying in $reconnectInterval...")
      scheduleNextTick(reconnectInterval)
      reconnectInterval = (reconnectInterval * 2).min(maxReconnectInterval)

    case (topic: SimulationEvent, payload: Any) =>
      topic match {
        case CycleCompleted(cycle, _) =>
          println(s"${self.path} cycle $cycle completed, invalidating caches")

          MyWebSocketActor.lastSimulatedCycle = cycle
          controllers.cachedCurrentCycle = cycle

          // 1. Invalidate fast/high-priority caches immediately
          AirlineCache.invalidateAll()
          AirplaneOwnershipCache.invalidateAll()
          ResponseCache.invalidateAll()

          // 2. Publish immediately so the client updates Airline info ASAP
          context.system.eventStream.publish((topic, payload))
          Broadcaster.checkAllPrompts()

          // 3. Background the heavy airport updates.
          val deadline = 60.seconds.fromNow

          Future {
            try {
              // Delay invalidating airport caches until right before we process them
              AirportCache.invalidateAll()
              AirportStatisticsCache.invalidateAll()

              val newChampions = AirportUtil.getAirportChampions(deadline)
              AirportUtil.cachedAirportChampions = newChampions

              Application.rebuildAirportsCache()
              if (bannerEnabled) GooglePhotoUtil.refreshBanners()
              println(s"Pre-warm completed for cycle $cycle")
            } catch {
              case ex: Throwable =>
                println(s"WARNING: Airport cache rebuild failed for cycle $cycle: ${ex.getMessage}")
            }
          }(blockingEc)

        case _ =>
          context.system.eventStream.publish((topic, payload))
      }

    case unknown =>
      println(s"Unknown message for local main actor : $unknown")
  }
}

object ActorCenter {
  val REMOTE_SYSTEM_NAME = "websocketActorSystem"
  val BRIDGE_ACTOR_NAME = "bridgeActor"
  val configFactory = ConfigFactory.load()
  val remoteConfig = configFactory.getConfig(REMOTE_SYSTEM_NAME)
  val remoteSystem = ActorSystem(REMOTE_SYSTEM_NAME, remoteConfig)

  val actorHost = if (configFactory.hasPath("sim.pekko-actor.host")) configFactory.getString("sim.pekko-actor.host") else "127.0.0.1"
  println("!!!!!!!!!!!!!!!PEKKO ACTOR HOST IS " + actorHost)

  val subscribers = mutable.HashSet[ActorRef]()
  val remoteMainActor = remoteSystem.actorSelection("pekko://" + REMOTE_SYSTEM_NAME + "@" + actorHost + "/user/" + BRIDGE_ACTOR_NAME)
  val localMainActor = remoteSystem.actorOf(Props(classOf[LocalMainActor], remoteMainActor), "local-main-actor")

  def getLocalSubscriberName(subscriberId: String): String = {
    "local-subscriber-" + subscriberId
  }
}

case class RemoteActor(remoteActor: ActorSelection)
