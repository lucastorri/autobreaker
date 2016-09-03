package com.unstablebuild.autobreaker

import atmos.dsl._

import scala.concurrent.duration._

case class CircuitBreakerSettings(
  totalAttempts: TerminationPolicy = 3.attempts,
  backoffPolicy: BackoffPolicy = LimitedExponentialBackoffPolicy(2.minutes, 1.second),
  maxFailures: Int = 5,
  callTimeout: FiniteDuration = 10.seconds,
  resetTimeout: FiniteDuration = 1.minute,
  knownError: Throwable => Boolean = _ => false
)
