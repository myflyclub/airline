package websocket

import org.apache.pekko.actor.{Actor, ActorRef, ActorSelection, ActorSystem, Props, Terminated}
import org.apache.pekko.remote.{AssociatedEvent, DisassociatedEvent, RemotingLifecycleEvent}
import com.patson.model.{Airline, NotificationCategory}
import com.patson.stream.{CycleCompleted, CycleInfo, KeepAlivePing, KeepAlivePong, ReconnectPing, SimulationEvent}
import com.patson.util.{AirlineCache, AirplaneOwnershipCache, AirportCache, AirportStatisticsCache}
import com.typesafe.config.ConfigFactory
import controllers.{AirlineTutorial, AirportUtil, GooglePhotoUtil, ResponseCache}
import models.PendingAction
import play.api.libs.json.{JsNumber, Json}
import websocket.chat.TriggerPing

import java.util.{Date, Timer, TimerTask}
import scala.collection.mutable

//Instead of maintaining a new actor connection whenever someone logs in, we will only maintain one connection between sim and web app, once sim finishes a cycle, it will send one message the the web app actor, and the web app actor will relay the message in an event stream, which is subscribed by each login section.
//
//For new login, the web app local actor will directly send one message to the remote actor, and the remote actor will in this case reply directly to the web app local actor - this is the ONLY time that the 2 talk directly

sealed class LocalActor(out : ActorRef, airlineId : Int) extends Actor {
  override def preStart() = {
    Broadcaster.subscribeToBroadcaster(self, airlineId)
    actorSystem.eventStream.subscribe(self, classOf[TriggerPing])
    actorSystem.eventStream.subscribe(self, classOf[(SimulationEvent, Any)])

    ActorCenter.remoteMainActor ! "getCycleInfo" //get cycle info once on start
  }

  def receive = {
    case Notification(message) =>
      //      println("going to send " + message + " back to the websocket")
      out ! message
    case (topic: SimulationEvent, payload: Any) => Some(topic).collect {
      case CycleCompleted(cycle, cycleEndTime) =>
        println(s"${self.path} Received cycle completed: $cycle")
        out ! Json.obj("messageType" -> "cycleCompleted", "cycle" -> cycle) //if a CycleCompleted is published to the stream, notify the out(websocket) of the cycle
        Broadcaster.checkPrompts(airlineId)
      case CycleInfo(cycle, fraction, cycleDurationEstimation) =>
        println(s"${self.path} Received cycle info on cycle: " + cycle)
        out ! Json.obj("messageType" -> "cycleInfo", "cycle" -> cycle, "fraction" -> fraction, "cycleDurationEstimation" -> cycleDurationEstimation)
    }
    case ping : TriggerPing =>
      //println(s"${new Date()} - ${self.path} trigger ping created at ${ping.creationDate}")
      out ! Json.obj("ping" -> "event")
    case BroadcastMessage(text) =>
      out ! Json.obj("messageType" -> "broadcastMessage", "message" -> text)
    case AirlineDirectMessage(airline, text) =>
      out ! Json.obj("messageType" -> "airlineMessage", "message" -> text)
    case AirlinePrompts(_, prompts) =>
      //println(s"$self get prompts")
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
          out ! Json.obj("messageType" -> "tutorial", "category" -> tutorial.category, "id" -> tutorial.id, "highlight" -> play.api.libs.json.JsString(tutorial.highlight.getOrElse("")))
      }
    case AirlinePendingActions(airline, pendingActions : List[PendingAction]) =>
      //println(s"$self get pending actions")
      out ! Json.obj("messageType" -> "pendingAction", "actions" -> Json.toJson(pendingActions.map(_.category.toString)))
    case any =>
      println("received " + any + " not handled")
  }

  override def postStop() = {
    Broadcaster.unsubscribeFromBroadcaster(self)
    actorSystem.eventStream.unsubscribe(self)

    println(self.path.toString + " stopped (post stop), unsubscribed from all event streams")
  }
}

class ResetTask(localActor : ActorRef, remoteActor : ActorSelection) extends TimerTask {
  override def run() : Unit = {
    println(s"${localActor.path} resubscribe due to ping timeout")
    remoteActor.tell("subscribe", localActor)
  }
}

//only 1 locally, fan out message to all local actors to reduce connections required
//also manage the broadcast actor
sealed class LocalMainActor(remoteActor : ActorSelection) extends Actor {
  //also create BroadcastActor
//  val broadcastActor = context.actorOf(Props(classOf[BroadcastActor]).withDispatcher("my-pinned-dispatcher"), "broadcast-actor")
//  context.watch(broadcastActor)

  val pingInterval = 60000 //how often do we check
  val resetTimeout = 10000
  var pendingResetTask : Option[ResetTask] = None
  val timer = new Timer()
  val configFactory = ConfigFactory.load()
  val bannerEnabled = if (configFactory.hasPath("bannerEnabled")) configFactory.getBoolean("bannerEnabled") else false

