/* ===================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */

package test

import akka.actor._
import spray.routing.SimpleRoutingApp
import akka.util.Timeout
import spray.httpx.RequestBuilding
import scala.concurrent.{ Await, Future }
import kamon.spray.KamonTraceDirectives
import scala.util.Random
import akka.routing.RoundRobinRouter
import kamon.trace.TraceRecorder
import kamon.Kamon
import kamon.metrics._
import spray.http.{ StatusCodes, Uri }
import kamon.metrics.Subscriptions.TickMetricSnapshot
import kamon.newrelic.WebTransactionMetrics

object SimpleRequestProcessor extends App with SimpleRoutingApp with RequestBuilding with KamonTraceDirectives {
  import scala.concurrent.duration._
  import spray.client.pipelining._
  import akka.pattern.ask

  implicit val system = ActorSystem("test")
  import system.dispatcher

  val printer = system.actorOf(Props[PrintWhatever])

  val act = system.actorOf(Props(new Actor {
    def receive: Actor.Receive = { case any ⇒ sender ! any }
  }), "com")

  //val buffer = system.actorOf(TickMetricSnapshotBuffer.props(30 seconds, printer))

  //Kamon(Metrics).subscribe(CustomMetric, "*", buffer, permanently = true)
  //Kamon(Metrics).subscribe(ActorMetrics, "*", printer, permanently = true)

  implicit val timeout = Timeout(30 seconds)

  val pipeline = sendReceive
  val replier = system.actorOf(Props[Replier].withRouter(RoundRobinRouter(nrOfInstances = 2)), "replier")
  val random = new Random()

  val requestCountRecorder = Kamon(Metrics).register(CustomMetric("GetCount"), CustomMetric.histogram(10, 3, Scale.Unit))

  startServer(interface = "localhost", port = 9090) {
    get {
      path("test") {
        traceName("test") {
          complete {
            val futures = pipeline(Get("http://10.254.209.14:8000/")).map(r ⇒ "Ok") :: pipeline(Get("http://10.254.209.14:8000/")).map(r ⇒ "Ok") :: Nil

            Future.sequence(futures).map(l ⇒ "Ok")
          }
        }
      } ~
        path("site") {
          complete {
            pipeline(Get("http://localhost:9090/site-redirect"))
          }
        } ~
        path("site-redirect") {
          redirect(Uri("http://localhost:4000/"), StatusCodes.MovedPermanently)

        } ~
        path("reply" / Segment) { reqID ⇒
          traceName("reply") {
            complete {
              (replier ? reqID).mapTo[String]
            }
          }
        } ~
        path("ok") {
          traceName("OK") {
            complete {
              println("Defined: " + requestCountRecorder)
              requestCountRecorder.map(_.record(1))
              "ok"
            }
          }
        } ~
        path("future") {
          traceName("OK-Future") {
            dynamic {
              complete(Future { "OK" })
            }
          }
        } ~
        path("kill") {
          dynamic {
            replier ! PoisonPill
            complete(Future { "OK" })
          }
        } ~
        path("error") {
          complete {
            throw new NullPointerException
            "okk"
          }
        }
    }
  }

}

class PrintWhatever extends Actor {
  def receive = {
    case TickMetricSnapshot(from, to, metrics) ⇒
      println(metrics.map { case (key, value) ⇒ key.name + " => " + value.metrics.mkString(",") }.mkString("|"))
    case anything ⇒ println(anything)
  }
}

object Verifier extends App {

  def go: Unit = {
    import scala.concurrent.duration._
    import spray.client.pipelining._

    implicit val system = ActorSystem("test")
    import system.dispatcher

    implicit val timeout = Timeout(30 seconds)

    val pipeline = sendReceive

    val futures = Future.sequence(for (i ← 1 to 500) yield {
      pipeline(Get("http://127.0.0.1:9090/reply/" + i)).map(r ⇒ r.entity.asString == i.toString)
    })
    println("Everything is: " + Await.result(futures, 10 seconds).forall(a ⇒ a == true))
  }

}

class Replier extends Actor with ActorLogging {
  def receive = {
    case anything ⇒
      if (TraceRecorder.currentContext.isEmpty)
        log.warning("PROCESSING A MESSAGE WITHOUT CONTEXT")

      log.info("Processing at the Replier, and self is: {}", self)
      sender ! anything
  }
}

object PingPong extends App {
  val system = ActorSystem()
  val pinger = system.actorOf(Props(new Actor {
    def receive: Actor.Receive = { case "pong" ⇒ sender ! "ping" }
  }))
  val ponger = system.actorOf(Props(new Actor {
    def receive: Actor.Receive = { case "ping" ⇒ sender ! "pong" }
  }))

  pinger.tell("pong", ponger)

}
