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

package org.digimead.digi.lib.mesh.hexapod

import java.util.UUID

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.Mesh.mesh2implementation
import org.digimead.lib.test.LoggingHelper
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers

import com.escalatesoft.subcut.inject.NewBindingModule

class AppHexapodTest extends FunSuite with ShouldMatchers with LoggingHelper with Loggable {
  after { adjustLoggingAfter }
  before {
    DependencyInjection(org.digimead.digi.lib.mesh.defaultFakeHexapod ~
      org.digimead.digi.lib.mesh.default ~ org.digimead.digi.lib.default, false)
    adjustLoggingBefore
  }

  test("AppHexapod DiffieHellman initialization test") {
    Mesh.subscribe(new Mesh.Sub {
      def notify(pub: Mesh.Pub, event: Mesh.Event) = event match {
        case Mesh.Event.Register(appHexapod: AppHexapod) =>
          // overwrite DiffieHellman while initialization
          appHexapod.setDiffieHellman(1, BigInt(1), BigInt(1), BigInt(1))
        case _ =>
      }
    })
    val uuid = UUID.randomUUID()
    val local = new AppHexapod(uuid)
    val custom = new NewBindingModule(module => {
      module.bind[AppHexapod] toSingle { local }
    })
    Hexapod
    val dh = local.getDiffieHellman.get
    assert(dh.g === 1)
    assert(dh.p === BigInt(1))
    assert(dh.publicKey === (1))
    assert(dh.secretKey === (1))
  }

  override def beforeAll(configMap: Map[String, Any]) { adjustLoggingBeforeAll(configMap) }
}
