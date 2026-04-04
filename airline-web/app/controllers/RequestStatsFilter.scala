package controllers

import javax.inject.{Inject, Singleton}
import org.apache.pekko.stream.Materializer
import play.api.mvc._
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{AtomicLong, LongAdder}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RequestStatsFilter @Inject()(implicit val mat: Materializer, ec: ExecutionContext) extends Filter {

  def apply(nextFilter: RequestHeader => Future[Result])
           (requestHeader: RequestHeader): Future[Result] = {
    RequestStats.totalRequests.incrementAndGet()

    // Extract user from session — pure in-memory lookup, no DB
    val userIdOpt = requestHeader.session.get("userToken").flatMap(SessionUtil.getUserId)

    // Normalize endpoint: strip numeric IDs to group e.g. /airlines/123/... → /airlines/N/...
    val endpoint = requestHeader.path.replaceAll("/\\d+", "/N")

    userIdOpt match {
      case Some(userId) =>
        val userMap = RequestStats.stats.computeIfAbsent(userId, _ => new ConcurrentHashMap[String, LongAdder]())
        userMap.computeIfAbsent(endpoint, _ => new LongAdder()).increment()
      case None =>
        RequestStats.anonStats.computeIfAbsent(endpoint, _ => new LongAdder()).increment()
    }

    nextFilter(requestHeader)
  }
}

object RequestStats {
  // userId -> endpoint -> count
  val stats = new ConcurrentHashMap[Int, ConcurrentHashMap[String, LongAdder]]()
  val anonStats = new ConcurrentHashMap[String, LongAdder]()
  val totalRequests = new AtomicLong(0)

  def getTopUsers(n: Int): Seq[(Int, Long)] = {
    import scala.jdk.CollectionConverters._
    stats.asScala.map { case (userId, endpoints) =>
      userId -> endpoints.asScala.values.map(_.sum()).sum
    }.toSeq.sortBy(-_._2).take(n)
  }

  def reset(): Unit = {
    stats.clear()
    anonStats.clear()
    totalRequests.set(0)
  }
}
