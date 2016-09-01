package com.unstablebuild.autobreaker.guice

import java.lang.annotation.Annotation

import akka.actor.Scheduler
import com.google.inject._
import com.google.inject.spi._
import com.unstablebuild.autobreaker.{AutoBreaker, Settings}

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext
import scala.util.Try

object AutoBreakerGuice {

  def prepare(module: Module): Module = {

    val elements = Elements.getElements(module).flatMap {
      case linked: LinkedKeyBinding[_] if needsCircuitBreaker(linked.getLinkedKey.getTypeLiteral.getRawType) =>
        overwrite(linked, new LinkedProvider(linked.getLinkedKey))
      case instance: InstanceBinding[_] if needsCircuitBreaker(instance.getClass) =>
        overwrite(instance, new InstanceProvider(instance.getInstance().asInstanceOf[AnyRef]))
      case other =>
        Seq(other)
    }

    Elements.getModule(elements)
  }

  private def needsCircuitBreaker(clazz: Class[_]): Boolean =
    clazz.isAnnotationPresent(classOf[WithCircuitBreaker])

  private def overwrite(binding: Binding[_], provider: Provider[_]): Seq[Element] =
    Elements.getElements(new OverrideModule(binding, provider))

  class LinkedProvider(key: Key[_]) extends BaseProvider {
    override def instance: AnyRef = injector.getInstance(key).asInstanceOf[AnyRef]
  }

  class InstanceProvider(val instance: AnyRef) extends BaseProvider

  trait BaseProvider extends Provider[Any] {

    @Inject() val injector: Injector = null

    implicit lazy val ec = injector.getInstance(classOf[ExecutionContext])
    implicit lazy val scheduler = injector.getInstance(classOf[Scheduler])

    def settings: Settings = Try(injector.getInstance(classOf[Settings])).getOrElse(AutoBreaker.defaultSettings)

    def instance: AnyRef

    override def get(): AnyRef = AutoBreaker.proxy(instance, settings)

  }

  class OverrideModule(binding: Binding[_], provider: Provider[_]) extends AbstractModule {
    override def configure(): Unit = {
      val newBinding = bind(binding.getKey.asInstanceOf[Key[Any]]).toProvider(provider)
      binding.acceptScopingVisitor(new BindingScopingVisitor[Unit] {
        override def visitEagerSingleton(): Unit = newBinding.asEagerSingleton()
        override def visitNoScoping(): Unit = ()
        override def visitScope(scope: Scope): Unit = newBinding.in(scope)
        override def visitScopeAnnotation(scopeAnnotation: Class[_ <: Annotation]): Unit = newBinding.in(scopeAnnotation)
      })
    }
  }

}
