package com.unstablebuild.autobreaker

import java.lang.reflect.Proxy

import akka.actor.Scheduler

import scala.concurrent.ExecutionContext

object CircuitBreakerProxy {

  val defaultSettings = CircuitBreakerSettings()

  def proxy[T](obj: T, settings: Settings = defaultSettings)(implicit ec: ExecutionContext, scheduler: Scheduler): T = {
    val proxy = Proxy.newProxyInstance(
      obj.getClass.getClassLoader,
      obj.getClass.getInterfaces,
      new CircuitBreakerHandler(obj, settings))

    proxy.asInstanceOf[T]
  }

}
