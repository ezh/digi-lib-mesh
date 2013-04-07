/**
 * Digi-Lib-Mesh - distributed mesh library for Digi components
 *
 * Copyright (c) 2012-2013 Alexey Aksenov ezh@ezh.msk.ru
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.digimead.digi.lib

import java.util.UUID

import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.Peer
import org.digimead.digi.lib.mesh.hexapod.AppHexapod

import com.escalatesoft.subcut.inject.NewBindingModule

package object mesh {
  @volatile var isReady = false
  lazy val default = new NewBindingModule(module => {
    lazy val meshSingleton = DependencyInjection.makeInitOnce(implicit module => new Mesh)
    module.bind[Mesh.Interface] toModuleSingle { meshSingleton(_) }
    lazy val peerSingleton = DependencyInjection.makeInitOnce(implicit module => new Peer)
    module.bind[Peer.Interface] toModuleSingle { peerSingleton(_) }
  }) ~ endpoint.default ~ communication.default ~ message.default
  lazy val defaultFakeHexapod = new NewBindingModule(module => {
    module.bind[AppHexapod] toSingle { new AppHexapod(UUID.fromString("00000000-0000-0000-0000-000000000000")) }
  })
  DependencyInjection.setPersistentInjectable("org.digimead.digi.lib.mesh.Mesh$")
  DependencyInjection.setPersistentInjectable("org.digimead.digi.lib.mesh.Peer$")
  DependencyInjection.setPersistentInjectable("org.digimead.digi.lib.mesh.hexapod.Hexapod$")
}
