package websocket

import org.apache.pekko.actor.{ActorSystem, Props}
import org.apache.pekko.testkit.{ImplicitSender, TestKit, TestProbe}
import com.patson.stream.CycleCompleted
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.JsObject

import scala.concurrent.duration._

// Being in `package websocket` gives access to:
//   - sealed class LocalActor
//   - implicit actorSystem from the package object
//
// The test actor system is separate from the package object's actorSystem.
// LocalActor subscribes to actorSystem.eventStream (package object), so
// publishing there delivers to any LocalActor regardless of which system it
// was created in — cross-system JVM-local delivery works in Pekko.
//
// NOTE: ActorCenter.remoteSystem binds port 12553 (test/resources/application.conf).
// This allows tests to run while the app is running on port 2553.
class LocalActorSpec
    extends TestKit(ActorSystem("LocalActorSpec"))
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "LocalActor" should {

    "forward cycleCompleted JSON to out when the event is published" in {
      val out = TestProbe()(system)
      system.actorOf(Props(classOf[LocalActor], out.ref, 42))

      // Allow preStart subscriptions to register on actorSystem.eventStream
      Thread.sleep(200)

      actorSystem.eventStream.publish((CycleCompleted(100, System.currentTimeMillis()), ()))

      val msg = out.expectMsgType[JsObject](3.seconds)
      (msg \ "messageType").as[String] shouldBe "cycleCompleted"
      (msg \ "cycle").as[Int] shouldBe 100
    }

    "not send spontaneous messages on creation" in {
      val out = TestProbe()(system)
      system.actorOf(Props(classOf[LocalActor], out.ref, 99))

      out.expectNoMessage(500.millis)
    }

    "not forward cycleInfo when a different SimulationEvent is published" in {
      val out = TestProbe()(system)
      system.actorOf(Props(classOf[LocalActor], out.ref, 55))
      Thread.sleep(200)

      // CycleInfo is handled, but produces a cycleInfo message not cycleCompleted
      import com.patson.stream.CycleInfo
      actorSystem.eventStream.publish((CycleInfo(5, 0.5, 30000L), ()))

      val msg = out.expectMsgType[JsObject](3.seconds)
      (msg \ "messageType").as[String] shouldBe "cycleInfo"
    }
  }
}
