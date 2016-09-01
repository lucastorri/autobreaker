package com.unstablebuild.autobreaker

import atmos.backoff.ExponentialBackoff
import atmos.dsl._

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

case class LimitedExponentialBackoffPolicy(maximum: FiniteDuration, initial: FiniteDuration) extends BackoffPolicy {

  private val exponential = ExponentialBackoff(initial)

  override def nextBackoff(attempts: Int, outcome: Try[Any]): FiniteDuration = {
    val next = exponential.nextBackoff(attempts, outcome)
    if (next > maximum) maximum else next
  }

}
