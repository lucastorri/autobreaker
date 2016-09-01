package com.unstablebuild.autobreaker

import java.lang.reflect.{InvocationHandler, InvocationTargetException, Method}

import akka.actor.Scheduler
import akka.pattern.{CircuitBreaker, CircuitBreakerOpenException}
import atmos.dsl._
import atmos.monitor.LogEventsWithSlf4j
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.{Proxy => BaseProxy}

class CircuitBreakerHandler(val self: Any, settings: Settings)(implicit ec: ExecutionContext, scheduler: Scheduler)
  extends InvocationHandler
    with BaseProxy
    with StrictLogging {

  private val future = classOf[Future[_]]
  private val breaker = new CircuitBreaker(scheduler,
    maxFailures = settings.maxFailures,
    callTimeout = settings.callTimeout,
    resetTimeout = settings.resetTimeout)

  private implicit val retryPolicy = retryFor(settings.totalAttempts)
    .using(settings.backoffPolicy)
    .monitorWith(LogEventsWithSlf4j(logger.underlying))
    .onError {
      case error: Throwable if settings.knownError(error) => stopRetrying
      case open: CircuitBreakerOpenException => stopRetrying
    }

  override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef = {

    def callMethod: AnyRef = method.invoke(self, args: _*)

    try {
      method.getReturnType match {
        case `future` => retryAsync()(revealError(breaker.withCircuitBreaker(hideError(future.cast(callMethod)))))
        case _ => callMethod
      }
    } catch {
      case e: InvocationTargetException => throw e.getTargetException
    }
  }

  private def hideError(f: Future[_]): Future[_] =
    f.recover {
      case error: Throwable if settings.knownError(error) => KnownError(error)
    }

  private def revealError(f: Future[_]): Future[_] =
    f.map {
      case KnownError(e) => throw e
      case value => value
    }

  private case class KnownError(e: Throwable)

}
