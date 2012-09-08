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

package org.digimead.digi.lib.mesh.communication

import java.util.UUID

import org.digimead.digi.lib.log.ConsoleLogger
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.log.Record
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.communication.Communication.communication2implementation
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.digi.lib.mesh.message.Ping
import org.scalatest.BeforeAndAfter
import org.scalatest.fixture.FunSuite
import org.scalatest.matchers.ShouldMatchers.convertToAnyShouldWrapper
import org.scalatest.matchers.ShouldMatchers.equal

class CommunicationTest extends FunSuite with BeforeAndAfter {
  type FixtureParam = Map[String, Any]

  override def withFixture(test: OneArgTest) {
    try {
      if (test.configMap.contains("log"))
        Logging.addLogger(ConsoleLogger)
      test(test.configMap)
    } finally {
      Logging.delLogger(ConsoleLogger)
    }
  }

  before {
    Record.init(new Record.DefaultInit)
    Logging.init(new Logging.DefaultInit)
    Logging.resume
    Mesh.init(new Mesh.DefaultInit)
    Communication.init(new Communication.DefaultInit)
  }

  after {
    Logging.deinit
  }

  test("communication test") {
    conf =>
      val ping = Ping(UUID.randomUUID(), None, None)
      Communication.push(ping) should equal(true)
      Communication.push(ping) should equal(false)
      Communication.react(Stimulus.IncomingMessage(ping))

      val heighborHexapod = new Hexapod(UUID.randomUUID)
      val pingFromHexapod = Ping(heighborHexapod.uuid, None, None)
      Communication.push(pingFromHexapod)
  }
}
