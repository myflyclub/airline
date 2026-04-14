package websocket

import javax.inject._
import play.api.inject.ApplicationLifecycle
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class ActorCenterLifecycle @Inject()(lifecycle: ApplicationLifecycle) {
  lifecycle.addStopHook { () =>
    ActorCenter.remoteSystem.terminate().map(_ => ())
  }
}
