package com.google.inject

import com.unstablebuild.autobreaker.guice.AutoBreakerGuice

trait AbstractAutoBreakerModule extends AbstractModule { self =>

  private[this] var configuring = false

  override final def configure(): Unit = {
    require(!configuring, "Module already being configured")
    configuring = true

    val module = new EmptyModule({ binder =>
      val originalBinder = self.binder()
      self.binder = binder
      setup()
      self.binder = originalBinder
    })

    install(AutoBreakerGuice.prepare(module))
    configuring = false
  }

  def setup(): Unit

}

class EmptyModule(withBinder: Binder => Unit) extends AbstractModule {
  override def configure(): Unit = withBinder(binder)
}
