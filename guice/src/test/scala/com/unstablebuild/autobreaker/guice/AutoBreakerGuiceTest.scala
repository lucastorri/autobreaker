package com.unstablebuild.autobreaker.guice

import akka.actor.{ActorSystem, Scheduler}
import akka.pattern.CircuitBreakerOpenException
import com.google.inject._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FlatSpec, MustMatchers}

import scala.concurrent.{ExecutionContext, Future}

class AutoBreakerGuiceTest extends FlatSpec with MustMatchers with ScalaFutures with BeforeAndAfterAll {

  val module = new TestModule()
  implicit val dispatcher = module.dispatcher

  val injector = Guice.createInjector(AutoBreakerGuice.prepare(module))
  val service = injector.getInstance(classOf[TestService])

  it must "wrap annotated classes" in {
    val result = (1 to 10).map(_ => service.message).last

    result.failed.futureValue mustBe a[CircuitBreakerOpenException]
  }

  it must "start eager singletons just once" in {
    AnotherService.instanceCount must be (1)
  }

  override protected def afterAll(): Unit = module.system.terminate()

}

trait TestService {
  def message: Future[String]
}

@WithCircuitBreaker
class FailingTestService extends TestService {
  override def message: Future[String] = Future.failed(new Exception("failed"))
}

class TestModule extends AbstractModule {

  val system = ActorSystem()
  val dispatcher = system.dispatcher

  override def configure(): Unit = {

    bind(classOf[TestService]).to(classOf[FailingTestService])

    bind(classOf[Scheduler]).toInstance(system.scheduler)
    bind(classOf[ExecutionContext]).toInstance(dispatcher)

    bind(classOf[AnotherService]).asEagerSingleton()
  }

}

class AnotherService {
  AnotherService.inc()
}

object AnotherService {

  private var _count = 0

  private[AnotherService] def inc(): Unit = synchronized(_count += 1)

  def instanceCount: Int = _count

}
