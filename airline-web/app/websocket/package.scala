

import org.apache.pekko.actor.ActorSystem

import scala.concurrent.ExecutionContext
package object websocket {
  implicit val actorSystem : ActorSystem = ActorSystem("airline-websocket-actor-system")
  implicit val executionContext : ExecutionContext = ExecutionContext.global
}