package com.unstablebuild.autobreaker

import akka.actor.ActorSystem
import akka.pattern.CircuitBreakerOpenException
import atmos.backoff.ConstantBackoff
import atmos.dsl._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, MustMatchers}

import scala.concurrent.Future
import scala.concurrent.duration._

class CircuitBreakerProxyTest extends FlatSpec with MustMatchers with ScalaFutures with BeforeAndAfterAll {

  val system = ActorSystem()
  implicit val dispatcher = system.dispatcher
  implicit val scheduler = system.scheduler

  it must "forward Object methods" in new context {

    serviceProxy.hashCode() must be (realService.hashCode())

    serviceProxy.toString must equal (realService.toString)

    serviceProxy must equal (realService)

  }

  it must "circuit break methods returning futures" in new context {

    7.times {
      serviceProxy.withFuture.futureValue must be (3)
      serviceProxy.withFuture(9).futureValue must be (81)
    }

    realService.fail = true

    val failing = 7.times {
      serviceProxy.withFuture
    }

    failing.last.failed.futureValue mustBe a[CircuitBreakerOpenException]
    serviceProxy.withFuture(7).failed.futureValue mustBe a[CircuitBreakerOpenException]

  }

  it must "not interfere with methods that don't return futures" in new context {

    10.times {
      serviceProxy.withoutFuture must equal ("hello")
      serviceProxy.withoutFuture("lucas") must equal ("hello lucas")
    }

    realService.fail = true

    10.times {
      an[Exception] must be thrownBy {
        serviceProxy.withoutFuture must equal ("hello")
      }
      an[Exception] must be thrownBy {
        serviceProxy.withoutFuture("lucas") must equal ("hello lucas")
      }
    }

  }

  it must "retry on errors before circuit is broken" in new context {

    realService.fail = true

    serviceProxy.withFuture.failed.futureValue
    realService.totalCalls must be (attempts)

    5.times {
      serviceProxy.withFuture.failed.futureValue
    }
    realService.totalCalls must be (settings.maxFailures)

  }

  it must "ignore retries on known errors" in new context {

    override val error: Throwable = SpecialError
    realService.fail = true

    serviceProxy.withFuture.failed.futureValue must be (SpecialError)
    realService.totalCalls must be (1)

  }

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = Span(500, Millis))

  trait context {

    val attempts = 2
    val settings: Settings = CircuitBreakerSettings(
      totalAttempts = attempts.attempts,
      maxFailures = 5,
      backoffPolicy = ConstantBackoff(5.millis),
      knownError = _ == SpecialError
    )
    val error: Throwable = new Exception("failing")

    lazy val realService = new ConfigurableTestService(error)
    lazy val serviceProxy = CircuitBreakerProxy.proxy[TestService](realService, settings)

  }

  implicit class Repeat(n: Int) {

    def times[T](f: => T): Seq[T] =
      (1 to n).map(_ => f)

  }

  override protected def afterAll(): Unit = system.terminate()

}

trait TestService {

  def withFuture: Future[Int]

  def withFuture(v: => Int): Future[Int]

  def withoutFuture: String

  def withoutFuture(name: String): String

}

class ConfigurableTestService(error: Throwable) extends TestService {

  var fail = false
  var totalCalls = 0

  override def withFuture: Future[Int] =
    call(if (fail) Future.failed(error) else Future.successful(3))

  override def withFuture(v: => Int): Future[Int] =
    call(if (fail) Future.failed(error) else Future.successful(v * v))

  override def withoutFuture: String =
    call(if (fail) throw error else "hello")

  override def withoutFuture(name: String): String =
    call(if (fail) throw error else s"hello $name")

  override def hashCode(): Int =
    23

  override def equals(obj: scala.Any): Boolean =
    super.equals(obj)

  override def toString: String =
    "TestService"

  private def call[T](v: => T): T = synchronized {
    totalCalls += 1
    v
  }

}

case object SpecialError extends Exception
