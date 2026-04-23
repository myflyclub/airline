package websocket

import org.apache.pekko.actor.{Actor, ActorRef, ActorSelection, ActorSystem, Cancellable, Props, Terminated}
import org.apache.pekko.pattern.after
import org.apache.pekko.remote.{AssociatedEvent, DisassociatedEvent, RemotingLifecycleEvent}
import com.patson.model.{Airline, NotificationCategory}
import com.patson.stream.{CycleCompleted, CycleInfo, KeepAlivePing, KeepAlivePong, ReconnectPing, SimulationEvent}
import com.patson.util.{AirlineCache, AirplaneOwnershipCache, AirportCache, AirportStatisticsCache}
import com.typesafe.config.ConfigFactory
import controllers.{AirlineTutorial, AirportUtil, Application, GooglePhotoUtil, ResponseCache}
import models.PendingAction
import play.api.libs.json.{JsNumber, JsString, Json}
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

  val pingInterval = 60.seconds
  val resetTimeout = 10.seconds
  val prewarmTimeout = 10.seconds

  var pendingResetTask: Option[Cancellable] = None
  var periodicPingTask: Option[Cancellable] = None

  val configFactory = ConfigFactory.load()
  val bannerEnabled = if (configFactory.hasPath("bannerEnabled")) configFactory.getBoolean("bannerEnabled") else false

  case object ConnectionTimeout

  override def preStart(): Unit = {
    super.preStart()
    remoteActor ! "subscribe"

    periodicPingTask = Some(
      context.system.scheduler.scheduleWithFixedDelay(Duration.Zero, pingInterval, self, KeepAlivePing)
    )
  }

  override def postStop(): Unit = {
    println(self.path.toString + " local main actor stopped (post stop)")
    periodicPingTask.foreach(_.cancel())
    pendingResetTask.foreach(_.cancel())
  }

  override def receive: Receive = {
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

    case Resubscribe(remote) =>
      println(s"${self.path} Attempting to resubscribe")
      remote ! "subscribe"

    case Terminated(actor) =>
      println(s"$actor is terminated!!")

    case KeepAlivePing =>
      remoteActor ! KeepAlivePing
      pendingResetTask.foreach(_.cancel())
      pendingResetTask = Some(context.system.scheduler.scheduleOnce(resetTimeout, self, ConnectionTimeout))

    case KeepAlivePong =>
      println("Connection to sim is healthy!")
      pendingResetTask.foreach(_.cancel())
      pendingResetTask = None

    case ConnectionTimeout =>
      println("CRITICAL: Sim connection timeout exceeded. Initiating reset logic...")
      remoteActor ! "subscribe" // Implicit reset task

    case unknown =>
      println(s"Unknown message for local main actor : $unknown")
  }
}

// Internal message for ReconnectActor exponential backoff
private case class ExecutePing(currentDelay: FiniteDuration)

sealed class ReconnectActor(remoteActor: ActorSelection) extends Actor {
  implicit val ec = context.dispatcher
  var disconnected = false
  var pingTask: Option[Cancellable] = None

  val INITIAL_SLEEP_TIME = 5.seconds
  val MAX_SLEEP_TIME = 10.minutes

  override def preStart(): Unit = {
    super.preStart()
    context.system.eventStream.subscribe(self, classOf[RemotingLifecycleEvent])
    remoteActor ! KeepAlivePing
  }

  override def postStop(): Unit = {
    pingTask.foreach(_.cancel())
  }

  override def receive: Receive = {
    case _: DisassociatedEvent =>
      if (!disconnected) {
        println(s"Disassociated. Start pinging the remote actor! from reconnect actor $this")
        disconnected = true
        self ! ExecutePing(INITIAL_SLEEP_TIME)
      }

    case _: AssociatedEvent =>
      if (disconnected) {
        println("Reconnected! stop pinging")
        pingTask.foreach(_.cancel())
        pingTask = None
        disconnected = false

        val localMainActor = context.system.actorSelection("/user/local-main-actor")
        localMainActor ! Resubscribe(remoteActor)
      }

    case ExecutePing(delay) =>
      if (disconnected) {
        remoteActor ! ReconnectPing()

        // Calculate exponential backoff for next ping
        val nextDelay = if ((delay * 2) > MAX_SLEEP_TIME) MAX_SLEEP_TIME else delay * 2

        // Schedule next ping instead of Thread.sleep()
        pingTask = Some(context.system.scheduler.scheduleOnce(delay, self, ExecutePing(nextDelay)))
      }
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
  val reconnectActor = remoteSystem.actorOf(Props(classOf[ReconnectActor], remoteMainActor), "reconnect-actor")

  def getLocalSubscriberName(subscriberId: String): String = {
    "local-subscriber-" + subscriberId
  }
}

case class RemoteActor(remoteActor: ActorSelection)
case class Resubscribe(remoteActor: ActorSelection)