package com.unstablebuild.autobreaker.guice

import akka.actor.{ActorSystem, Scheduler}
import akka.pattern.CircuitBreakerOpenException
import com.google.inject._
import com.google.inject.name.Names
import com.google.inject.util.Modules
import com.unstablebuild.autobreaker.{AutoBreaker, Settings}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FlatSpec, MustMatchers}

import scala.concurrent.{ExecutionContext, Future}

class AutoBreakerGuiceTest extends FlatSpec with MustMatchers with ScalaFutures with BeforeAndAfterAll {

  implicit val dispatcher = BaseModule.system.dispatcher

  it must "wrap annotated classes" in new context {

    override def module = new AbstractModule {
      override def configure(): Unit = bind(classOf[TestService]).to(classOf[FailingTestService])
    }

    val service = injector.getInstance(classOf[TestService])

    val result = (1 to 10).map(_ => service.message).last

    result.failed.futureValue mustBe a[CircuitBreakerOpenException]
  }

  it must "wrap annotated instances" in new context {

    override def module = new AbstractModule {
      override def configure(): Unit = bind(classOf[TestService]).toInstance(new FailingTestService)
    }

    val service = injector.getInstance(classOf[TestService])

    val result = (1 to 10).map(_ => service.message).last

    result.failed.futureValue mustBe a[CircuitBreakerOpenException]
  }

  it must "start eager singletons just once" in new context {

    override def module = new AbstractModule {
      override def configure(): Unit = bind(classOf[EagerService]).asEagerSingleton()
    }

    injector.getInstance(classOf[EagerService])
    injector.getInstance(classOf[EagerService])

    EagerService.instanceCount must be (1)
  }

  it must "use provided configurations" in new context {

    case object MyError extends Exception

    override def module = new AbstractModule {
      override def configure(): Unit = {
        bind(classOf[Settings]).toInstance(AutoBreaker.defaultSettings.copy(knownError = _ == MyError))
        bind(classOf[TestService]).toInstance(new CustomFailureTestService(MyError))
      }
    }

    val service = injector.getInstance(classOf[TestService])

    val result = (1 to 10).map(_ => service.message).last

    result.failed.futureValue must be (MyError)
  }

  it must "use named configurations" in new context {

    case object MyError extends Exception

    override def module = new AbstractModule {
      override def configure(): Unit = {
        bind(classOf[Settings])
          .annotatedWith(Names.named("custom-conf"))
          .toInstance(AutoBreaker.defaultSettings.copy(knownError = _ == MyError))
        bind(classOf[TestService]).toInstance(new CustomNamedFailureTestService(MyError))
        bind(classOf[AnotherService]).toInstance(new FailingAnotherService(MyError))
      }
    }

    val testService = injector.getInstance(classOf[TestService])
    val anotherService = injector.getInstance(classOf[AnotherService])

    val testServiceResult = (1 to 10).map(_ => testService.message).last
    val anotherServiceResult = (1 to 10).map(_ => anotherService.message).last

    testServiceResult.failed.futureValue must be (MyError)
    anotherServiceResult.failed.futureValue mustBe a[CircuitBreakerOpenException]
  }

  it must "fallback to provided configuration if cannot find named" in new context {

    case object MyFirstError extends Exception
    case object MySecondError extends Exception

    override def module = new AbstractModule {
      override def configure(): Unit = {
        bind(classOf[Settings])
          .toInstance(AutoBreaker.defaultSettings.copy(knownError = _ == MyFirstError))

        bind(classOf[Settings])
          .annotatedWith(Names.named("wrong-conf-name"))
          .toInstance(AutoBreaker.defaultSettings.copy(knownError = _ == MySecondError))

        bind(classOf[TestService]).toInstance(new CustomNamedFailureTestService(MyFirstError))
      }
    }

    val service = injector.getInstance(classOf[TestService])

    val result = (1 to 10).map(_ => service.message).last

    result.failed.futureValue must be (MyFirstError)
  }

  trait context {

    def module: Module

    lazy val injector =
      Guice.createInjector(
        AutoBreakerGuice.prepare(
          Modules.combine(
            BaseModule, module)))

  }

  override protected def afterAll(): Unit = BaseModule.system.terminate()

}

object BaseModule extends AbstractModule {

  val system = ActorSystem()
  val dispatcher = system.dispatcher

  override def configure(): Unit = {
    bind(classOf[Scheduler]).toInstance(system.scheduler)
    bind(classOf[ExecutionContext]).toInstance(dispatcher)
  }

}

trait TestService {
  def message: Future[String]
}

@WithCircuitBreaker
class FailingTestService extends TestService {
  override def message: Future[String] = Future.failed(new Exception("failed"))
}

@WithCircuitBreaker
class CustomFailureTestService(error: Throwable) extends TestService {
  override def message: Future[String] = Future.failed(error)
}

@WithCircuitBreaker(name = "custom-conf")
class CustomNamedFailureTestService(error: Throwable) extends TestService {
  override def message: Future[String] = Future.failed(error)
}

trait AnotherService {
  def message: Future[String]
}

@WithCircuitBreaker
class FailingAnotherService(error: Throwable) extends AnotherService {
  override def message: Future[String] = Future.failed(error)
}

class EagerService {
  EagerService.inc()
}

object EagerService {

  private var _count = 0

  private[EagerService] def inc(): Unit = synchronized(_count += 1)

  def instanceCount: Int = _count

}
