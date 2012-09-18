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
import org.digimead.digi.lib.mesh.message.Ping
import org.digimead.digi.lib.util.Serialization
import org.scalatest.BeforeAndAfter
import org.scalatest.fixture.FunSuite
import org.scalatest.matchers.ShouldMatchers.convertToAnyRefShouldWrapper
import org.scalatest.matchers.ShouldMatchers.convertToLongShouldWrapper
import org.scalatest.matchers.ShouldMatchers.equal
import org.digimead.digi.lib.mesh.Mesh

class MessageTestMultiJvmNode1 extends FunSuite with BeforeAndAfter {
  type FixtureParam = Map[String, Any]

  override def withFixture(test: OneArgTest) {
    try {
      if (test.configMap.contains("log") || System.getProperty("log") != null)
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
  }

  after {
    Logging.deinit
  }

  test("message serialization test") {
    conf =>
      val ping = Ping(UUID.randomUUID(), None)
      Logging.commonLogger.debug("ping " + ping + " ts: " + ping.timestamp)
      val pingAsByteArray = Serialization.serializeToArray(ping)
      val ping2 = Serialization.deserializeFromArray[Ping](pingAsByteArray)
      Some(ping) should equal(ping2)
      ping.timestamp should equal(ping2.get.timestamp)

      val ping3 = Ping(UUID.randomUUID(), Some(UUID.randomUUID()))
      Logging.commonLogger.debug("ping " + ping3 + " ts: " + ping3.timestamp)
      val ping3AsByteArray = Serialization.serializeToArray(ping3)
      val ping4 = Serialization.deserializeFromArray[Ping](ping3AsByteArray)
      Some(ping3) should equal(ping4)
      ping3.timestamp should equal(ping4.get.timestamp)
  }
}
