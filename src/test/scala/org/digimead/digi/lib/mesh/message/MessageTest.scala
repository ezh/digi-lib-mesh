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

package org.digimead.digi.lib.mesh.message

import java.util.UUID

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.digi.lib.util.Serialization
import org.digimead.lib.test.LoggingHelper
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers

class MessageTest extends FunSuite with ShouldMatchers with LoggingHelper with Loggable {
  after { adjustLoggingAfter }
  before {
    DependencyInjection(org.digimead.digi.lib.mesh.defaultFakeHexapod ~
      org.digimead.digi.lib.mesh.default ~ org.digimead.digi.lib.default, false)
    adjustLoggingBefore
  }

  test("message serialization test") {
    val ping = Ping(UUID.randomUUID(), None)(true)
    log.debug("ping " + ping + " ts: " + ping.timestamp)
    val pingAsByteArray = Serialization.serializeToArray(ping)
    val ping2 = Serialization.deserializeFromArray[Ping](pingAsByteArray)
    Some(ping) should equal(ping2)
    ping.timestamp should equal(ping2.get.timestamp)

    val ping3 = Ping(UUID.randomUUID(), Some(UUID.randomUUID()))(true)
    log.debug("ping " + ping3 + " ts: " + ping3.timestamp)
    val ping3AsByteArray = Serialization.serializeToArray(ping3)
    val ping4 = Serialization.deserializeFromArray[Ping](ping3AsByteArray)
    Some(ping3) should equal(ping4)
    ping3.timestamp should equal(ping4.get.timestamp)
  }

  override def beforeAll(configMap: Map[String, Any]) { adjustLoggingBeforeAll(configMap) }
}
