package websocket

import javax.inject._
import play.api.inject.ApplicationLifecycle

@Singleton
class ActorCenterLifecycle @Inject()(lifecycle: ApplicationLifecycle) {
  lifecycle.addStopHook { () =>
    ActorCenter.remoteSystem.terminate().map(_ => ())
  }
}
