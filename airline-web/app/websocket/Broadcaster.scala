package websocket

import org.apache.pekko.actor.ActorRef
import org.apache.pekko.event.{EventBus, LookupClassification}
import com.patson.model.{Airline, NonPlayerAirline}
import com.patson.util.AirlineCache
import controllers.{PendingActionUtil, PromptUtil}
import models.PendingAction

import java.util.Calendar

case class BroadcastMessage(text : String)
abstract class AirlineMessage(airline : Airline) {
  val getAirline = airline
}
case class AirlineDirectMessage(airline: Airline, text : String) extends AirlineMessage(airline)
case class AirlinePendingActions(airline : Airline, actions : List[PendingAction]) extends AirlineMessage(airline)
case class AirlinePrompts(airline : Airline, prompts : controllers.Prompts) extends AirlineMessage(airline)


case class BroadcastSubscribe(subscriber : ActorRef, airline : Airline, remoteAddress: String) {
  val creationTime = Calendar.getInstance().getTime
}

class BroadcastEventBus extends EventBus with LookupClassification {
  type Event = BroadcastMessage
  type Classifier = String
  type Subscriber = ActorRef

  // is used for extracting the classifier from the incoming events
  override protected def classify(event: Event): Classifier = ""

  // will be invoked for each event for all subscribers which registered themselves
  // for the event’s classifier
  override protected def publish(event: Event, subscriber: Subscriber): Unit = {
    subscriber ! event
  }

  // must define a full order over the subscribers, expressed as expected from
  // `java.lang.Comparable.compare`
  override protected def compareSubscribers(a: Subscriber, b: Subscriber): Int =
    a.compareTo(b)

  // determines the initial size of the index data structure
  // used internally (i.e. the expected number of different classifiers)
  override protected def mapSize(): Int = 128
}

class AirlineEventBus extends EventBus with LookupClassification {
  type Event = AirlineMessage
  type Classifier = Int
  type Subscriber = ActorRef

  // is used for extracting the classifier from the incoming events
  override protected def classify(event: Event): Classifier = event.getAirline.id

  // will be invoked for each event for all subscribers which registered themselves
  // for the event’s classifier
  override protected def publish(event: Event, subscriber: Subscriber): Unit = {
    subscriber ! event
  }

  // must define a full order over the subscribers, expressed as expected from
  // `java.lang.Comparable.compare`
  override protected def compareSubscribers(a: Subscriber, b: Subscriber): Int =
    a.compareTo(b)

  // determines the initial size of the index data structure
  // used internally (i.e. the expected number of different classifiers)
  override protected def mapSize(): Int = 128

  def subscribedAirlineIds: Iterable[Int] = subscribers.keys
}

object Broadcaster {
  val broadcastEventBus = new BroadcastEventBus()
  val airlineEventBus = new AirlineEventBus()

  def broadcastMessage(message : String): Unit = {
    broadcastEventBus.publish(BroadcastMessage(message))
  }
  def sendMessage(airline : Airline, message : String) = {
    airlineEventBus.publish(AirlineDirectMessage(airline, message))
  }

  def subscribeToBroadcaster(subscriber : ActorRef, airlineId : Int) = {
    //localMainActor ! BroadcastWrapper(BroadcastSubscribe(subscriber, airline, remoteAddress))
    broadcastEventBus.subscribe(subscriber, "")
    airlineEventBus.subscribe(subscriber, airlineId)
  }

  def checkPrompts(airlineId : Int) = {
    val airline = AirlineCache.getAirline(airlineId).get
    if (airline.airlineType != NonPlayerAirline) {
      val prompts = PromptUtil.getPrompts(airline)
      airlineEventBus.publish(AirlinePrompts(airline, prompts))
      airlineEventBus.publish(AirlinePendingActions(airline, PendingActionUtil.getPendingActions(airline))) //should send empty list if none, so front end can clear
    }
  }

  def checkAllPrompts(): Unit = {
    airlineEventBus.subscribedAirlineIds.foreach(checkPrompts)
  }

  def unsubscribeFromBroadcaster(subscribe: ActorRef) = {
    broadcastEventBus.unsubscribe(subscribe)
    airlineEventBus.unsubscribe(subscribe)
  }
}
