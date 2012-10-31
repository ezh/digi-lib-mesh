package org.digimead.digi.lib.mesh

import org.scala_tools.subcut.inject.NewBindingModule
import org.digimead.digi.lib.DependencyInjection
package object message {
  lazy val default = new NewBindingModule(module => {
    module.bind[Seq[Message.Factory]] toSingle { Seq(DiffieHellman, Ping) }
  })
}