package org.digimead.digi.lib.mesh

import org.scala_tools.subcut.inject.NewBindingModule

package object endpoint {
  lazy val default = new NewBindingModule(module => {
    module.bind[Seq[Endpoint.Factory]] toSingle { Seq(LocalEndpoint, UDPRemoteEndpoint) }
  })
}