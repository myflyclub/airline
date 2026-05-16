package controllers

import javax.inject.{Inject, Singleton}
import org.apache.pekko.actor.ActorSystem
import play.api.libs.concurrent.CustomExecutionContext

@Singleton
class JdbcExecutionContext @Inject() (actorSystem: ActorSystem)
  extends CustomExecutionContext(actorSystem, "jdbc-blocking-dispatcher")
