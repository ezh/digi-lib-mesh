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

package org.digimead.digi.lib.mesh.endpoint

import java.util.UUID

import scala.ref.WeakReference

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.lib.test.LoggingHelper
import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers

class LocalEndpointSpec extends FunSpec with ShouldMatchers with LoggingHelper with Loggable {
  after { adjustLoggingAfter }
  before {
    DependencyInjection(org.digimead.digi.lib.mesh.defaultFakeHexapod ~
      org.digimead.digi.lib.mesh.default ~ org.digimead.digi.lib.default, false)
    adjustLoggingBefore
  }

  describe("A LocalEndpoint") {
    it("should have (de)serialization via signature") {
      val epaddr = UUID.randomUUID()
      val ep = new LocalEndpoint(new WeakReference(null), Endpoint.Direction.InOut, new LocalEndpoint.Nature(epaddr), -1)
      ep.nature.address should be(epaddr.toString())
      ep.nature.toString should be("local://" + epaddr.toString())
      ep.signature should be("local'" + epaddr.toString() + "'-1'InOut'")
      ep.actualPriority should be(-1)
      val serialized = ep.signature
      val epCopy = Endpoint.fromSignature(null, serialized)
      epCopy.get.nature.toString should be(ep.nature.toString)
      epCopy.get.signature.toString should be(ep.signature.toString)
    }
  }

  override def beforeAll(configMap: Map[String, Any]) { adjustLoggingBeforeAll(configMap) }
}
