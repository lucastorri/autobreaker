package com.unstablebuild.autobreaker.guice

import java.lang.annotation.Annotation

import akka.actor.Scheduler
import com.google.inject._
import com.google.inject.name.{Named, Names}
import com.google.inject.spi._
import com.typesafe.scalalogging.StrictLogging
import com.unstablebuild.autobreaker.{AutoBreaker, Settings}

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext
import scala.util.Try

object AutoBreakerGuice extends StrictLogging {

  def prepare(module: Module): Module = {

    val elements = Elements.getElements(module).flatMap {

      case linked: LinkedKeyBinding[_] if needsCircuitBreaker(linked.getLinkedKey.getTypeLiteral.getRawType) =>
        overwrite(linked, new LinkedProvider(
          linked.getLinkedKey,
          annotationIn(linked.getLinkedKey.getTypeLiteral.getRawType)))

      case instance: InstanceBinding[_] if needsCircuitBreaker(instance.getInstance.getClass) =>
        overwrite(instance, new InstanceProvider(
          instance.getInstance.asInstanceOf[AnyRef],
          annotationIn(instance.getInstance.getClass)))

      case other =>
        Seq(other)

    }

    Elements.getModule(elements)
  }

  private def needsCircuitBreaker(clazz: Class[_]): Boolean =
    clazz.isAnnotationPresent(classOf[WithCircuitBreaker])

  private def annotationIn(clazz: Class[_]): WithCircuitBreaker =
    clazz.getAnnotation(classOf[WithCircuitBreaker])

  private def overwrite(binding: Binding[_], provider: Provider[_]): Seq[Element] =
    Elements.getElements(new OverrideModule(binding, provider))

  class LinkedProvider(key: Key[_], val annotation: WithCircuitBreaker) extends BaseProvider {
    override lazy val instance: AnyRef = injector.getInstance(key).asInstanceOf[AnyRef]
  }

  class InstanceProvider(val instance: AnyRef, val annotation: WithCircuitBreaker) extends BaseProvider

  trait BaseProvider extends Provider[Any] {

    @Inject() val injector: Injector = null

    implicit lazy val ec = injector.getInstance(classOf[ExecutionContext])
    implicit lazy val scheduler = injector.getInstance(classOf[Scheduler])

    def annotation: WithCircuitBreaker

    def settings: Settings =
      Try
        .apply {
          val settings = injector.getInstance(Key.get(classOf[Settings], Names.named(annotation.name())))
          logger.debug(s"Using named settings ${annotation.name()} for ${instance.getClass}")
          settings
        }
        .recover {
          case error if annotation.name().nonEmpty =>
            logger.warn(s"Could not find settings with name ${annotation.name()}")
            throw error
        }
        .orElse(Try {
          val settings = injector.getInstance(classOf[Settings])
          logger.debug(s"Using provided settings for ${instance.getClass}")
          settings
        })
        .getOrElse {
          logger.debug(s"Using default settings for ${instance.getClass}")
          AutoBreaker.defaultSettings
        }

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
