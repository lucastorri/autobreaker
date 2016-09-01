`autobreaker` wraps your objects and intercepts all methods returning [`Future`s](http://www.scala-lang.org/api/current/#scala.concurrent.Future) with a [circuit breaker](http://martinfowler.com/bliki/CircuitBreaker.html).

It is based on [atmos](https://github.com/zmanio/atmos) and [Akka's Circuit Breaker](http://doc.akka.io/docs/akka/current/common/circuitbreaker.html), using [Java's Proxy](https://docs.oracle.com/javase/8/docs/api/java/lang/reflect/Proxy.html) to intercept method calls.


## Usage

```scala
import com.unstablebuild.autobreaker._

trait MyService {

  def add(a: Int, b: Int): Future[Int]

}

val realService: MyService = ???
val serviceWithCircuitBreaker: MyService = CircuitBreakerProxy.proxy(realService)

// Use it normally, and if too many errors occur, the method will start to fail fast
serviceWithCircuitBreaker.add(11, 23)
```

Please check the unit tests for more examples.


## Install

To use it with [SBT](http://www.scala-sbt.org/), add the following to your `build.sbt` file:

```scala
libraryDependencies += TODO
```
