package com.unstablebuild.autobreaker

import java.lang.reflect.Proxy

import akka.actor.Scheduler
import akka.pattern.{CircuitBreaker, CircuitBreakerOpenException}
import atmos.dsl._
import atmos.monitor.LogEventsWithSlf4j
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}

class AutoBreaker(settings: Settings)(implicit ec: ExecutionContext, scheduler: Scheduler) extends StrictLogging {

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

  def call[T](f: => Future[T]): Future[T] =
    retryAsync()(revealError(breaker.withCircuitBreaker(hideError(f))))

  private def hideError[T](f: Future[T]): Future[_] =
    f.recover {
      case error: Throwable if settings.knownError(error) => KnownError(error)
    }

  private def revealError[T](f: Future[_]): Future[T] =
    f.map {
      case KnownError(e) => throw e
      case value => value.asInstanceOf[T]
    }

  private case class KnownError(e: Throwable)

}

object AutoBreaker {

  val defaultSettings = CircuitBreakerSettings()

  def proxy[T](obj: T, settings: Settings = defaultSettings)(implicit ec: ExecutionContext, scheduler: Scheduler): T = {
    val proxy = Proxy.newProxyInstance(
      obj.getClass.getClassLoader,
      interfaces(obj.getClass),
      new CircuitBreakerHandler(obj, apply(settings)))

    proxy.asInstanceOf[T]
  }

  def apply(settings: Settings = defaultSettings)(implicit ec: ExecutionContext, scheduler: Scheduler): AutoBreaker =
    new AutoBreaker(settings)

  private def interfaces(clazz: Class[_]): Array[java.lang.Class[_]] =
    Stream.iterate[Class[_]](clazz)(_.getSuperclass)
      .takeWhile(_ != null)
      .flatMap(_.getInterfaces)
      .toArray

}
