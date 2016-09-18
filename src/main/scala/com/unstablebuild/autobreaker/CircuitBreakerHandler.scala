package com.unstablebuild.autobreaker

import java.lang.reflect.{InvocationHandler, InvocationTargetException, Method}

import akka.actor.Scheduler
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.{Proxy => BaseProxy}

class CircuitBreakerHandler(val self: Any, breaker: AutoBreaker)(implicit ec: ExecutionContext, scheduler: Scheduler)
  extends InvocationHandler
    with BaseProxy
    with StrictLogging {

  private val future = classOf[Future[_]]

  override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef = {

    def callMethod: AnyRef = method.invoke(self, args: _*)

    try {
      method.getReturnType match {
        case `future` if canWrap(method) =>
          breaker.call(future.cast(callMethod))
        case _ =>
          callMethod
      }
    } catch {
      case e: InvocationTargetException => throw e.getTargetException
    }
  }

  private def canWrap(method: Method): Boolean =
    !self.getClass.getMethod(method.getName, method.getParameterTypes: _*).isAnnotationPresent(classOf[NoCircuitBreaker])

}
