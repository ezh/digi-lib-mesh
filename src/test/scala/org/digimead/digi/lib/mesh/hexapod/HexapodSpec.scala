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
import org.digimead.digi.lib.mesh.endpoint.Endpoint
import org.digimead.digi.lib.mesh.endpoint.LocalEndpoint
import org.digimead.lib.test.LoggingHelper
import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers

class HexapodSpec extends FunSpec with ShouldMatchers with LoggingHelper with Loggable {
  after { adjustLoggingAfter }
  before {
    DependencyInjection(org.digimead.digi.lib.mesh.defaultFakeHexapod ~
      org.digimead.digi.lib.mesh.default ~ org.digimead.digi.lib.default, false)
    adjustLoggingBefore
  }

  describe("A Hexapod") {
    it("should handle endpoints") {
      val hexapod1 = Hexapod(UUID.randomUUID())
      val ep1 = hexapod1.addEndpoint(new LocalEndpoint(Endpoint.Direction.InOut, new LocalEndpoint.Nature(UUID.randomUUID), 3)(_))
      val ep2 = hexapod1.addEndpoint(new LocalEndpoint(Endpoint.Direction.InOut, new LocalEndpoint.Nature(UUID.randomUUID), 1)(_))
      val ep3 = hexapod1.addEndpoint(new LocalEndpoint(Endpoint.Direction.InOut, new LocalEndpoint.Nature(UUID.randomUUID), 2)(_))
      val ep4 = hexapod1.addEndpoint(new LocalEndpoint(Endpoint.Direction.InOut, new LocalEndpoint.Nature(UUID.randomUUID), 3)(_))
      val ep5 = hexapod1.addEndpoint(new LocalEndpoint(Endpoint.Direction.InOut, new LocalEndpoint.Nature(UUID.randomUUID), 1)(_))
      val ep6 = hexapod1.addEndpoint(new LocalEndpoint(Endpoint.Direction.InOut, new LocalEndpoint.Nature(UUID.randomUUID), 2)(_))
      ep4.connect
      ep5.connect
      ep6.connect
      ep1 should not be (ep2)
      hexapod1.getEndpoints should be(Seq(ep4, ep6, ep5, ep1, ep3, ep2))
    }
  }

  override def beforeAll(configMap: Map[String, Any]) { adjustLoggingBeforeAll(configMap) }
}
