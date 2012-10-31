/**
 * Digi-Lib-Mesh - distributed mesh library for Digi components
 *
 * Copyright (c) 2012 Alexey Aksenov ezh@ezh.msk.ru
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

import org.scala_tools.subcut.inject.NewBindingModule
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.digi.lib.mesh.hexapod.AppHexapod
import java.util.UUID

package object mesh {
  @volatile var isReady = false
  lazy val default = new NewBindingModule(module => {
    lazy val meshSingleton = DependencyInjection.makeSingleton(implicit module => new Mesh, true)
    module.bind[Mesh.Interface] toModuleSingle { meshSingleton(_) }
    lazy val peerSingleton = DependencyInjection.makeSingleton(implicit module => new Peer, true)
    module.bind[Peer.Interface] toModuleSingle { peerSingleton(_) }
    module.bind[Hexapod.AppHexapod] toSingle { new AppHexapod(UUID.fromString("00000000-0000-0000-0000-000000000000")) }
  }) ~ endpoint.default ~ communication.default ~ message.default
}