  override def receive = {
    case (topic: SimulationEvent, payload: Any) =>
      println(s"Local main actor received topic $topic, re-publishing to ${actorSystem}")
      Some(topic).collect {
        case CycleCompleted(cycle, cycleEndTime) =>
          println(s"${self.path} invalidating cache")
          MyWebSocketActor.lastSimulatedCycle = cycle
          controllers.cachedCurrentCycle = cycle
          AirlineCache.invalidateAll()
          AirportCache.invalidateAll()
          AirportStatisticsCache.invalidateAll()
          AirplaneOwnershipCache.invalidateAll()
          ResponseCache.invalidateAll()
          AirportUtil.refreshAirports()
          if (bannerEnabled) {
            println("Banner is enabled. Refreshing banner on cycle complete")
            GooglePhotoUtil.refreshBanners()
          }
          println(s"${self.path} invalidated cache")
      }

      actorSystem.eventStream.publish(topic, payload) //relay to local event stream... since i don't know if I can subscribe to remote event stream...
    case Resubscribe(remoteActor) =>
      println(self.path.toString +  " Attempting to resubscribe")
      remoteActor ! "subscribe"
    case Terminated(actor) =>
      println(s"$actor is terminated!!")
//    case BroadcastWrapper(message) => {
//      broadcastActor ! message
//    }
    case KeepAlivePing =>
      remoteActor ! KeepAlivePing
      pendingResetTask.foreach(_.cancel())
      val resetTask = new ResetTask(self, remoteActor)
      timer.schedule(resetTask, resetTimeout)
      pendingResetTask = Some(resetTask)
    case KeepAlivePong => //diffuse the reset!
      println("Connection to sim is healthy!")
      pendingResetTask.foreach( _.cancel() )
    case unknown : Any => println(s"Unknown message for local main actor : $unknown")
  }



  override def preStart() = {
    super.preStart()
    remoteActor ! "subscribe"
    timer.schedule(new TimerTask {
      override def run() : Unit = {
          self ! KeepAlivePing
      }
    }, 0, pingInterval)
  }

  override def postStop() = {
    println(self.path.toString + " local main actor stopped (post stop)")
    timer.cancel()
  }
}

sealed class ReconnectActor(remoteActor : ActorSelection) extends Actor {
  var disconnected = false

  override def preStart() = {
    super.preStart()
    context.system.eventStream.subscribe(self, classOf[RemotingLifecycleEvent])
    remoteActor ! KeepAlivePing //establish connection
  }
  override def receive = {
    case lifeCycleEvent : DisassociatedEvent => {
      if (!disconnected) {
        println(s"Disassociated. Start pinging the remote actor! from reconnect actor $this")
        disconnected = true
        startPing(remoteActor)
      }
    }
    case lifeCycleEvent : AssociatedEvent => {
      if (disconnected) { //if previously disconnected
        val system = context.system
//        val localSubscribers = system.actorSelection(system./("local-subscriber-*"))
//        localSubscribers ! Resubscribe(remoteActor)
        val localMainActor = system.actorSelection(system./("local-main-actor"))
        localMainActor ! Resubscribe(remoteActor)
        disconnected = false
      }
    }
    case _ => // ignore unhandled messages (e.g. KeepAlivePong reply from initial connection ping)
  }
  def startPing(remoteActor : ActorSelection) = {
    new Thread() {
      override def run() = {
        var sleepTime = 5000
        val MAX_SLEEP_TIME = 10 * 60 * 1000 //10 mins
        while (disconnected) {
          remoteActor ! ReconnectPing()
          sleepTime *= 2
          sleepTime = Math.min(MAX_SLEEP_TIME, sleepTime)
          Thread.sleep(sleepTime)
        }
        println("Reconnected! stop pinging")
      }
    }.start()
  }
  def stopPing() = {
    disconnected = false
  }
}


object ActorCenter {
  val REMOTE_SYSTEM_NAME = "websocketActorSystem"
  val BRIDGE_ACTOR_NAME = "bridgeActor"
  val configFactory = ConfigFactory.load()
  val remoteConfig = configFactory.getConfig(REMOTE_SYSTEM_NAME)
  val remoteSystem = ActorSystem(REMOTE_SYSTEM_NAME, remoteConfig)

  val actorHost = if (configFactory.hasPath("sim.pekko-actor.host")) configFactory.getString("sim.pekko-actor.host") else "127.0.0.1"
  println("!!!!!!!!!!!!!!!AKK ACTOR HOST IS " + actorHost)

  val subscribers = mutable.HashSet[ActorRef]()
  val remoteMainActor = remoteSystem.actorSelection("pekko://" + REMOTE_SYSTEM_NAME + "@" + actorHost + "/user/" + BRIDGE_ACTOR_NAME)
  val localMainActor = remoteSystem.actorOf(Props(classOf[LocalMainActor], remoteMainActor), "local-main-actor")


  val reconnectActor = remoteSystem.actorOf(Props(classOf[ReconnectActor], remoteMainActor), "reconnect-actor")


  def getLocalSubscriberName(subscriberId : String) = {
    "local-subscriber-" + subscriberId
  }


}

case class RemoteActor(remoteActor : ActorSelection)
case class Resubscribe(remoteActor : ActorSelection)