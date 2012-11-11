package org.digimead.digi.lib.mesh

import com.escalatesoft.subcut.inject.NewBindingModule

package object endpoint {
  lazy val default = new NewBindingModule(module => {
    module.bind[Seq[Endpoint.Factory]] toSingle { Seq(LocalEndpoint, UDPRemoteEndpoint) }
  })
}