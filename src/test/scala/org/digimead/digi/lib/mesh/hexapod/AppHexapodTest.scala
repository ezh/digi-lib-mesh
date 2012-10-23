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

package org.digimead.digi.lib.mesh.hexapod

/*import java.util.UUID

import org.digimead.digi.lib.aop.Loggable
import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.Record
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.Mesh.mesh2implementation
import org.digimead.digi.lib.mesh.Peer
import org.digimead.lib.test.TestHelperLogging
import org.scalatest.BeforeAndAfter
import org.scalatest.fixture.FunSuite
import org.scalatest.matchers.ShouldMatchers

class AppHexapodTest_j1 extends FunSuite with BeforeAndAfter with ShouldMatchers with TestHelperLogging {
  type FixtureParam = Map[String, Any]
  val log = Logging.commonLogger

  override def withFixture(test: OneArgTest) {
    withLogging(test.configMap) {
      test(test.configMap)
    }
  }

  before {
    Record.init(new Record.DefaultInit)
    Logging.init(new Logging.DefaultInit)
    Logging.resume
    Mesh.init(new Mesh.DefaultInit)
    Peer.init(new Peer.DefaultInit)
  }

  after {
    Logging.deinit
  }

  test("AppHexapod DiffieHellman initialization test") {
    conf =>
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
      val dh = local.getDiffieHellman.get
      assert(dh.g === 1)
      assert(dh.p === BigInt(1))
      assert(dh.publicKey === (1))
      assert(dh.secretKey === (1))
  }
}
*/