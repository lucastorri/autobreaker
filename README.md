# autobreaker

`autobreaker` is a [Scala](http://scala-lang.org/) library that wraps your objects and intercepts all methods returning [`Future`s](http://www.scala-lang.org/api/current/#scala.concurrent.Future) with a [circuit breaker](http://martinfowler.com/bliki/CircuitBreaker.html).

It is based on [atmos](https://github.com/zmanio/atmos) and [Akka's Circuit Breaker](http://doc.akka.io/docs/akka/current/common/circuitbreaker.html), using [Java's Proxy](https://docs.oracle.com/javase/8/docs/api/java/lang/reflect/Proxy.html) to intercept method calls.


## Usage

```scala
import com.unstablebuild.autobreaker._

trait MyService {
  def add(a: Int, b: Int): Future[Int]
}

class FailingService extends MyService {
  override def add(a: Int, b: Int): Future[Int] = Future.failed(new Exception("error"))
}

val realService: MyService = new FailingService
val serviceWithCircuitBreaker = AutoBreaker.proxy(realService)

// Make it fail a few times
(1 to 10).foreach { _ => serviceWithCircuitBreaker.add(11, 23) }

// Try again and see that the service isn't called
serviceWithCircuitBreaker.add(11, 23)
// [warn] e.c.CircuitBreakerProxy - Attempt 1 of operation interrupted: akka.pattern.CircuitBreakerOpenException: Circuit Breaker is open; calls are failing fast
// akka.pattern.CircuitBreakerOpenException: Circuit Breaker is open; calls are failing fast

```

Please check the unit tests for more examples.

### Skipping a specific method

You can prevent a method from being intercept with the `NoCircuitBreaker` annotation. For example:

```scala
class AnotherImplementation extends MyService {
  @NoCircuitBreaker
  override def add(a: Int, b: Int): Future[Int] = Future.failed(new Exception("error"))
}

```


## Install

To use it with [SBT](http://www.scala-sbt.org/), add the following to your `build.sbt` file:

```scala
resolvers += Resolver.sonatypeRepo("public")

libraryDependencies += "com.unstablebuild" %% "autobreaker" % "0.5.2"
```


## Configuration

The following settings (and their default values) are available:

```scala
case class CircuitBreakerSettings(
  totalAttempts: TerminationPolicy = 3.attempts,
  backoffPolicy: BackoffPolicy = LimitedExponentialBackoffPolicy(2.minutes, 1.second),
  maxFailures: Int = 5,
  callTimeout: FiniteDuration = 10.seconds,
  resetTimeout: FiniteDuration = 1.minute,
  knownError: Throwable => Boolean = _ => false
)
```

Please see `atmos` and `akka` documentations for further reference.

`knownError` is used to decide if, given an exception type returned by the method, it should be retried or not, or counted as a failure on the circuit breaker. This allows the usage of custom exceptions to communicate the users about errors that don't affect the used method. For instance, you can decide that an exception communicating validation issues should not be considered as bad as a failure when communicating with a downstream system.


## Guice Integration

`autobreaker-guice` allows you to annotate your implementations with `@WithCircuitBreaker` and modify your [Guice](https://github.com/google/guice) module to automatically wrap the generated objects.

In order to use it, you need to add the following to your dependencies:

```scala
libraryDependencies += "com.unstablebuild" %% "autobreaker-guice" % "0.5.2"
```

Afterwards, it can be used like this:

```scala

@WithCircuitBreaker
class DownstreamService extends MyService {
  override def add(a: Int, b: Int): Future[Int] = ???
}

class TestModule extends AbstractModule {
  override def configure(): Unit = bind(classOf[MyService]).to(classOf[DownstreamService])
}

val module = new TestModule()
val injector = Guice.createInjector(AutoBreakerGuice.prepare(module))
val service = injector.getInstance(classOf[MyService])
```

An `ExecutionContext` and `Scheduler` should be available on your bindings.


### Settings

You can inject settings in two ways:

1. Bind a `CircuitBreakerSettings`;
2. Bind a named `CircuitBreakerSettings`.

For example:

```
bind(classOf[CircuitBreakerSettings])
          .toInstance(mySettings)

bind(classOf[CircuitBreakerSettings])
  .annotatedWith(Names.named("custom-conf"))
  .toInstance(mySettings)
```

To use named Settings, you can add the use named to the `WithCircuitBreaker` annotation:

```
@WithCircuitBreaker(name = "custom-conf")
class CustomNamedFailureTestService(error: Throwable) extends MyService { ... }
```

If a Settings instance with the given name is not found, the library will try to use an unnamed one. If it also can't fidn one, it will fall back to `AutoBreaker.defaultSettings`.
